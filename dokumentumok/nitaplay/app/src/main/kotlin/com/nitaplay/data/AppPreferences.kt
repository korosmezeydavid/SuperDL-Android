package com.nitaplay.data

import android.content.Context
import com.nitaplay.ui.theme.AppThemeId

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var themeId: AppThemeId
        get() = try {
            AppThemeId.valueOf(prefs.getString(KEY_THEME, AppThemeId.PURPLE_NIGHT.name)!!)
        } catch (_: Exception) {
            AppThemeId.PURPLE_NIGHT
        }
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    var useArtworkColors: Boolean
        get() = prefs.getBoolean(KEY_ARTWORK_COLORS, false)
        set(value) = prefs.edit().putBoolean(KEY_ARTWORK_COLORS, value).apply()

    var animationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ANIMATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_ANIMATIONS, value).apply()

    var seekStepSec: Int
        get() = prefs.getInt(KEY_SEEK_STEP, 10)
        set(value) = prefs.edit().putInt(KEY_SEEK_STEP, value).apply()

    var playMode: PlayMode
        get() = try {
            PlayMode.valueOf(prefs.getString(KEY_PLAY_MODE, PlayMode.SEQUENTIAL.name)!!)
        } catch (_: Exception) {
            PlayMode.SEQUENTIAL
        }
        set(value) = prefs.edit().putString(KEY_PLAY_MODE, value.name).apply()

    var eqProfile: String
        get() = prefs.getString(KEY_EQ_PROFILE, EQ_OFF) ?: EQ_OFF
        set(value) = prefs.edit().putString(KEY_EQ_PROFILE, value).apply()

    /** Custom 5-band gains in millibels, empty = use profile */
    var eqBands: FloatArray
        get() {
            val raw = prefs.getString(KEY_EQ_BANDS, null) ?: return FloatArray(5) { 0f }
            return raw.split(',').mapNotNull { it.toFloatOrNull() }.let { list ->
                if (list.size == 5) list.toFloatArray() else FloatArray(5) { 0f }
            }
        }
        set(value) = prefs.edit().putString(KEY_EQ_BANDS, value.joinToString(",")).apply()

    var useCustomEq: Boolean
        get() = prefs.getBoolean(KEY_EQ_CUSTOM, false)
        set(value) = prefs.edit().putBoolean(KEY_EQ_CUSTOM, value).apply()

    var musicSource: MusicSource
        get() = try {
            MusicSource.valueOf(prefs.getString(KEY_SOURCE, MusicSource.PHONE.name)!!)
        } catch (_: Exception) {
            MusicSource.PHONE
        }
        set(value) = prefs.edit().putString(KEY_SOURCE, value.name).apply()

    var defaultSleepMinutes: Int
        get() = prefs.getInt(KEY_SLEEP_DEFAULT, 30)
        set(value) = prefs.edit().putInt(KEY_SLEEP_DEFAULT, value).apply()

    fun savePosition(trackId: Long, positionMs: Long) {
        val e = prefs.edit()
        if (positionMs < 3000L) {
            e.remove(KEY_LAST_TRACK_ID).remove(KEY_LAST_POSITION_MS)
        } else {
            e.putLong(KEY_LAST_TRACK_ID, trackId).putLong(KEY_LAST_POSITION_MS, positionMs)
        }
        e.apply()
    }

    fun getSavedPosition(trackId: Long): Long {
        val savedId = prefs.getLong(KEY_LAST_TRACK_ID, -1L)
        return if (savedId == trackId) prefs.getLong(KEY_LAST_POSITION_MS, 0L) else 0L
    }

    fun getLastTrackId(): Long = prefs.getLong(KEY_LAST_TRACK_ID, -1L)

    fun getLastPositionMs(): Long = prefs.getLong(KEY_LAST_POSITION_MS, 0L)

    fun clearPosition() {
        prefs.edit().remove(KEY_LAST_TRACK_ID).remove(KEY_LAST_POSITION_MS).apply()
    }

    companion object {
        private const val PREFS = "nitaplay_prefs"
        private const val KEY_THEME = "theme"
        private const val KEY_ARTWORK_COLORS = "artwork_colors"
        private const val KEY_ANIMATIONS = "animations"
        private const val KEY_SEEK_STEP = "seek_step"
        private const val KEY_PLAY_MODE = "play_mode"
        private const val KEY_EQ_PROFILE = "eq_profile"
        private const val KEY_EQ_BANDS = "eq_bands"
        private const val KEY_EQ_CUSTOM = "eq_custom"
        private const val KEY_SOURCE = "music_source"
        private const val KEY_SLEEP_DEFAULT = "sleep_default"
        private const val KEY_LAST_TRACK_ID = "last_track_id"
        private const val KEY_LAST_POSITION_MS = "last_position_ms"
        const val EQ_OFF = "Kikapcsolva"
        val EQ_PROFILES = listOf(
            EQ_OFF, "Normál", "Pop", "Rock", "Jazz", "Klasszikus",
            "Basszus-erősítés", "Beszéd"
        )
        val SEEK_STEPS = listOf(5, 10, 30, 60)
        val SLEEP_OPTIONS = listOf(0, 15, 30, 45, 60, 90)
    }
}
