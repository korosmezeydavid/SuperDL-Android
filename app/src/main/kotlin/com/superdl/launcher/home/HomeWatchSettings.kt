package com.superdl.launcher.home

import android.content.Context
import com.superdl.launcher.sos.SosPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AZ OTTHON-FIGYELÉS BEÁLLÍTÁSAI.
 *
 * MIÉRT PRÓBA MÓD AZ ALAPÉRTELMEZÉS: ez az első funkció a programban, ami
 * MAGÁTÓL cselekszik a felhasználó helyett — üzenetet küld valaki másnak,
 * kérdés nélkül. Aki most kapcsolja be először, annak aznap semmiképp ne
 * menjen ki éles riasztás egy félreértés miatt. Próba módban a program
 * mindent végigcsinál, és BEMONDJA, mit küldött volna — de nem küld.
 * Az élesre váltás külön, tudatos lépés.
 *
 * MIÉRT EGY CÍMZETT AZ ALAPÉRTELMEZÉS: négy embert egyszerre riasztani
 * nagyobb tét, és az első hamis riasztás után senki nem kapcsolja vissza.
 * Aki többet akar, külön bekapcsolhatja.
 */
object HomeWatchSettings {

    private const val PREFS = "superdl"

    private const val KEY_ENABLED = "otthon_figyeles_be"
    private const val KEY_HOUR = "otthon_figyeles_ora"
    private const val KEY_MINUTE = "otthon_figyeles_perc"
    private const val KEY_PROBE = "otthon_figyeles_proba"
    private const val KEY_COUNTDOWN = "otthon_figyeles_visszaszamlalas"
    private const val KEY_SLOTS = "otthon_figyeles_cimzettek"
    private const val KEY_LAST = "otthon_figyeles_utolso"
    private const val KEY_LAST_AT = "otthon_figyeles_utolso_ido"

    private const val DEFAULT_HOUR = 22
    private const val DEFAULT_MINUTE = 0

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Ki-be ────────────────────────────────────────────────────────────

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    // ── Az ellenőrzés időpontja ──────────────────────────────────────────

    fun hour(context: Context): Int = prefs(context).getInt(KEY_HOUR, DEFAULT_HOUR)
    fun minute(context: Context): Int = prefs(context).getInt(KEY_MINUTE, DEFAULT_MINUTE)

    fun setTime(context: Context, hour: Int, minute: Int) {
        prefs(context).edit()
            .putInt(KEY_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_MINUTE, minute.coerceIn(0, 59))
            .apply()
    }

    fun speakTime(context: Context): String =
        String.format(Locale("hu"), "%d óra %02d perc", hour(context), minute(context))

    // ── Próba mód ────────────────────────────────────────────────────────

    fun isProbe(context: Context): Boolean = prefs(context).getBoolean(KEY_PROBE, true)

    fun setProbe(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PROBE, value).apply()
    }

    // ── Visszaszámlálás ──────────────────────────────────────────────────

    fun isCountdownEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COUNTDOWN, true)

    fun setCountdownEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_COUNTDOWN, value).apply()
    }

    // ── Címzettek ────────────────────────────────────────────────────────

    /** Az S.O.S. számok közül melyikek kapnak értesítést. Alapból csak az első. */
    fun slots(context: Context): Set<Int> {
        val raw = prefs(context).getString(KEY_SLOTS, "1") ?: "1"
        val out = raw.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..4 }
        return if (out.isEmpty()) setOf(1) else out.toSortedSet()
    }

    fun toggleSlot(context: Context, slot: Int): Boolean {
        require(slot in 1..4)
        val current = slots(context).toMutableSet()
        val nowOn = if (current.contains(slot)) {
            current.remove(slot); false
        } else {
            current.add(slot); true
        }
        // LEGALÁBB EGY CÍMZETT KELL. Egy figyelés, ami senkinek nem szól,
        // nem védőháló, csak a védőháló látszata.
        if (current.isEmpty()) current.add(slot)
        prefs(context).edit()
            .putString(KEY_SLOTS, current.sorted().joinToString(","))
            .apply()
        return nowOn && current.contains(slot)
    }

    /** A ténylegesen kitöltött telefonszámok. */
    fun numbers(context: Context): List<String> =
        slots(context).mapNotNull { slot ->
            SosPreferences.getNumber(context, slot).takeIf { it.isNotBlank() }
        }

    // ── Az utolsó ellenőrzés sorsa ───────────────────────────────────────

    fun noteOutcome(context: Context, text: String) {
        prefs(context).edit()
            .putString(KEY_LAST, text)
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .apply()
    }

    fun speakLastOutcome(context: Context): String {
        val text = prefs(context).getString(KEY_LAST, null)
            ?: return "Még nem volt otthon-ellenőrzés."
        val at = prefs(context).getLong(KEY_LAST_AT, 0L)
        val time = if (at > 0) {
            SimpleDateFormat("MMMM d., HH:mm", Locale("hu")).format(Date(at))
        } else {
            ""
        }
        return if (time.isBlank()) text else "$time: $text"
    }

    // ── Összefoglaló felolvasás ──────────────────────────────────────────

    fun speakStatus(context: Context): String {
        val sb = StringBuilder()
        sb.append(if (isEnabled(context)) "Az otthon-figyelés be van kapcsolva. " else "Az otthon-figyelés ki van kapcsolva. ")
        sb.append("Ellenőrzés ekkor: ${speakTime(context)}. ")
        sb.append(if (isProbe(context)) "PRÓBA módban van: nem küld üzenetet, csak bemondja. " else "ÉLES módban van: üzenetet küld. ")
        val numbers = numbers(context)
        sb.append(
            when (numbers.size) {
                0 -> "Figyelem: nincs kitöltve egyetlen S.O.S. szám sem, így nem lenne kinek üzenni. "
                1 -> "Egy címzettnek szól. "
                else -> "${numbers.size} címzettnek szól. "
            }
        )
        sb.append(if (isCountdownEnabled(context)) "A riasztás előtt visszaszámlál. " else "Visszaszámlálás nélkül riaszt. ")
        sb.append(HomeSignatureStore.get(context).speakSummary())
        return sb.toString()
    }
}
