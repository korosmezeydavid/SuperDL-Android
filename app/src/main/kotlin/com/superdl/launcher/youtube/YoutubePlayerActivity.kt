package com.superdl.launcher.youtube

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.superdl.launcher.R
import com.superdl.launcher.feedback.SoundFeedback
import com.superdl.launcher.feedback.SoundType
import com.superdl.launcher.gestures.SwipeGestureListener
import com.superdl.launcher.tts.TtsManager

/**
 * YOUTUBE LEJÁTSZÓ.
 *
 * KÉT ÚTON próbálkozik, ebben a sorrendben:
 *  1. BEÁGYAZOTT LEJÁTSZÓ — a YouTube hivatalos felülete. Ez a megbízhatóbb,
 *     mert nem érinti a YouTube robotvédelme.
 *  2. KÖZVETLEN LEJÁTSZÁS — ha a beágyazott nem indul (a feltöltő letiltotta),
 *     a hangot közvetlenül játsszuk le, a SuperDL-en belül maradva.
 *
 * A "153-as / 152-es hiba" javítása: a YouTube 2025 vége óta szigorúan
 * ellenőrzi, HONNAN érkezik a beágyazás. A beépített böngészőnézet alapból
 * NEM küld hivatkozó adatot, ezért a lejátszó elutasította a videót. A
 * megoldás: saját oldalba ágyazzuk, valódi hivatkozóval.
 */
class YoutubePlayerActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvPosition: TextView
    private lateinit var webView: WebView
    private lateinit var tts: TtsManager
    private lateinit var sounds: SoundFeedback
    private lateinit var gestureListener: SwipeGestureListener

    private lateinit var video: YoutubeVideo
    private var paused = false
    private var playbackStarted = false
    private var positionSec = 0
    private var durationSec = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private var loadTimeout: Runnable? = null

    /** Megpróbáltuk-e már a tartalék (közvetlen) lejátszást. */
    private var fallbackTried = false

    /** A tartalék lejátszó, ha a beágyazott nem indult. */
    private var directPlayer: android.media.MediaPlayer? = null

    /** A pozíció rendszeres mentése, hogy folytatni lehessen. */
    private var positionSaver: Runnable? = null

    /** Média-munkamenet a fülhallgató és az autórádió gombjaihoz. */
    private var mediaSession: android.support.v4.media.session.MediaSessionCompat? = null

    /** A jobbra söprés ideje — a kétszeri söprés ismeri fel a kedvencet. */
    private var lastRightSwipeAt = 0L

    /** Ha más alkalmazás megszólal, a videó elhallgat — nem beszélnek egymásra. */
    private val focusGuard by lazy {
        com.superdl.launcher.media.AudioFocusGuard(
            context = this,
            onPause = { mainHandler.post { if (playbackStarted && !paused) togglePause() } },
            onResume = { mainHandler.post { if (paused) togglePause() } }
        )
    }

    /** Fülhallgató kihúzásakor a videó elhallgat, nem üvölt a hangszóróból. */
    private val headphoneGuard by lazy {
        com.superdl.launcher.media.HeadphoneUnplugGuard(this) {
            mainHandler.post { if (playbackStarted && !paused) togglePause() }
        }
    }

    /** Hívás alatt a videó elhallgat, utána magától folytatja. */
    private val callGuard by lazy {
        com.superdl.launcher.call.CallPauseGuard(
            context = this,
            isPlaying = { playbackStarted && !paused },
            onPause = { if (!paused) togglePause() },
            onResume = { if (paused) togglePause() }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_player)
        applyImmersive()

        tvTitle = findViewById(R.id.tvPlayerTitle)
        tvStatus = findViewById(R.id.tvPlayerStatus)
        tvPosition = findViewById(R.id.tvPlayerPosition)
        webView = findViewById(R.id.webPlayerView)
        findViewById<TextView>(R.id.tvPlayerHint).text = getString(R.string.youtube_player_hint)

        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val channel = intent.getStringExtra(EXTRA_CHANNEL).orEmpty()
        durationSec = intent.getIntExtra(EXTRA_DURATION, 0)
        video = YoutubeVideo(videoId, title, channel, durationSec)

        tvTitle.text = title.ifBlank { "YouTube" }
        tvPosition.text = if (channel.isNotBlank()) channel else "YouTube lejátszás"
        tvStatus.text = getString(R.string.player_loading)

        tts = TtsManager(this)
        sounds = SoundFeedback(this)
        gestureListener = SwipeGestureListener(
            context = this,
            onSwipeUp = { sounds.play(SoundType.SWIPE_UP); togglePause() },
            onSwipeDown = { sounds.play(SoundType.SWIPE_DOWN); announcePosition() },
            onSwipeLeft = { sounds.play(SoundType.SWIPE_LEFT); stopAndFinish("Lejátszás leállítva.") },
            onSwipeRight = { sounds.play(SoundType.SWIPE_RIGHT); onRightSwipe() }
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = stopAndFinish("Lejátszás leállítva.")
        })

        setupMediaSession()
        callGuard.register()
        headphoneGuard.register()
        focusGuard.request()

        if (videoId.isBlank()) {
            tts.speakThen("A videó nem indítható.") { finish() }
            return
        }

        // TAKARÉKOS MÓD: nem itt játsszuk le, hanem átadjuk a háttér-
        // szolgáltatásnak, és bezárjuk ezt az ablakot.
        //
        // MIÉRT ÍGY: ez az ablak a képernyőhöz kötődik — ha lezárod a
        // telefont, a beágyazott lejátszó képfelülete megszűnik, és a hang is
        // vele megy. A szolgáltatás viszont nem függ a képernyőtől: ott a
        // lejátszás akkor is megy, ha a telefon a zsebedben van.
        //
        // A KIVÉTEL: ha épp a takarékos mód küldött ide vissza, mert nem
        // talált hangot, akkor NEM adjuk vissza neki — abból végtelen kör
        // lenne, és a felhasználó két mondat között ingázna örökké.
        if (YoutubeSaverPrefs.isEnabled(this) &&
            !intent.getBooleanExtra(EXTRA_SKIP_SAVER, false)
        ) {
            YoutubeAudioService.start(this, videoId, title.ifBlank { "YouTube" })
            finish()
            return
        }

        setupPlayer(videoId)
        tts.speak("Videó betöltése. Várj egy pillanatot.")

        // Ha 15 másodperc alatt nem indul el, jelezzük — ne tűnjön fagyásnak.
        val timeout = Runnable {
            if (!playbackStarted) handlePlayerError(-2)
        }
        loadTimeout = timeout
        mainHandler.postDelayed(timeout, 15_000L)
    }

    // ══════════════════════════════════════════════════════════════════════
    //  BEÁGYAZOTT LEJÁTSZÓ
    // ══════════════════════════════════════════════════════════════════════

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupPlayer(videoId: String) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
        }
        webView.addJavascriptInterface(JsBridge(), "AndroidBridge")
        webView.visibility = android.view.View.VISIBLE
        // A lejátszó nézete NE fogadjon érintést: a vezérlés kizárólag a
        // gesztusokon keresztül történik. Enélkül a lejátszó ELNYELTE az
        // érintéseket, és a képernyő "beragadt" — se fel, se le nem reagált.
        webView.isFocusable = false
        webView.isFocusableInTouchMode = false
        webView.isClickable = false
        webView.isLongClickable = false

        // A hivatkozó domain a SAJÁT oldalunk.
        // Ha azt állítanánk, hogy magáról a YouTube-ról jön a beágyazás, a
        // lejátszó gyanúsnak találja és elutasítja (152/153-as hiba).
        webView.loadDataWithBaseURL(
            PLAYER_ORIGIN,
            buildPlayerHtml(videoId),
            "text/html",
            "utf-8",
            null
        )
    }

    private fun buildPlayerHtml(videoId: String): String = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <!-- EZ A KULCS a 152/153-as hiba ellen: a YouTube szigorúan
               ellenőrzi, honnan érkezik a beágyazás. A beépített
               böngészőnézet alapból NEM küld hivatkozó adatot. -->
          <meta name="referrer" content="strict-origin-when-cross-origin">
          <style>
            html,body { margin:0; padding:0; background:#000; overflow:hidden; }
            iframe { width:100%; height:100%; border:0; }
          </style>
        </head>
        <body>
          <iframe id="player"
                  src="https://www.youtube-nocookie.com/embed/$videoId?enablejsapi=1&autoplay=1&playsinline=1&rel=0&fs=0&controls=0&modestbranding=1&origin=$PLAYER_ORIGIN"
                  referrerpolicy="strict-origin-when-cross-origin"
                  allow="autoplay; encrypted-media"
                  allowfullscreen></iframe>
          <script src="https://www.youtube.com/iframe_api"></script>
          <script>
            var player;
            function onYouTubeIframeAPIReady() {
              player = new YT.Player('player', {
                events: {
                  onReady: function(e) { e.target.playVideo(); },
                  onStateChange: function(e) {
                    if (e.data === YT.PlayerState.PLAYING) {
                      AndroidBridge.onPlaying(Math.floor(player.getDuration()));
                    } else if (e.data === YT.PlayerState.ENDED) {
                      AndroidBridge.onEnded();
                    }
                  },
                  onError: function(e) { AndroidBridge.onPlayerError(e.data); }
                }
              });
            }
            setTimeout(function() {
              if (!player || typeof player.getPlayerState !== 'function') {
                AndroidBridge.onPlayerError(-1);
              }
            }, 8000);
          </script>
        </body>
        </html>
    """.trimIndent()

    /** A beágyazott lejátszó állapotát kapjuk vissza. */
    private inner class JsBridge {
        @JavascriptInterface
        fun onPlaying(duration: Int) {
            mainHandler.post {
                if (duration > 0) durationSec = duration
                if (!playbackStarted) {
                    playbackStarted = true
                    loadTimeout?.let { mainHandler.removeCallbacks(it) }
                    tvStatus.text = getString(R.string.player_playing)
                    tts.speak("Lejátszás: ${video.title}")
                    applySavedPosition()
                    startPositionSaving()
                    YoutubeLibraryStore.saveLastVideo(this@YoutubePlayerActivity, video)
                }
                paused = false
            }
        }

        @JavascriptInterface
        fun onEnded() {
            mainHandler.post { stopAndFinish("A videó véget ért.") }
        }

        @JavascriptInterface
        fun onPlayerError(code: Int) {
            mainHandler.post { handlePlayerError(code) }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TARTALÉK: KÖZVETLEN LEJÁTSZÁS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * A LEJÁTSZÓ HIBÁJA — és a tartalék megoldás.
     *
     * Ha a beágyazott lejátszó nem indul (a feltöltő letiltotta a beágyazást,
     * vagy más korlátozás van), NEM hagyjuk a felhasználót hibaüzenet előtt:
     * megpróbáljuk KÖZVETLENÜL lejátszani a videó hangját, a SuperDL-en belül.
     * Így a kezdő felhasználónak sem kell külső alkalmazásba lépnie.
     */
    private fun handlePlayerError(code: Int) {
        android.util.Log.w("SDL_YOUTUBE", "lejatszo hiba: $code")
        loadTimeout?.let { mainHandler.removeCallbacks(it) }
        if (fallbackTried) {
            tvStatus.text = getString(R.string.player_error)
            tts.speak(
                "Ezt a videót a YouTube nem adja ki. Vagy a feltöltő korlátozta, " +
                    "vagy nem érhető el ebben az országban. Balra söprés a kilépéshez."
            )
            return
        }
        fallbackTried = true
        tvStatus.text = getString(R.string.player_loading)
        tts.speak("A beágyazott lejátszó nem indult. Megpróbálom közvetlenül lejátszani.")
        webView.loadUrl("about:blank")
        webView.visibility = android.view.View.GONE

        Thread {
            val urls = try {
                YoutubeStreamResolver.resolveInAppStreamUrls(video.videoId)
            } catch (e: Exception) {
                android.util.Log.w("SDL_YOUTUBE", "tartalek feloldas hiba: ${e.message}")
                emptyList()
            }
            android.util.Log.i("SDL_YOUTUBE", "TARTALEK: ${urls.size} cim erkezett")
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                if (urls.isEmpty()) {
                    tvStatus.text = getString(R.string.player_error)
                    tts.speak(
                        "Ezt a videót a YouTube nem adja ki. Vagy a feltöltő korlátozta, " +
                            "vagy nem érhető el ebben az országban. Próbálj másik videót. " +
                            "Balra söprés a kilépéshez."
                    )
                    return@post
                }
                startDirectPlayback(urls)
            }
        }.start()
    }

    /** Közvetlen lejátszás a feloldott címről, a SuperDL-en belül. */
    private fun startDirectPlayback(urls: List<String>) {
        var index = 0
        fun tryNext() {
            if (index >= urls.size) {
                tvStatus.text = getString(R.string.player_error)
                tts.speak("A közvetlen lejátszás sem sikerült. Balra söprés a kilépéshez.")
                return
            }
            val url = urls[index++]
            try {
                directPlayer?.release()
                directPlayer = android.media.MediaPlayer().apply {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build()
                    )
                    setDataSource(url)
                    setOnPreparedListener { mp ->
                        playbackStarted = true
                        durationSec = mp.duration / 1000
                        tvStatus.text = getString(R.string.player_playing)
                        tts.speak("Lejátszás: ${video.title}")
                        // FOLYTATÁS: ha van mentett pozíció, ODA ugrunk,
                        // MIELŐTT elindítanánk — így nem az elejéről szól.
                        val saved = YoutubeLibraryStore.getPosition(
                            this@YoutubePlayerActivity, video.videoId
                        )
                        if (saved > 0 && saved * 1000 < mp.duration) {
                            try {
                                mp.seekTo(saved * 1000)
                                positionSec = saved
                                tts.speakAdd(
                                    "Folytatás innen: ${saved / 60} perc ${saved % 60} másodperc."
                                )
                            } catch (_: Exception) {
                            }
                        }
                        mp.start()
                        paused = false
                        startPositionSaving()
                        YoutubeLibraryStore.saveLastVideo(this@YoutubePlayerActivity, video)
                    }
                    setOnCompletionListener { stopAndFinish("A videó véget ért.") }
                    setOnErrorListener { _, _, _ ->
                        mainHandler.post { tryNext() }
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                android.util.Log.w("SDL_YOUTUBE", "kozvetlen lejatszas hiba: ${e.message}")
                tryNext()
            }
        }
        tryNext()
    }

    // ══════════════════════════════════════════════════════════════════════
    //  POZÍCIÓ-MENTÉS ÉS FOLYTATÁS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * A LEJÁTSZÁS FOLYTATÁSA onnan, ahol abbahagytad.
     * Hosszú videónál — előadás, mese, hangoskönyv — ez a legfontosabb kényelem.
     */
    private fun applySavedPosition() {
        val saved = YoutubeLibraryStore.getPosition(this, video.videoId)
        if (saved <= 0) return
        tts.speakAdd("Folytatás innen: ${saved / 60} perc ${saved % 60} másodperc.")
        positionSec = saved
        // A beágyazott lejátszónál az ugrás CSAK AKKOR fog, ha a lejátszó már
        // tényleg elindult — ezért késleltetve, és ELLENŐRIZZÜK is.
        mainHandler.postDelayed({
            webView.evaluateJavascript("player && player.seekTo($saved, true);", null)
        }, 700L)
        mainHandler.postDelayed({
            webView.evaluateJavascript(
                "player && player.getCurrentTime ? Math.floor(player.getCurrentTime()) : -1"
            ) { value ->
                val pos = value?.trim('"')?.toIntOrNull() ?: -1
                if (pos in 0 until (saved - 5)) {
                    webView.evaluateJavascript("player && player.seekTo($saved, true);", null)
                }
            }
        }, 2000L)
    }

    /** A pozíció mentése tíz másodpercenként. */
    private fun startPositionSaving() {
        stopPositionSaving()
        positionSaver = object : Runnable {
            override fun run() {
                if (playbackStarted && !paused) {
                    webView.evaluateJavascript(
                        "player && player.getCurrentTime ? Math.floor(player.getCurrentTime()) : -1"
                    ) { value ->
                        val pos = value?.trim('"')?.toIntOrNull() ?: -1
                        if (pos > 0) {
                            positionSec = pos
                            YoutubeLibraryStore.savePosition(
                                this@YoutubePlayerActivity, video.videoId, pos, durationSec
                            )
                        }
                    }
                    directPlayer?.let { mp ->
                        try {
                            positionSec = mp.currentPosition / 1000
                            YoutubeLibraryStore.savePosition(
                                this@YoutubePlayerActivity, video.videoId, positionSec, durationSec
                            )
                        } catch (_: Exception) {
                        }
                    }
                }
                mainHandler.postDelayed(this, 10_000L)
            }
        }
        mainHandler.postDelayed(positionSaver!!, 10_000L)
    }

    private fun stopPositionSaving() {
        positionSaver?.let { mainHandler.removeCallbacks(it) }
        positionSaver = null
    }

    // ══════════════════════════════════════════════════════════════════════
    //  VEZÉRLÉS
    // ══════════════════════════════════════════════════════════════════════

    private fun togglePause() {
        if (!playbackStarted) {
            tts.speak("A videó még töltődik.")
            return
        }
        paused = !paused
        directPlayer?.let { mp ->
            try {
                if (paused) mp.pause() else mp.start()
            } catch (_: Exception) {
            }
        } ?: run {
            webView.evaluateJavascript(
                if (paused) "player && player.pauseVideo();" else "player && player.playVideo();",
                null
            )
        }
        tvStatus.text = getString(if (paused) R.string.player_paused else R.string.player_playing)
        tts.speak(if (paused) "Szünet." else "Folytatás.")
    }

    /** Hol tartunk a videóban. */
    private fun announcePosition() {
        if (!playbackStarted) {
            tts.speak("A videó még töltődik.")
            return
        }
        val pos = positionSec
        val total = durationSec
        val text = if (total > 0) {
            "${formatClock(pos)} a ${formatClock(total)}-ból."
        } else {
            formatClock(pos)
        }
        tts.speak(if (paused) "Szünetben, $text" else text)
    }

    /**
     * JOBBRA SÖPRÉS: először a videó adatai, MÁSODSZORRA (két másodpercen
     * belül) kedvencnek jelölés. Így egyetlen mozdulattal nem lehet véletlenül
     * kedvencet állítani, de aki akarja, gyorsan eléri.
     */
    private fun onRightSwipe() {
        val now = System.currentTimeMillis()
        if (now - lastRightSwipeAt < 2000L) {
            lastRightSwipeAt = 0L
            val nowFavorite = YoutubeLibraryStore.toggleFavoriteVideo(this, video)
            tts.speak(
                if (nowFavorite) "Kedvencekhez adva: ${video.title}"
                else "Levéve a kedvencekből."
            )
            return
        }
        lastRightSwipeAt = now
        val fav = if (YoutubeLibraryStore.isFavoriteVideo(this, video.videoId)) {
            " Kedvenc. Jobbra még egyszer: levétel."
        } else {
            " Jobbra még egyszer: kedvencekhez adás."
        }
        tts.speak(video.speakFull() + fav)
    }

    private fun formatClock(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return "$m perc $s másodperc"
    }

    /**
     * MÉDIA-MUNKAMENET: a fülhallgató és az autórádió gombjai.
     * Bluetooth fülhallgatón így működik a lejátszás-szünet gomb — nem kell
     * elővenni a telefont ahhoz, hogy megállítsd a videót.
     */
    private fun setupMediaSession() {
        try {
            val session = android.support.v4.media.session.MediaSessionCompat(this, "SuperDL-YT")
            session.setCallback(object :
                android.support.v4.media.session.MediaSessionCompat.Callback() {
                override fun onPlay() {
                    mainHandler.post { if (paused) togglePause() }
                }

                override fun onPause() {
                    mainHandler.post { if (!paused) togglePause() }
                }

                override fun onStop() {
                    mainHandler.post { stopAndFinish("Lejátszás leállítva.") }
                }
            })
            session.isActive = true
            mediaSession = session
        } catch (e: Exception) {
            android.util.Log.w("SDL_YOUTUBE", "media munkamenet hiba: ${e.message}")
        }
    }

    private fun stopAndFinish(message: String) {
        stopPositionSaving()
        try {
            directPlayer?.release()
        } catch (_: Exception) {
        }
        directPlayer = null
        try {
            webView.evaluateJavascript("player && player.stopVideo();", null)
        } catch (_: Exception) {
        }
        tts.speakThen(message) { finish() }
    }

    private fun applyImmersive() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            (android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    /**
     * MINDEN érintés ELŐSZÖR a gesztus-felismerőhöz megy.
     *
     * MIÉRT KELL: az onTouchEvent CSAK AKKOR fut le, ha egyetlen gyerek-elem
     * sem nyelte el az érintést. Amióta a beágyazott lejátszó valóban
     * megjelenik, az ELNYELI az összes érintést — a gesztusok oda sem érnek,
     * és a képernyő "beragad".
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        gestureListener.detector.onTouchEvent(event)
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean =
        gestureListener.detector.onTouchEvent(event) || super.onTouchEvent(event)

    override fun onDestroy() {
        callGuard.unregister()
        headphoneGuard.unregister()
        focusGuard.release()
        loadTimeout?.let { mainHandler.removeCallbacks(it) }
        stopPositionSaving()
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (_: Exception) {
        }
        mediaSession = null
        mainHandler.removeCallbacksAndMessages(null)
        try {
            directPlayer?.release()
        } catch (_: Exception) {
        }
        directPlayer = null
        try {
            webView.stopLoading()
            webView.destroy()
        } catch (_: Exception) {
        }
        tts.shutdown()
        sounds.release()
        super.onDestroy()
    }

    companion object {
        /**
         * A beágyazás hivatkozó domainje. A SuperDL saját oldala — a YouTube
         * ezt szabályos beágyazásnak fogadja el.
         */
        private const val PLAYER_ORIGIN = "https://super-dl.com"

        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_TITLE = "video_title"
        const val EXTRA_CHANNEL = "video_channel"
        const val EXTRA_DURATION = "video_duration"

        /**
         * „Most az egyszer ne add át a takarékos módnak."
         *
         * Csak a `YoutubeAudioService` visszaesése állítja be, amikor ő maga
         * nem talált hangot. A beállítás ettől NEM változik: a következő
         * videónál megint a takarékos mód próbálkozik először.
         */
        const val EXTRA_SKIP_SAVER = "skip_saver"
    }
}
