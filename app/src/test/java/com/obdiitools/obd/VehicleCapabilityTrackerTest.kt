package com.obdiitools.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleCapabilityTrackerTest {
    @Test
    fun confirmsOnlyAfterTwoDecodedSamples() {
        val tracker = VehicleCapabilityTracker()
        tracker.record("010C", 850)
        assertFalse("one sample must not confirm support", "010C" in tracker.confirmedPids)

        tracker.record("010C", 900)
        assertTrue("two decoded samples confirm support", "010C" in tracker.confirmedPids)
        assertEquals(CapabilityStatus.CONFIRMED, tracker.capabilities.value["010C"]?.status)
    }

    @Test
    fun toleratesTransientMissButDisablesAfterThreeConsecutiveFailures() {
        val tracker = VehicleCapabilityTracker()
        tracker.record("010D", 42)
        tracker.record("010D", 43)
        tracker.record("010D", null)
        assertTrue("one missed poll must not remove a confirmed PID", "010D" in tracker.confirmedPids)
        assertEquals(CapabilityStatus.INTERMITTENT, tracker.capabilities.value["010D"]?.status)

        tracker.record("010D", null)
        assertTrue("two misses remain intermittent", "010D" in tracker.confirmedPids)
        tracker.record("010D", null)
        assertFalse("three consecutive misses disable the PID", "010D" in tracker.confirmedPids)
        assertEquals(CapabilityStatus.UNAVAILABLE, tracker.capabilities.value["010D"]?.status)
    }

    @Test
    fun successfulSampleResetsFailureStreak() {
        val tracker = VehicleCapabilityTracker()
        tracker.record("0110", 12.5f)
        tracker.record("0110", 13.0f)
        tracker.record("0110", null)
        tracker.record("0110", 14.0f)

        assertTrue("a good sample resets the failure streak", "0110" in tracker.confirmedPids)
        assertEquals(0, tracker.capabilities.value["0110"]?.consecutiveFailures)
    }

    @Test
    fun resetClearsLearnedCapabilities() {
        val tracker = VehicleCapabilityTracker()
        tracker.record("010C", 1000)
        tracker.record("010C", 1200)
        tracker.reset()

        assertTrue(tracker.confirmedPids.isEmpty())
        assertTrue(tracker.capabilities.value.isEmpty())
    }

    @Test
    fun adaptiveSchedulerPollsAllVerifiedSensorsFairly() {
        val scheduler = AdaptivePidPollingScheduler(maxRequestsPerPass = 2)
        val supported = setOf("010C", "0105", "0110", "0106")
        val confirmed = supported
        val capabilities = supported.associateWith {
            PidCapability(it, CapabilityStatus.CONFIRMED, successfulSamples = 2)
        }

        val first = scheduler.selectDue(
            supported, confirmed, capabilities, emptyMap(), nowMs = 10_000L
        )
        assertEquals(2, first.size)

        val lastPolled = first.associateWith { 10_000L }
        val second = scheduler.selectDue(
            supported, confirmed, capabilities, lastPolled, nowMs = 10_000L
        )
        assertEquals(2, second.size)
        assertTrue("a later pass must select the remaining due sensors", second.none { it in first })
    }

    @Test
    fun adaptiveSchedulerRetriesUnavailableSensorsButRespectsRetryDelay() {
        val scheduler = AdaptivePidPollingScheduler()
        val pid = "010C"
        val capabilities = mapOf(
            pid to PidCapability(pid, CapabilityStatus.UNAVAILABLE, consecutiveFailures = 3)
        )
        val supported = setOf(pid)

        assertTrue(
            scheduler.selectDue(supported, emptySet(), capabilities, mapOf(pid to 1_000L), 20_000L).isEmpty()
        )
        assertEquals(
            listOf(pid),
            scheduler.selectDue(supported, emptySet(), capabilities, mapOf(pid to 1_000L), 31_000L)
        )
    }

    @Test
    fun adaptiveSchedulerSamplesAdvertisedSensorsWhileTheyAreLearning() {
        val scheduler = AdaptivePidPollingScheduler()
        val supported = setOf("010C")
        assertEquals(
            listOf("010C"),
            scheduler.selectDue(supported, emptySet(), emptyMap(), emptyMap(), nowMs = 1_000L)
        )
        assertTrue(
            scheduler.selectDue(supported, emptySet(), emptyMap(), mapOf("010C" to 1_000L), nowMs = 2_000L).isEmpty()
        )
    }

}
