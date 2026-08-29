package com.superdl.launcher.book

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
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
import java.io.File

/**
 * Vak-barát HANGOSKĂ–NYV-lejátszó (a zenelejátszó mintájára). Egy mappányi
 * hangfájl a sávjaival egy hangoskönyv; a program megjegyzi, hol tartottál
 * (mappánként, ezredmásodpercre), és onnan folytatja. A könyvjelzők a közös
 * tárba kerĂĽlnek, így a PC-vel szinkronizálhatók: a PC-n letett hang-könyvjelzőt
 * itt egy mozdulattal folytathatod.
 *
 * Fel/le: menĂĽ. Jobbra: kiválaszt. Balra: kilépés (a hely elmentve).
 * Hangerőgomb = tekerés.
 */
class AudiobookPlayerActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvPosition: TextView
    private lateinit var tts: TtsManager
    private lateinit var sounds: SoundFeedback
    private lateinit var gestureListener: SwipeGestureListener

    private var mediaPlayer: MediaPlayer? = null
    private var paused = false

    /** FĂĽlhallgató kihúzásakor a hangoskönyv elhallgat, nem szól a hangszóróból. */
    private val headphoneGuard by lazy {
        com.superdl.launcher.media.HeadphoneUnplugGuard(this) {
            runOnUiThread { if (!paused) togglePause() }
        }
    }

    private var tracks: List<File> = emptyList()
    private var currentIndex = 0
    private var bookPath = ""
    private var bookTitle = ""
    private var pendingSeekMs = 0          // a folytatás/könyvjelző-ugrás alkalmazása prepare-kor

    private val seekStepSec = 15
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    private var pausedByFocusLoss = false
    private var mediaSession: android.media.session.MediaSession? = null

    private enum class ControlItem(val label: String) {
        PLAY_PAUSE("SzĂĽnet vagy folytatás"),
        NEXT("Következő sáv"),
        PREVIOUS("Előző sáv"),
        SEEK_FORWARD("Előre tekerés 15 másodperc"),
        SEEK_BACKWARD("Vissza tekerés 15 másodperc"),
        POSITION("Hol tartok"),
        BOOKMARK("Könyvjelző ide"),
        STOP("Lejátszás leállítása")
    }

    private val menuItems = ControlItem.entries
    private var menuIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_player)
        applyImmersive()

        tvTitle = findViewById(R.id.tvPlayerTitle)
        tvStatus = findViewById(R.id.tvPlayerStatus)
        tvPosition = findViewById(R.id.tvPlayerPosition)
        findViewById<TextView>(R.id.tvPlayerHint).text =
            "Fel-le: vezérlők. Jobbra: kiválaszt. Balra: kilépés."

        tts = TtsManager(this)
        sounds = SoundFeedback(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        setupMediaSession()

        bookPath = AudiobookHolder.bookPath
        bookTitle = AudiobookHolder.bookTitle
        tracks = AudiobookHolder.tracks.map { File(it) }

        gestureListener = SwipeGestureListener(
            context = this,
            onSwipeUp = { sounds.play(SoundType.SWIPE_UP); navigateMenu(-1) },
            onSwipeDown = { sounds.play(SoundType.SWIPE_DOWN); navigateMenu(+1) },
            onSwipeRight = { sounds.play(SoundType.SWIPE_RIGHT); activateMenuItem() },
            onSwipeLeft = { sounds.play(SoundType.SWIPE_LEFT); stopAndFinish("Bezárva.") }
        )
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = stopAndFinish("Bezárva.")
        })

        if (tracks.isEmpty()) {
            tts.speakThen("Ebben a hangoskönyvben nincs lejátszható sáv.") { finish() }
            return
        }

        // Indulási hely: a könyvjelző-ugrás (startTrack/startMs) vagy a mentett folytatás
        var startTrack = AudiobookHolder.startTrack
        var startMs = AudiobookHolder.startMs
        if (startTrack.isBlank()) {
            BookStore.getAudioResume(this, bookPath)?.let { (t, ms) ->
                startTrack = t
                startMs = ms
            }
        }
        currentIndex = trackIndexOf(startTrack).coerceAtLeast(0)
        pendingSeekMs = startMs.coerceAtLeast(0)
        playCurrent(announce = true)
    }

    /** A sáv ESZKĂ–ZFüGGETLEN azonosítója: mappánál a könyv gyökerétől számított
     * relatív út (kötet-almappával), fájlnál a fájlnév. */
    private fun trackIdOf(f: File): String {
        val root = File(bookPath)
        return if (root.isDirectory) {
            f.absolutePath.removePrefix(root.absolutePath)
                .trimStart('/', '\\').replace('\\', '/')
        } else {
            f.name
        }
    }

    private fun trackIndexOf(trackId: String): Int {
        if (trackId.isBlank()) return 0
        val cel = trackId.replace('\\', '/').lowercase()
        var i = tracks.indexOfFirst { trackIdOf(it).lowercase() == cel }
        if (i < 0) {                                   // visszaesés: csak fájlnév
            val nev = cel.substringAfterLast('/')
            i = tracks.indexOfFirst { it.name.lowercase() == nev }
        }
        return if (i >= 0) i else 0
    }

    private fun currentTrack(): File = tracks[currentIndex]

    private fun trackDisplay(f: File): String = f.nameWithoutExtension

    private fun navigateMenu(delta: Int) {
        menuIndex = (menuIndex + delta + menuItems.size) % menuItems.size
        val item = menuItems[menuIndex]
        findViewById<TextView>(R.id.tvPlayerHint).text =
            "${item.label}  â€˘  jobbra: kiválaszt  â€˘  balra: kilépés"
        tts.speak(item.label)
    }

    private fun activateMenuItem() {
        when (menuItems[menuIndex]) {
            ControlItem.PLAY_PAUSE -> togglePause()
            ControlItem.NEXT -> skipTrack(+1)
            ControlItem.PREVIOUS -> skipTrack(-1)
            ControlItem.SEEK_FORWARD -> seekBy(seekStepSec)
            ControlItem.SEEK_BACKWARD -> seekBy(-seekStepSec)
            ControlItem.POSITION -> announcePosition()
            ControlItem.BOOKMARK -> addBookmarkHere()
            ControlItem.STOP -> stopAndFinish("Leállítva. A helyet megjegyeztem.")
        }
    }

    private fun playCurrent(announce: Boolean = false) {
        releasePlayer()
        val track = currentTrack()
        tvTitle.text = trackDisplay(track)
        tvPosition.text = "$bookTitle – ${currentIndex + 1}/${tracks.size}. sáv"
        tvStatus.text = getString(R.string.player_loading)
        if (!requestAudioFocus()) {
            tts.speak("Most nem tudom lejátszani, mert más használja a hangot. Próbáld újra.")
            return
        }
        // A fĂĽlhallgató kihúzását is figyeljĂĽk innentől.
        headphoneGuard.register()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(track.absolutePath)
                setOnPreparedListener {
                    tvStatus.text = getString(R.string.player_playing)
                    val seekMs = pendingSeekMs
                    pendingSeekMs = 0
                    if (seekMs in 1 until duration) seekTo(seekMs)
                    start()
                    paused = false
                    updateSessionState()
                    val hol = if (seekMs in 1 until duration)
                        " Folytatás innen: ${formatClock(seekMs)}." else ""
                    tts.speak("${currentIndex + 1} / ${tracks.size}. sáv. "
                        + "${trackDisplay(track)}.$hol")
                }
                setOnCompletionListener { onTrackCompleted() }
                setOnErrorListener { _, _, _ ->
                    tts.speak("Ez a sáv nem játszható le. Következő.")
                    skipTrack(+1, auto = true)
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            tts.speak("Ez a sáv nem játszható le. Következő.")
            skipTrack(+1, auto = true)
        }
    }

    private fun onTrackCompleted() {
        // a sáv vége magától a következőre lép; a könyv végén megáll
        if (currentIndex < tracks.size - 1) {
            currentIndex++
            pendingSeekMs = 0
            playCurrent()
        } else {
            saveResume()
            stopAndFinish("A hangoskönyv végére értél.")
        }
    }

    private fun skipTrack(delta: Int, auto: Boolean = false) {
        if (delta < 0) {
            // ha 3 mp-nél beljebb vagyunk, a sáv elejére; kĂĽlönben előző sáv
            val p = mediaPlayer
            if (!auto && p != null && p.currentPosition > 3000) {
                p.seekTo(0)
                tts.speak("A sáv elejéről.")
                return
            }
        }
        val ujj = currentIndex + delta
        if (ujj < 0) { tts.speak("Ez az első sáv."); return }
        if (ujj > tracks.size - 1) { tts.speak("Ez az utolsó sáv."); return }
        currentIndex = ujj
        pendingSeekMs = 0
        playCurrent()
    }

    private fun togglePause() {
        val player = mediaPlayer ?: return
        if (paused) {
            player.start()
            paused = false
            pausedByFocusLoss = false
            tvStatus.text = getString(R.string.player_playing)
            tts.speak("Folytatás.")
        } else {
            player.pause()
            paused = true
            pausedByFocusLoss = false
            tvStatus.text = getString(R.string.player_paused)
            saveResume()
            tts.speak("SzĂĽnet.")
        }
        updateSessionState()
    }

    private fun seekBy(deltaSec: Int) {
        val player = mediaPlayer ?: return
        val target = (player.currentPosition + deltaSec * 1000).coerceIn(0, player.duration)
        player.seekTo(target)
        val dir = if (deltaSec > 0) "előre" else "vissza"
        tts.speak("$dir ${Math.abs(deltaSec)} másodperc. ${formatClock(target)}.")
    }

    private fun announcePosition() {
        val player = mediaPlayer ?: return
        val pos = formatClock(player.currentPosition)
        val dur = formatClock(player.duration)
        val remaining = formatClock((player.duration - player.currentPosition).coerceAtLeast(0))
        tts.speak("${currentIndex + 1}. sáv. $pos a $dur-ból. Hátra van $remaining.")
    }

    private fun addBookmarkHere() {
        val player = mediaPlayer
        val ms = player?.currentPosition ?: pendingSeekMs
        val track = trackIdOf(currentTrack())
        val preview = "${currentIndex + 1}. sáv â€˘ ${formatClock(ms)}"
        val bm = BookStore.addAudioBookmark(this, bookPath, bookTitle, track, ms, preview)
        saveResume()
        if (bm != null) {
            tts.speak("Könyvjelző elmentve: $preview. Az Ătjáróban "
                + "szinkronizálhatod a PC-vel.")
        } else {
            tts.speak("Nem sikerĂĽlt könyvjelzőt tenni (túl sok van már).")
        }
    }

    private fun saveResume() {
        val player = mediaPlayer ?: return
        try {
            BookStore.saveAudioResume(
                this, bookPath, trackIdOf(currentTrack()), player.currentPosition
            )
        } catch (_: Exception) {
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event?.repeatCount == 0) seekBy(seekStepSec); true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event?.repeatCount == 0) seekBy(-seekStepSec); true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun formatClock(ms: Int): String {
        if (ms <= 0) return "0 másodperc"
        val totalSec = ms / 1000
        val mins = totalSec / 60
        val secs = totalSec % 60
        return if (mins > 0) "$mins perc $secs másodperc" else "$secs másodperc"
    }

    private fun stopAndFinish(message: String) {
        saveResume()
        releasePlayer()
        releaseMediaSession()
        tts.speak(message)
        finish()
    }

    // ==================== audio focus ====================
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.pause(); paused = true; pausedByFocusLoss = false
                    tvStatus.text = getString(R.string.player_paused)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.pause(); paused = true; pausedByFocusLoss = true
                    tvStatus.text = getString(R.string.player_paused)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (pausedByFocusLoss && paused) {
                    mediaPlayer?.start(); paused = false; pausedByFocusLoss = false
                    tvStatus.text = getString(R.string.player_playing)
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val attrs = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val request = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusListener)
                .setWillPauseWhenDucked(true)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusListener)
            }
        } catch (_: Exception) {
        }
        audioFocusRequest = null
    }

    // ==================== MediaSession (headset gombok) ====================
    private fun setupMediaSession() {
        try {
            val session = android.media.session.MediaSession(this, "SuperDL_Audiobook")
            session.setCallback(object : android.media.session.MediaSession.Callback() {
                override fun onPlay() { if (paused) togglePause() }
                override fun onPause() { if (!paused) togglePause() }
                override fun onSkipToNext() { skipTrack(+1) }
                override fun onSkipToPrevious() { skipTrack(-1) }
                override fun onStop() { stopAndFinish("Leállítva.") }
                override fun onSeekTo(pos: Long) {
                    try { mediaPlayer?.seekTo(pos.toInt()) } catch (_: Exception) {}
                }
            })
            session.isActive = true
            mediaSession = session
            updateSessionState()
        } catch (e: Exception) {
            mediaSession = null
        }
    }

    private fun updateSessionState() {
        val session = mediaSession ?: return
        try {
            val state = if (paused) android.media.session.PlaybackState.STATE_PAUSED
                        else android.media.session.PlaybackState.STATE_PLAYING
            val position = try { mediaPlayer?.currentPosition?.toLong() ?: 0L } catch (_: Exception) { 0L }
            val ps = android.media.session.PlaybackState.Builder()
                .setActions(
                    android.media.session.PlaybackState.ACTION_PLAY or
                        android.media.session.PlaybackState.ACTION_PAUSE or
                        android.media.session.PlaybackState.ACTION_PLAY_PAUSE or
                        android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT or
                        android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        android.media.session.PlaybackState.ACTION_STOP or
                        android.media.session.PlaybackState.ACTION_SEEK_TO
                )
                .setState(state, position, 1.0f)
                .build()
            session.setPlaybackState(ps)
            val duration = try { mediaPlayer?.duration?.toLong() ?: 0L } catch (_: Exception) { 0L }
            val md = android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, trackDisplay(currentTrack()))
                .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, bookTitle)
                .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, duration)
                .build()
            session.setMetadata(md)
        } catch (_: Exception) {
        }
    }

    private fun releaseMediaSession() {
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (_: Exception) {
        }
        mediaSession = null
    }

    private fun releasePlayer() {
        mediaPlayer?.runCatching {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        abandonAudioFocus()
        pausedByFocusLoss = false
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
        headphoneGuard.unregister()
        handler.removeCallbacksAndMessages(null)
        saveResume()
        releasePlayer()
        releaseMediaSession()
        tts.shutdown()
        sounds.release()
        super.onDestroy()
    }

    companion object {
        /** A hangoskönyv indítása a megadott mappával/sávokkal, opcionális
         * kezdő-sávval és pozícióval (könyvjelző-ugráshoz). */
        fun launch(
            ctx: Context, bookPath: String, bookTitle: String,
            tracks: List<String>, startTrack: String = "", startMs: Int = 0
        ) {
            AudiobookHolder.bookPath = bookPath
            AudiobookHolder.bookTitle = bookTitle
            AudiobookHolder.tracks = tracks
            AudiobookHolder.startTrack = startTrack
            AudiobookHolder.startMs = startMs
            ctx.startActivity(
                android.content.Intent(ctx, AudiobookPlayerActivity::class.java)
            )
        }
    }
}
