package com.superdl.launcher.callfilter

import android.content.Context
import com.superdl.launcher.storage.JsonPrefsHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * A KISZŰRT HÍVÁSOK NAPLÓJA.
 *
 * MIÉRT KELL: eddig semmi nem jegyezte fel, kit szűrt ki a program. A
 * hívást elutasította, és néma maradt. Vagyis nem lehetett megtudni, ki
 * próbált elérni — pedig lehet, hogy pont az volt a fontos.
 *
 * MEDDIG ŐRIZZÜK: harminc nap (Alph döntése). Ennél régebbi hívást
 * visszakeresni már senki nem fog, a lista viszont hosszú lesz tőle.
 */
object FilteredCallStore {

    private const val PREFS = "superdl"
    private const val KEY = "szurt_hivasok"
    private const val KEY_SCHEMA = "szurt_hivasok_schema"
    private const val SCHEMA_VERSION = 1

    /** Harminc nap. Ennél régebbi tétel magától kiesik. */
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60_000L

    /** Felső korlát a méretre is: egy telemarketinges roham ne hízlalja el. */
    private const val MAX_ITEMS = 300

    fun all(context: Context): List<FilteredCall> {
        val out = mutableListOf<FilteredCall>()
        val array = JsonPrefsHelper.readJsonArray(
            context, PREFS, KEY, KEY_SCHEMA, SCHEMA_VERSION
        )
        val limit = System.currentTimeMillis() - MAX_AGE_MS
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val at = obj.optLong("at", 0L)
            if (at < limit) continue
            out.add(
                FilteredCall(
                    id = obj.optInt("id", 0),
                    number = obj.optString("number", ""),
                    name = obj.optString("name", ""),
                    at = at,
                    reason = obj.optString("reason", "egyeb")
                )
            )
        }
        return out.sortedByDescending { it.at }
    }

    fun count(context: Context): Int = all(context).size

    /** Hány hívást szűrt ki a program ma. A helyzetjelentés ezt mondja be. */
    fun countToday(context: Context): Int {
        val start = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        return all(context).count { it.at >= start }
    }

    fun add(context: Context, number: String, name: String, reason: String) {
        try {
            val current = all(context).toMutableList()
            val nextId = (current.maxOfOrNull { it.id } ?: 0) + 1
            current.add(
                FilteredCall(
                    id = nextId,
                    number = number.trim(),
                    name = name.trim(),
                    at = System.currentTimeMillis(),
                    reason = reason
                )
            )
            save(context, current)
        } catch (_: Throwable) {
            // A NAPLÓZÁS SOHA NE VIGYE MAGÁVAL A SZŰRÉST. Ez a hívás
            // fogadásának pillanatában fut; ha itt bármi elszáll, a hívás
            // kezelése a fontos, nem a feljegyzés.
        }
    }

    fun remove(context: Context, id: Int) {
        save(context, all(context).filterNot { it.id == id })
    }

    fun clear(context: Context) {
        JsonPrefsHelper.saveJsonArray(
            context, PREFS, KEY, KEY_SCHEMA, SCHEMA_VERSION, JSONArray()
        )
    }

    private fun save(context: Context, items: List<FilteredCall>) {
        val array = JSONArray()
        items.sortedByDescending { it.at }.take(MAX_ITEMS).forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("number", entry.number)
                    .put("name", entry.name)
                    .put("at", entry.at)
                    .put("reason", entry.reason)
            )
        }
        JsonPrefsHelper.saveJsonArray(
            context, PREFS, KEY, KEY_SCHEMA, SCHEMA_VERSION, array
        )
    }
}
