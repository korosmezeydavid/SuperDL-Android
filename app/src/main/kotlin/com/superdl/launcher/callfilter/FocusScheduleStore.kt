package com.superdl.launcher.callfilter

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Az időzített fókusz-szabályok tárolása.
 *
 * Több szabály is lehet egyszerre (pl. "éjszakai nyugalom" + "vasárnapi pihenő").
 * Ha több is érvényben van, a LEGSZIGORÚBB nyer — mert a felhasználó
 * szándéka nyilván az volt, hogy akkor tényleg ne zavarják.
 */
object FocusScheduleStore {

    private const val PREFS = "superdl_focus"
    private const val KEY_LIST = "schedules"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAll(context: Context): List<FocusSchedule> {
        val raw = prefs(context).getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val daysArray = o.optJSONArray("napok")
                FocusSchedule(
                    id = o.optString("id"),
                    name = o.optString("nev", "Fókusz"),
                    mode = CallFilterMode.fromId(o.optString("mod")),
                    startMinute = o.optInt("kezdes"),
                    endMinute = o.optInt("vege"),
                    days = buildSet {
                        if (daysArray != null) {
                            for (j in 0 until daysArray.length()) add(daysArray.getInt(j))
                        }
                    },
                    enabled = o.optBoolean("bekapcsolva", true)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, schedules: List<FocusSchedule>) {
        val array = JSONArray()
        schedules.forEach { s ->
            array.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("nev", s.name)
                    put("mod", s.mode.id)
                    put("kezdes", s.startMinute)
                    put("vege", s.endMinute)
                    put("napok", JSONArray().apply { s.days.forEach { put(it) } })
                    put("bekapcsolva", s.enabled)
                }
            )
        }
        prefs(context).edit().putString(KEY_LIST, array.toString()).apply()
    }

    fun add(context: Context, schedule: FocusSchedule) {
        save(context, getAll(context) + schedule)
    }

    fun remove(context: Context, id: String) {
        save(context, getAll(context).filterNot { it.id == id })
    }

    fun toggle(context: Context, id: String): Boolean {
        var newState = false
        val updated = getAll(context).map {
            if (it.id == id) {
                newState = !it.enabled
                it.copy(enabled = newState)
            } else it
        }
        save(context, updated)
        return newState
    }

    /**
     * MELYIK szabály van most érvényben, ha van ilyen.
     * Több egyidejű szabálynál a LEGSZIGORÚBB nyer.
     */
    fun activeSchedule(context: Context, now: Calendar = Calendar.getInstance()): FocusSchedule? =
        getAll(context)
            .filter { it.isActiveAt(now) }
            // A CallFilterMode felsorolásában a legszigorúbb van elöl
            // (TOTAL_DND), ezért a legkisebb sorszám a legszigorúbb.
            .minByOrNull { it.mode.ordinal }

    /** A most érvényes mód, vagy null ha nincs időzített szabály. */
    fun activeMode(context: Context): CallFilterMode? = activeSchedule(context)?.mode
}
