package com.superdl.launcher.radio

import android.content.Context
import com.superdl.launcher.storage.JsonPrefsHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * IDŐZÍTETT RÁDIÓFELVÉTEL — egy beütemezett műsor.
 *
 * MIÉRT KELL: a vak felhasználó ugyanúgy lemarad a kedvenc műsoráról, mint
 * bárki más — csak neki nincs se videomagnója, se podcast-appja, ami helyette
 * rögzítené. Ez a funkció az ő magnója.
 *
 * MIÉRT ÍGY TÁROLJUK AZ ÁLLOMÁST: nem hivatkozásként, hanem a NEVÉVEL ÉS A
 * STREAM-CÍMÉVEL együtt. Ha a felhasználó közben törli a kedvencek közül az
 * adót, az időzítés akkor is működjön — különben némán elmaradna a felvétel,
 * és a felhasználó csak akkor venné észre, amikor már késő.
 */
data class RadioScheduleEntry(
    val id: Int,
    val stationName: String,
    val streamUrl: String,
    val hour: Int,
    val minute: Int,
    val durationMinutes: Int,
    /** Üres = egyszeri. Egyébként a Calendar.DAY_OF_WEEK értékei (1=vasárnap). */
    val days: Set<Int> = emptySet(),
    val enabled: Boolean = true
) {
    fun isOneTime(): Boolean = days.isEmpty()

    fun isActiveOnDay(dayOfWeek: Int): Boolean = days.isEmpty() || dayOfWeek in days

    fun speakTime(): String = "%d óra %02d perc".format(hour, minute)

    fun speakRepeat(): String = when {
        days.isEmpty() -> "egyszer"
        days.size == 7 -> "minden nap"
        days == setOf(2, 3, 4, 5, 6) -> "hétköznap"
        days == setOf(1, 7) -> "hétvégén"
        else -> days.sorted().joinToString(", ") { dayName(it) }
    }

    fun speakPreview(): String =
        "$stationName, ${speakTime()}, $durationMinutes perc, ${speakRepeat()}" +
            if (enabled) "" else ", kikapcsolva"

    private fun dayName(day: Int): String = when (day) {
        1 -> "vasárnap"
        2 -> "hétfő"
        3 -> "kedd"
        4 -> "szerda"
        5 -> "csütörtök"
        6 -> "péntek"
        else -> "szombat"
    }
}

object RadioScheduleStore {

    private const val PREFS = "superdl_radio"
    private const val KEY_ITEMS = "radio_schedule"
    private const val KEY_SCHEMA = "radio_schedule_schema"
    private const val SCHEMA_VERSION = 1
    private const val MAX_ENTRIES = 20

    fun getAll(context: Context): List<RadioScheduleEntry> {
        val arr = JsonPrefsHelper.readJsonArray(context, PREFS, KEY_ITEMS, KEY_SCHEMA, SCHEMA_VERSION)
        val list = mutableListOf<RadioScheduleEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val daysArr = o.optJSONArray("days")
            val days = mutableSetOf<Int>()
            if (daysArr != null) {
                for (j in 0 until daysArr.length()) days.add(daysArr.optInt(j))
            }
            list.add(
                RadioScheduleEntry(
                    id = o.optInt("id"),
                    stationName = o.optString("stationName"),
                    streamUrl = o.optString("streamUrl"),
                    hour = o.optInt("hour"),
                    minute = o.optInt("minute"),
                    durationMinutes = o.optInt("durationMinutes", 30),
                    days = days,
                    enabled = o.optBoolean("enabled", true)
                )
            )
        }
        return list.sortedWith(compareBy({ it.hour }, { it.minute }))
    }

    fun add(context: Context, entry: RadioScheduleEntry): RadioScheduleEntry? {
        val current = getAll(context).toMutableList()
        if (current.size >= MAX_ENTRIES) return null
        val nextId = (current.maxOfOrNull { it.id } ?: 0) + 1
        val saved = entry.copy(id = nextId)
        current.add(saved)
        save(context, current)
        return saved
    }

    fun delete(context: Context, id: Int) {
        save(context, getAll(context).filterNot { it.id == id })
    }

    fun setEnabled(context: Context, id: Int, enabled: Boolean) {
        save(context, getAll(context).map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun get(context: Context, id: Int): RadioScheduleEntry? =
        getAll(context).firstOrNull { it.id == id }

    private fun save(context: Context, entries: List<RadioScheduleEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            val days = JSONArray()
            e.days.sorted().forEach { days.put(it) }
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("stationName", e.stationName)
                    .put("streamUrl", e.streamUrl)
                    .put("hour", e.hour)
                    .put("minute", e.minute)
                    .put("durationMinutes", e.durationMinutes)
                    .put("days", days)
                    .put("enabled", e.enabled)
            )
        }
        JsonPrefsHelper.saveJsonArray(context, PREFS, KEY_ITEMS, KEY_SCHEMA, SCHEMA_VERSION, arr)
    }
}
