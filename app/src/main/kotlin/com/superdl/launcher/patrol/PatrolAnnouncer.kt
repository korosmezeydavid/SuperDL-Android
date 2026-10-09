package com.superdl.launcher.patrol

import android.content.Context
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.superdl.launcher.feedback.AlertSoundCategory
import com.superdl.launcher.feedback.AlertSoundPlayer
import com.superdl.launcher.system.QuietModeHelper
import com.superdl.launcher.tts.TtsEngineStore
import com.superdl.launcher.tts.TtsSettingsStore
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

object PatrolAnnouncer {

    private const val TAG = "SuperDL.PatrolAnnouncer"
    private const val MAX_QUEUE_SIZE = 12

    private data class AnnounceRequest(
        val appContext: Context,
        val message: String,
        val withBeep: Boolean,
        val soundCategory: AlertSoundCategory,
        val softChime: Boolean = false,
        val clockTime: Pair<Int, Int>? = null,
        val onDone: (() -> Unit)?
    )

    private val speaking = AtomicBoolean(false)
    private val queue = ConcurrentLinkedQueue<AnnounceRequest>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun announce(
        context: Context,
        message: String,
        withBeep: Boolean = true,
        soundCategory: AlertSoundCategory = AlertSoundCategory.GENERAL_NOTIFICATION,
        critical: Boolean = false,
        softChime: Boolean = false,
        clockTime: Pair<Int, Int>? = null,
        onDone: (() -> Unit)? = null
    ) {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) {
            onDone?.let { mainHandler.post(it) }
            return
        }
        // A kritikus bejelentések (pl. aktív navigáció kanyarai) átlépnek a
        // néma módon: a felhasználó kifejezetten kérte őket a vezetés indításával,
        // és biztonsági szempontból nem szabad elnémítani.
        if (!critical && QuietModeHelper.shouldSuppressNotificationAnnouncements(context)) {
            onDone?.let { mainHandler.post(it) }
            return
        }
        val request = AnnounceRequest(
            appContext = context.applicationContext,
            message = trimmed,
            withBeep = withBeep,
            soundCategory = soundCategory,
            softChime = softChime,
            clockTime = clockTime,
            onDone = onDone
        )
        if (speaking.compareAndSet(false, true)) {
            deliver(request)
        } else {
            if (queue.size >= MAX_QUEUE_SIZE) {
                val dropped = queue.poll()
                dropped?.onDone?.let { mainHandler.post(it) }
                Log.w(TAG, "Announcement queue full; dropped oldest pending message")
            }
            queue.offer(request)
        }
    }

    private fun deliver(request: AnnounceRequest) {
        val wakeLock = acquireWakeLock(request.appContext)
        val finish: () -> Unit = {
            releaseWakeLock(wakeLock)
            speaking.set(false)
            request.onDone?.let { mainHandler.post(it) }
            processNextQueued()
        }
        if (request.softChime) {
            // Egyetlen rövid, lágy csendülés az egész értesítő-hangsor helyett
            // (pl. óránkénti időbemondás, feloldás) – kevésbé zavaró.
            playSoftChime(request.appContext)
            mainHandler.postDelayed({
                val clock = request.clockTime
                if (clock != null) {
                    // BESZÉLŐ ÓRA: élő hangon, klipekből. Ha bármi hiányzik
                    // vagy nem szól, ugyanaz a mondat a felolvasóval megy.
                    TalkingClock.play(request.appContext, clock.first, clock.second) { ok ->
                        if (ok) finish() else speak(request.appContext, request.message, finish, speakerTime = true)
                    }
                } else {
                    speak(request.appContext, request.message, finish, speakerTime = true)
                }
            }, 700L)
        } else if (request.withBeep) {
            AlertSoundPlayer.playOnce(request.appContext, request.soundCategory)
            mainHandler.postDelayed({
                speak(request.appContext, request.message, finish)
            }, 320L)
        } else {
            speak(request.appContext, request.message, finish)
        }
    }

    private fun processNextQueued() {
        val next = queue.poll() ?: return
        if (speaking.compareAndSet(false, true)) {
            deliver(next)
        } else {
            queue.offer(next)
        }
    }

    /**
     * EGY HÁTTÉRBEN ELMONDOTT MONDAT — ÉS A HOZZÁ TARTOZÓ TAKARÍTÁS.
     *
     * EZ VOLT A BAJ (Alph, 2026-09-19): a beszéd napközben teljesen elnémult,
     * és csak újraindítás segített. Ez a metódus ÓRÁNKÉNT lefut (időbemondás),
     * és három sebe volt:
     *
     *  1. NEM VOLT IDŐKORLÁTJA. Ha a motor nem jelzett vissza, a `speaking`
     *     jelző örökre igaz maradt — onnantól az őrjárat MINDEN bemondása
     *     némán a sorba került, és soha nem szólalt meg.
     *  2. A MOTOR NEM MINDIG ZÁRULT BE. Ha a visszajelzés elmaradt, a
     *     beszédmotorhoz kötött kapcsolat nyitva maradt — óránként eggyel
     *     több árva kapcsolat.
     *  3. A BEZÁRÁS A MOTOR SAJÁT SZÁLÁN történt, épp amikor az még dolgozott.
     *     Ez az a mozdulat, amitől a beszédmotorok be szoktak ragadni — és
     *     ilyenkor nemcsak ez a bemondás hallgat el, hanem a program egész
     *     beszéde, mert ugyanazt a motort használja.
     *
     * Mostantól: MINDIG van időkorlát, a motor PONTOSAN EGYSZER zárul be, és
     * a bezárás a főszálon, egy kis szünet után történik.
     */
    private fun speak(context: Context, message: String, onDone: () -> Unit,
                      speakerTime: Boolean = false) {
        // TARTÓK, NEM EGYSZERŰ VÁLTOZÓK: a motor létrejötte és a motor első
        // visszajelzése VERSENYEZHET egymással. A tartóból mindkét irány
        // ugyanazt a példányt látja, akármelyik ér előbb ide.
        val engineHolder = arrayOfNulls<TextToSpeech>(1)
        val timeoutHolder = arrayOfNulls<Runnable>(1)
        val enginePackage = TtsEngineStore.getSelectedPackage(context)
        val finished = AtomicBoolean(false)

        val finish: (Boolean) -> Unit = { withBeep ->
            if (finished.compareAndSet(false, true)) {
                timeoutHolder[0]?.let { mainHandler.removeCallbacks(it) }
                // A BEZÁRÁS A FŐSZÁLON, KIS SZÜNET UTÁN — hogy a motor
                // befejezhesse, amit épp csinál.
                mainHandler.postDelayed({
                    val engine = engineHolder[0]
                    engineHolder[0] = null
                    try {
                        engine?.stop()
                        engine?.shutdown()
                    } catch (e: Exception) {
                        Log.w(TAG, "TTS shutdown failed", e)
                    }
                }, 250L)
                if (withBeep) playFallbackBeep()
                mainHandler.post(onDone)
            }
        }

        // IDŐKORLÁT: a hosszabb mondat több időt kap, de a végtelen soha.
        val limit = 8_000L + message.length * 90L
        val timeout = Runnable {
            Log.w(TAG, "Announcement timed out — releasing")
            finish(false)
        }
        timeoutHolder[0] = timeout
        mainHandler.postDelayed(timeout, limit)

        val listener = TextToSpeech.OnInitListener { status ->
            if (status != TextToSpeech.SUCCESS) {
                finish(true)
                return@OnInitListener
            }
            val engine = engineHolder[0] ?: run {
                finish(false)
                return@OnInitListener
            }
            try {
                val lang = engine.setLanguage(Locale("hu", "HU"))
                if (lang == TextToSpeech.LANG_MISSING_DATA ||
                    lang == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    engine.setLanguage(Locale.getDefault())
                }
                engine.setSpeechRate(TtsSettingsStore.getSpeechRate(context))
                if (speakerTime) {
                    // A klip nélküli időbemondás ne a média-folyam füleses
                    // útvonalára essen vissza. A pontos útvonal OEM-függő.
                    engine.setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                }
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == "patrol_announce") finish(false)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == "patrol_announce") finish(true)
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        if (utteranceId == "patrol_announce") finish(false)
                    }
                })
                val result =
                    engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, "patrol_announce")
                if (result != TextToSpeech.SUCCESS) {
                    Log.w(TAG, "Engine refused the announcement ($result)")
                    finish(true)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Announcement failed", e)
                finish(true)
            }
        }
        engineHolder[0] = try {
            if (enginePackage.isNullOrBlank()) {
                TextToSpeech(context, listener)
            } else {
                TextToSpeech(context, listener, enginePackage)
            }
        } catch (e: Exception) {
            Log.w(TAG, "TTS creation failed", e)
            finish(true)
            null
        }
    }

    private fun acquireWakeLock(context: Context): PowerManager.WakeLock? =
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SuperDL:PatrolAnnounce").apply {
                setReferenceCounted(false)
                acquire(45_000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wake lock acquire failed", e)
            null
        }

    private fun releaseWakeLock(wakeLock: PowerManager.WakeLock?) {
        try {
            if (wakeLock?.isHeld == true) wakeLock.release()
        } catch (e: Exception) {
            Log.w(TAG, "Wake lock release failed", e)
        }
    }

    private fun playSoftChime(context: Context) {
        try {
            // A periodikus időbemondás kezdőhangja: egy kellemes "kling"
            // hangfájl (snd_time_chime) a régi szintetikus ToneGenerator-bleep
            // helyett. A lejátszás után magától elengedi az erőforrást.
            val mp = android.media.MediaPlayer.create(
                context, com.superdl.launcher.R.raw.snd_time_chime
            )
            if (mp != null) {
                mp.setOnCompletionListener { it.release() }
                mp.start()
            } else {
                // Ha valamiért nem tölthető be a fájl, marad a régi bleep.
                val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
                tone.startTone(ToneGenerator.TONE_PROP_ACK, 130)
                mainHandler.postDelayed({ tone.release() }, 200L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Soft chime failed", e)
        }
    }

    private fun playFallbackBeep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
            mainHandler.postDelayed({ tone.release() }, 260L)
        } catch (e: Exception) {
            Log.w(TAG, "Fallback beep failed", e)
        }
    }
}
