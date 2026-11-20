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
            val wasUsable = previous.status == CapabilityStatus.CONFIRMED ||
                previous.status == CapabilityStatus.INTERMITTENT
            PidCapability(
                pid = key,
                status = when {
                    successes >= samplesToConfirm -> CapabilityStatus.CONFIRMED
                    wasUsable -> CapabilityStatus.INTERMITTENT
                    else -> CapabilityStatus.DISCOVERING
                },
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
                successfulSamples = 0,
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

/**
 * Selects a bounded set of verified (or still-learning) standard PIDs for each
 * background polling pass. Due-time ordering prevents slow groups from starving:
 * a sensor that has waited longer relative to its target interval moves forward.
 */
class AdaptivePidPollingScheduler(
    private val maxRequestsPerPass: Int = 4,
) {
    private data class Candidate(
        val pid: String,
        val overdueRatio: Double,
        val priority: Int,
    )

    init {
        require(maxRequestsPerPass >= 1)
    }

    fun selectDue(
        supportedPids: Set<String>,
        confirmedPids: Set<String>,
        capabilities: Map<String, PidCapability>,
        lastPolledAtMs: Map<String, Long>,
        nowMs: Long,
    ): List<String> {
        val definitions = AllPidDefinitions.ALL.associateBy { it.command }
        val candidates = supportedPids.asSequence()
            .map { it.uppercase() }
            .distinct()
            .mapNotNull { pid ->
                val definition = definitions[pid] ?: return@mapNotNull null
                val status = capabilities[pid]?.status ?: CapabilityStatus.DISCOVERING
                val intervalMs = when {
                    status == CapabilityStatus.UNAVAILABLE -> UNAVAILABLE_RETRY_MS
                    status == CapabilityStatus.DISCOVERING -> DISCOVERY_INTERVAL_MS
                    definition.category == PidCategory.ENGINE ||
                        definition.category == PidCategory.THROTTLE_LOAD -> FAST_INTERVAL_MS
                    definition.category == PidCategory.TEMPERATURE ||
                        definition.category == PidCategory.AIR_PRESSURE -> MEDIUM_INTERVAL_MS
                    else -> SLOW_INTERVAL_MS
                }
                val isConfirmed = pid in confirmedPids ||
                    status == CapabilityStatus.CONFIRMED ||
                    status == CapabilityStatus.INTERMITTENT
                // Newly advertised sensors are sampled slowly until verified.
                // Unavailable sensors are periodically retried so recovery is possible.
                if (!isConfirmed && status != CapabilityStatus.DISCOVERING &&
                    status != CapabilityStatus.UNAVAILABLE) return@mapNotNull null
                val last = lastPolledAtMs[pid]
                val age = if (last == null) Long.MAX_VALUE else (nowMs - last).coerceAtLeast(0L)
                if (last != null && age < intervalMs) return@mapNotNull null
                val ratio = if (last == null) Double.MAX_VALUE else age.toDouble() / intervalMs
                val priority = when (definition.category) {
                    PidCategory.ENGINE, PidCategory.THROTTLE_LOAD -> 0
                    PidCategory.TEMPERATURE, PidCategory.AIR_PRESSURE -> 1
                    else -> 2
                }
                Candidate(pid, ratio, priority)
            }
            .sortedWith(
                compareByDescending<Candidate> { it.overdueRatio }
                    .thenBy { it.priority }
                    .thenBy { it.pid }
            )
            .take(maxRequestsPerPass)
            .map { it.pid }
            .toList()
        return candidates
    }

    companion object {
        const val FAST_INTERVAL_MS = 1_500L
        const val MEDIUM_INTERVAL_MS = 3_000L
        const val SLOW_INTERVAL_MS = 8_000L
        const val DISCOVERY_INTERVAL_MS = 4_000L
        const val UNAVAILABLE_RETRY_MS = 30_000L
    }
}

