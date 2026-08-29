package com.superdl.launcher.tts

import android.content.Context

object TtsSettingsStore {

    private const val PREFS = "superdl"
    private const val KEY_SPEECH_RATE = "tts_speech_rate"

    fun getSpeechRate(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_SPEECH_RATE, 1.0f)
            .coerceIn(0.5f, 2.5f)

    fun setSpeechRate(context: Context, rate: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_SPEECH_RATE, rate.coerceIn(0.5f, 2.5f))
            .apply()
    }

    // ── BESZÉD HANGCSATORNÁJA ───────────────────────────────────────────────

    private const val KEY_SPEECH_CHANNEL = "tts_speech_channel"

    /** Kisegítő hangcsatorna: külön hangerő, de NEM minden készüléken szól. */
    const val CHANNEL_ACCESSIBILITY = "accessibility"

    /** Média hangcsatorna: MINDEN készüléken megszólal. */
    const val CHANNEL_MEDIA = "media"

    /**
     * Melyik hangcsatornán szóljon a program beszéde.
     *
     * ALAPBÓL A MÉDIA. Ez a fontos döntés: a kisegítő csatorna elvileg
     * szebb megoldás — külön hangerő, nem keveredik a zenével —, DE
     * bizonyos készülékeken egy sima alkalmazás hangja NEM JUT KI rajta.
     * Ilyenkor a program TELJESEN NÉMA marad, pedig a beszédmotor dolgozik:
     * a felhasználó azt hiszi, elromlott.
     *
     * Élesben megtapasztalva: Ulefone Armor 24 (Android 13) — működik.
     * Cat/Doogee S62 Pro (Android 11) — NÉMA.
     *
     * A néma telefon vakon használhatatlan, ezért a BIZTOS megoldás az
     * alapértelmezés. Aki szeretné a külön hangerőt, átállíthatja.
     */
    fun getSpeechChannel(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SPEECH_CHANNEL, CHANNEL_MEDIA) ?: CHANNEL_MEDIA

    fun setSpeechChannel(context: Context, channel: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SPEECH_CHANNEL, channel).apply()
    }

    fun toggleSpeechChannel(context: Context): String {
        val next = if (getSpeechChannel(context) == CHANNEL_MEDIA) {
            CHANNEL_ACCESSIBILITY
        } else {
            CHANNEL_MEDIA
        }
        setSpeechChannel(context, next)
        return next
    }

    // ── AUTOMATIKUS NYELVVÁLTÁS ─────────────────────────────────────────────

    private const val KEY_AUTO_LANGUAGE = "tts_auto_language"

    /**
     * Váltson-e a program nyelvet, ha idegen szöveget olvas fel.
     *
     * ALAPBÓL BE. A magyar motor az angol szöveget érthetetlenül mondja ki,
     * márpedig magyar képernyőkön rengeteg angol van. A felismerés
     * szándékosan óvatos: csak akkor vált, ha a szöveg TÚLNYOMÓRÉSZT idegen.
     */
    fun isAutoLanguage(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_LANGUAGE, true)

    fun toggleAutoLanguage(context: Context): Boolean {
        val next = !isAutoLanguage(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_LANGUAGE, next).apply()
        return next
    }

    // ── HANGSZEREPEK ────────────────────────────────────────────────────────

    private const val KEY_VOICE_ROLES = "tts_voice_roles"

    /**
     * Szólaljon-e más hangszínnel a program saját üzenete, mint a képernyő
     * tartalma.
     *
     * ALAPBÓL BE. Enélkül a felhasználónak a SZÖVEGBŐL kell kitalálnia, hogy
     * amit hall, az az alkalmazásból jött-e, vagy a program mondja neki.
     */
    fun isVoiceRoles(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_VOICE_ROLES, true)

    fun toggleVoiceRoles(context: Context): Boolean {
        val next = !isVoiceRoles(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_VOICE_ROLES, next).apply()
        return next
    }

    // ── MÁSODIK BESZÉDMOTOR A PROGRAM SAJÁT HANGJÁHOZ ───────────────────────

    private const val KEY_ROLE_ENGINE = "tts_role_engine"

    /**
     * A program saját üzeneteit MÁSIK beszédmotor mondja.
     *
     * MIÉRT VÁLASZTHATÓ, ÉS MIÉRT NEM ALAPÉRTELMEZÉS:
     * Ez a LEGHATÁROZOTTABB megkülönböztetés — két teljesen más hang, nem
     * csak más hangmagasság. DE nem mindenkinél van két motor telepítve, és
     * két motor párhuzamos futtatása több memóriát eszik.
     *
     * Ezért: alapból a hangszín-változat megy (minden készüléken működik),
     * de AKINEK VAN két motorja és ez az igénye, az választhatja ezt.
     *
     * @return a motor csomagneve, vagy null ha nincs beállítva
     */
    fun getRoleEngine(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ROLE_ENGINE, null)?.takeIf { it.isNotBlank() }

    fun setRoleEngine(context: Context, packageName: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ROLE_ENGINE, packageName.orEmpty()).apply()
    }
}