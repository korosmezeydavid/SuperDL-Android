package com.superdl.launcher.youtube

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object YoutubeStreamResolver {

    private val PIPED_INSTANCES = listOf(
        "https://api.piped.private.coffee",
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de"
    )

    fun resolveAudioStreamUrl(videoId: String): String? {
        for (base in PIPED_INSTANCES) {
            try {
                val url = resolveFromPiped("$base/streams/$videoId")
                if (!url.isNullOrBlank()) return url
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private fun resolveFromPiped(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", "SuperDL/1.8")
        }
        if (connection.responseCode !in 200..299) return null
        val body = connection.inputStream.bufferedReader().readText()
        val root = JSONObject(body)
        val audioStreams = root.optJSONArray("audioStreams") ?: return null
        var bestUrl: String? = null
        var bestBitrate = -1
        for (i in 0 until audioStreams.length()) {
            val stream = audioStreams.optJSONObject(i) ?: continue
            val streamUrl = stream.optString("url")
            if (streamUrl.isBlank()) continue
            val bitrate = stream.optInt("bitrate", 0)
            if (bitrate >= bestBitrate) {
                bestBitrate = bitrate
                bestUrl = streamUrl
            }
        }
        return bestUrl ?: audioStreams.optJSONObject(0)?.optString("url")?.ifBlank { null }
    }
}