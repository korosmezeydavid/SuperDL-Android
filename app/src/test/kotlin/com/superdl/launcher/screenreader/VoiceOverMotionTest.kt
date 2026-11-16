package com.superdl.launcher.screenreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class VoiceOverMotionTest {
    @Test fun rotationsSetTheDirection() {
        val samples = List(8) { step ->
            val angle = step * 0.2
            VoiceOverMotion.Sample(100.0, 100.0, 65 * cos(angle), 65 * sin(angle))
        }
        assertEquals(VoiceOverMotion.Action.ROTOR_FORWARD, VoiceOverMotion.classifySamples(samples, 1f))
        assertEquals(VoiceOverMotion.Action.ROTOR_BACKWARD, VoiceOverMotion.classifySamples(samples.reversed(), 1f))
    }

    @Test fun pivotRotationAllowsOneFingerToStayInPlace() {
        val samples = List(6) { step ->
            val angle = step * 0.13
            val dx = 105 * cos(angle)
            val dy = 105 * sin(angle)
            VoiceOverMotion.Sample(100 + dx / 2, 100 + dy / 2, dx, dy)
        }
        assertEquals(VoiceOverMotion.Action.ROTOR_FORWARD, VoiceOverMotion.classifySamples(samples, 1f))
    }

    @Test fun anOrdinarySwipeOrPinchDoesNotMoveTheRotor() {
        val swipe = List(6) { step -> VoiceOverMotion.Sample(100.0, 100.0 + step * 50, 55.0, 0.0) }
        val pinch = List(6) { step -> VoiceOverMotion.Sample(100.0, 100.0, 55.0 + step * 16, 0.0) }
        assertNull(VoiceOverMotion.classifySamples(swipe, 1f))
        assertNull(VoiceOverMotion.classifySamples(pinch, 1f))
    }

    @Test fun threeAlternatingLegsRecognizeTheScrub() {
        val samples = listOf(0.0, 36.0, 70.0, 32.0, 0.0, 37.0, 72.0)
            .map { x -> VoiceOverMotion.Sample(x, 100.0, 60.0, 0.0) }
        assertEquals(VoiceOverMotion.Action.BACK, VoiceOverMotion.classifySamples(samples, 1f))
        assertNull(VoiceOverMotion.classifySamples(samples.take(5), 1f))
    }
}
