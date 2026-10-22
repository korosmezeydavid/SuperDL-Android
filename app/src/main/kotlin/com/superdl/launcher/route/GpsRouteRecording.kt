package com.superdl.launcher.route

import android.location.Location
import java.util.concurrent.ConcurrentHashMap

enum class RouteEventType {
    START,
    STOP,
    TURN_LEFT,
    TURN_RIGHT,
    TURN_SLIGHT,
    U_TURN,
    CROSSING,
    WAYPOINT,
    /** A felvétel irányában érvényes, saját szövegű emlékeztető. */
    NOTE
}

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long,
    val accuracyM: Int? = null,
    val bearing: Float? = null
)

data class RouteEvent(
    val type: RouteEventType,
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long,
    val label: String? = null,
    val voiceNotePath: String? = null
)

data class GpsRouteRecording(
    val id: String,
    val name: String,
    val createdAtMs: Long,
    val points: List<RoutePoint>,
    val events: List<RouteEvent>
) {
    fun speakPreview(): String = name

    fun totalDistanceMeters(): Int {
        if (points.size < 2) return 0
        var total = 0
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            total += distanceBetween(prev, curr)
        }
        return total
    }

    private fun distanceBetween(from: RoutePoint, to: RoutePoint): Int {
        val result = FloatArray(1)
        Location.distanceBetween(
            from.latitude,
            from.longitude,
            to.latitude,
            to.longitude,
            result
        )
        return result[0].toInt().coerceAtLeast(0)
    }
}

object GpsRouteSession {
    /** A helyfigyelő és a felület külön szálon használja a felvételt. */
    val recordingLock = Any()

    @Volatile
    var isRecording: Boolean = false

    @Volatile
    var recordingName: String = ""

    @Volatile
    var points: MutableList<RoutePoint> = mutableListOf()

    @Volatile
    var events: MutableList<RouteEvent> = mutableListOf()

    @Volatile
    var lastRecordingLocation: Location? = null

    /** A későn visszatérő diktálás ne írhasson bele egy következő felvételbe. */
    @Volatile
    var recordingGeneration: Long = 0L

    /** Rövid hangjegyzet közben a rögzítő szolgáltatás nem beszélhet a mikrofonba. */
    @Volatile
    var isVoiceNoteRecording: Boolean = false

    @Volatile
    var isGuiding: Boolean = false

    @Volatile
    var activeRoute: GpsRouteRecording? = null

    @Volatile
    var lastLocation: Location? = null

    /** A diktálás után mentett jelölő indexe nem feltétlenül követi az útvonal sorrendjét. */
    val announcedEventIndices: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    /** Egy pontra több esemény eshet; mindegyik közelítési állapota külön él. */
    val approachThresholds: MutableMap<Int, Int> = ConcurrentHashMap()

    @Volatile
    var guidanceReversed: Boolean = false

    fun clearRecording() {
        synchronized(recordingLock) {
            recordingGeneration++
            isRecording = false
            isVoiceNoteRecording = false
            recordingName = ""
            points = mutableListOf()
            events = mutableListOf()
            lastRecordingLocation = null
        }
    }

    fun clearGuidance() {
        isGuiding = false
        activeRoute = null
        lastLocation = null
        announcedEventIndices.clear()
        approachThresholds.clear()
        guidanceReversed = false
    }
}
