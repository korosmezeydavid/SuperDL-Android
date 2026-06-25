package com.superdl.launcher.youtube

import android.media.MediaPlayer
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.superdl.launcher.R
import com.superdl.launcher.feedback.SoundFeedback
import com.superdl.launcher.feedback.SoundType
import com.superdl.launcher.gestures.SwipeGestureListener
import com.superdl.launcher.tts.TtsManager

class YoutubePlayerActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvPosition: TextView
    private lateinit var tts: TtsManager
    private lateinit var sounds: SoundFeedback
    private lateinit var gestureListener: SwipeGestureListener

    private var mediaPlayer: MediaPlayer? = null
    private var paused = false
    private lateinit var video: YoutubeVideo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_player)
        applyImmersive()

        tvTitle = findViewById(R.id.tvPlayerTitle)
        tvStatus = findViewById(R.id.tvPlayerStatus)
        tvPosition = findViewById(R.id.tvPlayerPosition)
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

        tts.speak("Videó betöltése. Várj egy pillanatot.")
        Thread {
            val streamUrl = YoutubeStreamResolver.resolveAudioStreamUrl(videoId)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (streamUrl.isNullOrBlank()) {
                    tts.speakThen("A videó stream nem elérhető.") { finish() }
                } else {
                    startPlayback(streamUrl)
                }
            }
        }.start()
    }

    private fun startPlayback(streamUrl: String) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(streamUrl)
                setOnPreparedListener {
                    tvStatus.text = getString(R.string.player_playing)
                    start()
                    paused = false
                    tts.speak("Lejátszás: ${video.title}")
                }
                setOnCompletionListener { stopAndFinish("A videó véget ért.") }
                setOnErrorListener { _, _, _ ->
                    tts.speakThen("Lejátszás hiba.") { finish() }
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            tts.speakThen("A videó nem indítható.") { finish() }
        }
    }

    private fun togglePause() {
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
        releasePlayer()
        tts.speakThen(message) { finish() }
    }

    private fun releasePlayer() {
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
        releasePlayer()
        tts.shutdown()
        sounds.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_TITLE = "video_title"
        const val EXTRA_CHANNEL = "video_channel"
    }
}