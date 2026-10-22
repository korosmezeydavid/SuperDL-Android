package com.superdl.launcher.route

import android.location.Location
import android.os.SystemClock

/** A személyes útvonal-emlékeztetőket nem kötjük régi vagy durva GPS-ponthoz. */
object GpsRouteFixQuality {
    private const val MAX_AGE_MS = 10_000L
    private const val MAX_ACCURACY_M = 25f

    fun isUsable(accuracyM: Float?, ageMs: Long): Boolean =
        accuracyM != null && accuracyM > 0f && accuracyM <= MAX_ACCURACY_M &&
            ageMs in 0..MAX_AGE_MS

    fun isUsable(location: Location?): Boolean {
        if (location == null || !location.hasAccuracy()) return false
        val fixNanos = location.elapsedRealtimeNanos
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        if (fixNanos <= 0L || fixNanos > nowNanos) return false
        return isUsable(location.accuracy, (nowNanos - fixNanos) / 1_000_000L)
    }
}
