package com.obdiitools.bluetooth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes all ELM327 I/O. Compound operations use withTransaction so
 * temporary ECU headers and their restoration cannot interleave with polling.
 */
class SerializedOBDConnection(
    private val delegate: OBDConnection,
) : OBDConnection {
    private val mutex = Mutex()

    override val isConnected: Boolean get() = delegate.isConnected
    override val lastByteReceivedMs: Long get() = delegate.lastByteReceivedMs
    override fun resetActivityTimer() = delegate.resetActivityTimer()

    override suspend fun <T> withTransaction(block: suspend OBDConnection.() -> T): T =
        mutex.withLock { delegate.block() }

    override suspend fun initialize(): Boolean = withTransaction { initialize() }
    override suspend fun query(command: String, timeoutMs: Long): String =
        withTransaction { query(command, timeoutMs) }
    override suspend fun queryLines(command: String, timeoutMs: Long): List<String> =
        withTransaction { queryLines(command, timeoutMs) }
    override suspend fun queryUds(ecuAddress: String, did: String): String =
        withTransaction { queryUds(ecuAddress, did) }
    override suspend fun pingEcu(address: String): String =
        withTransaction { pingEcu(address) }
    override suspend fun queryFreezeFrame(pid: String): String =
        withTransaction { queryFreezeFrame(pid) }
    override suspend fun querySupportedPids(): Set<String> =
        withTransaction { querySupportedPids() }

    override fun canMonitorFlow(): Flow<String> = flow {
        mutex.withLock {
            delegate.canMonitorFlow().collect { emit(it) }
        }
    }

    override fun close() = delegate.close()
}
