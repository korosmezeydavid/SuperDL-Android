package com.superdl.launcher.gps

import com.superdl.launcher.transit.TransitPlace

data class GpsPoi(
    val id: String,
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int,
    val bearingDegrees: Float,
    val clockDirection: String
) {
    fun speakRadar(): String =
        "$name, ${TransitPlace.formatDistance(distanceMeters)}re, $clockDirection irányában."

    fun speakGuidance(turnHint: String): String =
        "$name, ${TransitPlace.formatDistance(distanceMeters)}re. $turnHint."
}