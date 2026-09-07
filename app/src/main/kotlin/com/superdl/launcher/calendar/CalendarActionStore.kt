package com.superdl.launcher.calendar

import android.content.Context
import com.superdl.launcher.storage.SafePrefs
import org.json.JSONObject

/**
 * MELYIK NAPTÁRI PROGRAMHOZ MILYEN MŰVELET TARTOZIK.
 *
 * MIÉRT KÜLÖN TÁROLÓ: a naptári bejegyzések az Android naptárszolgáltatójából
 * jönnek. Abba nem tudunk saját mezőt tenni — és nem is akarunk: ha a
 * felhasználó másik naptárprogramot is használ, a mi kiegészítésünk nem
 * ronthatja el az ő adatait. Ezért a hozzárendelés MELLETTE él, a program
 * azonosítójához kötve. Ugyanaz a minta, mint a CalendarReminderStore-nál.
 *
 * KÖVETKEZMÉNY, AMIT VÁLLALUNK: ha a felhasználó törli és újra felveszi a
 * programot, a hozzárendelés elveszik, mert az azonosító más lesz. Ez a
 * kisebbik rossz — a naptáradatok épsége fontosabb.
 */
object CalendarActionStore {

    private const val PREFS = "superdl_calendar_actions"

    private fun key(eventId: Long): String = "esemeny_$eventId"

    /** A programhoz rendelt művelet, vagy null. */
    fun get(context: Context, eventId: Long): CalendarAction? = try {
        val raw = SafePrefs.get(context, PREFS).getString(key(eventId), null)
        if (raw.isNullOrBlank()) null else CalendarAction.fromJson(JSONObject(raw))
    } catch (_: Exception) {
        null
    }

    fun set(context: Context, eventId: Long, action: CalendarAction) {
        try {
            SafePrefs.get(context, PREFS).edit()
                .putString(key(eventId), action.toJson().toString())
                .apply()
        } catch (_: Exception) {
        }
    }

    fun clear(context: Context, eventId: Long) {
        try {
            SafePrefs.get(context, PREFS).edit().remove(key(eventId)).apply()
        } catch (_: Exception) {
        }
    }

    fun has(context: Context, eventId: Long): Boolean = get(context, eventId) != null

    /**
     * Egy mondat a program felolvasásához: van-e hozzárendelt művelet.
     * Enélkül a felhasználó nem tudná, melyik bejegyzéséhez tartozik művelet.
     */
    fun speakSuffix(context: Context, eventId: Long): String {
        val action = get(context, eventId) ?: return ""
        return " Hozzárendelt művelet: ${action.label()}."
    }
}
