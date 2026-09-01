package com.superdl.launcher.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * @param overrideEngine ha meg van adva, EZT a beszédmotort használja a
 *        program általános beállítása helyett
 * @param overrideRate saját beszédsebesség
 * @param overridePitch saját hangmagasság
 *
 * A felülbírálás azért kell, mert egyes részeknek MÁS hang való: a menühöz
 * gyors, tömör felolvasás illik, egy órákon át hallgatott könyvhöz viszont
 * lassabb tempó és kellemesebb hangszín. A felülbírált példány NEM
 * befolyásolja a program többi részét.
 */
class TtsManager(
    context: Context,
    private val overrideEngine: String? = null,
    private val overrideRate: Float? = null,
    private val overridePitch: Float? = null
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var selectedVoiceName: String? =
        if (overrideEngine != null) null else TtsEngineStore.getSelectedVoiceName(appContext)
    private var tts: TextToSpeech =
        createEngine(overrideEngine ?: TtsEngineStore.getSelectedPackage(appContext))
    private var isReady = false
    private var initFailed = false

    /** Megpróbáltuk-e már a tartalék beszédmotort. */
    private var fallbackTried = false

    /**
     * NÉMA MÓD: nincs használható beszédmotor.
     * Ilyenkor a program hangjelzésekkel és rezgéssel kommunikál.
     */
    @Volatile
    var silentMode = false
        private set
    private var onUtteranceDone: (() -> Unit)? = null
    private var pendingOnReady: (() -> Unit)? = null
    private val readyCallbacks = mutableListOf<() -> Unit>()

    var speechRate: Float = overrideRate ?: TtsSettingsStore.getSpeechRate(appContext)
        set(value) {
            field = value.coerceIn(0.5f, 2.5f)
            // A felülbírált példány NEM írja felül a program általános
            // beállítását — csak magára vonatkozik.
            if (overrideRate == null) TtsSettingsStore.setSpeechRate(appContext, field)
            if (isReady) tts.setSpeechRate(field)
        }

    private fun createEngine(enginePackage: String?): TextToSpeech =
        if (enginePackage.isNullOrBlank()) {
            TextToSpeech(appContext, this)
        } else {
            TextToSpeech(appContext, this, enginePackage)
        }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            initFailed = false
            val result = tts.setLanguage(Locale("hu", "HU"))
            isReady = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!isReady) {
                Log.w("TTS", "Magyar nyelv nem érhető el, visszaesés angolra")
                tts.setLanguage(Locale.ENGLISH)
                isReady = true
            }
            applySelectedVoice()
            configureAudioRouting()
            tts.setSpeechRate(speechRate)
            // SAJÁT HANGMAGASSÁG, ha meg van adva (pl. a könyvolvasónál).
            overridePitch?.let {
                try {
                    tts.setPitch(it.coerceIn(0.5f, 1.6f))
                } catch (_: Exception) {
                }
            }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId?.startsWith("SDL_DONE_") == true) {
                        val callback = onUtteranceDone
                        onUtteranceDone = null
                        callback?.let { handler.post(it) }
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId?.startsWith("SDL_DONE_") == true) {
                        val callback = onUtteranceDone
                        onUtteranceDone = null
                        callback?.let { handler.post(it) }
                    }
                }
            })
            pendingOnReady?.let { handler.post(it) }
            pendingOnReady = null
            if (readyCallbacks.isNotEmpty()) {
                val callbacks = readyCallbacks.toList()
                readyCallbacks.clear()
                callbacks.forEach { handler.post(it) }
            }
        } else {
            Log.e("TTS", "TTS motor inicializálás sikertelen: $status")
            isReady = false
            initFailed = true

            // BESZÉD-TARTALÉK — a paradoxon kezelése.
            //
            // Ha a beszédmotor nem indul, a SuperDL NÉMÁVÁ válik, és vakon
            // használhatatlan lesz. Márpedig a hibajelzés maga is beszéd
            // volna — ezért nem támaszkodhatunk csak arra a rendszerre, ami
            // épp elromlott.
            //
            // EGYSZER megpróbálunk MÁSIK telepített motorral. Ha az sem megy,
            // hangjelzésekkel és rezgéssel kommunikálunk tovább.
            if (!fallbackTried) {
                fallbackTried = true
                val alternative = findAlternativeEngine()
                if (alternative != null) {
                    Log.w("TTS", "Tartalek beszedmotor probalasa: $alternative")
                    try {
                        tts.shutdown()
                    } catch (_: Exception) {
                    }
                    tts = createEngine(alternative)
                    return
                }
                Log.e("TTS", "NINCS hasznalhato beszedmotor — nema mod")
            }
            silentMode = true
            notifySilentMode()

            val pending = pendingOnReady
            pendingOnReady = null
            val queued = readyCallbacks.toList()
            readyCallbacks.clear()
            handler.post {
                pending?.invoke()
                queued.forEach { it.invoke() }
            }
        }
    }

    /**
     * Másik telepített beszédmotor keresése, ha a jelenlegi nem indul.
     * A rendszer alapértelmezettjét részesítjük előnyben, mert az a
     * legvalószínűbb, hogy működik.
     */
    private fun findAlternativeEngine(): String? = try {
        val current = tts.defaultEngine
        val engines = tts.engines.map { it.name }
        // Először a rendszer alapértelmezettje, ha nem az volt a hibás.
        val systemDefault = current?.takeIf { it != overrideEngine }
        systemDefault?.takeIf { engines.contains(it) }
            ?: engines.firstOrNull { it != overrideEngine }
    } catch (e: Exception) {
        Log.w("TTS", "tartalek motor kereses hiba: ${e.message}")
        null
    }

    /**
     * A NÉMA MÓD jelzése — hang és rezgés, mert beszéddel nem megy.
     *
     * Három rövid rezgés: ez a "figyelem, baj van" jelzés. Enélkül a
     * felhasználó azt hinné, a telefon lefagyott.
     */
    private fun notifySilentMode() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as android.os.VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }
            val pattern = longArrayOf(0, 200, 150, 200, 150, 200)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.w("TTS", "rezges hiba: ${e.message}")
        }
    }

    /**
     * ÚJRAPRÓBÁLKOZÁS a beszéddel — a menüből indítható.
     * Ha a felhasználó telepített egy motort vagy javította a beállítást,
     * ezzel újraindítható a beszéd, alkalmazás-újraindítás nélkül.
     */
    fun retrySpeech(onResult: (Boolean) -> Unit) {
        fallbackTried = false
        silentMode = false
        initFailed = false
        isReady = false
        try {
            tts.shutdown()
        } catch (_: Exception) {
        }
        tts = createEngine(TtsEngineStore.getSelectedPackage(appContext))
        // Adunk időt az indulásra, aztán jelentünk.
        handler.postDelayed({ onResult(isReady && !silentMode) }, 2500L)
    }

    fun switchEngine(
        packageName: String?,
        voiceName: String? = selectedVoiceName,
        onReady: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null
    ) {
        selectedVoiceName = voiceName?.takeIf { it.isNotBlank() }
        TtsEngineStore.setSelection(appContext, packageName, selectedVoiceName)
        pendingOnReady = if (onReady != null || onFailed != null) {
            {
                if (isReady) onReady?.invoke() else onFailed?.invoke()
            }
        } else null
        onUtteranceDone = null
        isReady = false
        initFailed = false
        val rate = speechRate
        tts.stop()
        tts.shutdown()
        speechRate = rate
        tts = createEngine(packageName)
    }

    fun runWhenReady(action: () -> Unit) {
        if (isReady) {
            handler.post(action)
        } else {
            readyCallbacks.add(action)
        }
    }

    /**
     * ÉPPEN BEÁLLÍTOTT NYELV — hogy ne hívjuk fölöslegesen a motort.
     * Az alapértelmezés a magyar; ettől csak akkor térünk el, ha a szöveg
     * egyértelműen idegen.
     */
    private var currentLocale: Locale = Locale("hu", "HU")

    private val HUNGARIAN = Locale("hu", "HU")

    /**
     * A NYELV BEÁLLÍTÁSA a kimondandó szöveghez.
     *
     * MIÉRT MINDEN KIMONDÁS ELŐTT: így nem kell "visszaállítani" semmit — a
     * következő mondat úgyis magát állítja be. Ez egyszerűbb és
     * megbízhatóbb, mint utólag visszakapcsolni, mert nincs olyan állapot,
     * amiben félúton ragadhatnánk.
     *
     * HA A MOTOR NEM TUDJA az adott nyelvet, marad a magyar — jobb egy
     * furcsán hangzó angol mondat, mint a néma telefon.
     */
    private fun applyLanguageFor(text: String) {
        if (!TtsSettingsStore.isAutoLanguage(appContext)) {
            if (currentLocale != HUNGARIAN) setLocaleSafely(HUNGARIAN)
            return
        }
        val detected = LanguageDetector.detect(text) ?: HUNGARIAN
        if (detected == currentLocale) return
        setLocaleSafely(detected)
    }

    private fun setLocaleSafely(locale: Locale) {
        try {
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                // Nincs meg a nyelv: visszaállunk magyarra, és többé nem
                // próbálkozunk vele ebben a menetben.
                if (locale != HUNGARIAN) {
                    tts.setLanguage(HUNGARIAN)
                    currentLocale = HUNGARIAN
                }
                return
            }
            currentLocale = locale
            // A nyelv váltása a hangot is lecserélheti, ezért a tempót
            // újra be kell állítani.
            tts.setSpeechRate(speechRate)
        } catch (e: Exception) {
            Log.w("TTS", "nyelvvaltas hiba: ${e.message}")
        }
    }

    /** Az ÉPPEN beállított szerep — hogy ne állítgassuk fölöslegesen. */
    private var currentRole: SpeechRole? = null

    /**
     * MÁSODIK BESZÉDMOTOR a program saját üzeneteihez.
     *
     * Csak akkor jön létre, ha a felhasználó KIFEJEZETTEN kért ilyet — így
     * senki nem fizet érte memóriával, aki nem használja.
     */
    private var roleTts: TextToSpeech? = null
    private var roleTtsReady = false

    /** Kell-e külön motor, és van-e beállítva. */
    private fun roleEnginePackage(): String? {
        // A könyvolvasó saját hangú példányánál nincs értelme: ott a
        // felhasználó egyetlen, szándékosan választott hangot akar.
        if (overrideEngine != null) return null
        if (!TtsSettingsStore.isVoiceRoles(appContext)) return null
        return TtsSettingsStore.getRoleEngine(appContext)
    }

    /**
     * A második motor előkészítése — CSAK az első használatkor.
     * Ha nem sikerül, csendben visszaesünk a hangszín-változatra: jobb egy
     * kevésbé feltűnő különbség, mint egy néma üzenet.
     */
    private fun ensureRoleTts(pkg: String) {
        if (roleTts != null) return
        try {
            roleTts = TextToSpeech(appContext, { status ->
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        roleTts?.setLanguage(HUNGARIAN)
                        roleTts?.setSpeechRate(speechRate)
                        roleTtsReady = true
                        Log.i("TTS", "masodik beszedmotor kesz: $pkg")
                    } catch (e: Exception) {
                        Log.w("TTS", "masodik motor beallitas hiba: ${e.message}")
                    }
                } else {
                    Log.w("TTS", "masodik motor nem indult: $pkg")
                    roleTtsReady = false
                }
            }, pkg)
        } catch (e: Exception) {
            Log.w("TTS", "masodik motor letrehozas hiba: ${e.message}")
            roleTts = null
        }
    }

    /**
     * A MÁSODIK MOTORRAL mondja ki — ha van és kész.
     * @return sikerült-e; ha nem, a hívó a szokásos úton mondja ki
     */
    private fun speakWithRoleEngine(text: String, role: SpeechRole): Boolean {
        if (role == SpeechRole.CONTENT) return false
        val pkg = roleEnginePackage() ?: return false
        ensureRoleTts(pkg)
        val engine = roleTts
        if (engine == null || !roleTtsReady) return false
        return try {
            // A FIGYELMEZTETÉS a második motoron is mélyebb és lassabb —
            // hogy a szerepek közti különbség ott is megmaradjon.
            engine.setPitch(role.pitchFactor.coerceIn(0.5f, 1.6f))
            engine.setSpeechRate((speechRate * role.rateFactor).coerceIn(0.5f, 2.5f))
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "SDL_ROLE_${System.currentTimeMillis()}")
            true
        } catch (e: Exception) {
            Log.w("TTS", "masodik motor beszed hiba: ${e.message}")
            false
        }
    }

    /**
     * A SZEREP HANGSZÍNÉNEK BEÁLLÍTÁSA.
     *
     * KÉT ESETBEN NEM CSINÁLUNK SEMMIT:
     *  - ha a felhasználó kikapcsolta a szerepeket
     *  - ha ez egy SAJÁT HANGÚ példány (a könyvolvasóé): ott a felhasználó
     *    kifejezetten beállított egy hangszínt, azt nem írjuk felül
     */
    private fun applyRole(role: SpeechRole) {
        if (overridePitch != null) return
        if (!TtsSettingsStore.isVoiceRoles(appContext)) {
            if (currentRole != null) {
                setPitchSafely(1.0f)
                tts.setSpeechRate(speechRate)
                currentRole = null
            }
            return
        }
        if (role == currentRole) return
        setPitchSafely(role.pitchFactor)
        try {
            tts.setSpeechRate((speechRate * role.rateFactor).coerceIn(0.5f, 2.5f))
        } catch (_: Exception) {
        }
        currentRole = role
    }

    private fun setPitchSafely(pitch: Float) {
        try {
            tts.setPitch(pitch.coerceIn(0.5f, 1.6f))
        } catch (_: Exception) {
        }
    }

    fun speak(text: String, role: SpeechRole = SpeechRole.CONTENT) {
        if (initFailed) {
            Log.w("TTS", "TTS nem elérhető, kihagyva: $text")
            return
        }
        if (!isReady) {
            runWhenReady { speak(text, role) }
            return
        }
        onUtteranceDone = null
        val prepared = PronunciationDictionary.apply(appContext, orient(text))
        // MÁSODIK MOTOR: ha a felhasználó kért ilyet, a program saját
        // üzenetei azon szólnak. Ha nem sikerül, a szokásos úton megy tovább.
        if (speakWithRoleEngine(prepared, role)) return
        applyRole(role)
        // NYELVFELISMERÉS: a szótár UTÁN, mert a szótár magyar szavakra
        // cserélhet rövidítéseket — és attól a szöveg magyarabb lesz.
        applyLanguageFor(prepared)
        tts.speak(
            prepared,
            TextToSpeech.QUEUE_FLUSH,
            speakParams(),
            "SDL_${System.currentTimeMillis()}"
        )
    }

    /**
     * A meglévő hívások változatlanul működnek: alapértelmezés a tartalom-hang.
     * A szerepes változat külön aláírással érhető el, hogy egyetlen korábbi
     * hívást se kelljen átírni.
     */
    fun speakThen(text: String, onDone: () -> Unit) =
        speakThen(text, SpeechRole.CONTENT, onDone)

    fun speakThen(text: String, role: SpeechRole, onDone: () -> Unit) {
        if (initFailed) {
            Log.w("TTS", "TTS nem elérhető, speakThen kihagyva")
            handler.post(onDone)
            return
        }
        if (!isReady) {
            runWhenReady { speakThen(text, role, onDone) }
            return
        }
        onUtteranceDone = onDone
        applyRole(role)
        val id = "SDL_DONE_${System.currentTimeMillis()}"
        val prepared = PronunciationDictionary.apply(appContext, orient(text))
        applyLanguageFor(prepared)
        tts.speak(
            prepared,
            TextToSpeech.QUEUE_FLUSH,
            speakParams(),
            id
        )
    }

    /**
     * Kimondja a szöveget, ÉS AZONNAL elvégzi a műveletet — NEM várja meg a
     * beszéd végét.
     *
     * MIÉRT KELL: a speakThen a művelet elvégzését a beszéd BEFEJEZÉSÉHEZ köti,
     * ezért egy hosszabb mondat után a felhasználó fölöslegesen várt: rápöccintett
     * valamire, és a parancs csak másodpercekkel később indult el.
     * Ezzel a metódussal a művelet azonnal fut, a beszéd pedig közben szól.
     *
     * MIKOR NE HASZNÁLD:
     *  - ha utána MIKROFON indul (diktálás, hangfelvétel) — ott a beszéd
     *    belemondana a felvételbe, maradjon a speakThen,
     *  - ha a művelet elvégzése előtt a felhasználónak feltétlenül hallania kell
     *    a figyelmeztetést.
     */
    fun speakAndRun(text: String, action: () -> Unit) {
        speak(text)
        handler.post(action)
    }

    /**
     * A FELÜLET ELFORGATÁSA A BESZÉDBEN.
     *
     * Elforgatott módban a program utasításai („söpörj fel-le") hazugsággá
     * válnának. Ez az egyetlen hely, ahol MINDEN kimondott mondat átmegy,
     * ezért itt fordítjuk át — így nem maradhat ki képernyő, és a később
     * írt mondatok is automatikusan jók lesznek.
     */
    private fun orient(text: String): String =
        com.superdl.launcher.gestures.GestureWords.translate(text)

    fun speakAdd(text: String) {
        if (initFailed) return
        if (!isReady) {
            runWhenReady { speakAdd(text) }
            return
        }
        // A hozzáfűzött mondat a MÁR beállított nyelven szól: nem váltunk
        // közben, mert az félbeszakítaná a folyamatban lévő beszédet.
        tts.speak(
            PronunciationDictionary.apply(appContext, orient(text)),
            TextToSpeech.QUEUE_ADD,
            speakParams(),
            "SDL_ADD_${System.currentTimeMillis()}"
        )
    }

    fun isSpeaking(): Boolean = isReady && tts.isSpeaking

    fun stop() {
        onUtteranceDone = null
        tts.stop()
        // A második motort is meg kell állítani, különben tovább beszélne.
        try {
            roleTts?.stop()
        } catch (_: Exception) {
        }
    }

    fun speedUp() {
        speechRate += 0.1f
        speak("Sebesség: ${String.format(Locale.getDefault(), "%.1f", speechRate)}")
    }

    fun speedDown() {
        speechRate -= 0.1f
        speak("Sebesség: ${String.format(Locale.getDefault(), "%.1f", speechRate)}")
    }

    private fun configureAudioRouting() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // A HANGCSATORNA a beállításból jön.
            //
            // MIÉRT NEM FIX: a kisegítő csatorna bizonyos készülékeken NEM
            // ereszti ki egy sima alkalmazás hangját — ilyenkor a program
            // teljesen néma marad, pedig a beszédmotor dolgozik. Vakon ez
            // használhatatlan telefont jelent.
            val useAccessibility = TtsSettingsStore.getSpeechChannel(appContext) ==
                TtsSettingsStore.CHANNEL_ACCESSIBILITY
            val attributes = AudioAttributes.Builder()
                .setUsage(
                    if (useAccessibility) AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
                    else AudioAttributes.USAGE_MEDIA
                )
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                .build()
            tts.setAudioAttributes(attributes)
        }
    }

    private fun speakParams(): Bundle = Bundle().apply {
        val useAccessibility = TtsSettingsStore.getSpeechChannel(appContext) ==
            TtsSettingsStore.CHANNEL_ACCESSIBILITY
        putInt(
            TextToSpeech.Engine.KEY_PARAM_STREAM,
            if (useAccessibility) AudioManager.STREAM_ACCESSIBILITY
            else AudioManager.STREAM_MUSIC
        )
        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
    }

    private fun applySelectedVoice() {
        val voiceName = selectedVoiceName?.takeIf { it.isNotBlank() } ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val voices = tts.voices ?: return
        val voice = voices.firstOrNull { it.name == voiceName } ?: return
        tts.voice = voice
    }

    fun shutdown() {
        try {
            roleTts?.stop()
            roleTts?.shutdown()
        } catch (_: Exception) {
        }
        roleTts = null
        roleTtsReady = false
        handler.removeCallbacksAndMessages(null)
        onUtteranceDone = null
        pendingOnReady = null
        readyCallbacks.clear()
        tts.stop()
        tts.shutdown()
    }
}