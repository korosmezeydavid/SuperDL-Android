package com.superdl.launcher.feedback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.sin

object DeviceStateTonePlayer {

    private val handler = Handler(Looper.getMainLooper())

    fun play(event: DeviceStateEvent) {
        handler.post {
            when (event) {
                DeviceStateEvent.CHARGER_CONNECTED -> playSequence(
                    listOf(523 to 90, 784 to 90, 1046 to 120)
                )
                DeviceStateEvent.CHARGER_DISCONNECTED -> playSequence(
                    listOf(988 to 90, 587 to 130)
                )
                DeviceStateEvent.BATTERY_FULL -> playSequence(
                    listOf(880 to 80, 1175 to 80, 1568 to 80, 1976 to 140)
                )
                DeviceStateEvent.SCREEN_OFF -> playBurst(280, 110)
                DeviceStateEvent.SCREEN_ON -> playBurst(1175, 95)
            }
        }
    }

    private fun playSequence(notes: List<Pair<Int, Int>>) {
        Thread {
            for ((index, note) in notes.withIndex()) {
                playBurstSync(note.first, note.second)
                if (index < notes.lastIndex) Thread.sleep(70)
            }
        }.start()
    }

    private fun playBurst(freq: Int, durationMs: Int) {
        Thread { playBurstSync(freq, durationMs) }.start()
    }

    fun playBurstSync(freq: Int, durationMs: Int) {
        try {
            val sampleRate = 22050
            val sampleCount = (sampleRate * durationMs / 1000).coerceAtLeast(1)
            val buffer = ShortArray(sampleCount)
            val phaseInc = 2.0 * PI * freq / sampleRate
            var phase = 0.0
            for (i in 0 until sampleCount) {
                val attack = minOf(1.0, i / (sampleRate * 0.01))
                val release = minOf(1.0, (sampleCount - i) / (sampleRate * 0.03))
                val env = attack * release
                phase += phaseInc
                buffer[i] = (sin(phase) * Short.MAX_VALUE * 0.55 * env).toInt().toShort()
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(buffer, 0, buffer.size)
            track.play()
            Thread.sleep(durationMs.toLong() + 40)
            track.stop()
            track.release()
        } catch (_: Exception) {
            playToneGeneratorFallback(freq, durationMs)
        }
    }

    private fun playToneGeneratorFallback(freq: Int, durationMs: Int) {
        try {
            val toneType = when {
                freq >= 1400 -> ToneGenerator.TONE_PROP_BEEP2
                freq >= 900 -> ToneGenerator.TONE_PROP_BEEP
                freq >= 600 -> ToneGenerator.TONE_PROP_ACK
                else -> ToneGenerator.TONE_PROP_NACK
            }
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            tone.startTone(toneType, durationMs)
            handler.postDelayed({ tone.release() }, (durationMs + 80).toLong())
        } catch (_: Exception) {}
    }
}