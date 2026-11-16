package com.superdl.launcher.screenreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceOverTapTimingTest {
    @Test fun relaxedMultipleFingerTapsKeepTheSwipeAndHoldOutside() {
        assertTrue(VoiceOverTapTiming.isTap(3, 23f, 420))
        assertFalse(VoiceOverTapTiming.isTap(3, 28f, 160))
        assertFalse(VoiceOverTapTiming.isTap(2, 5f, 520))
        assertFalse(VoiceOverTapTiming.isTap(5, 5f, 160))
    }

    @Test fun multipleFingerSequencesGetMoreTimeAndSpace() {
        assertEquals(455L, VoiceOverTapTiming.pause(2, 300))
        assertTrue(VoiceOverTapTiming.sameArea(3, 88f))
        assertFalse(VoiceOverTapTiming.sameArea(3, 100f))
    }

    @Test fun oneFingerKeepsItsOriginalWindow() {
        assertEquals(300L, VoiceOverTapTiming.pause(1, 300))
        assertFalse(VoiceOverTapTiming.isTap(1, 19f, 220))
        assertFalse(VoiceOverTapTiming.sameArea(1, 72f))
    }
}
