package com.superdl.launcher.gps

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object GpsOverpassHelper {

    private const val USER_AGENT = "SuperDL/1.15 (vak-barat launcher; korosmezey.david.richard@gmail.com)"
    private const val RADIUS_METERS = 300
    private const val MAX_RESULTS = 35

    fun fetchNearbyCrossings(lat: Double, lon: Double, radiusM: Int = 20): List<CrossingPoint> {
        val query = """
            [out:json][timeout:10];
            (
              node(around:$radiusM,$lat,$lon)[highway=crossing];
              node(around:$radiusM,$lat,$lon)[crossing];
            );
            out body;
        """.trimIndent().replace("\n", "")
        val url = "https://overpass-api.de/api/interpreter"
        return try {
            val body = fetchPost(url, "data=${URLEncoder.encode(query, "UTF-8")}")
            val elements = JSONObject(body).optJSONArray("elements") ?: JSONArray()
            buildList {
                for (i in 0 until elements.length()) {
                    val item = elements.optJSONObject(i) ?: continue
                    if (item.optString("type") != "node") continue
                    val crossingLat = item.optDouble("lat")
                    val crossingLon = item.optDouble("lon")
                    if (crossingLat == 0.0 && crossingLon == 0.0) continue
                    add(CrossingPoint(crossingLat, crossingLon))
                }
            }.distinctBy { "${it.latitude}_${it.longitude}" }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun fetchNearbyPois(lat: Double, lon: Double): List<GpsPoiRaw> {
        val query = """
            [out:json][timeout:15];
            (
              node(around:$RADIUS_METERS,$lat,$lon)[shop];
              node(around:$RADIUS_METERS,$lat,$lon)[amenity];
              node(around:$RADIUS_METERS,$lat,$lon)[highway=bus_stop];
              node(around:$RADIUS_METERS,$lat,$lon)[public_transport=platform];
              node(around:$RADIUS_METERS,$lat,$lon)[railway=station];
              way(around:$RADIUS_METERS,$lat,$lon)[shop];
              way(around:$RADIUS_METERS,$lat,$lon)[amenity];
            );
            out center $MAX_RESULTS;
        """.trimIndent().replace("\n", "")
        val url = "https://overpass-api.de/api/interpreter"
        val body = fetchPost(url, "data=${URLEncoder.encode(query, "UTF-8")}")
        val elements = JSONObject(body).optJSONArray("elements") ?: JSONArray()
        val pois = mutableListOf<GpsPoiRaw>()
        for (i in 0 until elements.length()) {
            val item = elements.optJSONObject(i) ?: continue
            val tags = item.optJSONObject("tags") ?: continue
            if (!isRelevant(tags)) continue
            val name = tags.optString("name").ifBlank {
                tags.optString("brand").ifBlank { tags.optString("operator") }
            }
            if (name.isBlank()) continue
            val coords = extractCoordinates(item) ?: continue
            val category = categoryLabel(tags)
            val id = "${item.optString("type")}_${item.optLong("id")}"
            pois.add(
                GpsPoiRaw(
                    id = id,
                    name = name,
                    category = category,
                    latitude = coords.first,
                    longitude = coords.second
                )
            )
        }
        return pois.distinctBy { "${it.name}_${it.latitude}_${it.longitude}" }
    }

    private fun isRelevant(tags: JSONObject): Boolean {
        if (tags.has("shop")) return true
        if (tags.has("highway") && tags.optString("highway") == "bus_stop") return true
        if (tags.has("public_transport")) return true
        if (tags.has("railway")) return true
        val amenity = tags.optString("amenity")
        return amenity in RELEVANT_AMENITIES
    }

    private val RELEVANT_AMENITIES = setOf(
        "restaurant", "cafe", "fast_food", "pharmacy", "bank", "post_office",
        "pub", "bar", "dentist", "doctors", "clinic", "hospital", "library",
        "fuel", "marketplace", "food_court", "ice_cream", "bakery", "bicycle_parking"
    )

    private fun categoryLabel(tags: JSONObject): String {
        tags.optString("shop").takeIf { it.isNotBlank() }?.let { return shopLabel(it) }
        if (tags.optString("highway") == "bus_stop") return "megálló"
        if (tags.has("public_transport") || tags.has("railway")) return "tömegközlekedés"
        return amenityLabel(tags.optString("amenity"))
    }

    private fun shopLabel(value: String): String = when (value) {
        "supermarket", "convenience", "general", "mall", "department_store" -> "bolt"
        "bakery" -> "pékség"
        "clothes", "shoes", "hairdresser" -> "üzlet"
        "chemist" -> "gyógyszertár"
        else -> "bolt"
    }

    private fun amenityLabel(value: String): String = when (value) {
        "restaurant", "fast_food", "food_court" -> "étterem"
        "cafe", "ice_cream" -> "kávézó"
        "pharmacy" -> "gyógyszertár"
        "bank", "atm" -> "bank"
        "post_office" -> "posta"
        "fuel" -> "benzinkút"
        "hospital", "clinic", "doctors", "dentist" -> "egészségügy"
        "library" -> "könyvtár"
        "pub", "bar" -> "bár"
        "marketplace" -> "piac"
        else -> "létesítmény"
    }

    private fun extractCoordinates(item: JSONObject): Pair<Double, Double>? {
        val type = item.optString("type")
        if (type == "node") {
            val lat = item.optDouble("lat")
            val lon = item.optDouble("lon")
            if (lat != 0.0 || lon != 0.0) return lat to lon
        }
        val center = item.optJSONObject("center") ?: return null
        val lat = center.optDouble("lat")
        val lon = center.optDouble("lon")
        return if (lat != 0.0 || lon != 0.0) lat to lon else null
    }

    private fun fetchPost(url: String, formBody: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.doOutput = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.outputStream.use { it.write(formBody.toByteArray(Charsets.UTF_8)) }
        if (connection.responseCode !in 200..299) {
            throw GpsRadarException("OpenStreetMap lekérdezés sikertelen.")
        }
        return connection.inputStream.bufferedReader().readText()
    }
}

data class CrossingPoint(
    val latitude: Double,
    val longitude: Double
)

class GpsRadarException(message: String) : Exception(message)