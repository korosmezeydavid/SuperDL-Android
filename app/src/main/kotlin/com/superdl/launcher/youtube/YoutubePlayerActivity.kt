package com.superdl.launcher.youtube

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.superdl.launcher.R
import com.superdl.launcher.feedback.SoundFeedback
import com.superdl.launcher.feedback.SoundType
import com.superdl.launcher.gestures.SwipeGestureListener
import com.superdl.launcher.tts.TtsManager

class YoutubePlayerActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvPosition: TextView
    private lateinit var webView: WebView
    private lateinit var tts: TtsManager
    private lateinit var sounds: SoundFeedback
    private lateinit var gestureListener: SwipeGestureListener
    private val mainHandler = Handler(Looper.getMainLooper())

    private var mediaPlayer: MediaPlayer? = null
    private var paused = false
    private var usingWebView = false
    private var usingHtml5Video = false
    private lateinit var video: YoutubeVideo
    private var streamCandidates: List<String> = emptyList()
    private var streamAttempt = 0
    private var embedAttempt = 0
    private var playbackStarted = false
    private var prepareTimeoutRunnable: Runnable? = null
    private var streamResolveJob: Job? = null
    private var html5PlaybackRunnable: Runnable? = null

    private val thirdPartyEmbedUrls: List<String>
        get() = buildList {
            add("https://piped.video/embed/${video.videoId}?autoplay=1&controls=1")
            for (host in EMBED_HOSTS) {
                add("https://$host/embed/${video.videoId}?autoplay=1")
            }
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
        video = YoutubeVideo(videoId, title, channel, 0)

        tvTitle.text = title.ifBlank { "YouTube" }
        tvPosition.text = if (channel.isNotBlank()) channel else "YouTube lejátszás"
        tvStatus.text = getString(R.string.player_loading)

        tts = TtsManager(this)
        sounds = SoundFeedback(this)
        gestureListener = SwipeGestureListener(
            context = this,
            onSwipeUp = {
                sounds.play(SoundType.SWIPE_UP)
                togglePause()
            },
            onSwipeDown = {
                sounds.play(SoundType.SWIPE_DOWN)
                tts.speak(if (paused) "Szünetben." else "Lejátszás folyamatban.")
            },
            onSwipeLeft = {
                sounds.play(SoundType.SWIPE_LEFT)
                stopAndFinish("Lejátszás leállítva.")
            },
            onSwipeRight = {
                sounds.play(SoundType.SWIPE_RIGHT)
                tts.speak(video.speakFull())
            }
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = stopAndFinish("Lejátszás leállítva.")
        })

        if (videoId.isBlank()) {
            tts.speakThen("A videó nem indítható.") { finish() }
            return
        }

        setupWebView()
        tts.speak("Videó betöltése. Várj egy pillanatot.")

        streamResolveJob = lifecycleScope.launch {
            val urls = withContext(Dispatchers.IO) {
                YoutubeStreamResolver.resolveInAppStreamUrls(videoId).take(MAX_STREAM_ATTEMPTS)
            }
            if (isFinishing || isDestroyed) return@launch
            streamCandidates = urls
            if (urls.isEmpty()) {
                startThirdPartyEmbed()
            } else {
                tryNextStream()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = DESKTOP_USER_AGENT
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress in 1..99 && !playbackStarted) {
                    tvStatus.text = getString(R.string.player_loading)
                }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean = false
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                handleNavigation(request.url.toString())

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                handleNavigation(url)

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                if (handleNavigation(url)) {
                    view.stopLoading()
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!playbackStarted && !shouldBlockUrl(url)) {
                    markPlaybackStarted("WebView page: $url")
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError
            ) {
                if (request.isForMainFrame) {
                    Log.w(TAG, "WebView error ${error.errorCode} on ${request.url}")
                    if (usingHtml5Video) {
                        startThirdPartyEmbed()
                    } else {
                        tryNextEmbed()
                    }
                }
            }
        }
        webView.visibility = View.GONE
    }

    /** @return true ha blokkolva (ne töltse a WebView). */
    private fun handleNavigation(url: String): Boolean {
        if (shouldBlockUrl(url)) {
            Log.d(TAG, "Blocked navigation: $url")
            return true
        }
        return false
    }

    private fun shouldBlockUrl(url: String): Boolean {
        if (url.isBlank() || url == "about:blank") return false
        val lower = url.lowercase()
        if (lower.startsWith("intent:") ||
            lower.startsWith("vnd.youtube:") ||
            lower.startsWith("android-app:") ||
            lower.startsWith("market:")
        ) {
            return true
        }
        val host = Uri.parse(url).host?.lowercase().orEmpty()
        if (host.isBlank()) return true
        if (host == "youtube.com" ||
            host == "www.youtube.com" ||
            host == "m.youtube.com" ||
            host.endsWith(".youtube.com") ||
            host.endsWith(".youtube-nocookie.com")
        ) {
            return true
        }
        return !isAllowedPlaybackHost(host)
    }

    private fun isAllowedPlaybackHost(host: String): Boolean {
        return host.contains("piped.") ||
            host.contains("invidious") ||
            host.contains("yewtu.be") ||
            host.contains("googlevideo.com") ||
            host.contains("ytimg.com") ||
            host.contains("ggpht.com") ||
            host.endsWith(".si") ||
            host.contains("protokolla.fi") ||
            host.contains("artemislena.eu")
    }

    private fun tryNextStream() {
        if (playbackStarted || streamAttempt >= streamCandidates.size) {
            if (!playbackStarted) startThirdPartyEmbed()
            return
        }
        val streamUrl = streamCandidates[streamAttempt]
        streamAttempt++
        if (YoutubeStreamResolver.looksLikeVideoUrlPublic(streamUrl)) {
            startHtml5Playback(streamUrl)
        } else {
            startAudioPlayback(streamUrl)
        }
    }

    private fun startHtml5Playback(streamUrl: String) {
        usingWebView = true
        usingHtml5Video = true
        releasePlayer()
        tvTitle.visibility = View.GONE
        webView.visibility = View.VISIBLE
        tvStatus.text = getString(R.string.player_loading)
        val safeUrl = streamUrl.replace("\"", "%22")
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    html, body { margin: 0; padding: 0; background: #000; height: 100%; overflow: hidden; }
                    video { width: 100%; height: 100%; background: #000; }
                </style>
            </head>
            <body>
                <video id="player" controls autoplay playsinline webkit-playsinline src="$safeUrl"></video>
                <script>
                    var v = document.getElementById('player');
                    v.addEventListener('playing', function() {});
                    v.play().catch(function() {});
                </script>
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL("https://piped.video/", html, "text/html", "UTF-8", null)
        cancelHtml5PlaybackTimeout()
        html5PlaybackRunnable = Runnable {
            if (!playbackStarted && !isFinishing && !isDestroyed) {
                markPlaybackStarted("HTML5 video")
            }
        }
        mainHandler.postDelayed(html5PlaybackRunnable!!, 2_500L)
    }

    private fun startAudioPlayback(streamUrl: String) {
        releasePlayer()
        cancelPrepareTimeout()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(streamUrl)
                setOnPreparedListener {
                    cancelPrepareTimeout()
                    if (playbackStarted) {
                        releasePlayer()
                        return@setOnPreparedListener
                    }
                    usingWebView = false
                    usingHtml5Video = false
                    webView.visibility = View.GONE
                    tvTitle.visibility = View.VISIBLE
                    start()
                    markPlaybackStarted("MediaPlayer audio")
                }
                setOnCompletionListener { stopAndFinish("A videó véget ért.") }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error what=$what extra=$extra")
                    cancelPrepareTimeout()
                    tryNextStream()
                    true
                }
                prepareAsync()
            }
            prepareTimeoutRunnable = Runnable {
                Log.w(TAG, "MediaPlayer prepare timeout attempt $streamAttempt")
                releasePlayer()
                tryNextStream()
            }
            mainHandler.postDelayed(prepareTimeoutRunnable!!, PREPARE_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.w(TAG, "MediaPlayer setup failed", e)
            tryNextStream()
        }
    }

    private fun startThirdPartyEmbed() {
        usingWebView = true
        usingHtml5Video = false
        releasePlayer()
        tvTitle.visibility = View.GONE
        webView.visibility = View.VISIBLE
        embedAttempt = 0
        loadCurrentEmbed()
    }

    private fun loadCurrentEmbed() {
        val urls = thirdPartyEmbedUrls
        if (embedAttempt >= urls.size) {
            tvStatus.text = getString(R.string.player_error)
            tts.speak("A videó nem tölthető be. Balra swipe a kilépéshez.")
            return
        }
        val url = urls[embedAttempt]
        Log.d(TAG, "Third-party embed ${embedAttempt + 1}: $url")
        tvStatus.text = getString(R.string.player_loading)
        webView.loadUrl(url)
        embedAttempt++
    }

    private fun tryNextEmbed() {
        if (embedAttempt < thirdPartyEmbedUrls.size) {
            loadCurrentEmbed()
        } else {
            tvStatus.text = getString(R.string.player_error)
            tts.speak("A videó nem tölthető be. Balra swipe a kilépéshez.")
        }
    }

    private fun markPlaybackStarted(source: String) {
        if (playbackStarted) return
        playbackStarted = true
        Log.d(TAG, "Playback started via $source")
        paused = false
        tvStatus.text = getString(R.string.player_playing)
        tts.speak("Lejátszás: ${video.title}")
    }

    private fun cancelPrepareTimeout() {
        prepareTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        prepareTimeoutRunnable = null
    }

    private fun cancelHtml5PlaybackTimeout() {
        html5PlaybackRunnable?.let { mainHandler.removeCallbacks(it) }
        html5PlaybackRunnable = null
    }

    private fun togglePause() {
        if (usingWebView) {
            val script = if (paused) {
                "try{var v=document.querySelector('video');if(v)v.play();}catch(e){}"
            } else {
                "try{var v=document.querySelector('video');if(v)v.pause();}catch(e){}"
            }
            webView.evaluateJavascript(script, null)
            paused = !paused
            tvStatus.text = if (paused) getString(R.string.player_paused) else getString(R.string.player_playing)
            tts.speak(if (paused) "Szünet." else "Folytatás.")
            return
        }
        val player = mediaPlayer ?: return
        if (!player.isPlaying && !paused) return
        if (paused) {
            player.start()
            paused = false
            tvStatus.text = getString(R.string.player_playing)
            tts.speak("Folytatás.")
        } else {
            player.pause()
            paused = true
            tvStatus.text = getString(R.string.player_paused)
            tts.speak("Szünet.")
        }
    }

    private fun stopAndFinish(message: String) {
        cancelPrepareTimeout()
        releasePlayer()
        if (usingWebView) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.visibility = View.GONE
        }
        tts.speakThen(message) { finish() }
    }

    private fun releasePlayer() {
        cancelPrepareTimeout()
        mediaPlayer?.runCatching {
            stop()
            release()
        }
        mediaPlayer = null
    }

    private fun applyImmersive() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean =
        gestureListener.detector.onTouchEvent(event) || super.onTouchEvent(event)

    override fun onDestroy() {
        streamResolveJob?.cancel()
        streamResolveJob = null
        cancelPrepareTimeout()
        cancelHtml5PlaybackTimeout()
        mainHandler.removeCallbacksAndMessages(null)
        releasePlayer()
        webView.stopLoading()
        webView.destroy()
        tts.shutdown()
        sounds.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "YoutubePlayer"
        private const val PREPARE_TIMEOUT_MS = 8_000L
        private const val MAX_STREAM_ATTEMPTS = 5
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val EMBED_HOSTS = listOf(
            "invidious.nerdvpn.de",
            "invidious.privacyredirect.com",
            "invidious.f5.si",
            "inv.nadeko.net"
        )

        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_TITLE = "video_title"
        const val EXTRA_CHANNEL = "video_channel"
    }
}