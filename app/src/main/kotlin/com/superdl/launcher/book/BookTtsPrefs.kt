package com.superdl.launcher.book

import android.content.Context
import android.speech.tts.TextToSpeech

/**
 * A KÖNYVOLVASÓ SAJÁT HANGJA.
 *
 * MIÉRT KELL KÜLÖN: a menü felolvasásához gyors, tömör hang való — órákon át
 * hallgatott könyvhöz viszont egészen más. Lassabb tempó, kellemesebb
 * hangszín, akár MÁSIK beszédmotor. Aki sokat olvas, annak ez nem apróság:
 * a rossz hang egy óra után fárasztó, a jó hang észrevétlen marad.
 *
 * Ezért a könyvolvasó saját beállítást kap, ami NEM befolyásolja a program
 * többi részét.
 */
object BookTtsPrefs {

    private const val PREFS = "superdl_book_tts"
    private const val KEY_ENGINE = "engine"
    private const val KEY_RATE = "rate"
    private const val KEY_PITCH = "pitch"
    private const val KEY_USE_OWN = "use_own"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Használja-e a könyvolvasó a SAJÁT beállításait?
     * Ha nem, a program általános hangját használja — ez az alapértelmezés,
     * hogy senkinek ne változzon meg semmi hívatlanul.
     */
    fun isUseOwn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_USE_OWN, false)

    fun setUseOwn(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_USE_OWN, value).apply()
    }

    /** A választott beszédmotor csomagneve, vagy null = a rendszer alapértelmezettje. */
    fun getEngine(context: Context): String? =
        prefs(context).getString(KEY_ENGINE, null)?.takeIf { it.isNotBlank() }

    fun setEngine(context: Context, packageName: String?) {
        prefs(context).edit().putString(KEY_ENGINE, packageName.orEmpty()).apply()
    }

    /** Beszédsebesség: 0.5 = fele olyan gyors, 2.0 = kétszeres. */
    fun getRate(context: Context): Float =
        prefs(context).getFloat(KEY_RATE, 1.0f)

    fun setRate(context: Context, rate: Float) {
        prefs(context).edit().putFloat(KEY_RATE, rate.coerceIn(0.5f, 2.5f)).apply()
    }

    /** Hangmagasság: 0.5 = mélyebb, 1.5 = magasabb. */
    fun getPitch(context: Context): Float =
        prefs(context).getFloat(KEY_PITCH, 1.0f)

    fun setPitch(context: Context, pitch: Float) {
        prefs(context).edit().putFloat(KEY_PITCH, pitch.coerceIn(0.5f, 1.6f)).apply()
    }

    // ── LÉPTETÉS (a menüből, egy mozdulattal) ───────────────────────────────

    private val RATE_STEPS = floatArrayOf(0.6f, 0.75f, 0.9f, 1.0f, 1.15f, 1.3f, 1.5f, 1.8f, 2.2f)
    private val PITCH_STEPS = floatArrayOf(0.6f, 0.75f, 0.9f, 1.0f, 1.15f, 1.3f, 1.5f)

    fun nextRate(context: Context): Float {
        val current = getRate(context)
        val i = RATE_STEPS.indexOfFirst { kotlin.math.abs(it - current) < 0.03f }
        val next = RATE_STEPS[if (i < 0) 3 else (i + 1) % RATE_STEPS.size]
        setRate(context, next)
        return next
    }

    fun nextPitch(context: Context): Float {
        val current = getPitch(context)
        val i = PITCH_STEPS.indexOfFirst { kotlin.math.abs(it - current) < 0.03f }
        val next = PITCH_STEPS[if (i < 0) 3 else (i + 1) % PITCH_STEPS.size]
        setPitch(context, next)
        return next
    }

    /** A telepített beszédmotorok listája (név, csomagnév). */
    fun availableEngines(context: Context): List<Pair<String, String>> = try {
        val intent = android.content.Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        context.packageManager.queryIntentServices(intent, 0).map { info ->
            val label = info.serviceInfo.loadLabel(context.packageManager).toString()
            label to info.serviceInfo.packageName
        }.distinctBy { it.second }
    } catch (_: Exception) {
        emptyList()
    }

    /** Felolvasható összefoglaló a jelenlegi beállításról. */
    fun speakSummary(context: Context): String {
        if (!isUseOwn(context)) {
            return "A könyvolvasó a program általános hangját használja. " +
                "A Saját hang bekapcsolásával külön beállíthatod."
        }
        val engineName = getEngine(context)?.let { pkg ->
            availableEngines(context).firstOrNull { it.second == pkg }?.first
        } ?: "alapértelmezett motor"
        return "Könyvolvasó hangja: $engineName. " +
            "Sebesség: ${"%.2f".format(getRate(context)).replace('.', ',')}. " +
            "Hangmagasság: ${"%.2f".format(getPitch(context)).replace('.', ',')}."
    }
}
