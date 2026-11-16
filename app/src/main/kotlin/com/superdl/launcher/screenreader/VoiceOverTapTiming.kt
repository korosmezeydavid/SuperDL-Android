package com.superdl.launcher.screenreader

internal object VoiceOverTapTiming {
    fun isTap(fingers: Int, travelDp: Float, durationMs: Long): Boolean =
        fingers in 1..4 && if (fingers == 1) travelDp < 15f && durationMs in 0..369
        else travelDp < 25f && durationMs in 0..459

    fun sameArea(fingers: Int, distanceDp: Float): Boolean =
        distanceDp < if (fingers == 1) 62f else 100f

    fun pause(fingers: Int, systemTimeoutMs: Int): Long =
        if (fingers == 1) systemTimeoutMs.toLong() else maxOf(455, systemTimeoutMs).toLong()
}
