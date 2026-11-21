package com.obdiitools.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MazdaCx5ProtocolTest {
    private fun response(did: String, payload: String) =
        UdsResponse.parse("7E1", did, "62$did$payload")

    @Test
    fun decodesForwardAndNonForwardGearStates() {
        assertEquals("1", MazdaCx5Protocol.parseGear(response("1E12", "01")))
        assertEquals("6", MazdaCx5Protocol.parseGear(response("1E12", "06")))
        assertEquals("N", MazdaCx5Protocol.parseGear(response("1E12", "50")))
        assertEquals("R", MazdaCx5Protocol.parseGear(response("1E12", "60")))
        assertEquals("P", MazdaCx5Protocol.parseGear(response("1E12", "70")))
        assertNull(MazdaCx5Protocol.parseGear(response("1E12", "00")))
        assertNull(MazdaCx5Protocol.parseGear(response("1E12", "08")))
    }

    @Test
    fun decodesSignedSixteenthDegreeTemperature() {
        assertEquals(20.9375, MazdaCx5Protocol.parseTransmissionTemperatureC(response("1E1C", "014F"))!!, 0.00001)
        assertEquals(-1.0, MazdaCx5Protocol.parseTransmissionTemperatureC(response("1E1C", "FFF0"))!!, 0.00001)
    }

    @Test
    fun rejectsWrongDidNegativeAndShortResponses() {
        assertNull(MazdaCx5Protocol.parseGear(response("1E1C", "01")))
        assertNull(MazdaCx5Protocol.parseGear(UdsResponse.parse("7E1", "1E12", "7F2211")))
        assertNull(MazdaCx5Protocol.parseTransmissionTemperatureC(response("1E1C", "01")))
    }
    @Test
    fun discoveryCandidatesAreBoundedUniqueAndIncludeVerifiedBaseline() {
        val candidates = MazdaCx5Protocol.TCM_DISCOVERY_CANDIDATES
        assertEquals(8, candidates.size)
        assertEquals(candidates.size, candidates.map { it.did }.toSet().size)
        assertEquals("1E12", candidates[0].did)
        assertEquals("1E1C", candidates[1].did)
        assertEquals(true, candidates[0].vehicleVerified)
        assertEquals(true, candidates[1].vehicleVerified)
        assertEquals(false, candidates.drop(2).any { it.vehicleVerified })
        candidates.forEach { candidate ->
            assertEquals(4, candidate.did.length)
            assert(candidate.did.all { it in '0'..'9' || it in 'A'..'F' })
        }
    }

    @Test
    fun parsesMultiFrameMazdaTemperatureResponse() {
        val raw = "SEARCHING...\n0:621E1C01\n1:4F"
        val parsed = UdsResponse.parse("7E1", "1E1C", raw)
        assertEquals(true, parsed.isSuccess)
        assertEquals(20.9375, MazdaCx5Protocol.parseTransmissionTemperatureC(parsed)!!, 0.00001)
    }

    @Test
    fun candidateVerificationMetadataDoesNotPromoteCatalogueLeads() {
        val verified = MazdaCx5Protocol.TCM_DISCOVERY_CANDIDATES.filter { it.vehicleVerified }
        assertEquals(listOf("1E12", "1E1C"), verified.map { it.did })
    }

    @Test
    fun parsesPositiveAndNegativeDiscoveryRepliesWithoutInventingValues() {
        val positive = UdsResponse.parse("7E1", "163A", "62163A7F")
        assertEquals(true, positive.isSuccess)
        assertEquals("7F", positive.dataHex)
        val negative = UdsResponse.parse("7E1", "163A", "7F2231")
        assertEquals(false, negative.isSuccess)
        assertEquals("31", negative.errorCode)
    }

}
