package com.superdl.launcher.transit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitRideAlertEngineTest {

    @Test
    fun missingLocationSamplesSuspendTrackingWithoutAdvancing() {
        val engine = engine()
        engine.board()
        val update = engine.markLocationUnavailable()
        assertEquals(TransitRideStatus.UNCERTAIN, update.status)
        assertEquals(0, update.currentStopIndex)
        assertFalse(update.prepareToAlightNow)
        assertFalse(update.arrivedNow)
    }

    @Test
    fun noGpsFixCanStartRideWithoutBoardingConfirmation() {
        val engine = engine()
        val update = engine.onLocation(fix(1, 100_000), 101_000)
        assertEquals(TransitRideStatus.NOT_BOARDED, update.status)
        assertEquals(null, update.currentStopIndex)
        assertFalse(update.prepareToAlightNow)
    }

    @Test
    fun previousStopWarningAndArrivalAreEachEmittedOnce() {
        val engine = engine()
        val boarded = engine.board()
        assertEquals(2, boarded.stopsRemaining)
        assertEquals(1, boarded.nextStopIndex)
        assertFalse(boarded.prepareToAlightNow)

        assertFalse(engine.onLocation(fix(1, 100_000), 101_000).prepareToAlightNow)
        val previous = engine.onLocation(fix(1, 102_500), 103_500)
        assertEquals(TransitRideStatus.TRACKING, previous.status)
        assertEquals(1, previous.currentStopIndex)
        assertEquals(1, previous.stopsRemaining)
        assertTrue(previous.prepareToAlightNow)
        assertFalse(engine.snapshot().prepareToAlightNow)

        assertFalse(engine.onLocation(fix(2, 105_500), 106_500).arrivedNow)
        val destination = engine.onLocation(fix(2, 108_000), 109_000)
        assertEquals(TransitRideStatus.ARRIVED, destination.status)
        assertEquals(0, destination.stopsRemaining)
        assertEquals(null, destination.nextStopIndex)
        assertTrue(destination.arrivedNow)
        assertFalse(engine.onLocation(fix(2, 111_000), 112_000).arrivedNow)
        assertFalse(engine.board().prepareToAlightNow)
    }

    @Test
    fun unreliableOrContradictoryFixCannotAdvanceOrAlert() {
        val engine = engine()
        engine.board()
        val poorAccuracy = engine.onLocation(fix(1, 100_000, accuracy = 80.0), 101_000)
        assertEquals(TransitRideStatus.UNCERTAIN, poorAccuracy.status)
        assertEquals(0, poorAccuracy.currentStopIndex)
        assertFalse(poorAccuracy.prepareToAlightNow)

        val stale = engine.onLocation(fix(1, 100_000), 120_000)
        assertEquals(TransitRideStatus.UNCERTAIN, stale.status)
        val skippedStop = engine.onLocation(fix(2, 121_000), 122_000)
        assertEquals(TransitRideStatus.UNCERTAIN, skippedStop.status)
        assertEquals(0, skippedStop.currentStopIndex)

        // An unreliable sample does not count toward the two-fix confirmation.
        val firstReliable = engine.onLocation(fix(1, 124_000), 125_000)
        assertEquals(TransitRideStatus.TRACKING, firstReliable.status)
        assertFalse(firstReliable.prepareToAlightNow)
        val confirmed = engine.onLocation(fix(1, 126_500), 127_500)
        assertTrue(confirmed.prepareToAlightNow)
    }

    @Test
    fun nearbyOppositePlatformIsAmbiguous() {
        val sequence = sequence(47.0, 47.0010, 47.0011, 47.0030)
        val engine = TransitRideAlertEngine(sequence, boardingStopIndex = 0, destinationStopIndex = 3)
        engine.board()
        val nearFirstPlatform = TransitLocationFix(47.0010, 19.0, 5.0, 100_000)
        val first = engine.onLocation(nearFirstPlatform, 101_000)
        assertEquals(TransitRideStatus.UNCERTAIN, first.status)
        assertEquals(0, first.currentStopIndex)
        assertFalse(first.prepareToAlightNow)

        engine.correctCurrentStop(1)
        val nearPreviousStop = TransitLocationFix(47.0011, 19.0, 5.0, 105_000)
        val second = engine.onLocation(nearPreviousStop, 106_000)
        assertEquals(TransitRideStatus.UNCERTAIN, second.status)
        assertFalse(second.prepareToAlightNow)
    }

    @Test
    fun implausibleGpsJumpCannotAdvance() {
        val engine = engine()
        engine.board()
        engine.onLocation(fix(0, 100_000), 101_000)
        val jumped = engine.onLocation(fix(1, 100_100), 101_100)
        assertEquals(TransitRideStatus.UNCERTAIN, jumped.status)
        assertEquals(0, jumped.currentStopIndex)
        assertFalse(jumped.prepareToAlightNow)
    }

    @Test
    fun manualCorrectionCanAdvanceAndBacktrackWithoutRepeatingAlert() {
        val engine = engine()
        engine.board()
        val previous = engine.correctCurrentStop(1)
        assertTrue(previous.prepareToAlightNow)
        assertFalse(engine.correctCurrentStop(1).prepareToAlightNow)
        assertFalse(engine.correctCurrentStop(0).prepareToAlightNow)
        assertFalse(engine.correctCurrentStop(1).prepareToAlightNow)
        val arrived = engine.correctCurrentStop(2)
        assertTrue(arrived.arrivedNow)
        assertFalse(engine.correctCurrentStop(2).arrivedNow)
        assertThrows(IllegalArgumentException::class.java) {
            engine.correctCurrentStop(3)
        }
    }

    @Test
    fun directNextStopDestinationWarnsWhenBoarded() {
        val engine = TransitRideAlertEngine(sequence(47.0, 47.001), 0, 1)
        assertFalse(engine.snapshot().prepareToAlightNow)
        val boarded = engine.board()
        assertTrue(boarded.prepareToAlightNow)
        assertFalse(engine.board().prepareToAlightNow)
    }

    @Test
    fun circularTripNeedsOccurrenceIndexAndMayRequireManualArrival() {
        val sequence = sequence(47.0, 47.001, 47.002, 47.0)
        val engine = TransitRideAlertEngine(sequence, 0, 3)
        engine.board()
        val previous = engine.correctCurrentStop(2)
        assertTrue(previous.prepareToAlightNow)
        val ambiguousReturn = engine.onLocation(
            TransitLocationFix(47.0, 19.0, 5.0, 100_000), 101_000
        )
        assertEquals(TransitRideStatus.UNCERTAIN, ambiguousReturn.status)
        assertFalse(ambiguousReturn.arrivedNow)
        assertTrue(engine.correctCurrentStop(3).arrivedNow)
    }

    @Test
    fun checkpointRestoreKeepsOneTimeWarningAfterProcessRestart() {
        val selectedTrip = sequence(47.0, 47.001, 47.002)
        val original = TransitRideAlertEngine(selectedTrip, 0, 2)
        assertNull(original.checkpoint())
        original.board()
        assertTrue(original.correctCurrentStop(1).prepareToAlightNow)
        val checkpoint = original.checkpoint()!!

        val restored = TransitRideAlertEngine.restore(selectedTrip, 0, 2, checkpoint)!!
        assertEquals(TransitRideStatus.UNCERTAIN, restored.snapshot().status)
        assertEquals(1, restored.snapshot().currentStopIndex)
        assertFalse(restored.snapshot().prepareToAlightNow)
        assertFalse(restored.correctCurrentStop(1).prepareToAlightNow)
        assertTrue(restored.correctCurrentStop(2).arrivedNow)

        val arrivedCheckpoint = restored.checkpoint()!!
        val afterSecondRestart = TransitRideAlertEngine.restore(
            selectedTrip, 0, 2, arrivedCheckpoint
        )!!
        assertEquals(TransitRideStatus.ARRIVED, afterSecondRestart.snapshot().status)
        assertFalse(afterSecondRestart.correctCurrentStop(2).arrivedNow)
    }

    @Test
    fun checkpointCannotBeRestoredForAnotherTripPatternOrStopSelection() {
        val selectedTrip = sequence(47.0, 47.001, 47.002)
        val engine = TransitRideAlertEngine(selectedTrip, 0, 2)
        engine.board()
        val checkpoint = engine.checkpoint()!!

        assertNull(TransitRideAlertEngine.restore(sequence(47.0, 47.001, 47.003), 0, 2, checkpoint))
        assertNull(TransitRideAlertEngine.restore(selectedTrip, 1, 2, checkpoint))
        assertNull(TransitRideAlertEngine.restore(selectedTrip, 0, 1, checkpoint))
        assertNull(TransitRideAlertEngine.restore(
            selectedTrip, 0, 2, checkpoint.copy(tripId = "different-trip")
        ))
        assertNull(TransitRideAlertEngine.restore(
            selectedTrip, 0, 2, checkpoint.copy(currentStopIndex = 99)
        ))
    }

    private fun engine() = TransitRideAlertEngine(sequence(47.0, 47.001, 47.002), 0, 2)

    private fun fix(index: Int, time: Long, accuracy: Double = 5.0) =
        TransitLocationFix(47.0 + index * 0.001, 19.0, accuracy, time)

    private fun sequence(vararg latitudes: Double): GtfsTripStopSequence {
        val route = GtfsRoute("R", "4", "4-es villamos", 0)
        val trip = GtfsTrip("T", "R", "WEEKDAY", "Központ", 0)
        return GtfsTripStopSequence(route, trip, latitudes.mapIndexed { index, latitude ->
            GtfsStopCall(
                GtfsStop("S$index", "Megálló $index", latitude, 19.0),
                stopSequence = index + 1,
                arrivalTime = null,
                departureTime = null
            )
        })
    }
}
