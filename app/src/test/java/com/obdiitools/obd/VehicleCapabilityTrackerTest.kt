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
}
