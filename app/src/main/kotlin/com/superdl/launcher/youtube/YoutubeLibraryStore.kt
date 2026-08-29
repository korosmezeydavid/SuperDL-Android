package com.superdl.launcher.youtube

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * YOUTUBE KÖNYVTÁR — pozíciók, kedvenc videók, követett csatornák.
 *
 * Ez adja a "prémium" érzést: a program EMLÉKSZIK. Ha félbehagysz egy hosszú
 * videót, onnan folytatja. Amit szeretsz, egy mozdulattal visszakeresed.
 */
object YoutubeLibraryStore {

    private const val PREFS = "superdl_youtube"
    private const val KEY_FAV_VIDEOS = "fav_videos"
    private const val KEY_FAV_CHANNELS = "fav_channels"
    private const val KEY_LAST_VIDEO = "last_video"

    /** Ennél rövidebb videónál nincs értelme pozíciót menteni. */
    private const val MIN_RESUME_SECONDS = 90

    /** A legutolsó 10 másodpercet már "végignézettnek" tekintjük. */
    private const val END_TOLERANCE_SECONDS = 10

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── POZÍCIÓ-MENTÉS ──────────────────────────────────────────────────────

    /**
     * Hol tartottunk ebben a videóban.
     * @return másodperc, vagy 0 ha nincs mentve (vagy már végignéztük)
     */
    fun getPosition(context: Context, videoId: String): Int =
        prefs(context).getInt("pos_$videoId", 0)

    /**
     * A pozíció mentése.
     * A videó VÉGÉN töröljük a mentést — ha valaki végignézte, ne ajánljuk fel
     * a folytatást a legutolsó másodperctől.
     */
    fun savePosition(context: Context, videoId: String, positionSec: Int, durationSec: Int) {
        val p = prefs(context)
        if (durationSec in 1 until MIN_RESUME_SECONDS) {
            p.edit().remove("pos_$videoId").apply()
            return
        }
        val nearEnd = durationSec > 0 && positionSec >= durationSec - END_TOLERANCE_SECONDS
        if (positionSec < 15 || nearEnd) {
            p.edit().remove("pos_$videoId").apply()
        } else {
            p.edit().putInt("pos_$videoId", positionSec).apply()
        }
    }

    /** A legutóbb nézett videó — a "folytatás" ponthoz. */
    fun saveLastVideo(context: Context, video: YoutubeVideo) {
        prefs(context).edit().putString(KEY_LAST_VIDEO, videoToJson(video).toString()).apply()
    }

    fun getLastVideo(context: Context): YoutubeVideo? = try {
        prefs(context).getString(KEY_LAST_VIDEO, null)?.let { jsonToVideo(JSONObject(it)) }
    } catch (_: Exception) {
        null
    }

    // ── KEDVENC VIDEÓK ──────────────────────────────────────────────────────

    fun getFavoriteVideos(context: Context): List<YoutubeVideo> {
        return try {
            val raw = prefs(context).getString(KEY_FAV_VIDEOS, null) ?: return emptyList()
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { jsonToVideo(array.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isFavoriteVideo(context: Context, videoId: String): Boolean =
        getFavoriteVideos(context).any { it.videoId == videoId }

    /**
     * Kedvencnek jelölés vagy levétel.
     * @return igaz, ha MOSTANTÓL kedvenc
     */
    fun toggleFavoriteVideo(context: Context, video: YoutubeVideo): Boolean {
        val current = getFavoriteVideos(context).toMutableList()
        val existing = current.indexOfFirst { it.videoId == video.videoId }
        val nowFavorite: Boolean
        if (existing >= 0) {
            current.removeAt(existing)
            nowFavorite = false
        } else {
            // A legutóbb hozzáadott kerül ELŐRE — azt keresi az ember.
            current.add(0, video)
            nowFavorite = true
        }
        saveVideos(context, KEY_FAV_VIDEOS, current)
        return nowFavorite
    }

    fun removeFavoriteVideo(context: Context, videoId: String) {
        saveVideos(
            context, KEY_FAV_VIDEOS,
            getFavoriteVideos(context).filterNot { it.videoId == videoId }
        )
    }

    // ── KÖVETETT CSATORNÁK ──────────────────────────────────────────────────

    fun getFavoriteChannels(context: Context): List<String> {
        return try {
            val raw = prefs(context).getString(KEY_FAV_CHANNELS, null) ?: return emptyList()
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isFavoriteChannel(context: Context, channel: String): Boolean =
        getFavoriteChannels(context).any { it.equals(channel, ignoreCase = true) }

    fun toggleFavoriteChannel(context: Context, channel: String): Boolean {
        if (channel.isBlank()) return false
        val current = getFavoriteChannels(context).toMutableList()
        val existing = current.indexOfFirst { it.equals(channel, ignoreCase = true) }
        val nowFavorite: Boolean
        if (existing >= 0) {
            current.removeAt(existing)
            nowFavorite = false
        } else {
            current.add(0, channel)
            nowFavorite = true
        }
        val array = JSONArray()
        current.forEach { array.put(it) }
        prefs(context).edit().putString(KEY_FAV_CHANNELS, array.toString()).apply()
        return nowFavorite
    }

    // ── Segédek ─────────────────────────────────────────────────────────────

    private fun saveVideos(context: Context, key: String, videos: List<YoutubeVideo>) {
        val array = JSONArray()
        // Legfeljebb 200 elemet tartunk meg, hogy ne hízzon vég nélkül.
        videos.take(200).forEach { array.put(videoToJson(it)) }
        prefs(context).edit().putString(key, array.toString()).apply()
    }

    private fun videoToJson(v: YoutubeVideo): JSONObject = JSONObject().apply {
        put("id", v.videoId)
        put("cim", v.title)
        put("csatorna", v.channel)
        put("hossz", v.durationSeconds)
    }

    private fun jsonToVideo(o: JSONObject): YoutubeVideo? {
        val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
        return YoutubeVideo(
            videoId = id,
            title = o.optString("cim", "Névtelen videó"),
            channel = o.optString("csatorna", ""),
            durationSeconds = o.optInt("hossz", 0)
        )
    }
}
