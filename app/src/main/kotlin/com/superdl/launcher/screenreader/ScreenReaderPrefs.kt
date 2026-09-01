package com.superdl.launcher.screenreader

import android.content.Context

/**
 * A SuperDL képernyőolvasó beállításai és BIZTONSÁGI RETESZE.
 *
 * A képernyőolvasó a telefon egészének érintés-kezelését befolyásolja, ezért
 * kell egy hely, ahonnan EGY MOZDULATTAL leállítható, ha bármi rosszul sülne el.
 *
 * Három szint van:
 *  1. enabled          — a felhasználó ki/be kapcsolója (menüből)
 *  2. emergencyDisable — VÉSZLEÁLLÍTÁS: ha ez be van kapcsolva, a szolgáltatás
 *                        semmit nem csinál, akkor sem, ha a rendszerben
 *                        engedélyezve van.
 *  3. failureCount     — hibaszámláló: több egymást követő hiba után magától
 *                        vészleállítás lép életbe.
 */
object ScreenReaderPrefs {

    const val TAG = "SDL_SCREENREADER"

    private const val PREFS = "superdl_screenreader"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_EMERGENCY = "emergency_disable"
    private const val KEY_FAILURES = "failure_count"

    /** Ennyi egymást követő hiba után magától leáll. */
    private const val MAX_FAILURES = 3

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Fő kapcsoló ─────────────────────────────────────────────────────────

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false) && !isEmergencyDisabled(context)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        // Kézi bekapcsoláskor a vészleállítást és a hibaszámlálót nullázzuk:
        // a felhasználó tudatosan újra megpróbálja.
        if (on) clearEmergency(context)
    }

    // ── Vészleállítás (a "biztonsági retesz") ───────────────────────────────

    fun isEmergencyDisabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EMERGENCY, false)

    /**
     * Azonnali leállítás. Ezt hívja a menü vészkapcsolója, és ezt hívja a
     * szolgáltatás is, ha sorozatos hibába fut.
     */
    fun emergencyStop(context: Context, reason: String) {
        android.util.Log.w(TAG, "VESZLEALLITAS: $reason")
        prefs(context).edit()
            .putBoolean(KEY_EMERGENCY, true)
            .putBoolean(KEY_ENABLED, false)
            .apply()
    }

    fun clearEmergency(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_EMERGENCY, false)
            .putInt(KEY_FAILURES, 0)
            .apply()
    }

    // ── Hibaszámláló ────────────────────────────────────────────────────────

    /** Hiba történt. Ha túl sok egymás után, magától vészleállítás jön. */
    fun reportFailure(context: Context, reason: String) {
        val next = prefs(context).getInt(KEY_FAILURES, 0) + 1
        prefs(context).edit().putInt(KEY_FAILURES, next).apply()
        android.util.Log.w(TAG, "Hiba ($next/$MAX_FAILURES): $reason")
        if (next >= MAX_FAILURES) {
            emergencyStop(context, "tul sok egymast koveto hiba")
        }
    }

    /** Sikeres működés — a hibaszámláló nullázódik. */
    fun reportSuccess(context: Context) {
        if (prefs(context).getInt(KEY_FAILURES, 0) != 0) {
            prefs(context).edit().putInt(KEY_FAILURES, 0).apply()
        }
    }

    fun speakStatus(context: Context): String = when {
        isEmergencyDisabled(context) ->
            "A képernyőolvasó vészleállítás alatt van. A bekapcsolással újraindíthatod."
        prefs(context).getBoolean(KEY_ENABLED, false) ->
            "A képernyőolvasó bekapcsolva. Csak külső alkalmazásokban működik."
        else ->
            "A képernyőolvasó kikapcsolva."
    }

    // ── OLVASÁSI BEÁLLÍTÁSOK ────────────────────────────────────────────────

    private const val KEY_COUNTER = "speak_counter"
    private const val KEY_PHONETIC = "phonetic_alphabet"

    /**
     * Bemondja-e a pozíciót ("3 / 47") minden elemnél.
     * Tanuláskor hasznos, gyakorlott használatnál viszont lassít — ezért
     * kikapcsolható.
     */
    fun isSpeakCounter(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COUNTER, true)

    fun toggleSpeakCounter(context: Context): Boolean {
        val next = !isSpeakCounter(context)
        prefs(context).edit().putBoolean(KEY_COUNTER, next).apply()
        return next
    }

    /**
     * Betűnkénti olvasásnál a betűző ábécét használja ("Aladár, Béla, Cecil").
     * Kódoknál, rendszámoknál életmentő, egyébként lassít — ezért választható.
     */
    fun isPhonetic(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PHONETIC, false)

    fun togglePhonetic(context: Context): Boolean {
        val next = !isPhonetic(context)
        prefs(context).edit().putBoolean(KEY_PHONETIC, next).apply()
        return next
    }

    // ── ALKALMAZÁSONKÉNT MEGJEGYZETT BEÁLLÍTÁS ──────────────────────────────

    /**
     * Minden alkalmazáshoz KÜLÖN megjegyezzük, milyen módban és milyen
     * részletességgel olvastál benne utoljára.
     *
     * MIÉRT HASZNOS: a böngészőben jellemzően "címsorok" módban akarsz
     * tájékozódni, a beállításokban "minden elem", egy üzenetküldőben pedig
     * "szöveg". Enélkül minden belépéskor újra át kellene állítanod.
     */
    fun saveAppMode(context: Context, packageName: String, mode: String, granularity: String) {
        prefs(context).edit()
            .putString("mode_$packageName", mode)
            .putString("gran_$packageName", granularity)
            .apply()
    }

    fun loadAppMode(context: Context, packageName: String): Pair<String?, String?> {
        val p = prefs(context)
        return p.getString("mode_$packageName", null) to p.getString("gran_$packageName", null)
    }

    // ── AUTOMATIKUS FELOLVASÁS ÚJ KÉPERNYŐNÉL ──────────────────────────────

    private const val KEY_AUTO_READ = "auto_read_screen"

    /**
     * Új képernyőre lépve magától elmondja a képernyő címét és az első pár
     * elemet — így nem kell "vakon" tapogatózni, hogy hova kerültél.
     */
    fun isAutoRead(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_READ, true)

    fun toggleAutoRead(context: Context): Boolean {
        val next = !isAutoRead(context)
        prefs(context).edit().putBoolean(KEY_AUTO_READ, next).apply()
        return next
    }

    // ── ÉRTESÍTÉSEK BEMONDÁSA ───────────────────────────────────────────────

    private const val KEY_ANNOUNCE_NOTIF = "announce_notifications"

    /**
     * Bemondja-e az ÉRKEZŐ értesítéseket más alkalmazásban.
     * Alapból BE: enélkül egy üzenet vagy emlékeztető észrevétlen maradna,
     * amit egy látó felhasználó egy pillantással látna.
     */
    fun isAnnounceNotifications(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ANNOUNCE_NOTIF, true)

    fun toggleAnnounceNotifications(context: Context): Boolean {
        val next = !isAnnounceNotifications(context)
        prefs(context).edit().putBoolean(KEY_ANNOUNCE_NOTIF, next).apply()
        return next
    }

    // ── FELDERÍTÉS ÉRINTÉSSEL ───────────────────────────────────────────────

    private const val KEY_TOUCH_EXPLORE = "touch_explore"

    /**
     * Engedélyezett-e a tapintásos felderítés.
     *
     * ALAPBÓL BE. Csak akkor indul, ha három másodpercig egy helyben tartod
     * az ujjad — véletlenül tehát nem kapcsol be. Aki mégsem szeretné (mert
     * zavarja, vagy remeg a keze), kikapcsolhatja.
     */
    fun isTouchExploreEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TOUCH_EXPLORE, true)

    fun toggleTouchExplore(context: Context): Boolean {
        val next = !isTouchExploreEnabled(context)
        prefs(context).edit().putBoolean(KEY_TOUCH_EXPLORE, next).apply()
        return next
    }

    // ── A FELDERÍTÉS INDÍTÁSI IDEJE ─────────────────────────────────────────

    private const val KEY_EXPLORE_HOLD = "touch_explore_hold_ms"

    /**
     * Mennyi ideig kell egy helyben tartani az ujjat, hogy induljon a
     * felderítés.
     *
     * MIÉRT ÁLLÍTHATÓ: nagyon eltérő igények vannak. Aki gyakorlott, annak a
     * három másodperc örökkévalóság — naponta sokszor várna feleslegesen.
     * Akinek viszont remeg a keze vagy lassabban mozog, annál az egy
     * másodperc VÉLETLENÜL is összejön, és állandóan bekapcsolna.
     *
     * ALAPBÓL HÁROM: ez a biztonságos alap, mert véletlenül nem indul el.
     */
    fun getExploreHoldMs(context: Context): Long =
        prefs(context).getLong(KEY_EXPLORE_HOLD, 3_000L)

    /** Léptetés: 3 másodperc, 2, 1, majd újra 3. */
    fun cycleExploreHold(context: Context): Long {
        val next = when (getExploreHoldMs(context)) {
            3_000L -> 2_000L
            2_000L -> 1_000L
            else -> 3_000L
        }
        prefs(context).edit().putLong(KEY_EXPLORE_HOLD, next).apply()
        return next
    }

    // ── HANGTÉRKÉP: MELYIK HANGNYELV ────────────────────────────────────────

    private const val KEY_SCREEN_MAP_STYLE = "screen_map_style"

    /**
     * MIÉRT ÁLLÍTHATÓ, ÉS MIÉRT NÉGY:
     *
     * Ez az egyetlen olyan funkció, aminél előre NEM tudható, hogy jó lesz-e.
     * Nem logika kérdése, hanem HALLÁSÉLMÉNYÉ — papíron nem dől el. Ezért nem
     * egy megoldást építünk, hanem négyet, és a fül dönt.
     *
     * A négy változat két kérdést jár körül:
     *   - Szerkezetet halljunk vagy leltárt? (Csoportos kontra Pásztázó)
     *   - Segít-e a beszéd, vagy csak lassít? (Beszédes kontra a többi)
     *
     * Ha kiderül, melyik a nyertes, a többi kikerülhet — de amíg nincs
     * kipróbálva, egyik sem "az igazi".
     */
    fun getScreenMapStyle(context: Context): Int =
        prefs(context).getInt(KEY_SCREEN_MAP_STYLE, 0).coerceIn(0, 3)

    fun cycleScreenMapStyle(context: Context): Int {
        val next = (getScreenMapStyle(context) + 1) % 4
        prefs(context).edit().putInt(KEY_SCREEN_MAP_STYLE, next).apply()
        return next
    }

    // ── HANGTÉRKÉP: A TEMPÓ ─────────────────────────────────────────────────

    private const val KEY_SCREEN_MAP_TEMPO = "screen_map_tempo"

    /**
     * A hangtérkép tempója: 0 = nyugodt, 1 = normál, 2 = gyors.
     *
     * MIÉRT LETT EBBŐL BEÁLLÍTÁS: az első próba visszajelzése az volt, hogy
     * "nagyon gyorsan ledarálja". Ez nem hangnyelv-kérdés — mind a négy nyelv
     * ugyanattól lesz olvashatatlan. És nem is egyszerűen "lassítani kell":
     * aki már megszokta, annak a lassú fárasztó lesz, mert naponta sokszor
     * használja.
     *
     * ALAPBÓL NYUGODT. Egy új dolgot előbb meg kell tudni hallani; sietni
     * csak azután érdemes, ha már érted, mit hallasz. A gyorsítás egy
     * menüpont, a meg nem értett hangkép viszont elveszett funkció.
     */
    fun getScreenMapTempo(context: Context): Int =
        prefs(context).getInt(KEY_SCREEN_MAP_TEMPO, 0).coerceIn(0, 2)

    fun cycleScreenMapTempo(context: Context): Int {
        val next = (getScreenMapTempo(context) + 1) % 3
        prefs(context).edit().putInt(KEY_SCREEN_MAP_TEMPO, next).apply()
        return next
    }
}
