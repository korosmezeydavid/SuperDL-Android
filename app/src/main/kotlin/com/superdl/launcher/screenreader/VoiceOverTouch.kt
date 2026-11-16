package com.superdl.launcher.screenreader

import android.accessibilityservice.AccessibilityService as Gesture
import android.accessibilityservice.TouchInteractionController
import android.os.Handler
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.annotation.RequiresApi
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Android 13+ VoiceOver input, with live sampling before the platform rejects a rotation. */
@RequiresApi(33)
internal class VoiceOverTouch(
    private val controller: TouchInteractionController,
    private val handler: Handler,
    private val density: Float,
    private val onGesture: (Int) -> Unit,
    private val onExplore: (Float, Float) -> Boolean,
    private val onRotor: (Boolean) -> Unit,
    private val onRotorSound: (Boolean) -> Unit,
    private val onJump: (Float) -> Unit,
    private val onLongPress: () -> Unit,
    private val onSplitTap: () -> Unit,
    private val onPracticePassThrough: () -> Boolean,
    private val onScrub: () -> Unit,
    private val onPassThrough: () -> Unit,
    private val canDelegate: () -> Boolean,
    private val isKeyboardExploration: (Float, Float) -> Boolean,
    private val onQuickSettings: () -> Unit
) : TouchInteractionController.Callback {
    private data class Point(val x: Float, val y: Float)

    private val start = mutableMapOf<Int, Point>()
    private val latest = mutableMapOf<Int, Point>()
    private val rotation = mutableListOf<VoiceOverMotion.Sample>()
    private var startedAt = 0L
    private var largestTravel = 0f
    private var fingerCount = 0
    private var rotated = false
    private var scrubbed = false
    private var firstId = -1
    private var secondId = -1
    private var tapCount = 0
    private var tapFingers = 0
    private var tapPoint: Point? = null
    private var disposed = false
    private var rotorSoundAngle: Double? = null
    private val silenceRotor = Runnable { onRotorSound(false) }
    private val resolveTaps = Runnable { finishTaps() }
    private var exploring = false
    private var splitAnchorFocused = false
    private var consumed = false
    private var splitPointer = -1
    private var splitDownAt = 0L
    private var pendingLabel = false
    private val explore = Runnable {
        if (fingerCount == 1 && largestTravel < 10 * density && !disposed) {
            exploring = true
            splitAnchorFocused = latest.values.firstOrNull()?.let { onExplore(it.x, it.y) } == true
        }
    }
    private val hold = Runnable {
        if (!matchesSecondTap() || consumed || largestTravel >= 12 * density || disposed) return@Runnable
        when (fingerCount) {
            1 -> {
                if (onPracticePassThrough()) {
                    consumed = true
                    flushTaps()
                    return@Runnable
                }
                if (!canDelegate() || controller.state != TouchInteractionController.STATE_TOUCH_INTERACTING) return@Runnable
                consumed = true
                flushTaps()
                runCatching { controller.requestDelegating() }
                    .onSuccess { onPassThrough() }
            }
            2 -> {
                pendingLabel = true
                flushTaps()
            }
        }
    }

    fun attach(): Boolean = try {
        controller.registerCallback({ command -> handler.post(command) }, this)
        android.util.Log.d(ScreenReaderPrefs.TAG, "Élő VoiceOver érintéskezelés bekapcsolva.")
        true
    } catch (error: RuntimeException) {
        android.util.Log.w(ScreenReaderPrefs.TAG, "Az élő rotor érintéskezelése nem indult el.", error)
        false
    }

    fun detach() {
        disposed = true
        resetStroke()
        flushTaps()
        runCatching { controller.unregisterCallback(this) }
    }

    override fun onStateChanged(state: Int) {
        if (state == TouchInteractionController.STATE_TOUCH_EXPLORING ||
            state == TouchInteractionController.STATE_DELEGATING
        ) resetStroke()
    }

    override fun onMotionEvent(event: MotionEvent) {
        if (disposed) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetStroke()
                if (isKeyboardExploration(event.x, event.y)) {
                    flushTaps()
                    if (runCatching { controller.requestTouchExploration() }.isSuccess) return
                }
                startedAt = event.eventTime
                handler.removeCallbacks(resolveTaps)
                sample(event)
                if (tapCount == 0) handler.postDelayed(explore, 265L)
                else if (matchesSecondTap() && tapFingers == 1) handler.postDelayed(hold, 520L)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                handler.removeCallbacks(explore)
                handler.removeCallbacks(hold)
                sample(event)
                if (event.pointerCount == 2) {
                    if (exploring && splitAnchorFocused && !consumed) {
                        splitPointer = event.getPointerId(event.actionIndex)
                        splitDownAt = event.eventTime
                    } else if (matchesSecondTap() && tapFingers == 2) {
                        handler.postDelayed(hold, 520L)
                    }
                }
                if (com.superdl.launcher.BuildConfig.DEBUG && event.pointerCount == 2) {
                    android.util.Log.d(ScreenReaderPrefs.TAG, "Élő VoiceOver kétujjas mozdulat kezdődött.")
                }
            }
            MotionEvent.ACTION_MOVE -> {
                sample(event)
                if (largestTravel >= 10 * density) handler.removeCallbacks(explore)
                if (largestTravel >= 12 * density) {
                    handler.removeCallbacks(hold)
                    pendingLabel = false
                }
                if (consumed || pendingLabel) return
                if (exploring && event.pointerCount == 1) {
                    splitAnchorFocused = onExplore(event.rawX, event.rawY)
                } else if (!exploring) detectRotation(event)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                stopRotorSound()
                handler.removeCallbacks(hold)
                sample(event)
                val lifted = event.getPointerId(event.actionIndex)
                val origin = start[lifted]
                val end = latest[lifted]
                if (exploring && !consumed && fingerCount == 2 && lifted == splitPointer &&
                    event.eventTime - splitDownAt in 0..230 && origin != null && end != null &&
                    hypot(end.x - origin.x, end.y - origin.y) < 12 * density
                ) {
                    consumed = true
                    flushTaps()
                    onSplitTap()
                }
                splitPointer = -1
            }
            MotionEvent.ACTION_UP -> {
                sample(event)
                finishStroke(event.eventTime)
                resetStroke()
            }
            MotionEvent.ACTION_CANCEL -> {
                resetStroke()
                flushTaps()
            }
        }
    }

    private fun sample(event: MotionEvent) {
        fingerCount = maxOf(fingerCount, event.pointerCount)
        for (index in 0 until event.pointerCount) {
            val id = event.getPointerId(index)
            val point = Point(event.getRawX(index), event.getRawY(index))
            val origin = start.getOrPut(id) { point }
            latest[id] = point
            largestTravel = maxOf(largestTravel, hypot(point.x - origin.x, point.y - origin.y))
        }
    }

    private fun detectRotation(event: MotionEvent) {
        if (fingerCount != 2 || event.pointerCount != 2 || scrubbed) {
            stopRotorSound()
            return
        }
        if (firstId < 0) {
            firstId = event.getPointerId(0)
            secondId = event.getPointerId(1)
        }
        val a = latest[firstId] ?: return
        val b = latest[secondId] ?: return
        val sample = VoiceOverMotion.Sample(
            (a.x.toDouble() + b.x) / 2, (a.y.toDouble() + b.y) / 2,
            (b.x - a.x).toDouble(), (b.y - a.y).toDouble()
        )
        if (rotated) refreshRotorSound(atan2(sample.dy, sample.dx))
        if (rotation.size >= 64) rotation.removeAt(0)
        rotation += sample
        val direction = VoiceOverMotion.classifySamples(rotation, density)
        when (direction) {
            VoiceOverMotion.Action.ROTOR_FORWARD, VoiceOverMotion.Action.ROTOR_BACKWARD -> {
                rotated = true
                refreshRotorSound(atan2(sample.dy, sample.dx), force = true)
                flushTaps()
                if (com.superdl.launcher.BuildConfig.DEBUG) {
                    android.util.Log.d(ScreenReaderPrefs.TAG, "Élő VoiceOver rotor: $direction")
                }
                onRotor(direction == VoiceOverMotion.Action.ROTOR_FORWARD)
                rotation.clear()
                rotation += sample
            }
            VoiceOverMotion.Action.BACK -> if (!rotated) scrubbed = true
            null -> Unit
        }
    }

    private fun finishStroke(endedAt: Long) {
        handler.removeCallbacks(explore)
        handler.removeCallbacks(hold)
        if (consumed || exploring || rotated) return
        if (pendingLabel) {
            onGesture(Gesture.GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD)
            return
        }
        if (scrubbed) {
            flushTaps()
            onScrub()
            return
        }
        if (VoiceOverTapTiming.isTap(fingerCount, largestTravel / density, endedAt - startedAt)) {
            val point = tapCenter(latest) ?: return
            val previous = tapPoint
            if (tapFingers != fingerCount || (previous != null &&
                    !VoiceOverTapTiming.sameArea(fingerCount, hypot(previous.x - point.x, previous.y - point.y) / density)
                )) {
                if (tapFingers > 1) flushTaps() else finishTaps()
            }
            tapFingers = fingerCount
            tapCount++
            tapPoint = point
            if ((fingerCount == 2 && tapCount == 4) || (fingerCount == 3 && tapCount == 3)) {
                finishTaps()
            } else {
                handler.postDelayed(resolveTaps, VoiceOverTapTiming.pause(fingerCount, ViewConfiguration.getDoubleTapTimeout()))
            }
            return
        }
        flushTaps()
        val displacement = start.mapNotNull { (id, origin) ->
            latest[id]?.let { Point(it.x - origin.x, it.y - origin.y) }
        }
        if (displacement.isEmpty()) return
        val dx = displacement.map { it.x }.average()
        val dy = displacement.map { it.y }.average()
        if (maxOf(abs(dx), abs(dy)) < 28 * density) return
        val direction = if (abs(dx) > abs(dy)) {
            if (dx > 0) 0 else 1
        } else if (dy < 0) 2 else 3
        swipes.getOrNull(fingerCount - 1)?.get(direction)?.let(onGesture)
    }

    private fun matchesSecondTap(): Boolean {
        val first = tapPoint ?: return false
        val point = tapCenter(start) ?: return false
        return tapCount == 1 && fingerCount == tapFingers &&
            VoiceOverTapTiming.sameArea(fingerCount, hypot(point.x - first.x, point.y - first.y) / density)
    }

    private fun tapCenter(points: Map<Int, Point>): Point? {
        if (points.isEmpty()) return null
        return Point(points.values.map { it.x }.average().toFloat(), points.values.map { it.y }.average().toFloat())
    }

    private fun finishTaps() {
        val count = tapCount
        val fingers = tapFingers
        val point = tapPoint
        flushTaps()
        when {
            fingers == 1 && count == 1 && point != null -> onExplore(point.x, point.y)
            fingers == 1 && count == 2 -> onGesture(Gesture.GESTURE_DOUBLE_TAP)
            fingers == 1 && count == 3 -> onLongPress()
            fingers == 2 && count == 1 -> onGesture(Gesture.GESTURE_2_FINGER_SINGLE_TAP)
            fingers == 2 && count == 2 -> onGesture(Gesture.GESTURE_2_FINGER_DOUBLE_TAP)
            fingers == 2 && count == 3 -> onGesture(Gesture.GESTURE_2_FINGER_TRIPLE_TAP)
            fingers == 2 && count == 4 -> onQuickSettings()
            fingers == 3 && count == 1 -> onGesture(Gesture.GESTURE_3_FINGER_SINGLE_TAP)
            fingers == 3 && count == 2 -> onGesture(Gesture.GESTURE_3_FINGER_DOUBLE_TAP)
            fingers == 3 && count == 3 -> onGesture(Gesture.GESTURE_3_FINGER_TRIPLE_TAP)
            fingers == 4 && count == 1 && point != null -> onJump(point.y)
        }
    }

    private fun refreshRotorSound(angle: Double, force: Boolean = false) {
        val previous = rotorSoundAngle
        if (!force && previous != null) {
            val turn = angle - previous
            if (abs(atan2(kotlin.math.sin(turn), kotlin.math.cos(turn))) < 0.055) return
        }
        rotorSoundAngle = angle
        onRotorSound(true)
        handler.removeCallbacks(silenceRotor)
        handler.postDelayed(silenceRotor, 135L)
    }

    private fun stopRotorSound() {
        if (rotorSoundAngle == null) return
        rotorSoundAngle = null
        handler.removeCallbacks(silenceRotor)
        onRotorSound(false)
    }

    private fun resetStroke() {
        stopRotorSound()
        handler.removeCallbacks(explore)
        handler.removeCallbacks(hold)
        exploring = false
        splitAnchorFocused = false
        consumed = false
        splitPointer = -1
        pendingLabel = false
        start.clear()
        latest.clear()
        rotation.clear()
        largestTravel = 0f
        fingerCount = 0
        firstId = -1
        secondId = -1
        rotated = false
        scrubbed = false
    }

    private fun flushTaps() {
        handler.removeCallbacks(resolveTaps)
        tapPoint = null
        tapFingers = 0
        tapCount = 0
    }

    private companion object {
        val swipes = arrayOf(
            intArrayOf(Gesture.GESTURE_SWIPE_RIGHT, Gesture.GESTURE_SWIPE_LEFT,
                Gesture.GESTURE_SWIPE_UP, Gesture.GESTURE_SWIPE_DOWN),
            intArrayOf(Gesture.GESTURE_2_FINGER_SWIPE_RIGHT, Gesture.GESTURE_2_FINGER_SWIPE_LEFT,
                Gesture.GESTURE_2_FINGER_SWIPE_UP, Gesture.GESTURE_2_FINGER_SWIPE_DOWN),
            intArrayOf(Gesture.GESTURE_3_FINGER_SWIPE_RIGHT, Gesture.GESTURE_3_FINGER_SWIPE_LEFT,
                Gesture.GESTURE_3_FINGER_SWIPE_UP, Gesture.GESTURE_3_FINGER_SWIPE_DOWN),
            intArrayOf(Gesture.GESTURE_4_FINGER_SWIPE_RIGHT, Gesture.GESTURE_4_FINGER_SWIPE_LEFT,
                Gesture.GESTURE_4_FINGER_SWIPE_UP, Gesture.GESTURE_4_FINGER_SWIPE_DOWN)
        )
    }
}
