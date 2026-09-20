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

    /**
     * A BESZÉDMOTOR VISSZAJELZÉSE — EZ A METÓDUS SOHA NEM DOBHAT KIVÉTELT.
     *
     * MIÉRT KÜLÖN VÉDVE: ezt nem mi hívjuk, hanem a rendszer, KÉSŐBB, a
     * főszálon (TextToSpeech.dispatchOnInit). Ezért a TtsManager létrehozása
     * köré tett try/catch itt SEMMIT NEM ÉR — a kivétel jóval a konstruktor
     * lefutása után érkezik, és egy elkapatlan kivétel a főszálon az EGÉSZ
     * folyamatot megöli.
     *
     * Élesben pontosan ez történt: bekapcsolás után, a feloldás előtt a
     * configureAudioRouting() a titkosított beállítás-tárolóhoz nyúlt,
     * kivételt dobott, és magával vitte a PIN segédet is — a felhasználó nem
     * tudta feloldani a telefonját. A tárolót azóta a SafePrefs védi, de az
     * öv mellé itt a nadrágtartó is kell: ha bármelyik lépés elszáll, a
     * beszéd elnémul, de a program ÉL.
     */
    override fun onInit(status: Int) {
        try {
            onInitInner(status)
        } catch (e: Throwable) {
            Log.w("TTS", "onInit hiba (a folyamat tovabb el): ${e.message}")
            isReady = false
            initFailed = true
        }
    }

    private fun onInitInner(status: Int) {
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
                override fun onStart(utteranceId: String?) {
                    noteEngineSignal()
                }

                override fun onDone(utteranceId: String?) {
                    noteEngineSignal()
                    fireDone(utteranceId)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    noteEngineSignal()
                    fireDone(utteranceId)
                }

                /**
                 * MEGSZAKÍTOTT BESZÉD — ÉS EZ NEM ELHANYAGOLHATÓ.
                 *
                 * Ha egy mondatot félbevág egy újabb beszéd (QUEUE_FLUSH), az
                 * Android NEM `onDone`-t hív, hanem EZT. Enélkül a `speakThen`
                 * visszahívása örökre elveszett: a beszédhez kötött MŰVELET
                 * soha nem futott le, és a program némán nem csinált semmit.
                 *
                 * A beszéd tájékoztatás, nem feltétel. Egy félbeszakított
                 * mondat után a művelet ATTÓL MÉG elvégzendő.
                 */
                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    noteEngineSignal()
                    fireDone(utteranceId)
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
        // A NÉMA MÓD NEM EGYIRÁNYÚ AJTÓ.
        //
        // Eddig innen csak egy naplósor vezetett kifelé: ha a motor egyszer
        // nem indult el, a program élete végéig hallgatott. Márpedig a motor
        // legtöbbször nem „elromlik", hanem ELENGEDI a kapcsolatot — egy
        // másodperccel később ugyanaz a motor tökéletesen működik.
        //
        // Mostantól minden megszólalási kísérlet egyben egy újraélesztési
        // lehetőség is. A várakozási idő megvédi az akkumulátort.
        if (initFailed || silentMode) {
            if (!tryRevive("a beszédmotor nem indult el", text, role)) {
                Log.w("TTS", "TTS nem elérhető, kihagyva: $text")
            }
            return
        }
        if (!isReady) {
            runWhenReady { speak(text, role) }
            // HA A MOTOR SOHA NEM JELENTKEZIK BE, a mondat egy listába kerül,
            // ami soha nem ürül ki — a program „beszél", a telefon néma.
            armReadyWatchdog(text, role)
            return
        }
        // A MOTOR NÉMÁN IS MEGHALHAT: elfogadja a mondatot (SUCCESS), aztán
        // se hang, se visszajelzés. Ilyenkor semmilyen hibakód nem árulkodik,
        // egyedül a CSEND. Ha kértünk beszédet, és a motor azóta egyetlen
        // jelet sem adott, újraélesztjük.
        if (engineWentQuiet()) {
            if (tryRevive("a motor elfogadta a mondatot, de nem szólalt meg", text, role)) return
        }
        // EGY FÜGGŐ MŰVELETET NEM EJTÜNK EL.
        //
        // EZ VOLT A BAJ (Alph, 2026-09-18): néhány művelet a beszéd VÉGÉHEZ
        // van kötve — például a hangcímke felvétele. Előbb elhangzik, hogy
        // „Mondd be, mi ez", és csak UTÁNA indul a mikrofon, hogy a program
        // saját mondata ne kerüljön rá a címkére.
        //
        // Ez a sor viszont eldobta a függő műveletet, ha közben BÁRMI más
        // megszólalt: egy értesítés bemondása, az óra, egy akkumulátor-
        // figyelmeztetés, az őrjárat. Onnantól a felvétel SOHA nem indult el,
        // és a program semmit nem mondott róla. A felhasználó annyit látott,
        // hogy rásöpör a műveletre, és nem történik semmi — és azt sem
        // tudhatta, mitől függ, hiszen attól függött, szólt-e közben más.
        //
        // Mostantól a mondata elmarad, de a MŰVELETE lefut. Ugyanaz az elv,
        // amit a speakThen már követ.
        val pending = onUtteranceDone
        onUtteranceDone = null
        if (pending != null) {
            doneWatchdog?.let { handler.removeCallbacks(it) }
            doneWatchdog = null
            handler.post(pending)
        }
        val prepared = PronunciationDictionary.apply(appContext, orient(text))
        // MÁSODIK MOTOR: ha a felhasználó kért ilyet, a program saját
        // üzenetei azon szólnak. Ha nem sikerül, a szokásos úton megy tovább.
        if (speakWithRoleEngine(prepared, role)) return
        applyRole(role)
        // NYELVFELISMERÉS: a szótár UTÁN, mert a szótár magyar szavakra
        // cserélhet rövidítéseket — és attól a szöveg magyarabb lesz.
        applyLanguageFor(prepared)
        // A KÉRÉS IDEJE ELŐBB: a motor visszajelzése MÁSIK SZÁLON érkezik, és
        // ha utólag írnánk be, egy gyors motor jelzése „korábbinak" látszana
        // a kérésnél — a program pedig fölöslegesen élesztgetné magát.
        lastSpeakRequestAt = System.currentTimeMillis()
        val result = tts.speak(
            prepared,
            TextToSpeech.QUEUE_FLUSH,
            speakParams(),
            "SDL_${System.currentTimeMillis()}"
        )
        noteSpeakResult(result, text, role)
    }

    // ── A BESZÉD NEM HALHAT MEG CSENDBEN ──────────────────────────────────
    //
    // EZ VOLT A BAJ (Alph, 2026-09-19): „a beszéd az előbb teljesen elnémult,
    // csak újraindítással oldottam meg."
    //
    // A speak() eddig ELDOBTA a beszédmotor válaszát. Ha a rendszer közben
    // megölte a motor folyamatát — memóriahiány, motorfrissítés, energia-
    // takarékos leállítás —, a tts.speak() ERROR-t adott vissza, és a program
    // ezt nem nézte meg. Onnantól MINDEN mondat a semmibe ment: a program azt
    // hitte, beszél, a felhasználó pedig néma telefont kapott. Egyetlen kiút
    // az alkalmazás újraindítása volt.
    //
    // Ez vakon a legrosszabb fajta hiba: a telefon nem omlik össze, nem
    // hibázik, egyszerűen NINCS. Aki nem látja a képernyőt, annak ilyenkor
    // megszűnik a kapcsolat a készülékkel.
    //
    // Mostantól a program megnézi a választ, és ha a motor nem fogadja el a
    // mondatot, ÚJRAÉLESZTI magát, majd megismétli, amit mondani akart.

    /** Hány mondatot utasított vissza egymás után a motor. */
    private var speakFailures = 0

    /** Mikor élesztettük újra utoljára a motort — a hurok ellen. */
    private var lastRecoveryAt = 0L

    /** Mikor kértünk utoljára beszédet, amit a motor el is fogadott. */
    private var lastSpeakRequestAt = 0L

    /** Mikor adott a motor utoljára BÁRMILYEN életjelet. */
    private var lastEngineSignalAt = 0L

    /**
     * Hány újraélesztés következett egymás után úgy, hogy közben a motor
     * egyszer sem szólalt meg. Ez állítja meg a végtelen kört.
     */
    private var reviveChain = 0

    /** Összes újraélesztés ebben a futásban — a hibajelentésnek. */
    private var reviveTotal = 0

    private var readyWatchdog: Runnable? = null

    companion object {
        /** Ennyit várunk két újraélesztés között. */
        private const val REVIVE_COOLDOWN_MS = 20_000L

        /** Ennyi csend után tekintjük némán megdöglöttnek a motort. */
        private const val QUIET_DEATH_MS = 8_000L

        /** Ennyi ideig várunk a motor bejelentkezésére. */
        private const val READY_WAIT_MS = 5_000L

        /** Ennyi eredménytelen újraélesztés után megállunk. */
        private const val MAX_REVIVE_CHAIN = 3

        /**
         * A HIBAJELENTÉSNEK: volt-e néma beszéd-leállás, és mikor.
         *
         * Azért statikus, mert a jelentést nem a beszélő példány írja — és
         * épp az a lényeg, hogy a néma leállás NYOMOT HAGYJON.
         */
        @Volatile
        private var lastSpeechRecovery: String? = null

        fun lastSpeechRecoveryInfo(): String? = lastSpeechRecovery

        internal fun noteRecovery(stamp: String) {
            lastSpeechRecovery = stamp
        }
    }

    /** A motor életjelet adott: minden rendben, a lánc szakad. */
    private fun noteEngineSignal() {
        lastEngineSignalAt = System.currentTimeMillis()
        reviveChain = 0
        speakFailures = 0
    }

    /**
     * NÉMÁN MEGHALT-E A MOTOR: kértünk beszédet, elfogadta, és azóta
     * egyetlen jelet sem adott.
     */
    private fun engineWentQuiet(): Boolean {
        if (lastSpeakRequestAt == 0L) return false
        if (lastEngineSignalAt >= lastSpeakRequestAt) return false
        return System.currentTimeMillis() - lastSpeakRequestAt > QUIET_DEATH_MS
    }

    /**
     * A MOTOR ÚJRAÉLESZTÉSE — minden néma halál egyetlen kijárata.
     *
     * @return igaz, ha tényleg nekiláttunk (különben a hívó a régi úton megy)
     */
    private fun tryRevive(reason: String, text: String?, role: SpeechRole): Boolean {
        val now = System.currentTimeMillis()
        // NE PRÓBÁLKOZZUNK VÉGTELENÜL. Ha percenként újraélesztenénk a motort,
        // azzal csak az akkumulátort ennénk meg — a felhasználó meg úgyis néma
        // telefont kapna, csak melegebbet.
        if (now - lastRecoveryAt < REVIVE_COOLDOWN_MS) return false
        if (reviveChain >= MAX_REVIVE_CHAIN) {
            Log.w("TTS", "Az ujraelesztes $reviveChain. alkalommal sem hozott hangot — megallunk")
            return false
        }
        lastRecoveryAt = now
        reviveChain++
        reviveTotal++
        speakFailures = 0

        // MÁSODIK PRÓBÁLKOZÁSRA MÁSIK MOTORT. Ha ugyanaz a motor kétszer sem
        // szólal meg, nincs értelme harmadszor is őt kérni: ilyenkor a
        // rendszer alapértelmezettje a legjobb esély.
        val selected = TtsEngineStore.getSelectedPackage(appContext)
        val target = if (reviveChain >= 2) findAlternativeEngine() ?: selected else selected

        Log.w("TTS", "NEMA BESZED — ujraelesztes ($reason), motor: $target")
        noteRecovery(
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(now)) +
                " — $reason, újraélesztve (összesen $reviveTotal alkalommal)"
        )

        fallbackTried = false
        silentMode = false
        initFailed = false
        isReady = false
        lastSpeakRequestAt = 0L
        try {
            tts.shutdown()
        } catch (_: Exception) {
        }
        tts = createEngine(target)
        // AMIT MONDANI AKART, AZT MONDJA IS KI. A felhasználó egy mondatot
        // várt; ha csak a motor éledne újra, ő azt hinné, elrontotta valamit.
        if (text != null) runWhenReady { speak(text, role) }
        armReadyWatchdog(text, role)
        return true
    }

    /**
     * ŐRSZEM A BEJELENTKEZÉSRE.
     *
     * A `runWhenReady` listája csak akkor ürül ki, ha a motor jelentkezik.
     * Ha soha nem jelentkezik, a mondatok szépen sorban ODABENT maradnak, és
     * kívülről ez pontosan úgy néz ki, mint a néma telefon. Ez az őrszem
     * gondoskodik róla, hogy a várakozásnak vége legyen.
     */
    private fun armReadyWatchdog(text: String?, role: SpeechRole) {
        readyWatchdog?.let { handler.removeCallbacks(it) }
        val watchdog = Runnable {
            readyWatchdog = null
            if (isReady && !silentMode) return@Runnable
            // A felgyűlt mondatokat eldobjuk: fél perccel később felolvasni
            // egy menüsort rosszabb, mint nem felolvasni.
            readyCallbacks.clear()
            tryRevive("a motor nem jelentkezett be", text, role)
        }
        readyWatchdog = watchdog
        handler.postDelayed(watchdog, READY_WAIT_MS)
    }

    private fun noteSpeakResult(result: Int, text: String, role: SpeechRole) {
        if (result == TextToSpeech.SUCCESS) {
            speakFailures = 0
            return
        }
        lastSpeakRequestAt = 0L
        speakFailures++
        Log.w("TTS", "A motor visszautasitotta a mondatot ($result), $speakFailures. alkalommal")
        if (speakFailures < 2) return
        tryRevive("a motor nem fogadta el a mondatot", text, role)
    }

    /**
     * A meglévő hívások változatlanul működnek: alapértelmezés a tartalom-hang.
     * A szerepes változat külön aláírással érhető el, hogy egyetlen korábbi
     * hívást se kelljen átírni.
     */
    fun speakThen(text: String, onDone: () -> Unit) =
        speakThen(text, SpeechRole.CONTENT, onDone)

    /**
     * A BESZÉDHEZ KÖTÖTT MŰVELET ELSÜTÉSE — pontosan egyszer.
     *
     * Négy útról érkezhet: onDone, onError, onStop (megszakítás), és a
     * biztonsági időkorlát. Amelyik előbb ér ide, az viszi.
     */
    private fun fireDone(utteranceId: String?) {
        if (utteranceId?.startsWith("SDL_DONE_") != true) return
        val callback = onUtteranceDone
        onUtteranceDone = null
        doneWatchdog?.let { handler.removeCallbacks(it) }
        doneWatchdog = null
        callback?.let { handler.post(it) }
    }

    private var doneWatchdog: Runnable? = null

    fun speakThen(text: String, role: SpeechRole, onDone: () -> Unit) {
        if (initFailed || silentMode) {
            Log.w("TTS", "TTS nem elérhető, speakThen kihagyva")
            // A MŰVELET LEFUT, a beszéd pedig közben próbál visszajönni.
            // A mondatot NEM adjuk át az újraélesztésnek: ez a mondat egy
            // művelethez tartozik, amit épp most végzünk el — később
            // felmondani félrevezető volna.
            tryRevive("a beszédmotor nem indult el", null, role)
            handler.post(onDone)
            return
        }
        if (!isReady) {
            // A MŰVELET NEM VÁRHAT A MOTORRA A VILÁG VÉGÉIG. Ha a motor nem
            // jelentkezik be, a beszéd elmarad, de a művelet lefut — pont
            // úgy, ahogy a megszakított mondat után is.
            val fired = java.util.concurrent.atomic.AtomicBoolean(false)
            val once = { if (fired.compareAndSet(false, true)) onDone() }
            runWhenReady { speakThen(text, role, once) }
            handler.postDelayed({
                if (!isReady) {
                    Log.w("TTS", "A motor nem jelentkezett be — a muvelet igy is fut")
                    once()
                    tryRevive("a motor nem jelentkezett be", null, role)
                }
            }, READY_WAIT_MS)
            return
        }
        // Ha volt egy korábbi, még függő visszahívás, azt NEM ejtjük el:
        // a mondata ugyan elmarad, a művelete viszont elvégzendő.
        val previous = onUtteranceDone
        onUtteranceDone = onDone
        doneWatchdog?.let { handler.removeCallbacks(it) }
        previous?.let { handler.post(it) }

        applyRole(role)
        val id = "SDL_DONE_${System.currentTimeMillis()}"
        val prepared = PronunciationDictionary.apply(appContext, orient(text))
        applyLanguageFor(prepared)
        lastSpeakRequestAt = System.currentTimeMillis()
        val result = tts.speak(prepared, TextToSpeech.QUEUE_FLUSH, speakParams(), id)

        if (result != TextToSpeech.SUCCESS) {
            lastSpeakRequestAt = 0L
            // A beszéd próbáljon visszajönni a következő mondatra.
            tryRevive("a motor nem fogadta el a mondatot", null, role)
            // A MOTOR EL SEM INDULT. Ilyenkor SEMMILYEN visszajelzés nem jön —
            // se onDone, se onError, se onStop —, tehát a visszahívás örökre
            // ott ragadna. Frissen telepített telefonon ez valóságos eset:
            // a beszédmotor még tölthet le nyelvi adatot.
            Log.w("TTS", "A speak() nem indult el ($result), a művelet azonnal fut")
            fireDone(id)
            return
        }

        // BIZTONSÁGI IDŐKORLÁT. Bármi történjék a motorral, a művelet elsül.
        // Egy beszédmotor hibája soha nem akaszthatja meg a programot.
        val timeout = 4000L + prepared.length * 90L
        val watchdog = Runnable { fireDone(id) }
        doneWatchdog = watchdog
        handler.postDelayed(watchdog, timeout)
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
            // A FLAG_AUDIBILITY_ENFORCED-OT SZÁNDÉKOSAN NEM HASZNÁLJUK.
            //
            // Ez a jelző arra való, hogy a rendszer KIKÉNYSZERÍTSE a hangot
            // (eredetileg a fényképezőgép zárhangjához, ahol jogszabály
            // követeli). Amíg rajta volt, a beszéd a rendszer által
            // kikényszerített csatornán szólt: a felhasználó hangerő-gombja
            // NEM hatott rá. Pontosan ezt jelentették a tesztelők — „a
            // SuperDL üvölt, és hiába nyomom a hangerő le gombot".
            //
            // Egy képernyőolvasót muszáj lehalkítani tudni. Aki éjjel
            // hallgatja, annak ez nem apróság.
            val attributes = AudioAttributes.Builder()
                .setUsage(
                    if (useAccessibility) AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
                    else AudioAttributes.USAGE_MEDIA
                )
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts.setAudioAttributes(attributes)
        }
    }

    /**
     * AZ A HANGCSATORNA, AMIN A BESZÉD SZÓL.
     *
     * Az activityk ezt adják át a `volumeControlStream`-nek, hogy a
     * hangerő-gombok MINDIG a beszédet állítsák — akkor is, ha éppen nem
     * szól semmi más. Enélkül a rendszer a csengőhang hangerejét
     * állítgatta, a beszéd meg maradt, amilyen volt.
     */
    fun speechStream(): Int =
        if (TtsSettingsStore.getSpeechChannel(appContext) == TtsSettingsStore.CHANNEL_ACCESSIBILITY)
            AudioManager.STREAM_ACCESSIBILITY
        else
            AudioManager.STREAM_MUSIC

    private fun speakParams(): Bundle = Bundle().apply {
        putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, speechStream())
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