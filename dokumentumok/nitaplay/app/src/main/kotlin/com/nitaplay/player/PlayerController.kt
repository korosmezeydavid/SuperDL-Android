package com.nitaplay.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.nitaplay.data.PlayMode
import com.nitaplay.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI-oldali híd a PlaybackService felé.
 */
class PlayerController(private val context: Context) {
    private var service: PlaybackService? = null
    private var bound = false

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    val playbackState: StateFlow<PlayerUiState>
        get() = service?.state ?: _fallbackState

    private val _fallbackState = MutableStateFlow(PlayerUiState())

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as? PlaybackService.LocalBinder ?: return
            service = b.service
            bound = true
            _connected.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            _connected.value = false
        }
    }

    fun bind() {
        val intent = Intent(context, PlaybackService::class.java)
        try {
            context.startService(intent)
        } catch (_: Exception) {
            // Android 8+/Samsung One UI: háttérből tiltott lehet a startService.
            // A bindService BIND_AUTO_CREATE-tel így is létrehozza a szolgáltatást.
        }
        try {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (_: Exception) {
        }
    }

    fun unbind() {
        if (bound) {
            try {
                context.unbindService(connection)
            } catch (_: Exception) {
            }
            bound = false
        }
        service = null
        _connected.value = false
    }

    fun play(tracks: List<Track>, index: Int = 0) {
        ensureService()
        service?.playQueue(tracks, index)
    }

    fun togglePlayPause() = service?.togglePlayPause()
    fun next() = service?.skipNext()
    fun previous() = service?.skipPrevious()
    fun seekTo(ms: Int) = service?.seekTo(ms)
    fun seekBy(deltaSec: Int) = service?.seekBy(deltaSec)
    fun setPlayMode(mode: PlayMode) = service?.setPlayMode(mode)
    fun setSleepTimer(minutes: Int) = service?.setSleepTimer(minutes)
    fun cancelSleepTimer() = service?.cancelSleepTimer()
    fun applyEqProfile(name: String) = service?.applyEqProfile(name)
    fun applyEqBands(gains: FloatArray) = service?.applyEqBands(gains)
    fun stop() = service?.stopPlayback()

    private fun ensureService() {
        if (service == null) {
            val intent = Intent(context, PlaybackService::class.java)
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
            try {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            } catch (_: Exception) {
            }
        }
    }
}

data class PlayerUiState(
    val track: Track? = null,
    val queue: List<Track> = emptyList(),
    val index: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val playMode: PlayMode = PlayMode.SEQUENTIAL,
    val sleepRemainingSec: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val eqSupported: Boolean = true
)
