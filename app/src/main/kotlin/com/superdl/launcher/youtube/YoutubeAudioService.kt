package com.superdl.launcher.youtube

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.superdl.launcher.call.CallPauseGuard
import com.superdl.launcher.media.AudioFocusGuard
import com.superdl.launcher.media.HeadphoneUnplugGuard
import com.superdl.launcher.tts.TtsManager

/**
 * TAKARÉKOS MÓD — csak a hang, és a képernyő lezárása után is szól.
 *
 * KÉT KÉRÉS, EGY MEGOLDÁS. A tesztelésen két dolog jött elő:
 *   1. „ha lezárod a képernyőt, folytassa tovább a lejátszást"
 *   2. „internet takarékos mód, amikor csak a hangot játssza le"
 * Ez ugyanaz a gépezet. A videó azért áll meg a képernyő lezárásakor, mert a
 * beágyazott böngészőnézet képfelülete megszűnik — ezen nincs mit kapcsolgatni.
 * Ha viszont eleve CSAK A HANGOT játsszuk, akkor nincs képfelület, amit el
 * lehetne veszíteni: a lejátszás egy szolgáltatásban fut, és a képernyő
 * állapota nem érdekli.
 *
 * AMIT NYERÜNK VELE:
 *   - a hangfolyam TÖREDÉK annyi adat, mint a videó (ez a takarékos mód)
 *   - kevesebb akku, mert nincs videó-dekódolás
 *   - zsebben, lezárt képernyővel is szól
 *   - értesítés és zárképernyő-gombok, mint egy zenelejátszónál
 *
 * ── AMI ITT IS ÉRVÉNYES ────────────────────────────────────────────────────
 * Fülhallgató kihúzása: szünet. Hívás: elhallgat, utána folytatja. Ha más
 * alkalmazás megszólal: félreáll. Ugyanazokat az őröket használjuk, mint a
 * zenelejátszó és a rádió — egy helyen megírva, mindenhol ugyanúgy.
 */
class YoutubeAudioService : Service() {

    companion object {
        private const val TAG = "SDL_YT_AUDIO"
        private const val CHANNEL_ID = "superdl_youtube_audio"
        private const val NOTIF_ID = 4711

        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_TITLE = "title"

        const val ACTION_START = "com.superdl.launcher.YT_AUDIO_START"
        const val ACTION_TOGGLE = "com.superdl.launcher.YT_AUDIO_TOGGLE"
        const val ACTION_STOP = "com.superdl.launcher.YT_AUDIO_STOP"

        /** Fut-e éppen — a menü és a lejátszó ebből tudja, van-e mit leállítani. */
        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var currentTitle: String = ""
            private set

        fun start(context: Context, videoId: String, title: String) {
            val intent = Intent(context, YoutubeAudioService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_VIDEO_ID, videoId)
                putExtra(EXTRA_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            try {
                context.startService(
                    Intent(context, YoutubeAudioService::class.java).apply { action = ACTION_STOP }
                )
            } catch (_: Exception) {
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var tts: TtsManager? = null
    private var mediaSession: android.support.v4.media.session.MediaSessionCompat? = null

    private var title: String = ""
    private var paused = false

    /** A visszaeséshez kell: melyik videót akartuk épp hallani. */
    private var lastVideoId: String = ""

    private val headphoneGuard by lazy {
        HeadphoneUnplugGuard(this) { handler.post { pauseIfPlaying() } }
    }

    private val focusGuard by lazy {
        AudioFocusGuard(
            context = this,
            onPause = { handler.post { pauseIfPlaying() } },
            onResume = { handler.post { resumeIfPaused() } }
        )
    }

    private val callGuard by lazy {
        CallPauseGuard(
            context = this,
            isPlaying = { player?.isPlaying == true },
            onPause = { handler.post { pauseIfPlaying() } },
            onResume = { handler.post { resumeIfPaused() } }
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                if (paused) resumeIfPaused() else pauseIfPlaying()
                return START_STICKY
            }
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
        }

        val videoId = intent?.getStringExtra(EXTRA_VIDEO_ID).orEmpty()
        title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "YouTube" }
        currentTitle = title

        if (videoId.isBlank()) {
            stopEverything()
            return START_NOT_STICKY
        }

        // ELŐTÉRBE AZONNAL. A rendszer néhány másodpercet ad rá; ha csak a
        // hangfolyam feloldása után hívnánk meg, a szolgáltatás elhasalna.
        startForegroundSafely(buildNotification(loading = true))
        isRunning = true

        tts = try {
            TtsManager(this)
        } catch (_: Exception) {
            null
        }
        speak("Takarékos mód. Csak a hang. Betöltés.")

        headphoneGuard.register()
        callGuard.register()
        focusGuard.request()

        lastVideoId = videoId
        YoutubeDiagnostics.begin(videoId)

        // A FELOLDÁS HÁLÓZATOT HASZNÁL, tehát háttérszálon kell.
        Thread {
            val urls = try {
                YoutubeStreamResolver.resolveStreamUrls(videoId)
            } catch (e: Exception) {
                Log.w(TAG, "feloldas hiba: ${e.message}")
                YoutubeDiagnostics.note("feloldás elakadt: ${e.javaClass.simpleName}")
                emptyList()
            }
            // CSAK A HANG. Ez a takarékos mód lényege: a videó-címeket
            // kihagyjuk, és a hangfolyamot vesszük, ami töredék adat.
            val audioOnly = urls.filterNot { YoutubeStreamResolver.looksLikeVideoUrlPublic(it) }
            val candidates = if (audioOnly.isNotEmpty()) audioOnly else urls

            handler.post {
                if (candidates.isEmpty()) {
                    // ELSŐ ZSÁKUTCA: a YouTube egyetlen címet sem adott ki.
                    // Itt a FELOLDÓ szorul javításra — a lejátszóval nincs baj.
                    YoutubeDiagnostics.finish(this, "nem jött hangfolyam (nulla cím)")
                    failOver("A YouTube most nem adott ki hangfolyamot ehhez a videóhoz.")
                } else {
                    YoutubeDiagnostics.note("lejátszás indul, ${candidates.size} címmel")
                    playFirstWorking(candidates, 0)
                }
            }
        }.start()

        return START_STICKY
    }

    /**
     * Sorban végigpróbáljuk a címeket.
     *
     * MIÉRT KELL: a YouTube címei rövid életűek és néha elutasítanak. Egy
     * cím hibája nem jelenti, hogy a videó nem játszható — csak azt, hogy a
     * következőt kell megpróbálni.
     */
    private fun playFirstWorking(urls: List<String>, index: Int) {
        if (index >= urls.size) {
            // MÁSODIK ZSÁKUTCA: volt cím, de a lejátszó mindet elutasította.
            // Itt a KLIENS-ÁLCA szorul javításra (származás-igazoló jelző),
            // nem a feloldó. A két esetet a felhasználó sem, mi sem tudtuk
            // eddig megkülönböztetni, mert majdnem ugyanaz a mondat volt.
            YoutubeDiagnostics.note("mind a ${urls.size} címet elutasította a lejátszó")
            YoutubeDiagnostics.finish(this, "megvolt a hang, de a lejátszás elutasítva")
            failOver("Megvan a hang, de a YouTube elutasította a lejátszást.")
            return
        }
        try {
            player?.release()
            val mp = MediaPlayer()
            player = mp
            mp.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(urls[index])
            mp.setOnPreparedListener {
                paused = false
                it.start()
                setupMediaSession()
                updateNotification()
                // A SIKERT IS FELÍRJUK. Ha legközelebb más panaszkodik, ebből
                // látszik, hogy ezen a telefonon a gépezet EGYSZER már ment —
                // és melyik úton.
                YoutubeDiagnostics.finish(this, "szól (${index + 1}. cím)")
                speak("$title. Szól.")
            }
            mp.setOnCompletionListener {
                speak("Vége.")
                handler.postDelayed({ stopEverything() }, 2000L)
            }
            mp.setOnErrorListener { _, what, extra ->
                // Csendben tovább a következő címre — a felhasználót nem
                // érdekli, hányadik próbálkozásnál járunk. A NYOMBA viszont
                // bekerül: a -1005 (kapcsolat elveszett) és a 403-as burkolt
                // alakja itt válik el egymástól.
                YoutubeDiagnostics.note("${index + 1}. cím hibája: what=$what extra=$extra")
                handler.post { playFirstWorking(urls, index + 1) }
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "lejatszas hiba: ${e.message}")
            handler.post { playFirstWorking(urls, index + 1) }
        }
    }

    /**
     * A ZSÁKUTCA HELYETT KIJÁRAT.
     *
     * EDDIG EZ TÖRTÉNT: elhangzott egy mondat, majd három másodperc múlva a
     * szolgáltatás leállt. A felhasználó ott maradt csendben, minden
     * lehetőség nélkül — pedig a rendes, képes lejátszó sokszor elindul akkor
     * is, amikor a hangfolyam feloldása nem sikerül (a beágyazott lejátszót
     * ugyanis nem érinti a YouTube robotvédelme).
     *
     * Hang kép nélkül jobb, mint semmi; de a néma leállás a legrosszabb
     * kimenet mind közül. Ezért most átadjuk a rendes lejátszónak, és MEG IS
     * MONDJUK, hogy ez történik — enélkül a felhasználó csak annyit érzékel,
     * hogy „valami történt".
     *
     * A takarékos mód beállítása ettől NEM változik. Ez egyetlen videóra szóló
     * kerülőút, nem csendes visszakapcsolás a felhasználó háta mögött.
     */
    private fun failOver(reason: String) {
        val videoId = lastVideoId
        if (videoId.isBlank()) {
            speak("$reason Próbáld meg később.")
            handler.postDelayed({ stopEverything() }, 3000L)
            return
        }
        speak("$reason Megnyitom a rendes lejátszóval, ott a kép is jön.")
        handler.postDelayed({
            try {
                val intent = Intent(this, YoutubePlayerActivity::class.java).apply {
                    putExtra(YoutubePlayerActivity.EXTRA_VIDEO_ID, videoId)
                    putExtra(YoutubePlayerActivity.EXTRA_TITLE, title)
                    putExtra(YoutubePlayerActivity.EXTRA_SKIP_SAVER, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "visszaeses hiba: ${e.message}")
            }
            stopEverything()
        }, 3500L)
    }

    private fun pauseIfPlaying() {
        val mp = player ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
                paused = true
                updateNotification()
                updateSessionState()
            }
        } catch (_: Exception) {
        }
    }

    private fun resumeIfPaused() {
        val mp = player ?: return
        try {
            if (!mp.isPlaying) {
                mp.start()
                paused = false
                updateNotification()
                updateSessionState()
            }
        } catch (_: Exception) {
        }
    }

    private fun stopEverything() {
        isRunning = false
        currentTitle = ""
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        headphoneGuard.unregister()
        callGuard.unregister()
        focusGuard.release()
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (_: Exception) {
        }
        mediaSession = null
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {
        }
        stopSelf()
    }

    private fun speak(text: String) {
        try {
            tts?.speak(text)
        } catch (_: Exception) {
        }
    }

    // ── Értesítés ──────────────────────────────────────────────────────────

    private fun startForegroundSafely(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "eloterbe helyezes hiba: ${e.message}")
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YouTube takarékos lejátszás",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "A háttérben szóló YouTube-hang vezérlése."
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(loading: Boolean = false): Notification {
        ensureChannel()
        val toggleIntent = PendingIntent.getService(
            this, 1,
            Intent(this, YoutubeAudioService::class.java).apply { action = ACTION_TOGGLE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, YoutubeAudioService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = when {
            loading -> "Betöltés…"
            paused -> "Szünet — csak hang"
            else -> "Szól — csak hang"
        }

        val builder = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title.ifBlank { "YouTube" })
            .setContentText(text)
            .setOngoing(!paused)
            .setOnlyAlertOnce(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .addAction(
                if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (paused) "Folytatás" else "Szünet",
                toggleIntent
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Leállítás", stopIntent)

        // MEDIA-STÍLUSÚ ÉRTESÍTÉS SZÁNDÉKOSAN NINCS.
        //
        // Ahhoz külön könyvtár (androidx.media) kellene, és az itt csak
        // KINÉZETET adna: a vezérlés maga a média-munkamenetből jön, ami
        // megvan. Vak felhasználónál az értesítés SZÖVEGE és a két gomb
        // számít, nem az, hogy albumborítós elrendezésben látszik-e.
        // Egy 10 megabájtos könyvtár egy látványért nem éri meg.
        return builder.build()
    }

    private fun updateNotification() {
        try {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            manager.notify(NOTIF_ID, buildNotification())
        } catch (_: Exception) {
        }
    }

    // ── Média-munkamenet: zárképernyő és fülhallgató-gombok ────────────────

    private fun setupMediaSession() {
        if (mediaSession != null) return
        try {
            val session = android.support.v4.media.session.MediaSessionCompat(this, "SuperDL-YT-Audio")
            session.setCallback(object :
                android.support.v4.media.session.MediaSessionCompat.Callback() {
                override fun onPlay() {
                    handler.post { resumeIfPaused() }
                }

                override fun onPause() {
                    handler.post { pauseIfPlaying() }
                }

                override fun onStop() {
                    handler.post { stopEverything() }
                }
            })
            session.setMetadata(
                android.support.v4.media.MediaMetadataCompat.Builder()
                    .putString(
                        android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE,
                        title
                    )
                    .build()
            )
            session.isActive = true
            mediaSession = session
            updateSessionState()
        } catch (e: Exception) {
            Log.w(TAG, "media munkamenet hiba: ${e.message}")
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
                            android.support.v4.media.session.PlaybackStateCompat.ACTION_STOP
                    )
                    .setState(state, pos, 1f)
                    .build()
            )
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
