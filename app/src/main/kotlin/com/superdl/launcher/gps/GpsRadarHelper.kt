package com.superdl.launcher.gps

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper

object GpsRadarHelper {

    fun loadNearbyPois(
        context: Context,
        headingDegrees: Float,
        onResult: (List<GpsPoi>) -> Unit,
        onError: (String) -> Unit
    ) {
        val location = GpsLocationHelper.getLastLocation(context)
            ?: run {
                onError("Helymeghatározás nem elérhető. Kapcsold be a G P S-t.")
                return
            }
        runAsync(
            onError = onError,
            block = {
                val raw = GpsOverpassHelper.fetchNearbyPois(location.latitude, location.longitude)
                val pois = raw.map { GpsRadarMath.enrichPoi(it, location.latitude, location.longitude, headingDegrees) }
                    .sortedBy { it.distanceMeters }
                GpsRadarStore.lastLocation = location
                GpsRadarStore.lastHeading = headingDegrees
                GpsRadarStore.nearbyPois = pois
                pois
            },
            onResult = onResult
        )
    }

    fun refreshPoisWithLocation(
        location: Location,
        headingDegrees: Float,
        onUpdated: ((List<GpsPoi>) -> Unit)? = null
    ) {
        runAsync(
            onError = { },
            block = {
                val raw = GpsOverpassHelper.fetchNearbyPois(location.latitude, location.longitude)
                val pois = raw.map { GpsRadarMath.enrichPoi(it, location.latitude, location.longitude, headingDegrees) }
                    .sortedBy { it.distanceMeters }
                GpsRadarStore.lastLocation = location
                GpsRadarStore.lastHeading = headingDegrees
                GpsRadarStore.nearbyPois = pois
                val targetId = GpsRadarStore.targetPoi?.id
                if (targetId != null) {
                    GpsRadarStore.targetPoi = pois.firstOrNull { it.id == targetId }
                        ?: GpsRadarStore.targetPoi?.let { old ->
                            GpsRadarMath.enrichPoi(
                                GpsPoiRaw(old.id, old.name, old.category, old.latitude, old.longitude),
                                location.latitude,
                                location.longitude,
                                headingDegrees
                            )
                        }
                }
                pois
            },
            onResult = { onUpdated?.invoke(it) }
        )
    }

    fun updatePoiDirections(pois: List<GpsPoi>, location: Location, headingDegrees: Float): List<GpsPoi> =
        pois.map { poi ->
            GpsRadarMath.enrichPoi(
                GpsPoiRaw(poi.id, poi.name, poi.category, poi.latitude, poi.longitude),
                location.latitude,
                location.longitude,
                headingDegrees
            )
        }.sortedBy { it.distanceMeters }

    fun speakAllPois(pois: List<GpsPoi>): String {
        if (pois.isEmpty()) return "Nincs közeli hely a 300 méteres körzetben."
        val limit = pois.take(12)
        val body = limit.joinToString(" ") { it.speakRadar() }
        val suffix = if (pois.size > limit.size) " Összesen ${pois.size} hely található."
        else " Összesen ${pois.size} hely."
        return "G P S kitekintő. $body$suffix Swipe fel-le választás, jobbra műveletek, le saját hely mentése."
    }

    private fun <T> runAsync(
        onError: (String) -> Unit,
        block: () -> T,
        onResult: (T) -> Unit
    ) {
        Thread {
            try {
                val result = block()
                Handler(Looper.getMainLooper()).post { onResult(result) }
            } catch (e: GpsRadarException) {
                Handler(Looper.getMainLooper()).post { onError(e.message ?: "G P S kitekintő hiba.") }
            } catch (_: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onError("G P S kitekintő lekérdezés sikertelen. Ellenőrizd az internetet és a G P S-t.")
                }
            }
        }.start()
    }
}