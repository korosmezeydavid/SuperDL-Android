package com.superdl.launcher.history

import android.content.Context

/**
 * MENNYIT MUTASSON A HÍVÁSNAPLÓ ÉS AZ ÜZENETLISTA.
 *
 * MIÉRT KELL: mind a kettő húsz tételnél megállt, és nem lehetett
 * visszamenni régebbre. Aki a múlt heti hívást keresi, annál a húsz kevés —
 * és a program némán állt meg a huszadiknál, anélkül hogy jelezte volna,
 * hogy van még.
 *
 * MIÉRT NEM EGYSZERŰEN „MIND": egy több ezer tételes lista fel-le söpréssel
 * használhatatlan, és a betöltése is megakasztaná a programot. Ezért marad
 * a húsz az alapértelmezés, és aki többet akar, az feljebb veszi.
 */
object HistoryPrefs {

    private const val PREFS = "superdl"

    private const val KEY_CALL_LIMIT = "hivasnaplo_darab"
    private const val KEY_SMS_LIMIT = "uzenet_darab"

    const val DEFAULT_LIMIT = 20

    /** A felajánlott értékek. A 0 jelentése: mind. */
    val CHOICES = listOf(20, 40, 60, 100, 200, 0)

    fun label(value: Int): String = if (value <= 0) "Mind" else "$value darab"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Hívásnapló ───────────────────────────────────────────────────────

    fun callLimit(context: Context): Int =
        prefs(context).getInt(KEY_CALL_LIMIT, DEFAULT_LIMIT)

    fun setCallLimit(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_CALL_LIMIT, sane(value)).apply()
    }

    /** A tényleges korlát a lekérdezéshez: a „mind" is kap egy felső határt. */
    fun callQueryLimit(context: Context): Int = queryLimit(callLimit(context))

    // ── Üzenetek ─────────────────────────────────────────────────────────

    fun smsLimit(context: Context): Int =
        prefs(context).getInt(KEY_SMS_LIMIT, DEFAULT_LIMIT)

    fun setSmsLimit(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_SMS_LIMIT, sane(value)).apply()
    }

    fun smsQueryLimit(context: Context): Int = queryLimit(smsLimit(context))

    // ── Segédek ──────────────────────────────────────────────────────────

    private fun sane(value: Int): Int = if (value in CHOICES) value else DEFAULT_LIMIT

    /**
     * A „mind" sem lehet végtelen. Ötszáznál a fel-le söprögetés már
     * reménytelen, a betöltés meg megakasztaná a programot — egy lista,
     * amiben nem lehet navigálni, nem több, hanem kevesebb.
     */
    private fun queryLimit(value: Int): Int = if (value <= 0) 500 else value

    fun speakStatus(context: Context): String = buildString {
        append("Hívásnapló: ")
        append(label(callLimit(context)).lowercase())
        append(". Üzenetek: ")
        append(label(smsLimit(context)).lowercase())
        append(".")
    }
}
