package com.obdiitools.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.obdiitools.util.AppLogger
import com.obdiitools.obd.ELM327Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
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
    private var gatt: BluetoothGatt? = null
    @Volatile private var txChar: BluetoothGattCharacteristic? = null

    @Volatile private var connected = false
    @Volatile private var _lastByteReceivedMs: Long = System.currentTimeMillis()
    override val lastByteReceivedMs: Long get() = _lastByteReceivedMs
    override fun resetActivityTimer() { _lastByteReceivedMs = System.currentTimeMillis() }

    companion object {
        private const val TAG = "ELM327BLE"

        // Known UART-over-BLE service/characteristic pairs (tried in order)
        private val KNOWN_PROFILES = listOf(
            // HM-10 / CC2541 — most common cheap ELM327 BLE adapters (e.g. Veepeak OBDCheck BLE)
            UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB") to
                    UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB"),
            // Alternative UART profile used by some adapters
            UUID.fromString("0000FFF0-0000-1000-8000-00805F9B34FB") to
                    UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB"),
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
            connected = newState == BluetoothProfile.STATE_CONNECTED
            if (connected) gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            AppLogger.d(TAG, "onServicesDiscovered status=$status services=${gatt.services.map { it.uuid }}")
            // Try known UART-over-BLE profiles first, then fall back to auto-detect
            val char = KNOWN_PROFILES.firstNotNullOfOrNull { (svcUuid, charUuid) ->
                gatt.getService(svcUuid)?.getCharacteristic(charUuid)
            } ?: gatt.services.firstNotNullOfOrNull { svc ->
                svc.characteristics.firstOrNull { c ->
                    val p = c.properties
                    (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                     p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) &&
                     p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                }
            } ?: run {
                AppLogger.w(TAG, "No suitable characteristic found — services: ${gatt.services.map { svc -> svc.uuid to svc.characteristics.map { it.uuid } }}")
                return
            }

            writeNoResponse = char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0 &&
                              char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            txChar = char
            AppLogger.d(TAG, "Using char=${char.uuid} writeNoResponse=$writeNoResponse properties=${char.properties}")
            gatt.setCharacteristicNotification(char, true)

            // Write CCCD immediately — don't request MTU first.
            // Many HM-10/CC2541 adapters (e.g. Veepeak OBDCheck BLE) never call
            // onMtuChanged, so chaining CCCD on it leaves notifications dead.
            val descriptor = char.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                AppLogger.d(TAG, "Writing CCCD ENABLE_NOTIFICATION_VALUE")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
                // notifyReady released in onDescriptorWrite
            } else {
                AppLogger.d(TAG, "No CCCD descriptor — peripheral auto-notifies")
                notifyReady.release()
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            AppLogger.d(TAG, "onDescriptorWrite status=$status descriptor=${descriptor.uuid}")
            notifyReady.release()
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            writeAck.release()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
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
            AppLogger.v(TAG, "onCharacteristicChanged ${value.size} bytes: ${value.take(20).map { it.toInt().toChar() }.joinToString("")}")
            rxChannel.trySend(value)
        }
    }

    override val isConnected: Boolean get() = connected && gatt != null

    override suspend fun initialize(): Boolean {
        gatt = device.connectGatt(context, false, callback)

        // Wait for service discovery — BLE can take longer than classic BT
        var waited = 0
        while (txChar == null && waited < 8000 && currentCoroutineContext().isActive) {
            Thread.sleep(100)
            waited += 100
        }
        if (txChar == null) {
            AppLogger.w(TAG, "Service discovery timed out after ${waited}ms")
            return false
        }
        AppLogger.d(TAG, "txChar ready after ${waited}ms, waiting for CCCD…")

        // Wait for CCCD write to complete before sending any commands
        val cccReady = notifyReady.tryAcquire(3000, TimeUnit.MILLISECONDS)
        AppLogger.d(TAG, "CCCD ready=$cccReady, sending BLE init sequence")

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
        readResponseLines(timeoutMs).firstOrNull() ?: ""
    }

    override suspend fun queryLines(command: String, timeoutMs: Long): List<String> = withContext(Dispatchers.IO) {
        drainRxChannel()
        sendRaw(command)
        readResponseLines(timeoutMs)
    }

    override suspend fun queryUds(ecuAddress: String, did: String): String = withContext(Dispatchers.IO) {
        drainRxChannel()
        sendRaw("ATSH $ecuAddress")
        Thread.sleep(150)
        drainRxChannel()
        sendRaw("22$did")
        val lines = readResponseLines(3000)
        val response = lines.firstOrNull { l ->
            val u = l.replace(" ", "").uppercase()
            u.startsWith("62") || u.startsWith("7F")
        } ?: lines.firstOrNull() ?: ""
        sendRaw("ATSH 7DF")
        Thread.sleep(100)
        drainRxChannel()
        response
    }

    override suspend fun pingEcu(address: String): String = withContext(Dispatchers.IO) {
        drainRxChannel()
        sendRaw("ATSH $address")
        Thread.sleep(50)
        drainRxChannel()
        sendRaw("1001")
        readResponseLines(200).firstOrNull() ?: ""
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
                            c == '>' -> { /* prompt */ }
                            else -> sb.append(c)
                        }
                    }
                } catch (_: TimeoutCancellationException) { /* no data in 200ms, continue */ }
            }
        } finally {
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

    override fun close() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        connected = false
    }

    private fun sendRaw(command: String) {
        val char = txChar ?: return
        val bytes = "$command\r".toByteArray(Charsets.UTF_8)
        if (writeNoResponse) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt?.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            } else {
                @Suppress("DEPRECATION")
                char.value = bytes
                @Suppress("DEPRECATION")
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                gatt?.writeCharacteristic(char)
            }
            // No-response writes fire-and-forget; no ACK callback is issued.
        } else {
            writeAck.drainPermits()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt?.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                char.value = bytes
                @Suppress("DEPRECATION")
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                gatt?.writeCharacteristic(char)
            }
            writeAck.tryAcquire(500, TimeUnit.MILLISECONDS)
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
