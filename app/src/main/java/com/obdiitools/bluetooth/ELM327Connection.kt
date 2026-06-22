package com.obdiitools.bluetooth

import android.bluetooth.BluetoothSocket
import com.obdiitools.obd.ELM327Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class ELM327Connection(private val socket: BluetoothSocket) : OBDConnection {

    private val outputStream: OutputStream = socket.outputStream
    private val inputStream: InputStream   = socket.inputStream

    @Volatile private var _lastByteReceivedMs: Long = System.currentTimeMillis()
    override val lastByteReceivedMs: Long get() = _lastByteReceivedMs
    override fun resetActivityTimer() { _lastByteReceivedMs = System.currentTimeMillis() }

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            for (cmd in ELM327Protocol.INIT_COMMANDS) {
                sendRaw(cmd)
                val waitMs = if (cmd == "ATZ") 1200L else 300L
                Thread.sleep(waitMs)
                drainInput()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun query(command: String, timeoutMs: Long): String = withContext(Dispatchers.IO) {
        try {
            sendRaw(command)
            readResponseLines(timeoutMs).firstOrNull() ?: ""
        } catch (e: java.io.IOException) {
            throw e  // let polling loop detect disconnection
        } catch (e: Exception) {
            ""
        }
    }

    override suspend fun queryLines(command: String, timeoutMs: Long): List<String> = withContext(Dispatchers.IO) {
        try {
            sendRaw(command)
            readResponseLines(timeoutMs)
        } catch (e: java.io.IOException) {
            throw e  // let polling loop detect disconnection
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun queryUds(ecuAddress: String, did: String): String = withContext(Dispatchers.IO) {
        try {
            // Point at the target ECU
            sendRaw("ATSH $ecuAddress")
            Thread.sleep(150)
            drainInput()
            // UDS Mode 22: ReadDataByIdentifier — 3000 ms allows multi-frame reassembly
            sendRaw("22$did")
            val lines = readResponseLines(timeoutMs = 3000)
            // For multi-frame ISO-TP responses the ELM327 may emit the total-length
            // byte on the first line; search all lines for the UDS response prefix.
            val response = lines.firstOrNull { l ->
                val u = l.replace(" ", "").uppercase()
                u.startsWith("62") || u.startsWith("7F")
            } ?: lines.firstOrNull() ?: ""
            // Always restore broadcast address so normal polling is unaffected
            sendRaw("ATSH 7DF")
            Thread.sleep(100)
            drainInput()
            response
        } catch (e: Exception) {
            runCatching {
                sendRaw("ATSH 7DF")
                Thread.sleep(100)
                drainInput()
            }
            ""
        }
    }

    override suspend fun pingEcu(address: String): String = withContext(Dispatchers.IO) {
        try {
            drainInput()
            sendRaw("ATSH $address")
            Thread.sleep(50)
            drainInput()
            sendRaw("1001")
            readResponseLines(timeoutMs = 200).firstOrNull() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    override suspend fun queryFreezeFrame(pid: String): String = withContext(Dispatchers.IO) {
        try {
            val pidByte = pid.takeLast(2)
            sendRaw("02${pidByte}00")
            readResponseLines(timeoutMs = 1000)
                .firstOrNull { it.uppercase().startsWith("42") } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    override suspend fun querySupportedPids(): Set<String> = withContext(Dispatchers.IO) {
        val supported = mutableSetOf<String>()
        val groups = listOf("0100", "0120", "0140", "0160", "0180", "01A0", "01C0")
        for (groupPid in groups) {
            val prefix = "41" + groupPid.takeLast(2).uppercase()
            // Use queryLines so that "SEARCHING..." or other status lines on the first
            // OBD query after init don't hide the real bitmask response.
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
            // Bit 0 of the bitmask = "next group supported" flag
            if ((bitmask and 1L) == 0L) break
        }
        supported
    }

    /**
     * Streams raw CAN frames via ELM327 ATMA (Monitor All).
     * Temporarily enables headers (ATH1) and spaces (ATS1) for parseable output.
     * Cancelled when the collecting coroutine is cancelled; restores ELM327 state in finally.
     */
    override fun canMonitorFlow(): Flow<String> = flow {
        sendRaw("ATS1")
        Thread.sleep(100)
        drainInput()
        sendRaw("ATH1")
        Thread.sleep(100)
        drainInput()
        sendRaw("ATMA")

        val sb = StringBuilder()
        try {
            while (currentCoroutineContext().isActive) {
                if (inputStream.available() > 0) {
                    val b = inputStream.read()
                    if (b == -1) break
                    val c = b.toChar()
                    when {
                        c == '\n' || c == '\r' -> {
                            val line = sb.toString().trim()
                            if (line.isNotBlank() && !line.startsWith(">")) emit(line)
                            sb.clear()
                        }
                        c == '>' -> { /* prompt — ATMA still active, ignore */ }
                        else -> sb.append(c)
                    }
                } else {
                    delay(5)
                }
            }
        } finally {
            runCatching {
                // Any byte stops ATMA
                outputStream.write('\r'.code)
                outputStream.flush()
                Thread.sleep(400)
                drainInput()
                sendRaw("ATS0")
                Thread.sleep(100)
                drainInput()
                sendRaw("ATH0")
                Thread.sleep(100)
                drainInput()
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun sendRaw(command: String) {
        outputStream.write("$command\r".toByteArray(Charsets.UTF_8))
        outputStream.flush()
    }

    private fun readResponseLines(timeoutMs: Long): List<String> {
        val lines = mutableListOf<String>()
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        try {
            while (System.currentTimeMillis() < deadline) {
                if (inputStream.available() > 0) {
                    val b = inputStream.read()
                    if (b == -1) break
                    _lastByteReceivedMs = System.currentTimeMillis()
                    val c = b.toChar()
                    when {
                        c == '>' -> {
                            val line = sb.toString().replace(" ", "").trim()
                            if (line.isNotBlank()) lines.add(line)
                            sb.clear()
                            break
                        }
                        c == '\r' || c == '\n' -> {
                            val line = sb.toString().replace(" ", "").trim()
                            if (line.isNotBlank()) lines.add(line)
                            sb.clear()
                        }
                        else -> sb.append(c)
                    }
                } else {
                    Thread.sleep(5)
                }
            }
        } catch (e: java.io.IOException) {
            throw e  // propagate so polling loop can detect disconnection
        } catch (e: Exception) {
            // InterruptedException or other non-I/O errors
        }
        return lines
    }

    private fun drainInput() {
        try {
            val deadline = System.currentTimeMillis() + 1500
            while (System.currentTimeMillis() < deadline) {
                if (inputStream.available() > 0) {
                    val b = inputStream.read()
                    if (b == -1 || b.toChar() == '>') return
                } else {
                    Thread.sleep(20)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }

    override val isConnected: Boolean get() = socket.isConnected
}
