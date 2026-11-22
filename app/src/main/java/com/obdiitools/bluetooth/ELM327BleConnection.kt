package com.obdiitools.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import com.obdiitools.util.AppLogger
import com.obdiitools.obd.ELM327Protocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

@SuppressLint("MissingPermission")
class ELM327BleConnection(
    private val context: Context,
    private val device: BluetoothDevice,
) : OBDConnection {

    private val rxChannel = Channel<ByteArray>(capacity = 256)
    private val writeAck = Semaphore(0)
    // Released when CCCD write completes (or immediately if no CCCD needed)
    private val notifyReady = Semaphore(0)
    private val servicesReady = Semaphore(0)
    @Volatile private var initializationFailed = false
    @Volatile private var lastWriteStatus = BluetoothGatt.GATT_SUCCESS
    private var gatt: BluetoothGatt? = null
    @Volatile private var txChar: BluetoothGattCharacteristic? = null
    @Volatile private var rxChar: BluetoothGattCharacteristic? = null

    @Volatile private var connected = false
    @Volatile private var _lastByteReceivedMs: Long = System.currentTimeMillis()
    override val lastByteReceivedMs: Long get() = _lastByteReceivedMs
    override fun resetActivityTimer() { _lastByteReceivedMs = System.currentTimeMillis() }

    companion object {
        private const val TAG = "ELM327BLE"

        private data class BleProfile(val serviceUuid: UUID, val txUuid: UUID, val rxUuid: UUID)

        // Known UART-over-BLE profiles tried in order.
        // txUuid = write to adapter; rxUuid = notifications from adapter (may be the same char).
        private val KNOWN_PROFILES = listOf(
            // HM-10 / CC2541 — bidirectional single char (FFE1 has both WRITE and NOTIFY)
            BleProfile(
                serviceUuid = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB"),
                txUuid      = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB"),
                rxUuid      = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB"),
            ),
            // FFF0 profile — split: FFF2 write, FFF1 notify (e.g. Veepeak OBDCheck BLE+)
            BleProfile(
                serviceUuid = UUID.fromString("0000FFF0-0000-1000-8000-00805F9B34FB"),
                txUuid      = UUID.fromString("0000FFF2-0000-1000-8000-00805F9B34FB"),
                rxUuid      = UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB"),
            ),
        )
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        // ATZ is deliberately absent: on HM-10/CC2541 adapters ATZ resets the ELM327 MCU
        // which can also reset the BLE radio, dropping the GATT connection.
        // We rely on ATE0 etc. to put the chip into a known state without a full reset.
        private val BLE_INIT_COMMANDS = listOf(
            "ATE0",   // Echo off
            "ATL0",   // Linefeeds off
            "ATS0",   // Spaces off
            "ATH0",   // Headers off
            "ATAT2",  // Adaptive timing mode 2 — more lenient for BLE latency
            "ATSP0",  // Auto select OBD protocol
        )
    }

    @Volatile private var writeNoResponse = false

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            AppLogger.d(TAG, "onConnectionStateChange status=$status newState=$newState")
            connected = status == BluetoothGatt.GATT_SUCCESS &&
                newState == BluetoothProfile.STATE_CONNECTED
            if (connected) {
                if (!gatt.discoverServices()) {
                    initializationFailed = true
                    servicesReady.release()
                }
            } else {
                initializationFailed = true
                servicesReady.release()
                notifyReady.release()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            AppLogger.d(TAG, "onServicesDiscovered status=$status services=${gatt.services.map { it.uuid }}")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                initializationFailed = true
                servicesReady.release()
                notifyReady.release()
                return
            }

            // Try known profiles first (supports split TX/RX), then fall back to auto-detect.
            val (txC, rxC) = KNOWN_PROFILES.firstNotNullOfOrNull { profile ->
                val svc = gatt.getService(profile.serviceUuid) ?: return@firstNotNullOfOrNull null
                val tx  = svc.getCharacteristic(profile.txUuid)  ?: return@firstNotNullOfOrNull null
                val rx  = svc.getCharacteristic(profile.rxUuid)  ?: return@firstNotNullOfOrNull null
                tx to rx
            } ?: run {
                // Auto-detect: find a single characteristic with both write and notify.
                val bidirectional = gatt.services.firstNotNullOfOrNull { svc ->
                    svc.characteristics.firstOrNull { c ->
                        val p = c.properties
                        (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                         p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) &&
                         p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                    }
                } ?: run {
                    AppLogger.w(TAG, "No suitable characteristic found — services: ${gatt.services.map { svc -> svc.uuid to svc.characteristics.map { it.uuid to it.properties } }}")
                    initializationFailed = true
                    servicesReady.release()
                    notifyReady.release()
                    return
                }
                bidirectional to bidirectional
            }

            writeNoResponse = txC.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0 &&
                              txC.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            txChar = txC
            rxChar = rxC
            AppLogger.d(TAG, "Using tx=${txC.uuid} rx=${rxC.uuid} writeNoResponse=$writeNoResponse tx.properties=${txC.properties} rx.properties=${rxC.properties}")

            if (!gatt.setCharacteristicNotification(rxC, true)) {
                initializationFailed = true
                servicesReady.release()
                notifyReady.release()
                return
            }

            // Write CCCD on the RX characteristic immediately — don't wait for MTU.
            val descriptor = rxC.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                AppLogger.d(TAG, "Writing CCCD ENABLE_NOTIFICATION_VALUE")
                val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
                if (!started) {
                    initializationFailed = true
                    notifyReady.release()
                }
                // notifyReady released in onDescriptorWrite
            } else {
                AppLogger.d(TAG, "No CCCD descriptor — peripheral auto-notifies")
                notifyReady.release()
            }
            servicesReady.release()
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            AppLogger.d(TAG, "onDescriptorWrite status=$status descriptor=${descriptor.uuid}")
            if (status != BluetoothGatt.GATT_SUCCESS) initializationFailed = true
            notifyReady.release()
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            lastWriteStatus = status
            writeAck.release()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid != rxChar?.uuid) return
            @Suppress("DEPRECATION")
            val bytes = characteristic.value
            AppLogger.v(TAG, "onCharacteristicChanged (deprecated) ${bytes?.size ?: 0} bytes")
            if (bytes != null) rxChannel.trySend(bytes)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid != rxChar?.uuid) return
            AppLogger.v(TAG, "onCharacteristicChanged ${value.size} bytes: ${value.take(20).map { it.toInt().toChar() }.joinToString("")}")
            rxChannel.trySend(value)
        }
    }

    override val isConnected: Boolean get() = connected && gatt != null

    override suspend fun initialize(): Boolean {
        gatt = device.connectGatt(context, false, callback)

        // Service discovery and notification subscription must succeed before any command.
        val servicesDiscovered = servicesReady.tryAcquire(8000, TimeUnit.MILLISECONDS)
        if (!servicesDiscovered || initializationFailed || !isConnected || txChar == null) {
            AppLogger.w(TAG, "BLE service discovery failed or timed out")
            close()
            return false
        }
        val cccReady = notifyReady.tryAcquire(3000, TimeUnit.MILLISECONDS)
        if (!cccReady || initializationFailed) {
            AppLogger.w(TAG, "BLE notification subscription failed or timed out")
            close()
            return false
        }
        AppLogger.d(TAG, "BLE services and notifications ready; sending init sequence")

        // Configure the ELM327 — ATZ is intentionally skipped (see BLE_INIT_COMMANDS)
        for (cmd in BLE_INIT_COMMANDS) {
            sendRaw(cmd)
            Thread.sleep(300L)
            drainRxChannel()
        }

        // Force OBD protocol detection now (ATSP0 auto-selects on the first OBD request).
        // BLE latency + car wake-up can take 3–5 s; doing it here with a generous timeout
        // prevents querySupportedPids() from timing out on the first call.
        AppLogger.d(TAG, "Sending OBD wake-up probe (0100)…")
        sendRaw("0100")
        val probeLines = readResponseLines(5000)
        AppLogger.d(TAG, "OBD probe response: $probeLines")
        drainRxChannel()

        return true
    }

    override suspend fun query(command: String, timeoutMs: Long): String = withContext(Dispatchers.IO) {
        drainRxChannel()
        sendRaw(command)
        firstUsefulResponse(readResponseLines(timeoutMs))
    }

    override suspend fun queryLines(command: String, timeoutMs: Long): List<String> = withContext(Dispatchers.IO) {
        drainRxChannel()
        sendRaw(command)
        readResponseLines(timeoutMs)
    }

    override suspend fun queryUds(ecuAddress: String, did: String): String = withContext(Dispatchers.IO) {
        var headerChanged = false
        try {
            drainRxChannel()
            sendRaw("ATSH $ecuAddress")
            headerChanged = true
            Thread.sleep(150)
            drainRxChannel()
            sendRaw("22$did")
            // Preserve indexed continuation lines for the UDS parser.
            readResponseLines(3000).joinToString("\n")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            if (headerChanged) withContext(NonCancellable) {
                runCatching {
                    if (isConnected) {
                        sendRaw("ATSH 7DF")
                        Thread.sleep(100)
                        drainRxChannel()
                    }
                }
            }
        }
    }

    override suspend fun pingEcu(address: String): String = withContext(Dispatchers.IO) {
        drainRxChannel()
        sendRaw("ATSH $address")
        Thread.sleep(50)
        drainRxChannel()
        sendRaw("1001")
        val udsLines = readResponseLines(600)
        val udsResponse = udsLines.joinToString("\n")
        val udsEvidence = udsLines.any { line ->
            val frame = line.replace(Regex("(?i)^[0-9A-F]:"), "")
                .filterNot { it.isWhitespace() }.uppercase()
            frame.startsWith("5001") || frame.startsWith("7F10")
        }
        if (udsEvidence) {
            udsResponse
        } else {
            // Fallback for standard OBD ECUs that do not answer UDS session control.
            sendRaw("0100")
            readResponseLines(1000).joinToString("\n")
        }
    }

    override suspend fun queryFreezeFrame(pid: String): String = withContext(Dispatchers.IO) {
        val pidByte = pid.takeLast(2)
        drainRxChannel()
        sendRaw("02${pidByte}00")
        readResponseLines(1000).firstOrNull { it.uppercase().startsWith("42") } ?: ""
    }

    override suspend fun querySupportedPids(): Set<String> = withContext(Dispatchers.IO) {
        val supported = mutableSetOf<String>()
        val groups = listOf("0100", "0120", "0140", "0160", "0180", "01A0", "01C0")
        for (groupPid in groups) {
            val prefix = "41" + groupPid.takeLast(2).uppercase()
            val lines = queryLines(groupPid, 1200)
            val cleaned = lines
                .map { it.replace(" ", "").uppercase() }
                .firstOrNull { it.startsWith(prefix) } ?: continue
            if (cleaned.length < prefix.length + 8) continue
            val bitmask = cleaned.substring(prefix.length, prefix.length + 8).toLongOrNull(16) ?: continue
            val groupBase = groupPid.substring(2).toInt(16)
            for (bit in 0..31) {
                if ((bitmask and (1L shl (31 - bit))) != 0L) {
                    val pidNum = groupBase + bit + 1
                    supported.add("01%02X".format(pidNum))
                }
            }
            if ((bitmask and 1L) == 0L) break
        }
        supported
    }

    override fun canMonitorFlow(): Flow<String> = flow {
        val sb = StringBuilder()
        try {
            sendRaw("ATS1")
            Thread.sleep(100)
            drainRxChannel()
            sendRaw("ATH1")
            Thread.sleep(100)
            drainRxChannel()
            sendRaw("ATMA")
            while (currentCoroutineContext().isActive) {
                try {
                    val bytes = withTimeout(200) { rxChannel.receive() }
                    for (b in bytes) {
                        val c = b.toInt().toChar()
                        when {
                            c == '\n' || c == '\r' -> {
                                val line = sb.toString().trim()
                                if (line.isNotBlank() && !line.startsWith(">")) emit(line)
                                sb.clear()
                            }
                            c == '>' -> throw java.io.IOException("Adapter returned to command prompt; ATMA stopped")
                            else -> sb.append(c)
                        }
                    }
                } catch (_: TimeoutCancellationException) { /* no data in 200ms, continue */ }
                catch (_: ClosedReceiveChannelException) { break }
            }
        } finally {
            // Do not attempt adapter-state restoration after an unexpected disconnect.
            if (isConnected) {
                runCatching {
                    sendRaw("\r")
                    Thread.sleep(400)
                    drainRxChannel()
                    sendRaw("ATS0")
                    Thread.sleep(100)
                    drainRxChannel()
                    sendRaw("ATH0")
                    Thread.sleep(100)
                    drainRxChannel()
                }
            }
        }
    }

    override fun close() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        txChar = null
        rxChar = null
        connected = false
        rxChannel.close()
    }


    private fun firstUsefulResponse(lines: List<String>): String =
        lines.firstOrNull { line ->
            val normalized = line.filterNot { it.isWhitespace() }.uppercase()
            normalized.isNotEmpty() &&
                !normalized.startsWith("SEARCHING") &&
                !normalized.startsWith("BUSINIT") &&
                normalized != "OK"
        } ?: ""

    private fun sendRaw(command: String) {
        val char = txChar ?: throw java.io.IOException("BLE transmit characteristic is unavailable")
        val activeGatt = gatt ?: throw java.io.IOException("BLE GATT connection is closed")
        if (!connected) throw java.io.IOException("BLE adapter is disconnected")
        val bytes = "$command\r".toByteArray(Charsets.UTF_8)

        if (writeNoResponse) {
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activeGatt.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                char.value = bytes
                @Suppress("DEPRECATION")
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                activeGatt.writeCharacteristic(char)
            }
            if (!started) throw java.io.IOException("BLE write was rejected by the platform")
            return
        }

        writeAck.drainPermits()
        lastWriteStatus = BluetoothGatt.GATT_SUCCESS
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activeGatt.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            char.value = bytes
            @Suppress("DEPRECATION")
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            activeGatt.writeCharacteristic(char)
        }
        if (!started) throw java.io.IOException("BLE write was rejected by the platform")
        if (!writeAck.tryAcquire(500, TimeUnit.MILLISECONDS)) {
            throw java.io.IOException("BLE characteristic write timed out")
        }
        if (lastWriteStatus != BluetoothGatt.GATT_SUCCESS) {
            throw java.io.IOException("BLE characteristic write failed with status $lastWriteStatus")
        }
    }

    private fun readResponseLines(timeoutMs: Long): List<String> {
        val lines = mutableListOf<String>()
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val bytes = rxChannel.tryReceive().getOrNull() ?: run {
                Thread.sleep(5)
                null
            } ?: continue
            _lastByteReceivedMs = System.currentTimeMillis()
            for (b in bytes) {
                val c = b.toInt().toChar()
                when {
                    c == '>' -> {
                        val line = sb.toString().replace(" ", "").trim()
                        if (line.isNotBlank()) lines.add(line)
                        sb.clear()
                        return lines
                    }
                    c == '\r' || c == '\n' -> {
                        val line = sb.toString().replace(" ", "").trim()
                        if (line.isNotBlank()) lines.add(line)
                        sb.clear()
                    }
                    else -> sb.append(c)
                }
            }
        }
        return lines
    }

    private fun drainRxChannel() {
        while (rxChannel.tryReceive().isSuccess) { /* drain */ }
    }
}
