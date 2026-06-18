package com.obdiitools.bluetooth

import kotlinx.coroutines.flow.Flow

interface OBDConnection {
    val isConnected: Boolean
    suspend fun initialize(): Boolean
    suspend fun query(command: String, timeoutMs: Long = 500): String
    suspend fun queryLines(command: String, timeoutMs: Long = 1000): List<String>
    suspend fun queryUds(ecuAddress: String, did: String): String
    suspend fun pingEcu(address: String): String
    suspend fun queryFreezeFrame(pid: String): String
    suspend fun querySupportedPids(): Set<String>
    fun canMonitorFlow(): Flow<String>
    fun close()
}
