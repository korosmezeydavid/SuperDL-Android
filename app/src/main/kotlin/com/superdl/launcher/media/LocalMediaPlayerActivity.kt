package com.superdl.launcher.media

import android.app.Activity
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.superdl.launcher.call.CallPauseGuard
import com.superdl.launcher.gestures.SwipeGestureListener
import com.superdl.launcher.tts.TtsManager
import java.io.File

/**
 * SAJÁT MÉDIALEJÁTSZÓ — zenéhez ÉS videóhoz, a fájlkezelőből.
 *
 * MIÉRT KELLETT: a fájlkezelő eddig ÁTADTA a fájlt egy másik alkalmazásnak
 * (ACTION_VIEW). Egy friss telepítésű telefonon viszont gyakran NINCS
 * médialejátszó — és a felhasználó annyit hallott, hogy „nincs hozzá
 * alkalmazás". Vagyis a saját zenéjét nem tudta meghallgatni a saját
 * telefonján. Egy launchernek, ami az EGÉSZ telefont akarja adni, ez hiány.
 *
 * MIÉRT EGY LEJÁTSZÓ MINDKETTŐRE: a vak felhasználó számára a videó és a
 * hangfájl között alig van különbség — mindkettőt HALLGATJA. Két külön
 * lejátszó két külön kezelést jelentene, feleslegesen.
 *
 * ── MOZDULATOK ─────────────────────────────────────────────────────────────
 *   jobbra  — szünet és folytatás
 *   balra   — kilépés
 *   fel/le  — 10 másodperc vissza / előre
 *
 * ── AMIT MINDEN RENDES LEJÁTSZÓTÓL ELVÁRUNK, ÉS ITT IS MEGVAN ──────────────
 *   - fülhallgató kihúzásakor MEGÁLL (nem kezd üvölteni a hangszóróból)
 *   - hívás alatt elhallgat, utána magától folytatja
 *   - ha más alkalmazás megszólal, elhallgat, majd visszatér
 *   - a fülhallgató és az autórádió gombjai működnek (média-munkamenet)
 *   - a képernyő nem alszik el videó közben
 *
 * Ezek nem újdonságok a programban: a zenelejátszó, a rádió, a podcast és a
 * YouTube ugyanezeket használja. Egy helyen vannak megírva, és itt is
 * ugyanazokat hívjuk — így nem tud szétcsúszni a viselkedésük.
 */
class LocalMediaPlayerActivity : Activity() {

    companion object {
        const val EXTRA_PATH = "media_path"
        const val EXTRA_TITLE = "media_title"

        /** Videó-e a kiterjesztés alapján. Ettől függ, kell-e képfelület. */
        fun isVideo(file: File): Boolean {
            val ext = file.extension.lowercase()
            return ext in setOf(
                "mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v", "flv", "wmv", "mpg", "mpeg"
            )
        }
    }

    private lateinit var tts: TtsManager
    private lateinit var gestureListener: SwipeGestureListener
    private lateinit var title: TextView
    private lateinit var status: TextView
    private var surface: SurfaceView? = null

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private var prepared = false
    private var paused = false
    private var mediaFile: File? = null

    private var mediaSession: android.support.v4.media.session.MediaSessionCompat? = null

    /** Fülhallgató kihúzása: azonnal szünet. */
    private val headphoneGuard by lazy {
        HeadphoneUnplugGuard(this) {
            handler.post { if (prepared && !paused) togglePause(spoken = "Fülhallgató kihúzva. Szünet.") }
        }
    }

    /** Ha más alkalmazás megszólal, mi elhallgatunk — nem beszélünk egymásra. */
    private val focusGuard by lazy {
        AudioFocusGuard(
            context = this,
            onPause = { handler.post { if (prepared && !paused) togglePause(silent = true) } },
            onResume = { handler.post { if (prepared && paused) togglePause(silent = true) } }
        )
    }

    /** Hívás alatt elhallgat, utána magától folytatja. */
    private val callGuard by lazy {
        CallPauseGuard(
            context = this,
            isPlaying = { prepared && !paused },
            onPause = { handler.post { if (prepared && !paused) togglePause(silent = true) } },
            onResume = { handler.post { if (prepared && paused) togglePause(silent = true) } }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent?.getStringExtra(EXTRA_PATH)
        val file = path?.let { File(it) }
        mediaFile = file

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(24, 24, 24, 24)
        }
        title = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            text = intent?.getStringExtra(EXTRA_TITLE) ?: file?.name ?: "Lejátszás"
        }
        status = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        root.addView(title)

        // A KÉPFELÜLET csak videónál kell. Hangfájlnál nemcsak fölösleges,
        // hanem zavaró is: fekete téglalap, ami elveszi a helyet a szövegtől.
        if (file != null && isVideo(file)) {
            val holder = FrameLayout(this)
            val sv = SurfaceView(this)
            surface = sv
            holder.addView(
                sv,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            root.addView(
                holder,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
                )
            )
        }
        root.addView(status)
        setContentView(root)

        tts = TtsManager(this)
        gestureListener = SwipeGestureListener(
            context = this,
            onSwipeUp = { seek(-10_000) },
            onSwipeDown = { seek(+10_000) },
            onSwipeRight = { togglePause() },
            onSwipeLeft = { stopAndFinish() }
        )

        if (file == null || !file.exists()) {
            tts.speak("Ez a fájl nem érhető el.")
            handler.postDelayed({ finish() }, 2500L)
            return
        }

        setupMediaSession()
        headphoneGuard.register()
        callGuard.register()
        focusGuard.request()

        start(file)
    }

    private fun start(file: File) {
        try {
            val mp = MediaPlayer()
            player = mp
            mp.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(
                        if (isVideo(file)) android.media.AudioAttributes.CONTENT_TYPE_MOVIE
                        else android.media.AudioAttributes.CONTENT_TYPE_MUSIC
                    )
                    .build()
            )
            mp.setDataSource(this, Uri.fromFile(file))

            val sv = surface
            if (sv != null) {
                // A KÉPFELÜLET nem azonnal áll készen. Ha előbb indítanánk a
                // videót, kép nélkül szólna — ezért megvárjuk.
                sv.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(h: SurfaceHolder) {
                        try {
                            mp.setDisplay(h)
                        } catch (_: Exception) {
                        }
                    }

                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {}
                    override fun surfaceDestroyed(h: SurfaceHolder) {}
                })
                // Videónál a képernyő ne aludjon el — nem a hang miatt, hanem
                // mert egy látó ismerős is nézheti velünk.
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            mp.setOnPreparedListener {
                prepared = true
                paused = false
                it.start()
                updateSessionState()
                tts.speak(
                    "${title.text}. ${lengthText()}. " +
                        "Jobbra szünet, fel-le tíz másodperc, balra kilépés."
                )
                tick()
            }
            mp.setOnCompletionListener {
                tts.speak("Vége.")
                handler.postDelayed({ stopAndFinish(silent = true) }, 1800L)
            }
            mp.setOnErrorListener { _, _, _ ->
                tts.speak("Ezt a fájlt nem tudom lejátszani.")
                handler.postDelayed({ stopAndFinish(silent = true) }, 2500L)
                true
            }
            tts.speak("Betöltés.")
            mp.prepareAsync()
        } catch (e: Exception) {
            tts.speak("Ezt a fájlt nem tudom lejátszani.")
            handler.postDelayed({ finish() }, 2500L)
        }
    }

    // ── Vezérlés ───────────────────────────────────────────────────────────

    private fun togglePause(silent: Boolean = false, spoken: String? = null) {
        val mp = player ?: return
        if (!prepared) return
        try {
            if (mp.isPlaying) {
                mp.pause()
                paused = true
                if (!silent) tts.speak(spoken ?: "Szünet. ${positionText()}")
            } else {
                mp.start()
                paused = false
                if (!silent) tts.speak(spoken ?: "Folytatás.")
                tick()
            }
            updateSessionState()
        } catch (_: Exception) {
        }
    }

    /**
     * TEKERÉS. A hangos visszajelzés nem díszítés: vakon a tekerés csak akkor
     * használható, ha HALLOD, hova kerültél.
     */
    private fun seek(deltaMs: Int) {
        val mp = player ?: return
        if (!prepared) return
        try {
            val target = (mp.currentPosition + deltaMs).coerceIn(0, mp.duration)
            mp.seekTo(target)
            tts.speak(positionText())
        } catch (_: Exception) {
        }
    }

    private fun positionText(): String {
        val mp = player ?: return ""
        return try {
            "${timeText(mp.currentPosition)} / ${timeText(mp.duration)}"
        } catch (_: Exception) {
            ""
        }
    }

    private fun lengthText(): String {
        val mp = player ?: return ""
        return try {
            "Hossza ${timeText(mp.duration)}"
        } catch (_: Exception) {
            ""
        }
    }

    /** Perc és másodperc, kimondható alakban. */
    private fun timeText(ms: Int): String {
        val total = ms / 1000
        val min = total / 60
        val sec = total % 60
        return if (min > 0) "$min perc $sec másodperc" else "$sec másodperc"
    }

    /** A képernyőn látszó állapot frissítése, másodpercenként. */
    private fun tick() {
        handler.removeCallbacksAndMessages("tick")
        val mp = player ?: return
        try {
            status.text = if (paused) "Szünet — ${positionText()}" else positionText()
        } catch (_: Exception) {
        }
        if (!paused && mp.isPlaying) {
            handler.postDelayed({ tick() }, 1000L)
        }
    }

    // ── Média-munkamenet: fülhallgató- és autórádió-gombok ─────────────────

    private fun setupMediaSession() {
        try {
            val session = android.support.v4.media.session.MediaSessionCompat(this, "SuperDLMedia")
            session.setCallback(object :
                android.support.v4.media.session.MediaSessionCompat.Callback() {
                override fun onPlay() {
                    handler.post { if (paused) togglePause() }
                }

                override fun onPause() {
                    handler.post { if (!paused) togglePause() }
                }

                override fun onStop() {
                    handler.post { stopAndFinish() }
                }

                override fun onSeekTo(pos: Long) {
                    handler.post {
                        try {
                            player?.seekTo(pos.toInt())
                        } catch (_: Exception) {
                        }
                    }
                }
            })
            session.isActive = true
            mediaSession = session
        } catch (_: Exception) {
        }
    }

    private fun updateSessionState() {
        val session = mediaSession ?: return
        try {
            val state = if (paused) {
                android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED
            } else {
                android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
            }
            val pos = try {
                player?.currentPosition?.toLong() ?: 0L
            } catch (_: Exception) {
                0L
            }
            session.setPlaybackState(
                android.support.v4.media.session.PlaybackStateCompat.Builder()
                    .setActions(
                        android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE or
                            android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY or
                            android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE or
                            android.support.v4.media.session.PlaybackStateCompat.ACTION_STOP or
                            android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO
                    )
                    .setState(state, pos, 1f)
                    .build()
            )
        } catch (_: Exception) {
        }
    }

    private fun stopAndFinish(silent: Boolean = false) {
        if (!silent) tts.speak("Lejátszás leállítva.")
        finish()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean =
        gestureListener.detector.onTouchEvent(event) || super.onTouchEvent(event)

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        headphoneGuard.unregister()
        callGuard.unregister()
        focusGuard.release()
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        try {
            tts.shutdown()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
}
