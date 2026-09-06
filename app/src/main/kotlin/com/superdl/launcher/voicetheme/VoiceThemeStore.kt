package com.superdl.launcher.voicetheme

import android.content.Context
import java.util.Calendar

/**
 * A BESZÉDTÉMA BEÁLLÍTÁSAI.
 *
 * NÉVZAVAR ELLEN: a „hangtéma" szó a programban a SÖPRÉS-hangokat jelenti
 * (feedback/SoundTheme.kt: síp, koppanás, sci-fi). Ez itt más: kimondott
 * mondatok felvett hanggal. Ezért BESZÉDTÉMA a neve mindenhol.
 *
 * Alapból KIKAPCSOLVA. Egy új, hangulati funkció ne szóljon bele senki
 * telefonjába kérdés nélkül.
 */
object VoiceThemeStore {

    private const val PREFS = "superdl"

    private const val KEY_ENABLED = "voice_theme_enabled"
    private const val KEY_ACTIVE = "voice_theme_active"
    private const val KEY_EVENT_PREFIX = "voice_theme_ev_"

    private const val KEY_MORNING_ENABLED = "voice_theme_morning_enabled"
    private const val KEY_MORNING_MINUTES = "voice_theme_morning_minutes"
    private const val KEY_MORNING_ON_UNLOCK = "voice_theme_morning_on_unlock"
    private const val KEY_MORNING_LAST_DAY = "voice_theme_morning_last_day"

    private const val KEY_NIGHT_ENABLED = "voice_theme_night_enabled"
    private const val KEY_NIGHT_MINUTES = "voice_theme_night_minutes"

    private const val KEY_DAILY_CAP = "voice_theme_daily_cap"
    private const val KEY_COUNT_DAY = "voice_theme_count_day"
    private const val KEY_COUNT = "voice_theme_count"

    /** Hányszor szólalhat meg naponta. 0 = korlátlan. */
    val DAILY_CAPS = listOf(0, 3, 5, 8, 12, 20)

    private fun prefs(context: Context) =
        com.superdl.launcher.storage.SafePrefs.get(context, PREFS)

    // ── Mesterkapcsoló ──────────────────────────────────────────────────────

    /**
     * ALAPBÓL BE VAN KAPCSOLVA, mert az Elena téma a program része.
     *
     * Egy kikapcsolt funkció, amihez beépített hang tartozik, csak arra jó,
     * hogy senki ne találja meg. A hangulati események amúgy is ritkák
     * (töltő, feltöltés), a napi keret pedig védi a felhasználót attól,
     * hogy sokká váljon. Kikapcsolni egy söpréssel lehet.
     */
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * Az aktív téma mappaneve a `hangtemak` mappán belül.
     *
     * AZ ALAPÉRTELMEZETT MINDIG AZ ELENA: az a beépített téma, ami az
     * alkalmazással érkezik. Üres string = a felhasználó KIFEJEZETTEN azt
     * választotta, hogy egyik téma se szóljon; ilyenkor a beépített
     * mondatok jönnek.
     */
    fun getActiveTheme(context: Context): String =
        prefs(context).getString(KEY_ACTIVE, VoiceThemeAssets.BUILT_IN_ID)
            .orEmpty()

    fun setActiveTheme(context: Context, folderName: String) {
        prefs(context).edit().putString(KEY_ACTIVE, folderName).apply()
    }

    /**
     * A téma EMBERI NEVE a mappanév mellé.
     *
     * A mappanév ékezet nélküli és géppel olvasható („nagypapa_hangja"), de a
     * felhasználónak azt kell hallania, amit ő mondott be. Ezt külön tároljuk,
     * mert a mappanévből visszafelé nem állítható elő.
     */
    fun getThemeName(context: Context, id: String): String =
        prefs(context).getString("voice_theme_name_$id", "").orEmpty().ifBlank { id }

    fun setThemeName(context: Context, id: String, name: String) {
        prefs(context).edit().putString("voice_theme_name_$id", name).apply()
    }

    // ── Eseményenkénti kapcsoló ─────────────────────────────────────────────

    fun isEventEnabled(context: Context, event: VoiceEvent): Boolean =
        prefs(context).getBoolean(KEY_EVENT_PREFIX + event.id, true)

    fun setEventEnabled(context: Context, event: VoiceEvent, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_EVENT_PREFIX + event.id, enabled).apply()
    }

    // ── Reggeli köszönés ────────────────────────────────────────────────────

    fun isMorningEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MORNING_ENABLED, false)

    fun setMorningEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MORNING_ENABLED, enabled).apply()
    }

    fun getMorningMinutes(context: Context): Int =
        prefs(context).getInt(KEY_MORNING_MINUTES, 7 * 60)

    fun setMorningMinutes(context: Context, minutes: Int) {
        prefs(context).edit()
            .putInt(KEY_MORNING_MINUTES, minutes.coerceIn(0, 23 * 60 + 59)).apply()
    }

    /**
     * ALAPBÓL IGAZ, és ez szándékos: egy üres szobának köszönni zaj, annak
     * köszönni, aki most vette kézbe a telefont, kedvesség. Ilyenkor a
     * beállított idő után az ELSŐ képernyő-feloldáskor szólal meg.
     */
    fun isMorningOnUnlock(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MORNING_ON_UNLOCK, true)

    fun setMorningOnUnlock(context: Context, onUnlock: Boolean) {
        prefs(context).edit().putBoolean(KEY_MORNING_ON_UNLOCK, onUnlock).apply()
    }

    /** Melyik napon köszöntünk már ma reggel (év napja), hogy csak egyszer. */
    fun getMorningLastDay(context: Context): Int =
        prefs(context).getInt(KEY_MORNING_LAST_DAY, -1)

    fun setMorningLastDay(context: Context, dayOfYear: Int) {
        prefs(context).edit().putInt(KEY_MORNING_LAST_DAY, dayOfYear).apply()
    }

    // ── Esti köszönés ───────────────────────────────────────────────────────

    fun isNightEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NIGHT_ENABLED, false)

    fun setNightEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NIGHT_ENABLED, enabled).apply()
    }

    fun getNightMinutes(context: Context): Int =
        prefs(context).getInt(KEY_NIGHT_MINUTES, 22 * 60)

    fun setNightMinutes(context: Context, minutes: Int) {
        prefs(context).edit()
            .putInt(KEY_NIGHT_MINUTES, minutes.coerceIn(0, 23 * 60 + 59)).apply()
    }

    // ── Napi keret ──────────────────────────────────────────────────────────

    /**
     * MIÉRT VAN EGYÁLTALÁN: a báj attól báj, hogy ritka. Napi keret nélkül a
     * téma két hét alatt idegesítővé válik, és a felhasználó nem a keretet
     * kapcsolja ki, hanem az egész funkciót.
     *
     * Ez NEM vonatkozik a merülésre: az figyelmeztetés, nem hangulat.
     */
    fun getDailyCap(context: Context): Int {
        val stored = prefs(context).getInt(KEY_DAILY_CAP, 5)
        return DAILY_CAPS.firstOrNull { it == stored } ?: 5
    }

    fun cycleDailyCap(context: Context): Int {
        val current = getDailyCap(context)
        val index = DAILY_CAPS.indexOf(current).let { if (it < 0) 0 else it }
        val next = DAILY_CAPS[(index + 1) % DAILY_CAPS.size]
        prefs(context).edit().putInt(KEY_DAILY_CAP, next).apply()
        return next
    }

    fun speakDailyCap(context: Context): String {
        val cap = getDailyCap(context)
        return if (cap == 0) "korlátlan" else "napi $cap alkalom"
    }

    /**
     * Belefér-e még a mai keretbe. A számláló naponta magától nullázódik.
     * @return igaz, ha megszólalhat
     */
    fun consumeQuota(context: Context): Boolean {
        val cap = getDailyCap(context)
        if (cap == 0) return true
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val p = prefs(context)
        val day = p.getInt(KEY_COUNT_DAY, -1)
        val count = if (day == today) p.getInt(KEY_COUNT, 0) else 0
        if (count >= cap) return false
        p.edit().putInt(KEY_COUNT_DAY, today).putInt(KEY_COUNT, count + 1).apply()
        return true
    }

    // ── Felolvasható óra ────────────────────────────────────────────────────

    fun speakClock(totalMinutes: Int): String {
        val hour = totalMinutes / 60
        val minute = totalMinutes % 60
        return if (minute == 0) "$hour óra" else "$hour óra $minute perc"
    }
}
