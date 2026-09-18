package com.superdl.launcher.assistant

import android.content.Context
import com.superdl.launcher.storage.JsonPrefsHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ElenaCustomWakePhrase(
    val id: String,
    val phrase: String,
    val audioPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

object ElenaWakeStore {

    private const val PREFS = "elena_wake_prefs"
    private const val KEY_LISTEN_ENABLED = "listen_enabled"
    private const val KEY_LAST_STOP = "last_stop_reason"
    private const val KEY_LAST_STOP_AT = "last_stop_at"
    private const val KEY_CUSTOM_PHRASES = "custom_phrases"
    private const val SCHEMA_VERSION_KEY = "schema_version"
    private const val CURRENT_SCHEMA = 1

    @Volatile
    var listeningPaused: Boolean = false

    fun isListenEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_LISTEN_ENABLED, false)

    fun setListenEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LISTEN_ENABLED, enabled)
            .apply()
    }

    /**
     * MIÉRT ÁLLT LE MAGÁTÓL A FIGYELÉS.
     *
     * A 2026-09-18-i hiba azért volt nehezen megfogható, mert a figyelő
     * kikapcsolta magát, és a hibajelentésben ennek NYOMA SEM VOLT: minden
     * engedély megvolt, összeomlás nem történt, a napló tiszta volt. Amit a
     * program magától tesz, azt le is kell írnia, különben a jelentés nem
     * jelentés.
     */
    fun noteSelfStop(context: Context, reason: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_STOP, reason)
            .putLong(KEY_LAST_STOP_AT, System.currentTimeMillis())
            .apply()
    }

    /** A legutóbbi önkikapcsolás leírása, vagy null, ha még nem volt ilyen. */
    fun lastSelfStop(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val reason = prefs.getString(KEY_LAST_STOP, null)?.takeIf { it.isNotBlank() }
            ?: return null
        val at = prefs.getLong(KEY_LAST_STOP_AT, 0L)
        if (at <= 0L) return reason
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(at))
        return "$stamp — $reason"
    }

    fun toggleListenEnabled(context: Context): Boolean {
        val next = !isListenEnabled(context)
        setListenEnabled(context, next)
        return next
    }

    fun customPhrases(context: Context): List<ElenaCustomWakePhrase> {
        val array = JsonPrefsHelper.readJsonArray(
            context,
            PREFS,
            KEY_CUSTOM_PHRASES,
            SCHEMA_VERSION_KEY,
            CURRENT_SCHEMA
        )
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val phrase = obj.optString("phrase").trim()
            if (phrase.isBlank()) return@mapNotNull null
            ElenaCustomWakePhrase(
                id = obj.optString("id", "phrase_$index"),
                phrase = phrase,
                audioPath = obj.optString("audioPath").takeIf { it.isNotBlank() },
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }

    fun addCustomPhrase(context: Context, phrase: String, audioPath: String? = null): Boolean {
        val normalized = VoiceAssistantHelper.normalize(phrase)
        if (normalized.length < 3) return false
        val phrases = customPhrases(context).toMutableList()
        if (phrases.any { VoiceAssistantHelper.normalize(it.phrase) == normalized }) return false
        phrases.add(
            ElenaCustomWakePhrase(
                id = "custom_${System.currentTimeMillis()}",
                phrase = normalized,
                audioPath = audioPath
            )
        )
        saveCustomPhrases(context, phrases)
        return true
    }

    fun removeCustomPhrase(context: Context, id: String): Boolean {
        val phrases = customPhrases(context)
        val target = phrases.find { it.id == id } ?: return false
        target.audioPath?.let { path ->
            runCatching { File(path).delete() }
        }
        saveCustomPhrases(context, phrases.filter { it.id != id })
        return true
    }

    fun clearCustomPhrases(context: Context) {
        customPhrases(context).forEach { phrase ->
            phrase.audioPath?.let { path ->
                runCatching { File(path).delete() }
            }
        }
        saveCustomPhrases(context, emptyList())
    }

    fun customWakeDir(context: Context): File =
        File(context.filesDir, "elena_wake_samples").apply { mkdirs() }

    private fun saveCustomPhrases(context: Context, phrases: List<ElenaCustomWakePhrase>) {
        val array = JSONArray()
        for (phrase in phrases) {
            array.put(
                JSONObject()
                    .put("id", phrase.id)
                    .put("phrase", phrase.phrase)
                    .put("createdAt", phrase.createdAt)
                    .apply {
                        phrase.audioPath?.let { put("audioPath", it) }
                    }
            )
        }
        JsonPrefsHelper.saveJsonArray(
            context,
            PREFS,
            KEY_CUSTOM_PHRASES,
            SCHEMA_VERSION_KEY,
            CURRENT_SCHEMA,
            array
        )
    }
}