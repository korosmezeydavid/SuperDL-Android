package com.superdl.launcher.transit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object TransitHelper {

    private const val BKK_BASE = "https://futar.bkk.hu/api/query/v1/ws/otp/api/where"
    private const val BKK_KEY = "bkk-web"
    private const val BKK_RADIUS = 450
    private const val BUDAPEST_MIN_LAT = 47.30
    private const val BUDAPEST_MAX_LAT = 47.62
    private const val BUDAPEST_MIN_LON = 18.90
    private const val BUDAPEST_MAX_LON = 19.40

    fun fetchNearbyStops(
        context: Context,
        onResult: (List<TransitPlace>) -> Unit,
        onError: (String) -> Unit
    ) {
        val location = getLastLocation(context)
            ?: run {
                onError("Helymeghatározás nem elérhető. Kapcsold be a G P S-t.")
                return
            }
        runAsync(onError, {
            if (isInBudapest(location)) {
                fetchBkkNearby(location).ifEmpty { OsmHelper.nearbyStops(location.latitude, location.longitude) }
            } else {
                OsmHelper.nearbyStops(location.latitude, location.longitude)
            }
        }) { places ->
            if (places.isEmpty()) onError("Nem találtam közeli megállót.")
            else onResult(places)
        }
    }

    fun searchStop(
        context: Context,
        stopName: String,
        onResult: (List<TransitPlace>) -> Unit,
        onError: (String) -> Unit
    ) {
        val trimmed = stopName.trim()
        if (trimmed.isBlank()) {
            onError("Üres megállónév.")
            return
        }
        val location = getLastLocation(context)
        runAsync(onError, {
            val geocoded = OsmHelper.geocode("$trimmed megálló")
                .ifEmpty { OsmHelper.geocode(trimmed) }
            val places = geocoded.map { geo ->
                val distance = location?.let { dist(it, geo.lat, geo.lon) }
                TransitPlace(
                    name = geo.shortName,
                    address = geo.fullName,
                    distanceMeters = distance
                )
            }.distinctBy { it.name }

            if (places.isNotEmpty()) places
            else if (location != null && isInBudapest(location)) {
                fetchBkkStopsForLocation(location)
                    .filter { it.name.contains(trimmed, ignoreCase = true) || trimmed.contains(it.name, ignoreCase = true) }
            } else {
                emptyList()
            }
        }) { result ->
            if (result.isEmpty()) onError("Nem találtam megállót: $trimmed.")
            else onResult(result)
        }
    }

    fun fetchTransitRoute(
        context: Context,
        destination: String,
        onResult: (TransitRoute) -> Unit,
        onError: (String) -> Unit
    ) {
        val trimmed = destination.trim()
        if (trimmed.isBlank()) {
            onError("Üres célállomás.")
            return
        }
        val location = getLastLocation(context)
            ?: run {
                onError("Helymeghatározás nem elérhető. Kapcsold be a G P S-t.")
                return
            }
        runAsync(onError, {
            val target = OsmHelper.geocode(trimmed).firstOrNull()
                ?: throw TransitApiException("Nem található célállomás: $trimmed.")

            if (isInBudapest(location) && isInBudapest(target.lat, target.lon)) {
                fetchBkkRoute(location, target)?.let { return@runAsync it }
            }
            OsmHelper.walkingRoute(
                location.latitude, location.longitude, target.lat, target.lon
            ) ?: throw TransitApiException("Nem sikerült útvonalat tervezni ide: ${target.shortName}.")
        }) { route ->
            onResult(route)
        }
    }

    private fun fetchBkkNearby(location: Location): List<TransitPlace> {
        val url = bkkUrl(
            "arrivals-and-departures-for-location",
            "lat=${location.latitude}",
            "lon=${location.longitude}",
            "radius=$BKK_RADIUS",
            "limit=12",
            "minutesAfter=40",
            "includeReferences=true"
        )
        val json = JSONObject(fetchText(url))
        val data = json.optJSONObject("data") ?: return emptyList()
        val references = data.optJSONObject("references") ?: JSONObject()
        val stopsRef = references.optJSONObject("stops") ?: JSONObject()
        val routesRef = references.optJSONObject("routes") ?: JSONObject()
        val list = data.optJSONArray("list") ?: JSONArray()

        val stopDepartures = linkedMapOf<String, MutableList<String>>()
        val stopIds = linkedSetOf<String>()

        for (i in 0 until list.length()) {
            val group = list.optJSONObject(i) ?: continue
            val routeId = group.optString("routeId")
            val route = routesRef.optJSONObject(routeId)
            val routeName = route?.optString("shortName").orEmpty()
            val vehicle = vehicleName(route?.optString("type"))
            val headsign = group.optString("headsign")
            val stopTimes = group.optJSONArray("stopTimes") ?: continue
            for (j in 0 until stopTimes.length()) {
                val stopTime = stopTimes.optJSONObject(j) ?: continue
                val stopId = stopTime.optString("stopId")
                if (stopId.isBlank()) continue
                stopIds.add(stopId)
                val departure = stopTime.optLong("predictedDepartureTime")
                    .takeIf { it > 0 } ?: stopTime.optLong("departureTime")
                val depText = formatDeparture(departure)
                val line = buildString {
                    if (vehicle.isNotBlank()) append("$vehicle ")
                    if (routeName.isNotBlank()) append("$routeName ")
                    append(headsign)
                    append(", $depText")
                }.trim()
                stopDepartures.getOrPut(stopId) { mutableListOf() }.add(line)
            }
        }

        return stopIds.mapNotNull { stopId ->
            val stop = stopsRef.optJSONObject(stopId) ?: return@mapNotNull null
            val name = stop.optString("name").ifBlank { return@mapNotNull null }
            val lat = stop.optDouble("lat")
            val lon = stop.optDouble("lon")
            TransitPlace(
                name = name,
                address = stop.optString("localityName").ifBlank { "BKK" },
                distanceMeters = dist(location, lat, lon),
                nextDepartures = stopDepartures[stopId]?.distinct()?.take(3).orEmpty()
            )
        }.sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
            .distinctBy { it.name }
            .take(8)
    }

    private fun fetchBkkStopsForLocation(location: Location): List<TransitPlace> {
        val url = bkkUrl(
            "stops-for-location",
            "lat=${location.latitude}",
            "lon=${location.longitude}",
            "radius=2500",
            "includeReferences=false"
        )
        val json = JSONObject(fetchText(url))
        val list = json.optJSONObject("data")?.optJSONArray("list") ?: JSONArray()
        val places = mutableListOf<TransitPlace>()
        for (i in 0 until list.length()) {
            val stop = list.optJSONObject(i) ?: continue
            val name = stop.optString("name")
            if (name.isBlank()) continue
            places.add(
                TransitPlace(
                    name = name,
                    address = stop.optString("localityName").ifBlank { "BKK" },
                    distanceMeters = dist(location, stop.optDouble("lat"), stop.optDouble("lon"))
                )
            )
        }
        return places.distinctBy { it.name }.sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
    }

    private fun fetchBkkRoute(origin: Location, target: GeoPlace): TransitRoute? {
        val url = bkkUrl(
            "plan-trip",
            "fromPlace=${origin.latitude},${origin.longitude}",
            "toPlace=${target.lat},${target.lon}",
            "mode=TRANSIT,WALK",
            "time=now"
        )
        val json = JSONObject(fetchText(url, timeoutMs = 25000))
        val plan = json.optJSONObject("data")
            ?.optJSONObject("entry")
            ?.optJSONObject("plan")
            ?: return null
        val itinerary = plan.optJSONArray("itineraries")?.optJSONObject(0) ?: return null
        val durationSec = itinerary.optInt("duration", 0)
        val duration = formatDuration(durationSec)
        val legs = itinerary.optJSONArray("legs") ?: JSONArray()
        val steps = mutableListOf<TransitRouteStep>()
        for (i in 0 until legs.length()) {
            val leg = legs.optJSONObject(i) ?: continue
            val instruction = formatBkkLeg(leg)
            if (instruction.isNotBlank()) steps.add(TransitRouteStep(instruction))
        }
        if (steps.isEmpty()) return null
        val destinationName = target.shortName
        return TransitRoute("Útvonal ide: $destinationName", duration, steps)
    }

    private fun formatBkkLeg(leg: JSONObject): String {
        return when (leg.optString("mode")) {
            "WALK" -> {
                val from = leg.optJSONObject("from")?.optString("name").orEmpty()
                val to = leg.optJSONObject("to")?.optString("name").orEmpty()
                val durationMin = (leg.optLong("duration", 0L) / 60000L).toInt().coerceAtLeast(1)
                when {
                    to.isNotBlank() -> "Gyalog $durationMin perc a $to megállóig."
                    from.isNotBlank() -> "Gyalog $durationMin perc innen: $from."
                    else -> "Gyalog $durationMin perc."
                }
            }
            "BUS", "TRAM", "SUBWAY", "TROLLEYBUS", "RAIL", "FERRY" -> {
                val vehicle = vehicleName(leg.optString("mode"))
                val line = leg.optString("routeShortName")
                val from = leg.optJSONObject("from")?.optString("name").orEmpty()
                val to = leg.optJSONObject("to")?.optString("name").orEmpty()
                val headsign = leg.optString("headsign")
                buildString {
                    append(vehicle)
                    if (line.isNotBlank()) append(" $line")
                    append(" járat")
                    if (headsign.isNotBlank()) append(", $headsign irány")
                    if (from.isNotBlank()) append(". Felszállás: $from")
                    if (to.isNotBlank()) append(". Leszállás: $to")
                }.trim()
            }
            else -> ""
        }
    }

    private fun vehicleName(type: String?): String = when (type?.uppercase(Locale.ROOT)) {
        "TRAM" -> "Villamos"
        "BUS" -> "Busz"
        "SUBWAY" -> "Metró"
        "TROLLEYBUS" -> "Trolibusz"
        "RAIL" -> "Vonat"
        "FERRY" -> "Hajó"
        else -> "Járat"
    }

    private fun formatDeparture(epochSeconds: Long): String {
        if (epochSeconds <= 0L) return "hamarosan"
        val now = System.currentTimeMillis()
        val target = epochSeconds * 1000L
        val diffMin = ((target - now) / 60000L).toInt()
        return when {
            diffMin <= 0 -> "most"
            diffMin == 1 -> "1 perc múlva"
            diffMin < 60 -> "$diffMin perc múlva"
            else -> {
                val fmt = SimpleDateFormat("HH:mm", Locale("hu", "HU")).apply {
                    timeZone = TimeZone.getTimeZone("Europe/Budapest")
                }
                fmt.format(Date(target))
            }
        }
    }

    private fun formatDuration(seconds: Int): String {
        val minutes = ((seconds + 59) / 60).coerceAtLeast(1)
        return if (minutes < 60) "$minutes perc" else "${minutes / 60} óra ${minutes % 60} perc"
    }

    private fun isInBudapest(location: Location): Boolean =
        isInBudapest(location.latitude, location.longitude)

    private fun isInBudapest(lat: Double, lon: Double): Boolean =
        lat in BUDAPEST_MIN_LAT..BUDAPEST_MAX_LAT && lon in BUDAPEST_MIN_LON..BUDAPEST_MAX_LON

    private fun dist(origin: Location, lat: Double, lon: Double): Int {
        val result = FloatArray(1)
        Location.distanceBetween(origin.latitude, origin.longitude, lat, lon, result)
        return result[0].toInt()
    }

    private fun bkkUrl(endpoint: String, vararg params: String): String {
        val query = buildList {
            add("key=$BKK_KEY")
            add("version=2")
            add("appVersion=SuperDL/1.6")
            addAll(params)
        }.joinToString("&")
        return "$BKK_BASE/$endpoint?$query"
    }

    private fun fetchText(url: String, timeoutMs: Int = 12000): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.setRequestProperty("User-Agent", "SuperDL/1.6")
        if (connection.responseCode !in 200..299) {
            throw TransitApiException("Tömegközlekedés lekérdezés sikertelen.")
        }
        return connection.inputStream.bufferedReader().readText()
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
            } catch (e: TransitApiException) {
                Handler(Looper.getMainLooper()).post { onError(e.message ?: "Tömegközlekedés hiba.") }
            } catch (_: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onError("Tömegközlekedés lekérdezés sikertelen. Ellenőrizd az internetkapcsolatot.")
                }
            }
        }.start()
    }

    private fun getLastLocation(context: Context): Location? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).mapNotNull { provider ->
            try {
                manager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            }
        }.maxByOrNull { it.time }
    }
}