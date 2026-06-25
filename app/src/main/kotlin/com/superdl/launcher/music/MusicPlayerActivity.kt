package com.superdl.launcher.music

import android.media.MediaPlayer
import android.net.Uri
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

class MusicPlayerActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvPosition: TextView
    private lateinit var tts: TtsManager
    private lateinit var sounds: SoundFeedback
    private lateinit var gestureListener: SwipeGestureListener

    private var mediaPlayer: MediaPlayer? = null
    private var paused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_player)
        applyImmersive()

        tvTitle = findViewById(R.id.tvPlayerTitle)
        tvStatus = findViewById(R.id.tvPlayerStatus)
        tvPosition = findViewById(R.id.tvPlayerPosition)
        findViewById<TextView>(R.id.tvPlayerHint).text = getString(R.string.music_player_hint)

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val artist = intent.getStringExtra(EXTRA_ARTIST).orEmpty()
        val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)

        tvTitle.text = title.ifBlank { "Zene" }
        tvPosition.text = if (artist.isNotBlank()) artist else "Zenelejátszás"
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
                val spoken = if (artist.isNotBlank()) "$artist. $title" else title
                tts.speak(spoken)
            }
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = stopAndFinish("Lejátszás leállítva.")
        })

        if (uri == null) {
            tts.speakThen("A zene nem indítható.") { finish() }
            return
        }

        startPlayback(uri, title, artist)
    }

    private fun startPlayback(uri: Uri, title: String, artist: String) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@MusicPlayerActivity, uri)
                setOnPreparedListener {
                    tvStatus.text = getString(R.string.player_playing)
                    start()
                    paused = false
                    val spoken = buildString {
                        if (artist.isNotBlank()) append("$artist. ")
                        append(title)
                    }
                    tts.speak("Lejátszás: $spoken")
                }
                setOnCompletionListener { stopAndFinish("A zene véget ért.") }
                setOnErrorListener { _, _, _ ->
                    tts.speakThen("Lejátszás hiba.") { finish() }
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            tts.speakThen("A zene nem indítható.") { finish() }
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
        const val EXTRA_URI = "music_uri"
        const val EXTRA_TITLE = "music_title"
        const val EXTRA_ARTIST = "music_artist"
    }
}