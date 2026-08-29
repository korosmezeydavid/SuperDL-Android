package com.nitaplay.player

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.nitaplay.MainActivity
import com.nitaplay.NitaPlayApp
import com.nitaplay.R
import com.nitaplay.data.AppPreferences
import com.nitaplay.data.PlayMode
import com.nitaplay.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PlaybackService : Service() {

    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: AppPreferences
    private lateinit var audioManager: AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var equalizer: EqualizerController? = null
    private var mediaSession: MediaSession? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var pausedByFocusLoss = false
    private var queue: List<Track> = emptyList()
    private var index = 0
    private var playMode = PlayMode.SEQUENTIAL
    private var sleepEndAt = 0L

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                // Fülhallgató kihúzása — azonnal álljon meg
                pauseInternal(user = true)
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val player = mediaPlayer
            if (player != null) {
                try {
                    val pos = player.currentPosition
                    val dur = player.duration.coerceAtLeast(0)
                    val sleepLeft = if (sleepEndAt > 0) {
                        ((sleepEndAt - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                    } else 0
                    if (sleepEndAt > 0 && System.currentTimeMillis() >= sleepEndAt) {
                        fadeOutAndStop()
                        return
                    }
                    // Alvó időzítő: utolsó 15 mp-ben halkítás
                    if (sleepEndAt > 0 && sleepLeft <= 15) {
                        val vol = (sleepLeft / 15f).coerceIn(0f, 1f)
                        player.setVolume(vol, vol)
                    }
                    _state.update {
                        it.copy(
                            positionMs = pos,
                            durationMs = if (dur > 0) dur else it.durationMs,
                            sleepRemainingSec = sleepLeft
                        )
                    }
                    updateSessionState()
                    // Pozíció mentése ~5 mp-enként
                    if (pos % 5000 < 500) {
                        queue.getOrNull(index)?.let { t ->
                            prefs.savePosition(t.id, pos.toLong())
                        }
                    }
                } catch (_: Exception) {
                }
            }
            handler.postDelayed(this, 400)
        }
    }

    inner class LocalBinder : Binder() {
        val service: PlaybackService get() = this@PlaybackService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        playMode = prefs.playMode
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        equalizer = EqualizerController(this)
        setupMediaSession()
        try {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(noisyReceiver, filter)
            }
        } catch (_: Exception) {
        }
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> togglePlayPause()
            ACTION_PAUSE -> pauseInternal(user = true)
            ACTION_NEXT -> skipNext()
            ACTION_PREV -> skipPrevious()
            ACTION_STOP -> stopPlayback()
        }
        return START_STICKY
    }

    fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        queue = tracks
        index = startIndex.coerceIn(0, tracks.lastIndex)
        playCurrent(resumeIfSaved = true)
    }

    fun togglePlayPause() {
        val player = mediaPlayer
        if (player == null) {
            if (queue.isNotEmpty()) playCurrent(resumeIfSaved = true)
            return
        }
        if (player.isPlaying) {
            pauseInternal(user = true)
        } else {
            if (requestAudioFocus()) {
                player.start()
                pausedByFocusLoss = false
                _state.update { it.copy(isPlaying = true) }
                updateSessionState()
                startForegroundSafely(buildNotification())
            }
        }
    }

    fun skipNext() {
        if (queue.isEmpty()) return
        index = when (playMode) {
            PlayMode.SHUFFLE -> queue.indices.random()
            else -> (index + 1) % queue.size
        }
        playCurrent(resumeIfSaved = false)
    }

    fun skipPrevious() {
        if (queue.isEmpty()) return
        val player = mediaPlayer
        if (player != null && player.currentPosition > 3000) {
            player.seekTo(0)
            _state.update { it.copy(positionMs = 0) }
            return
        }
        index = (index - 1 + queue.size) % queue.size
        playCurrent(resumeIfSaved = false)
    }

    fun seekTo(ms: Int) {
        try {
            mediaPlayer?.seekTo(ms.coerceAtLeast(0))
            _state.update { it.copy(positionMs = ms.coerceAtLeast(0)) }
        } catch (_: Exception) {
        }
    }

    fun seekBy(deltaSec: Int) {
        val player = mediaPlayer ?: return
        val target = (player.currentPosition + deltaSec * 1000)
            .coerceIn(0, player.duration.coerceAtLeast(0))
        seekTo(target)
    }

    fun setPlayMode(mode: PlayMode) {
        playMode = mode
        prefs.playMode = mode
        _state.update { it.copy(playMode = mode) }
    }

    fun setSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        sleepEndAt = System.currentTimeMillis() + minutes * 60_000L
        _state.update { it.copy(sleepRemainingSec = minutes * 60) }
    }

    fun cancelSleepTimer() {
        sleepEndAt = 0L
        try {
            mediaPlayer?.setVolume(1f, 1f)
        } catch (_: Exception) {
        }
        _state.update { it.copy(sleepRemainingSec = 0) }
    }

    fun applyEqProfile(name: String) {
        equalizer?.applyProfile(name)
    }

    fun applyEqBands(gains: FloatArray) {
        equalizer?.applyCustomBands(gains)
    }

    fun stopPlayback() {
        savePosition()
        releasePlayer()
        cancelSleepTimer()
        _state.value = PlayerUiState(playMode = playMode, eqSupported = equalizer?.supported != false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun playCurrent(resumeIfSaved: Boolean) {
        val track = queue.getOrNull(index) ?: return
        releasePlayer(keepFocus = false)
        _state.update {
            it.copy(
                track = track,
                queue = queue,
                index = index,
                isPlaying = false,
                isLoading = true,
                positionMs = 0,
                durationMs = track.durationMs.toInt(),
                playMode = playMode,
                error = null,
                eqSupported = equalizer?.supported != false
            )
        }
        if (!requestAudioFocus()) {
            _state.update { it.copy(isLoading = false, error = "A hang más alkalmazásé.") }
            return
        }
        try {
            val player = MediaPlayer()
            mediaPlayer = player
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.setDataSource(this, track.contentUri)
            player.setOnPreparedListener {
                try {
                    equalizer?.attach(it.audioSessionId)
                    equalizer?.applySaved()
                } catch (_: Exception) {
                }
                val duration = it.duration.coerceAtLeast(0)
                var startPos = 0
                if (resumeIfSaved) {
                    val saved = prefs.getSavedPosition(track.id)
                    if (saved > 3000L && saved < duration - 3000L) {
                        startPos = saved.toInt()
                        it.seekTo(startPos)
                        prefs.clearPosition()
                    }
                }
                it.setVolume(1f, 1f)
                it.start()
                _state.update {
                    it.copy(
                        isPlaying = true,
                        isLoading = false,
                        positionMs = startPos,
                        durationMs = duration,
                        eqSupported = equalizer?.supported == true
                    )
                }
                updateSessionState()
                startForegroundSafely(buildNotification())
            }
            player.setOnCompletionListener { onCompleted() }
            player.setOnErrorListener { _, _, _ ->
                _state.update { it.copy(isLoading = false, error = "Nem játszható le ez a szám.") }
                handler.postDelayed({ skipNext() }, 800)
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "play failed", e)
            _state.update { it.copy(isLoading = false, error = "Lejátszási hiba.") }
            handler.postDelayed({ skipNext() }, 800)
        }
    }

    private fun onCompleted() {
        when (playMode) {
            PlayMode.REPEAT_ONE -> playCurrent(resumeIfSaved = false)
            PlayMode.SEQUENTIAL -> {
                if (index < queue.lastIndex) {
                    index++
                    playCurrent(resumeIfSaved = false)
                } else {
                    pauseInternal(user = true)
                    seekTo(0)
                }
            }
            PlayMode.REPEAT_ALL -> {
                index = (index + 1) % queue.size
                playCurrent(resumeIfSaved = false)
            }
            PlayMode.SHUFFLE -> {
                index = queue.indices.random()
                playCurrent(resumeIfSaved = false)
            }
        }
    }

    private fun pauseInternal(user: Boolean) {
        try {
            mediaPlayer?.pause()
        } catch (_: Exception) {
        }
        if (user) pausedByFocusLoss = false
        savePosition()
        _state.update { it.copy(isPlaying = false) }
        updateSessionState()
        startForegroundSafely(buildNotification())
    }

    private fun fadeOutAndStop() {
        val player = mediaPlayer
        if (player == null) {
            cancelSleepTimer()
            return
        }
        var step = 10
        val fade = object : Runnable {
            override fun run() {
                step--
                if (step <= 0 || mediaPlayer == null) {
                    pauseInternal(user = true)
                    cancelSleepTimer()
                    return
                }
                val v = step / 10f
                try {
                    mediaPlayer?.setVolume(v, v)
                } catch (_: Exception) {
                }
                handler.postDelayed(this, 150)
            }
        }
        handler.post(fade)
    }

    private fun savePosition() {
        val track = queue.getOrNull(index) ?: return
        try {
            val pos = mediaPlayer?.currentPosition?.toLong() ?: return
            prefs.savePosition(track.id, pos)
        } catch (_: Exception) {
        }
    }

    private fun releasePlayer(keepFocus: Boolean = false) {
        savePosition()
        equalizer?.release()
        mediaPlayer?.runCatching {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        if (!keepFocus) abandonAudioFocus()
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> pauseInternal(user = true)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.pause()
                    pausedByFocusLoss = true
                    _state.update { it.copy(isPlaying = false) }
                    updateSessionState()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (pausedByFocusLoss) {
                    mediaPlayer?.start()
                    pausedByFocusLoss = false
                    _state.update { it.copy(isPlaying = true) }
                    updateSessionState()
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusListener)
                .setWillPauseWhenDucked(true)
                .build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusListener)
            }
        } catch (_: Exception) {
        }
        audioFocusRequest = null
    }

    private fun setupMediaSession() {
        try {
            val session = MediaSession(this, "NitaPlay")
            session.setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    if (_state.value.isPlaying.not()) togglePlayPause()
                }
                override fun onPause() {
                    if (_state.value.isPlaying) pauseInternal(user = true)
                }
                override fun onSkipToNext() = skipNext()
                override fun onSkipToPrevious() = skipPrevious()
                override fun onStop() = stopPlayback()
                override fun onSeekTo(pos: Long) = seekTo(pos.toInt())
            })
            session.isActive = true
            mediaSession = session
        } catch (e: Exception) {
            Log.w(TAG, "MediaSession failed", e)
        }
    }

    private fun updateSessionState() {
        val session = mediaSession ?: return
        val s = _state.value
        try {
            val state = if (s.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
            session.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP or
                            PlaybackState.ACTION_SEEK_TO
                    )
                    .setState(state, s.positionMs.toLong(), 1f)
                    .build()
            )
            val t = s.track ?: return
            session.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, t.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, t.displayArtist())
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, t.album)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, s.durationMs.toLong())
                    .build()
            )
        } catch (_: Exception) {
        }
    }

    private fun startForegroundSafely(notification: Notification) {
        try {
            startForeground(NOTIF_ID, notification)
        } catch (e: Exception) {
            // Android 12+ tilthatja a háttérből indított foreground service-t
            // (Samsung One UI különösen szigorú). Ne omoljon össze — a lejátszás megy,
            // csak sima értesítést mutatunk.
            Log.w(TAG, "startForeground blocked, falling back to notify", e)
            try {
                val nm = getSystemService(NotificationManager::class.java)
                nm?.notify(NOTIF_ID, notification)
            } catch (_: Exception) {
            }
        }
    }

    private fun buildNotification(): Notification {
        val s = _state.value
        val track = s.track
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPause = PendingIntent.getService(
            this, 1,
            Intent(this, PlaybackService::class.java).setAction(
                if (s.isPlaying) ACTION_PAUSE else ACTION_PLAY
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val next = PendingIntent.getService(
            this, 2,
            Intent(this, PlaybackService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val prev = PendingIntent.getService(
            this, 3,
            Intent(this, PlaybackService::class.java).setAction(ACTION_PREV),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, NitaPlayApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(track?.title ?: getString(R.string.app_name))
            .setContentText(track?.displayArtist() ?: "")
            .setContentIntent(open)
            .setOngoing(s.isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_previous),
                    "Előző", prev
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(
                        this,
                        if (s.isPlaying) android.R.drawable.ic_media_pause
                        else android.R.drawable.ic_media_play
                    ),
                    if (s.isPlaying) "Szünet" else "Lejátszás", playPause
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_next),
                    "Következő", next
                ).build()
            )

        val mediaStyle = Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2)
        mediaSession?.let { mediaStyle.setMediaSession(it.sessionToken) }
        builder.setStyle(mediaStyle)

        // Artwork if available
        track?.artworkUri?.let { uri ->
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) builder.setLargeIcon(bmp)
                }
            } catch (_: Exception) {
            }
        }
        return builder.build()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(noisyReceiver)
        } catch (_: Exception) {
        }
        releasePlayer()
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (_: Exception) {
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NitaPlay.Playback"
        private const val NOTIF_ID = 42
        const val ACTION_PLAY = "com.nitaplay.PLAY"
        const val ACTION_PAUSE = "com.nitaplay.PAUSE"
        const val ACTION_NEXT = "com.nitaplay.NEXT"
        const val ACTION_PREV = "com.nitaplay.PREV"
        const val ACTION_STOP = "com.nitaplay.STOP"
    }
}
