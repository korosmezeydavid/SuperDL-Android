package com.superdl.launcher.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import java.util.concurrent.atomic.AtomicBoolean

object AlertSoundPlayer {

    const val DELAY_AFTER_WAKE_BEEP_MS = 700L
    const val DELAY_BEFORE_ALERT_UI_MS = 450L

    private val WAKE_DOUBLE_BEEP = listOf(
        1046 to 160,
        0 to 240,
        1046 to 160
    )

    private val FALLBACK_LOOP_SEQUENCE = listOf(
        880 to 140,
        0 to 220,
        1175 to 180
    )

    fun playAlertWakeBeep(context: Context? = null, force: Boolean = true) {
        if (context != null && !AlertSoundSettingsStore.shouldPlay(context, force)) return
        val volume = context?.let { toneVolume(it) } ?: 100
        try {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, volume)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 170)
            Thread.sleep(230)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 170)
            Thread.sleep(200)
            tone.release()
        } catch (_: Exception) {
            playToneSequenceSync(context, WAKE_DOUBLE_BEEP, force)
        }
    }

    fun resolveUri(context: Context, preset: AlertSoundPreset): Uri? {
        val type = preset.ringtoneType ?: return null
        return RingtoneManager.getDefaultUri(type)
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, type)
    }

    fun startLooping(context: Context, category: AlertSoundCategory): () -> Unit {
        if (!AlertSoundSettingsStore.shouldPlay(context)) return {}
        val preset = AlertSoundStore.getPreset(context, category)
        return startLoopingPreset(context, preset, usage = AudioAttributes.USAGE_ALARM)
    }

    fun startLoopingPreset(
        context: Context,
        preset: AlertSoundPreset,
        usage: Int = AudioAttributes.USAGE_ALARM
    ): () -> Unit {
        if (!AlertSoundSettingsStore.shouldPlay(context)) return {}
        val uri = resolveUri(context, preset)
        if (uri != null) {
            val player = try {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(usage)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context, uri)
                    isLooping = true
                    prepare()
                    applyVolume(context, this)
                    start()
                }
            } catch (_: Exception) {
                null
            }
            if (player != null) {
                val focusRelease = requestAlarmFocus(context)
                return {
                    focusRelease()
                    stop(player)
                }
            }
        }
        val running = AtomicBoolean(true)
        val thread = Thread(
            {
                while (running.get()) {
                    playToneSequenceSync(context, effectiveSequence(preset), force = false)
                    if (running.get()) Thread.sleep(900)
                }
            },
            "SuperDL-AlertToneLoop"
        )
        thread.start()
        return {
            running.set(false)
            thread.interrupt()
        }
    }

    fun playOnce(context: Context, category: AlertSoundCategory) {
        preview(context, AlertSoundStore.getPreset(context, category))
    }

    fun preview(context: Context, preset: AlertSoundPreset) {
        if (!AlertSoundSettingsStore.shouldPlay(context, force = true)) return
        val uri = resolveUri(context, preset)
        if (uri != null) {
            val player = try {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context, uri)
                    prepare()
                    applyVolume(context, this)
                    setOnCompletionListener { stop(this) }
                    start()
                }
            } catch (_: Exception) {
                null
            }
            if (player != null) {
                requestAlarmFocus(context)
                return
            }
        }
        Thread({
            val sequence = effectiveSequence(preset)
            playToneSequenceSync(context, sequence, force = true)
        }, "SuperDL-AlertPreview").start()
    }

    private fun effectiveSequence(preset: AlertSoundPreset): List<Pair<Int, Int>> =
        preset.toneSequence ?: FALLBACK_LOOP_SEQUENCE

    private fun applyVolume(context: Context, player: MediaPlayer) {
        val scale = AlertSoundSettingsStore.volumeScale(context)
        player.setVolume(scale, scale)
    }

    private fun toneVolume(context: Context): Int =
        (AlertSoundSettingsStore.getVolumePercent(context) * ToneGenerator.MAX_VOLUME / 100)
            .coerceIn(1, ToneGenerator.MAX_VOLUME)

    private fun requestAlarmFocus(context: Context): () -> Unit {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            audioManager.requestAudioFocus(request)
            return { audioManager.abandonAudioFocusRequest(request) }
        }
        @Suppress("DEPRECATION")
        audioManager.requestAudioFocus(
            null,
            AudioManager.STREAM_ALARM,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )
        @Suppress("DEPRECATION")
        return { audioManager.abandonAudioFocus(null) }
    }

    fun stop(mediaPlayer: MediaPlayer?) {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
    }

    private fun playToneSequenceSync(
        context: Context?,
        notes: List<Pair<Int, Int>>,
        force: Boolean
    ) {
        if (context != null && !AlertSoundSettingsStore.shouldPlay(context, force)) return
        val volume = context?.let { toneVolume(it) } ?: 85
        for ((index, note) in notes.withIndex()) {
            if (note.first > 0 && note.second > 0) {
                playAlarmBurst(context, note.first, note.second, volume)
            } else if (note.second > 0) {
                Thread.sleep(note.second.toLong())
            }
            if (index < notes.lastIndex) Thread.sleep(70)
        }
    }

    private fun playAlarmBurst(context: Context?, freq: Int, durationMs: Int, volume: Int) {
        try {
            val sampleRate = 22050
            val sampleCount = (sampleRate * durationMs / 1000).coerceAtLeast(1)
            val buffer = ShortArray(sampleCount)
            val phaseInc = 2.0 * Math.PI * freq / sampleRate
            var phase = 0.0
            val amp = Short.MAX_VALUE * 0.55 * (volume / 100.0)
            for (i in 0 until sampleCount) {
                val attack = minOf(1.0, i / (sampleRate * 0.01))
                val release = minOf(1.0, (sampleCount - i) / (sampleRate * 0.03))
                val env = attack * release
                phase += phaseInc
                buffer[i] = (Math.sin(phase) * amp * env).toInt().toShort()
            }
            val track = android.media.AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setLegacyStreamType(AudioManager.STREAM_ALARM)
                        .build()
                )
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                .build()
            track.write(buffer, 0, buffer.size)
            val scale = context?.let { AlertSoundSettingsStore.volumeScale(it) } ?: 1f
            track.setVolume(scale)
            track.play()
            Thread.sleep(durationMs.toLong() + 40)
            track.stop()
            track.release()
        } catch (_: Exception) {
            try {
                val tone = ToneGenerator(AudioManager.STREAM_ALARM, volume)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, durationMs)
                Thread.sleep((durationMs + 40).toLong())
                tone.release()
            } catch (_: Exception) {
            }
        }
    }
}