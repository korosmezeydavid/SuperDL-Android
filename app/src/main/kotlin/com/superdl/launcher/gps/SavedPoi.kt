package com.superdl.launcher.gps

import android.location.Location

data class SavedPoi(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val category: String = "mentett",
    val savedAtMs: Long = System.currentTimeMillis()
) {
    fun speakPreview(): String = name

    fun toGpsPoi(fromLocation: Location?, headingDegrees: Float): GpsPoi {
        val refLat = fromLocation?.latitude ?: latitude
        val refLon = fromLocation?.longitude ?: longitude
        return GpsRadarMath.enrichPoi(
            GpsPoiRaw("saved_$id", name, category, latitude, longitude),
            refLat,
            refLon,
            headingDegrees
        )
    }
}