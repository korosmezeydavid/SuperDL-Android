package com.superdl.launcher.route

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsRouteDirectionTrackerTest {
    @Test fun initialDirectionIsUnknownUntilSmallForwardStepsConfirmIt() {
        val tracker = GpsRouteDirectionTracker()
        assertEquals(GpsRouteTravelDirection.UNKNOWN, tracker.observe(5))
        assertEquals(GpsRouteTravelDirection.UNKNOWN, tracker.observe(6))
        assertEquals(GpsRouteTravelDirection.UNKNOWN, tracker.observe(7))
        assertEquals(GpsRouteTravelDirection.FORWARD, tracker.observe(8))
    }

    @Test fun reverseCanBeConfirmedOneIndexAtATime() {
        val tracker = GpsRouteDirectionTracker()
        assertEquals(GpsRouteTravelDirection.UNKNOWN, tracker.observe(8))
        assertEquals(GpsRouteTravelDirection.UNKNOWN, tracker.observe(7))
        assertEquals(GpsRouteTravelDirection.UNKNOWN, tracker.observe(6))
        assertEquals(GpsRouteTravelDirection.REVERSE, tracker.observe(5))
    }

    @Test fun oneOppositeJitterDoesNotReverseAConfirmedDirection() {
        val tracker = GpsRouteDirectionTracker()
        listOf(1, 2, 3, 4).forEach { tracker.observe(it) }
        assertEquals(GpsRouteTravelDirection.FORWARD, tracker.observe(3))
        assertEquals(GpsRouteTravelDirection.FORWARD, tracker.observe(4))
    }

    @Test fun reversalBecomesUnknownBeforeOppositeDirectionIsConfirmed() {
        val tracker = GpsRouteDirectionTracker()
        listOf(1, 2, 3, 4).forEach { tracker.observe(it) }
        assertEquals(GpsRouteTravelDirection.FORWARD, tracker.observe(3))
        assertEquals(GpsRouteTravelDirection.UNKNOWN, tracker.observe(2))
        assertEquals(GpsRouteTravelDirection.UNKNOWN, tracker.observe(1))
        assertEquals(GpsRouteTravelDirection.REVERSE, tracker.observe(0))
    }

    @Test fun abruptIndexJumpRebasesWithoutBeingTreatedAsTravel() {
        val tracker = GpsRouteDirectionTracker()
        listOf(1, 2, 3, 4).forEach { tracker.observe(it) }
        assertEquals(GpsRouteTravelDirection.UNKNOWN, tracker.observe(30))
        assertEquals(GpsRouteTravelDirection.UNKNOWN, tracker.observe(29))
        assertEquals(GpsRouteTravelDirection.UNKNOWN, tracker.observe(28))
        assertEquals(GpsRouteTravelDirection.REVERSE, tracker.observe(27))
    }

    @Test fun stationaryAndAlternatingFixesDoNotEstablishDirection() {
        val tracker = GpsRouteDirectionTracker()
        listOf(5, 5, 6, 5, 6, 5, 5).forEach {
            assertEquals(GpsRouteTravelDirection.UNKNOWN, tracker.observe(it))
        }
    }

    @Test fun resetAndInvalidIndexDiscardPriorTrend() {
        val tracker = GpsRouteDirectionTracker()
        listOf(1, 2, 3, 4).forEach { tracker.observe(it) }
        assertEquals(GpsRouteTravelDirection.UNKNOWN, tracker.observe(-1))
        assertEquals(GpsRouteTravelDirection.UNKNOWN, tracker.observe(10))
        assertEquals(GpsRouteTravelDirection.UNKNOWN, tracker.observe(11))
        tracker.reset()
        assertEquals(GpsRouteTravelDirection.UNKNOWN, tracker.observe(20))
    }
}
