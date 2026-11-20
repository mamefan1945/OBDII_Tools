package com.obdiitools.obd

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CapabilityStatus {
    DISCOVERING,
    CONFIRMED,
    INTERMITTENT,
    UNAVAILABLE,
}

data class PidCapability(
    val pid: String,
    val status: CapabilityStatus,
    val successfulSamples: Int = 0,
    val consecutiveFailures: Int = 0,
)

/**
 * Learns actual vehicle support from decoded live samples, not from support
 * bitmaps alone. Two successful samples confirm a PID; three consecutive
 * failed reads mark it unavailable. A single missed poll does not remove a
 * working dashboard item.
 */
class VehicleCapabilityTracker(
    private val samplesToConfirm: Int = 2,
    private val failuresToDisable: Int = 3,
) {
    private val mutableCapabilities = MutableStateFlow<Map<String, PidCapability>>(emptyMap())
    val capabilities: StateFlow<Map<String, PidCapability>> = mutableCapabilities.asStateFlow()

    val confirmedPids: Set<String>
        get() = mutableCapabilities.value.values
            .filter { it.status == CapabilityStatus.CONFIRMED || it.status == CapabilityStatus.INTERMITTENT }
            .map { it.pid }
            .toSet()

    init {
        require(samplesToConfirm >= 1)
        require(failuresToDisable >= 1)
    }

    @Synchronized
    fun record(pid: String, value: Any?) {
        val key = pid.uppercase()
        if (!key.matches(Regex("^[0-9A-F]{4}$"))) return
        val previous = mutableCapabilities.value[key]
            ?: PidCapability(key, CapabilityStatus.DISCOVERING)
        val valid = when (value) {
            is Float -> value.isFinite()
            is Double -> value.isFinite()
            is Int, is Long, is Short, is Byte -> true
            else -> false
        }
        val next = if (valid) {
            val successes = previous.successfulSamples + 1
            PidCapability(
                pid = key,
                status = if (successes >= samplesToConfirm) CapabilityStatus.CONFIRMED else CapabilityStatus.DISCOVERING,
                successfulSamples = successes,
                consecutiveFailures = 0,
            )
        } else {
            val failures = previous.consecutiveFailures + 1
            val wasUsable = previous.status == CapabilityStatus.CONFIRMED ||
                previous.status == CapabilityStatus.INTERMITTENT
            PidCapability(
                pid = key,
                status = when {
                    failures >= failuresToDisable -> CapabilityStatus.UNAVAILABLE
                    wasUsable -> CapabilityStatus.INTERMITTENT
                    else -> CapabilityStatus.DISCOVERING
                },
                successfulSamples = previous.successfulSamples,
                consecutiveFailures = failures,
            )
        }
        mutableCapabilities.value = mutableCapabilities.value + (key to next)
    }

    @Synchronized
    fun reset() {
        mutableCapabilities.value = emptyMap()
    }
}
