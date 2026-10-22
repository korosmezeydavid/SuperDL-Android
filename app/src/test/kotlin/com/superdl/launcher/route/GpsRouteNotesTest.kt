package com.superdl.launcher.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsRouteNotesTest {
    @Test fun samePointNotesAreNotLostAfterTheFirstAnnouncement() {
        val points = listOf(0, 4, 4, 8)
        val types = listOf(
            RouteEventType.START,
            RouteEventType.WAYPOINT,
            RouteEventType.NOTE,
            RouteEventType.STOP
        )
        assertEquals(1, GpsRouteMatcher.selectNextEventIndex(points, types, 4, false, setOf(0)))
        assertEquals(2, GpsRouteMatcher.selectNextEventIndex(points, types, 4, false, setOf(0, 1)))
        assertEquals(3, GpsRouteMatcher.selectNextEventIndex(points, types, 4, false, setOf(0, 1, 2)))
    }

    @Test fun freeTextInstructionIsNeverReadBackwards() {
        val points = listOf(0, 4, 4, 8)
        val types = listOf(
            RouteEventType.START,
            RouteEventType.WAYPOINT,
            RouteEventType.NOTE,
            RouteEventType.STOP
        )
        assertEquals(1, GpsRouteMatcher.selectNextEventIndex(points, types, 4, true))
        assertEquals(0, GpsRouteMatcher.selectNextEventIndex(points, types, 4, true, setOf(1)))
    }

    @Test fun delayedNoteCannotHideLaterTurn() {
        // A jegyzet mentése a későbbi diktálás után történt, ezért a lista végére került.
        val points = listOf(0, 4, 10, 5)
        val types = listOf(
            RouteEventType.START,
            RouteEventType.WAYPOINT,
            RouteEventType.TURN_RIGHT,
            RouteEventType.NOTE
        )
        assertEquals(3, GpsRouteMatcher.selectNextEventIndex(points, types, 5, false, setOf(0, 1)))
        assertEquals(2, GpsRouteMatcher.selectNextEventIndex(points, types, 5, false, setOf(0, 1, 3)))
    }

    @Test fun allPendingNotesAtOnePointCanBeSpokenTogether() {
        val points = listOf(0, 4, 4, 4, 8)
        val types = listOf(
            RouteEventType.START,
            RouteEventType.TURN_RIGHT,
            RouteEventType.NOTE,
            RouteEventType.NOTE,
            RouteEventType.STOP
        )
        assertEquals(listOf(1, 2, 3), GpsRouteMatcher.pendingEventsAtSamePoint(
            points, types, 1, false, emptySet()
        ))
        assertEquals(listOf(1), GpsRouteMatcher.pendingEventsAtSamePoint(
            points, types, 1, false, setOf(2, 3)
        ))
    }

    @Test fun approachWarningsAdvanceFromFiftyToTwentyToTenMetres() {
        assertEquals(50, GpsRouteMatcher.approachThreshold(48))
        assertEquals(20, GpsRouteMatcher.approachThreshold(19))
        assertEquals(10, GpsRouteMatcher.approachThreshold(8))
        assertEquals(null, GpsRouteMatcher.approachThreshold(51))
    }

    @Test fun noteNeedsFreshPreciseGpsFix() {
        assertTrue(GpsRouteFixQuality.isUsable(8f, 2_000L))
        assertFalse(GpsRouteFixQuality.isUsable(30f, 2_000L))
        assertFalse(GpsRouteFixQuality.isUsable(8f, 11_000L))
        assertFalse(GpsRouteFixQuality.isUsable(null, 2_000L))
    }
}
