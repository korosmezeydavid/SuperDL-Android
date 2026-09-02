package com.superdl.launcher.tts

import android.content.Context

/**
 * BESZÉD RÉSZLETESSÉGE — mennyit magyarázzon a program.
 *
 * MIÉRT KELL: ami egy kezdőnek életmentő, az egy gyakorlottnak bosszúság.
 * "Söpörj fel-le a választáshoz, jobbra a megnyitáshoz, balra vissza" —
 * ezt a századik alkalommal már senki nem akarja végighallgatni. A legrosszabb
 * eset: egy külső alkalmazás indításakor a program VÉGIGMONDJA a tájékoztatót,
 * és addig nem is indul el az alkalmazás.
 *
 * Ezért a magyarázatok KIKAPCSOLHATÓK — de külön-külön, hogy mindenki a maga
 * szintjén használhassa. Alapból minden BE van kapcsolva: az új felhasználót
 * nem hagyjuk magára.
 */
object VerbosityPrefs {

    private const val PREFS = "superdl_verbosity"

    private const val KEY_HINTS = "gesture_hints"
    private const val KEY_APP_LAUNCH = "app_launch_info"
    private const val KEY_LIST_COUNT = "list_counts"
    private const val KEY_PUNCTUATION_TIP = "punctuation_tip"
    private const val KEY_KEYBOARD_INTRO = "keyboard_intro"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * MOZDULAT-ÚTMUTATÓK: "Söpörj fel-le a választáshoz, jobbra a megnyitáshoz".
     * Kezdőnek nélkülözhetetlen, gyakorlottnak lassítja a munkát.
     */
    fun isHints(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HINTS, true)

    fun toggleHints(context: Context): Boolean {
        val next = !isHints(context)
        prefs(context).edit().putBoolean(KEY_HINTS, next).apply()
        return next
    }

    /**
     * KÜLSŐ ALKALMAZÁS TÁJÉKOZTATÓ: "Külső alkalmazás. Bekapcsolom neked a
     * képernyőolvasót..." — és amíg elhangzik, az alkalmazás NEM indul el.
     * Aki már tudja, mi történik, annak ez tiszta időveszteség.
     */
    fun isAppLaunchInfo(context: Context): Boolean =
        prefs(context).getBoolean(KEY_APP_LAUNCH, true)

    fun toggleAppLaunchInfo(context: Context): Boolean {
        val next = !isAppLaunchInfo(context)
        prefs(context).edit().putBoolean(KEY_APP_LAUNCH, next).apply()
        return next
    }

    /** LISTA-DARABSZÁMOK: "7 elem", "3 / 47". Tanuláskor hasznos. */
    fun isListCounts(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LIST_COUNT, true)

    fun toggleListCounts(context: Context): Boolean {
        val next = !isListCounts(context)
        prefs(context).edit().putBoolean(KEY_LIST_COUNT, next).apply()
        return next
    }

    /** DIKTÁLÁSI TIPP: "mondhatsz írásjeleket is". */
    fun isPunctuationTip(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PUNCTUATION_TIP, true)

    fun togglePunctuationTip(context: Context): Boolean {
        val next = !isPunctuationTip(context)
        prefs(context).edit().putBoolean(KEY_PUNCTUATION_TIP, next).apply()
        return next
    }

    /**
     * BILLENTYŰZET-TÁJÉKOZTATÓ (Alph kérése, 2026-09-02): a Braille, a
     * mátrix és a diktáló billentyűzet bemutató mondata a megnyitáskor —
     * „Braille billentyűzet. Fogd a telefont… Söprés jobbra: szóköz…".
     * Kezdőnek ez a tanulás fele; aki naponta ír vele, annak minden
     * szövegmezőnél újra végighallgatni tiszta bosszúság.
     * KIKAPCSOLVA csak a billentyűzet NEVE hangzik el — az sosem marad el,
     * mert vakon tudni kell, melyik jött elő.
     */
    fun isKeyboardIntro(context: Context): Boolean =
        prefs(context).getBoolean(KEY_KEYBOARD_INTRO, true)

    fun toggleKeyboardIntro(context: Context): Boolean {
        val next = !isKeyboardIntro(context)
        prefs(context).edit().putBoolean(KEY_KEYBOARD_INTRO, next).apply()
        return next
    }

    /**
     * Segéd: csak akkor adja vissza a szöveget, ha az útmutatók be vannak
     * kapcsolva. Így egy sorral kihagyható a magyarázat:
     *     tts.speak("Névjegyek. " + hint(context, "Fel-le válogatás..."))
     */
    fun hint(context: Context, text: String): String =
        if (isHints(context)) text else ""

    /** Felolvasható összefoglaló. */
    fun speakStatus(context: Context): String {
        val on = mutableListOf<String>()
        val off = mutableListOf<String>()
        (if (isHints(context)) on else off) += "mozdulat-útmutatók"
        (if (isAppLaunchInfo(context)) on else off) += "alkalmazás-tájékoztató"
        (if (isListCounts(context)) on else off) += "darabszámok"
        (if (isPunctuationTip(context)) on else off) += "diktálási tipp"
        (if (isKeyboardIntro(context)) on else off) += "billentyűzet-tájékoztató"
        return buildString {
            if (on.isNotEmpty()) append("Bekapcsolva: ${on.joinToString(", ")}. ")
            if (off.isNotEmpty()) append("Kikapcsolva: ${off.joinToString(", ")}.")
        }
    }
}
