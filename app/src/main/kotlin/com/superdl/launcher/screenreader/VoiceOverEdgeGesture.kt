package com.superdl.launcher.screenreader

import kotlin.math.abs

/** Quantizes an inward drag in dp. Only a stage already signalled can be committed. */
internal class VoiceOverEdgeGesture {
    enum class Action { NEAR, FAR }

    private var active = false
    private var reached = 0
    private var signalled = 0

    fun begin() {
        active = true
        reached = 0
        signalled = 0
    }

    fun move(inwardDp: Float, sidewaysDp: Float) {
        if (!active) return
        if (inwardDp < -12f || (abs(sidewaysDp) > 32f && abs(sidewaysDp) > inwardDp)) {
            cancel()
            return
        }
        reached = when {
            inwardDp >= 165f -> 3
            inwardDp >= 70f -> 2
            inwardDp >= 14f -> 1
            else -> 0
        }
    }

    fun nextTone(): Int? {
        if (!active || signalled >= reached) return null
        return ++signalled
    }

    fun finish(): Action? {
        val action = if (active) when (signalled) {
            2 -> Action.NEAR
            3 -> Action.FAR
            else -> null
        } else null
        cancel()
        return action
    }

    fun cancel() {
        active = false
        reached = 0
        signalled = 0
    }
}
