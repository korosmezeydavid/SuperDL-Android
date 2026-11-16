package com.superdl.launcher.screenreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceOverEdgeGestureTest {
    private val gesture = VoiceOverEdgeGesture()

    @Test fun shortDragPlaysFirstToneWithoutAnAction() {
        gesture.begin()
        gesture.move(25f, 0f)
        assertEquals(1, gesture.nextTone())
        assertNull(gesture.finish())
    }

    @Test fun secondToneSelectsNearActionOnlyOnRelease() {
        gesture.begin()
        gesture.move(76f, 0f)
        assertEquals(1, gesture.nextTone())
        assertEquals(2, gesture.nextTone())
        assertNull(gesture.nextTone())
        assertEquals(VoiceOverEdgeGesture.Action.NEAR, gesture.finish())
        assertNull(gesture.finish())
    }

    @Test fun aLongerSecondStageLeavesRoomForRelease() {
        gesture.begin()
        gesture.move(150f, 0f)
        assertEquals(1, gesture.nextTone())
        assertEquals(2, gesture.nextTone())
        assertNull(gesture.nextTone())
        assertEquals(VoiceOverEdgeGesture.Action.NEAR, gesture.finish())
    }

    @Test fun thirdToneSelectsFarAction() {
        gesture.begin()
        gesture.move(178f, 0f)
        assertEquals(listOf(1, 2, 3), List(3) { gesture.nextTone() })
        assertEquals(VoiceOverEdgeGesture.Action.FAR, gesture.finish())
    }

    @Test fun fastReleaseCannotSelectAnUnsignalledAction() {
        gesture.begin()
        gesture.move(178f, 0f)
        assertEquals(1, gesture.nextTone())
        assertEquals(2, gesture.nextTone())
        assertEquals(VoiceOverEdgeGesture.Action.NEAR, gesture.finish())
        assertNull(gesture.nextTone())
    }

    @Test fun aSmallRetreatKeepsTheLastAudibleSelection() {
        gesture.begin()
        gesture.move(80f, 0f)
        assertEquals(1, gesture.nextTone())
        assertEquals(2, gesture.nextTone())
        gesture.move(24f, 0f)
        assertEquals(VoiceOverEdgeGesture.Action.NEAR, gesture.finish())
    }

    @Test fun retreatAfterTheThirdToneStillSelectsTheFarAction() {
        gesture.begin()
        gesture.move(178f, 0f)
        repeat(3) { gesture.nextTone() }
        gesture.move(68f, 0f)
        assertEquals(VoiceOverEdgeGesture.Action.FAR, gesture.finish())
    }

    @Test fun cancellationAndSidewaysMovementNeverCommit() {
        gesture.begin()
        gesture.move(120f, 0f)
        gesture.nextTone()
        gesture.nextTone()
        gesture.cancel()
        assertNull(gesture.finish())
        gesture.begin()
        gesture.move(24f, 45f)
        gesture.move(120f, 0f)
        assertNull(gesture.nextTone())
        assertNull(gesture.finish())
    }
}
