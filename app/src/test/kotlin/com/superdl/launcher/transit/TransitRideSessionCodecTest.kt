package com.superdl.launcher.transit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitRideSessionCodecTest {
    @Test
    fun selectedTripAndOccurrenceIndicesSurviveLocalRoundTrip() {
        val selected = TransitRideSession(sequence(), 0, 3, null)
        val decoded = TransitRideSessionCodec.decode(TransitRideSessionCodec.encode(selected))
        assertEquals(selected, decoded)
        assertEquals("S0", decoded!!.sequence.stops[0].stop.id)
        assertEquals("S0", decoded.sequence.stops[3].stop.id)
    }

    @Test
    fun checkpointSurvivesRestartWithoutRepeatingPreviousStopAlert() {
        val sequence = sequence()
        val engine = TransitRideAlertEngine(sequence, 0, 3)
        engine.board()
        assertTrue(engine.correctCurrentStop(2).prepareToAlightNow)
        val selected = TransitRideSession(sequence, 0, 3, engine.checkpoint())
        val decoded = TransitRideSessionCodec.decode(TransitRideSessionCodec.encode(selected))!!
        val restored = TransitRideAlertEngine.restore(
            decoded.sequence, decoded.boardingStopIndex, decoded.destinationStopIndex,
            decoded.checkpoint!!
        )!!
        assertEquals(TransitRideStatus.UNCERTAIN, restored.snapshot().status)
        assertFalse(restored.correctCurrentStop(2).prepareToAlightNow)
        assertTrue(restored.correctCurrentStop(3).arrivedNow)
    }

    @Test
    fun truncatedVersionMismatchAndTrailingDataAreRejected() {
        val bytes = TransitRideSessionCodec.encode(TransitRideSession(sequence(), 0, 3, null))
        assertNull(TransitRideSessionCodec.decode(bytes.copyOf(7)))
        assertNull(TransitRideSessionCodec.decode(bytes.copyOf().also { it[3] = 2 }))
        assertNull(TransitRideSessionCodec.decode(bytes + byteArrayOf(1)))
    }

    @Test
    fun inconsistentSelectionOrCheckpointCannotBeStored() {
        val sequence = sequence()
        assertThrows(IllegalArgumentException::class.java) {
            TransitRideSessionCodec.encode(TransitRideSession(sequence, 3, 0, null))
        }
        val engine = TransitRideAlertEngine(sequence, 0, 3)
        engine.board()
        val wrongTrip = engine.checkpoint()!!.copy(tripId = "other")
        assertThrows(IllegalArgumentException::class.java) {
            TransitRideSessionCodec.encode(TransitRideSession(sequence, 0, 3, wrongTrip))
        }
    }

    private fun sequence(): GtfsTripStopSequence {
        val route = GtfsRoute("R", "4", "4-es villamos", 0)
        val trip = GtfsTrip("T", "R", "WEEKDAY", "Központ", 0)
        return GtfsTripStopSequence(route, trip, listOf(
            call("S0", "Kiindulás", 47.0, 1),
            call("S1", "Első", 47.001, 2),
            call("S2", "Cél előtt", 47.002, 3),
            call("S0", "Kiindulás", 47.0, 4)
        ))
    }

    private fun call(id: String, name: String, latitude: Double, occurrence: Int) =
        GtfsStopCall(
            GtfsStop(id, name, latitude, 19.0), occurrence,
            arrivalTime = "12:00:00", departureTime = "12:01:00"
        )
}
