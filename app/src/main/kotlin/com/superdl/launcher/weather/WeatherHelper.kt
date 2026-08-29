package com.superdl.launcher.weather

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class WeatherInfo(
    val location: String,
    val condition: String,
    val temperatureC: Int,
    val humidity: Int,
    val windKmh: Int
) {
    fun speakSummary(): String {
        val loc = if (location.isNotBlank()) "$location. " else ""
        return "${loc}${condition}. Hőmérséklet: $temperatureC fok. Páratartalom: $humidity százalék. Szél: $windKmh kilométer per óra."
    }
}

object WeatherHelper {

    /**
     * IDŐJÁRÁS LEKÉRÉSE.
     *
     * @param context ha megadod, a TELEFON SAJÁT HELYZETÉT használjuk
     * @param city ha megadod, ezt a várost kérdezzük le
     *
     * MIÉRT KELL A HELYZET — EGY VALÓDI HIBA JAVÍTÁSA (2026-08-19):
     * Korábban helyzet nélkül kérdeztük le az időjárást. Ilyenkor a
     * szolgáltatás az INTERNETKAPCSOLAT alapján találgat — és a mobilhálózat
     * kijárata gyakran egészen más városban van, mint a felhasználó.
     * A fejlesztő telefonja például SZÉKESFEHÉRVÁRT mondott, miközben
     * Budapesten volt.
     *
     * Ez nem apróság: az időjárás pont az a funkció, ahol a HELY a lényeg.
     * Egy vak felhasználó nem tudja "ránézésre" ellenőrizni, hogy a program
     * jó városról beszél-e — elhiszi, és aszerint öltözik.
     */
    fun fetch(
        context: android.content.Context? = null,
        city: String? = null,
        onResult: (WeatherInfo) -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            try {
                val info = when {
                    !city.isNullOrBlank() -> fetchCity(city)
                    else -> {
                        val coords = context?.let { lastKnownCoordinates(it) }
                        if (coords != null) fetchCoordinates(coords.first, coords.second)
                        else fetchAuto()
                    }
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onResult(info)
                }
            } catch (_: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onError("Időjárás lekérése sikertelen. Ellenőrizd az internetkapcsolatot.")
                }
            }
        }.start()
    }

    private fun fetchAuto(): WeatherInfo = parseWttrJson(httpGet("https://wttr.in/?format=j1&lang=hu"))

    /**
     * IDŐJÁRÁS PONTOS KOORDINÁTÁRA.
     * A szolgáltatás elfogadja a szélesség és hosszúság párost — így nem
     * kell találgatnia az internetkapcsolatból.
     */
    private fun fetchCoordinates(lat: Double, lon: Double): WeatherInfo {
        val point = String.format(Locale.US, "%.4f,%.4f", lat, lon)
        return parseWttrJson(httpGet("https://wttr.in/$point?format=j1&lang=hu"))
    }

    /**
     * A TELEFON HELYZETE — két forrásból, ebben a sorrendben.
     *
     * 1. A rendszer utolsó ismert helyzete: ez a legfrissebb.
     * 2. A program saját, korábban mentett helyzete: ez akkor is megvan,
     *    ha a helymeghatározás épp ki van kapcsolva.
     *
     * Ha egyik sincs, null — akkor marad a régi, találgatós módszer.
     */
    private fun lastKnownCoordinates(context: android.content.Context): Pair<Double, Double>? {
        // 1. A rendszer utolsó ismert helyzete.
        try {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) {
                val manager = context.getSystemService(android.content.Context.LOCATION_SERVICE)
                    as? android.location.LocationManager
                val providers = listOf(
                    android.location.LocationManager.GPS_PROVIDER,
                    android.location.LocationManager.NETWORK_PROVIDER
                )
                providers.forEach { provider ->
                    val location = manager?.getLastKnownLocation(provider)
                    if (location != null) return location.latitude to location.longitude
                }
            }
        } catch (_: Exception) {
        }

        // 2. A program saját mentett helyzete.
        try {
            val prefs = context.getSharedPreferences("last_location_store", android.content.Context.MODE_PRIVATE)
            val lat = prefs.getFloat("lat", 0f)
            val lon = prefs.getFloat("lon", 0f)
            if (lat != 0f && lon != 0f) return lat.toDouble() to lon.toDouble()
        } catch (_: Exception) {
        }
        return null
    }

    private fun fetchCity(city: String): WeatherInfo {
        val encoded = URLEncoder.encode(city, "UTF-8")
        return parseWttrJson(httpGet("https://wttr.in/$encoded?format=j1&lang=hu"))
    }

    private fun parseWttrJson(body: String): WeatherInfo {
        val root = JSONObject(body)
        val current = root.getJSONArray("current_condition").getJSONObject(0)
        val area = root.optJSONArray("nearest_area")?.optJSONObject(0)
        val location = area?.optJSONArray("areaName")?.optJSONObject(0)?.optString("value").orEmpty()
        val condition = current.optJSONArray("lang_hu")?.optJSONObject(0)?.optString("value")
            ?: current.optJSONArray("weatherDesc")?.optJSONObject(0)?.optString("value")
            ?: "Ismeretlen"
        return WeatherInfo(
            location = location,
            condition = condition.trim(),
            temperatureC = current.optString("temp_C", "0").toIntOrNull() ?: 0,
            humidity = current.optString("humidity", "0").toIntOrNull() ?: 0,
            windKmh = current.optString("windspeedKmph", "0").toIntOrNull() ?: 0
        )
    }

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", "SuperDL/1.8")
            setRequestProperty("Accept-Language", "hu-HU,hu;q=0.9")
        }
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${connection.responseCode}")
        }
        return connection.inputStream.bufferedReader().readText()
    }
}