package com.superdl.launcher.contacts

import android.content.Context

/**
 * A NÉVJEGYZÉK MEGJELENÍTÉSI BEÁLLÍTÁSAI.
 *
 * Két dolgot lehet állítani, mert erre két, egymással ellentétes igény van:
 *
 * 1. BETŰINDEX: van, aki a kezdőbetűk szerinti csoportosítást szereti (gyors
 *    ugrás a "K" betűhöz), és van, aki egyben, egyetlen listában akarja látni
 *    az összes névjegyet. Egyik sem "jobb" — ezért választható.
 *
 * 2. TELJES TELEFONSZÁM: alapból csak az utolsó négy számjegyet mondja ki a
 *    program ("vége 1234"), hogy egy hangosan felolvasott lista ne fecsegje ki
 *    a teljes számokat idegenek előtt. Aki egyedül használja a telefonját,
 *    nyugodtan kérheti a teljes számot.
 *
 * A GYORSÍTÓTÁR (cache) MIÉRT KELL: a maskPhone() a program húsz különböző
 * pontján hívódik — köztük olyan helyeken is (bejövő hívás csengetése,
 * hívás közbeni képernyő), ahol nincs kéznél Context. Ezért a beállítást
 * egyszer beolvassuk, és onnantól a memóriából olvassuk.
 *
 * ALAPÉRTELMEZÉS BIZTONSÁGRA: amíg nincs beolvasva, a maszkolt (rövid) alak
 * érvényes — vagyis ha bármi félremegy, kevesebbet mond ki, nem többet.
 */
object ContactPrefs {

    private const val PREFS = "superdl_contacts_ui"
    private const val KEY_LETTER_INDEX = "letter_index"
    private const val KEY_FULL_NUMBER = "full_number"

    @Volatile
    private var letterIndexCache = true

    @Volatile
    private var fullNumberCache = false

    @Volatile
    private var warmed = false

    /** A beállítások beolvasása a memóriába. Feloldás után hívjuk az induláskor. */
    fun warm(context: Context) {
        try {
            val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            letterIndexCache = p.getBoolean(KEY_LETTER_INDEX, true)
            fullNumberCache = p.getBoolean(KEY_FULL_NUMBER, false)
            warmed = true
        } catch (_: Exception) {
            // Direct Boot vagy más hiba: marad a biztonságos alapérték.
        }
    }

    private fun ensureWarm(context: Context) {
        if (!warmed) warm(context)
    }

    fun isLetterIndexEnabled(context: Context): Boolean {
        ensureWarm(context)
        return letterIndexCache
    }

    fun setLetterIndexEnabled(context: Context, value: Boolean) {
        letterIndexCache = value
        warmed = true
        try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_LETTER_INDEX, value).apply()
        } catch (_: Exception) {
        }
    }

    fun isFullNumberEnabled(context: Context): Boolean {
        ensureWarm(context)
        return fullNumberCache
    }

    fun setFullNumberEnabled(context: Context, value: Boolean) {
        fullNumberCache = value
        warmed = true
        try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_FULL_NUMBER, value).apply()
        } catch (_: Exception) {
        }
    }

    /** Context nélküli olvasás a maskPhone()-nak. Bemelegítés előtt: maszkolva. */
    fun fullNumberFast(): Boolean = fullNumberCache

    fun speakStatus(context: Context): String {
        val index = if (isLetterIndexEnabled(context)) "bekapcsolva" else "kikapcsolva"
        val full = if (isFullNumberEnabled(context)) "teljes szám" else "csak az utolsó négy számjegy"
        return "Betűindex: $index. Telefonszám kimondása: $full."
    }
}
