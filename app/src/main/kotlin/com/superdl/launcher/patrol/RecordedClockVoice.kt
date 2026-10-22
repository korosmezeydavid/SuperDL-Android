package com.superdl.launcher.patrol

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log

/** A felhasználó által adott egész órás WAV-felvételek lejátszója. */
object RecordedClockVoice {
    private val handler = Handler(Looper.getMainLooper())

    internal fun clipName(hour: Int): String? =
        if (hour in 0..23) "%02d.wav".format(if (hour == 0) 24 else hour) else null

    fun play(context: Context, voice: String, hour: Int, onDone: (Boolean) -> Unit) {
        val clip = clipName(hour)
        if (voice !in listOf(ClockVoiceStore.LILLY, ClockVoiceStore.MARTIN) || clip == null) {
            handler.post { onDone(false) }
            return
        }
        handler.post {
            var player: MediaPlayer? = null
            var finished = false
            fun finish(ok: Boolean) {
                if (finished) return
                finished = true
                try { player?.release() } catch (_: Throwable) {}
                onDone(ok)
            }
            try {
                val descriptor = context.assets.openFd("clock/$voice/$clip")
                player = MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                    descriptor.close()
                    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                        ?.let { setPreferredDevice(it) }
                    setOnCompletionListener { finish(true) }
                    setOnErrorListener { _, _, _ -> finish(false); true }
                    setOnPreparedListener { it.start() }
                    prepareAsync()
                }
            } catch (error: Throwable) {
                Log.w("SuperDL.ClockVoice", "Órahang nem indítható: $voice/$clip", error)
                finish(false)
            }
        }
    }
}
