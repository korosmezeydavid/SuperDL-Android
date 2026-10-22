package com.superdl.launcher.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsShortRouteTest {
    @Test fun twoPointRouteCanStartForwardFromUnambiguousFirstEndpoint() {
        assertEquals(
            GpsRouteTravelDirection.FORWARD,
            GpsRouteMatcher.initialShortRouteDirection(2, 4, 80, 8f)
        )
    }

    @Test fun threePointRouteCanStartInReverseFromUnambiguousLastEndpoint() {
        assertEquals(
            GpsRouteTravelDirection.REVERSE,
            GpsRouteMatcher.initialShortRouteDirection(3, 90, 6, 10f)
        )
    }

    @Test fun overlappingEndpointsOrImpreciseFixDoNotChooseADirection() {
        assertEquals(
            GpsRouteTravelDirection.UNKNOWN,
            GpsRouteMatcher.initialShortRouteDirection(2, 6, 19, 5f)
        )
        assertEquals(
            GpsRouteTravelDirection.UNKNOWN,
            GpsRouteMatcher.initialShortRouteDirection(3, 6, 42, 25f)
        )
        assertEquals(
            GpsRouteTravelDirection.UNKNOWN,
            GpsRouteMatcher.initialShortRouteDirection(2, 40, 100, 5f)
        )
        assertEquals(
            GpsRouteTravelDirection.UNKNOWN,
            GpsRouteMatcher.initialShortRouteDirection(4, 3, 80, 5f)
        )
    }

    @Test fun twoPointRouteDoesNotFinishAtItsStartingIndex() {
        assertFalse(GpsRouteMatcher.isDestinationReached(2, 0, 0, 5f, reversed = false))
        assertFalse(GpsRouteMatcher.isDestinationReached(2, 1, 0, 5f, reversed = true))
        assertTrue(GpsRouteMatcher.isDestinationReached(2, 1, 0, 5f, reversed = false))
        assertTrue(GpsRouteMatcher.isDestinationReached(2, 0, 0, 5f, reversed = true))
    }

    @Test fun threePointRouteRequiresActualDestinationIndex() {
        assertFalse(GpsRouteMatcher.isDestinationReached(3, 1, 0, 5f, reversed = false))
        assertFalse(GpsRouteMatcher.isDestinationReached(3, 1, 0, 5f, reversed = true))
        assertTrue(GpsRouteMatcher.isDestinationReached(3, 2, 8, 5f, reversed = false))
        assertTrue(GpsRouteMatcher.isDestinationReached(3, 0, 8, 5f, reversed = true))
        assertFalse(GpsRouteMatcher.isDestinationReached(3, 2, 16, 5f, reversed = false))
        assertFalse(GpsRouteMatcher.isDestinationReached(3, 2, 0, 25f, reversed = false))
        assertFalse(GpsRouteMatcher.isDestinationReached(4, 2, 40, 5f, reversed = false))
    }
}
