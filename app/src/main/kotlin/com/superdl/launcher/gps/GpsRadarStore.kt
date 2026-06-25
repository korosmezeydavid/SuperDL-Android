package com.superdl.launcher.gps

import android.location.Location

object GpsRadarStore {

    @Volatile
    var nearbyPois: List<GpsPoi> = emptyList()

    @Volatile
    var targetPoi: GpsPoi? = null

    @Volatile
    var lastLocation: Location? = null

    @Volatile
    var lastHeading: Float = 0f

    @Volatile
    var isGuiding: Boolean = false

    @Volatile
    var approachSavedPoi: Boolean = false

    @Volatile
    var lastApproachThreshold: Int? = null

    fun clear() {
        nearbyPois = emptyList()
        targetPoi = null
        lastLocation = null
        lastHeading = 0f
        isGuiding = false
        approachSavedPoi = false
        lastApproachThreshold = null
    }
}