package com.superdl.launcher.screenreader

import android.view.MotionEvent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Matches completed strokes recorded by Android while touch exploration is active. */
internal object VoiceOverMotion {
    enum class Action { ROTOR_FORWARD, ROTOR_BACKWARD, BACK }

    fun classify(events: List<MotionEvent>, density: Float): Action? {
        val lastAction = events.lastOrNull()?.actionMasked ?: return null
        if (lastAction == MotionEvent.ACTION_CANCEL || events.any { it.pointerCount > 2 }) return null
        val moves = events.filter { it.pointerCount == 2 }
        val first = moves.firstOrNull() ?: return null
        val firstId = first.getPointerId(0)
        val secondId = first.getPointerId(1)
        val samples = mutableListOf<Sample>()
        for (event in moves) {
            val a = event.findPointerIndex(firstId)
            val b = event.findPointerIndex(secondId)
            if (a < 0 || b < 0) return null
            for (history in 0 until event.historySize) {
                samples += sample(
                    event.getHistoricalX(a, history), event.getHistoricalY(a, history),
                    event.getHistoricalX(b, history), event.getHistoricalY(b, history)
                )
            }
            samples += sample(event.getX(a), event.getY(a), event.getX(b), event.getY(b))
        }
        val action = classifySamples(samples, density)
        // Android may report an unrecognized rotation before the last finger lifts.
        // A scrub, by contrast, should be complete before it closes a screen.
        val ended = lastAction == MotionEvent.ACTION_UP || lastAction == MotionEvent.ACTION_POINTER_UP
        return action.takeUnless { it == Action.BACK && !ended }
    }

    internal fun classifySamples(samples: List<Sample>, density: Float): Action? {
        if (samples.size < 4 || density <= 0f) return null
        val start = samples.first()
        val end = samples.last()
        val centerTravel = samples.maxOf { hypot(it.x - start.x, it.y - start.y) }
        val turns = samples.zipWithNext { a, b ->
            val delta = atan2(b.dy, b.dx) - atan2(a.dy, a.dx)
            atan2(kotlin.math.sin(delta), kotlin.math.cos(delta))
        }
        val turn = turns.sum()
        val angularTravel = turns.sumOf { abs(it) }
        val gaps = samples.map { hypot(it.dx, it.dy) }
        val minGap = gaps.min()
        val maxGap = gaps.max()
        val permittedCenterTravel = maxOf(36.0 * density, maxGap * 0.55)
        if (minGap >= 21 * density && maxGap <= minGap * 1.65 &&
            centerTravel <= permittedCenterTravel &&
            abs(turn) >= PI / 6 && abs(turn) >= angularTravel * 0.8
        ) {
            return if (turn > 0) Action.ROTOR_FORWARD else Action.ROTOR_BACKWARD
        }

        val threshold = 26 * density
        if (abs(end.y - start.y) > 44 * density || abs(turn) > PI / 6) return null
        if (samples.maxOf { hypot(it.dx - start.dx, it.dy - start.dy) } > 30 * density) return null
        var anchor = start.x
        var lastDirection = 0
        var legs = 0
        for (sample in samples.drop(1)) {
            val change = sample.x - anchor
            if (abs(change) < threshold) continue
            val direction = if (change > 0) 1 else -1
            if (lastDirection != direction) {
                legs++
                lastDirection = direction
            }
            anchor = sample.x
        }
        return if (legs == 3) Action.BACK else null
    }

    private fun sample(x1: Float, y1: Float, x2: Float, y2: Float): Sample =
        Sample((x1.toDouble() + x2) / 2, (y1.toDouble() + y2) / 2, (x2 - x1).toDouble(), (y2 - y1).toDouble())

    internal data class Sample(val x: Double, val y: Double, val dx: Double, val dy: Double)
}
