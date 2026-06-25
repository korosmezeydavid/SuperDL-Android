package com.superdl.launcher.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import java.util.concurrent.atomic.AtomicBoolean

object AlertSoundPlayer {

    const val DELAY_AFTER_WAKE_BEEP_MS = 700L
    const val DELAY_BEFORE_ALERT_UI_MS = 450L

    private val WAKE_DOUBLE_BEEP = listOf(
        1046 to 160,
        0 to 240,
        1046 to 160
    )

    /** Könnyű, azonnali beep-beep – még befagyás előtt is hallható. */
    fun playAlertWakeBeep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 170)
            Thread.sleep(230)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 170)
            Thread.sleep(200)
            tone.release()
        } catch (_: Exception) {
            playToneSequenceSync(WAKE_DOUBLE_BEEP)
        }
    }

    fun resolveUri(@Suppress("UNUSED_PARAMETER") context: Context, preset: AlertSoundPreset): Uri? {
        val type = preset.ringtoneType ?: return null
        return RingtoneManager.getDefaultUri(type)
    }

    fun startLooping(context: Context, category: AlertSoundCategory): () -> Unit {
        val preset = AlertSoundStore.getPreset(context, category)
        return startLoopingPreset(context, preset, usage = AudioAttributes.USAGE_ALARM)
    }

    fun startLoopingPreset(
        context: Context,
        preset: AlertSoundPreset,
        usage: Int = AudioAttributes.USAGE_ALARM
    ): () -> Unit {
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
                    start()
                }
            } catch (_: Exception) {
                null
            }
            if (player != null) {
                return { stop(player) }
            }
        }
        val running = AtomicBoolean(true)
        val thread = Thread(
            {
                while (running.get()) {
                    playToneSequenceSync(preset.toneSequence ?: break)
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
        val uri = resolveUri(context, preset)
        if (uri != null) {
            try {
                RingtoneManager.getRingtone(context, uri)?.play()
            } catch (_: Exception) {
                playFallbackBeep()
            }
            return
        }
        Thread({
            val sequence = preset.toneSequence
            if (sequence != null) playToneSequenceSync(sequence) else playFallbackBeep()
        }, "SuperDL-AlertPreview").start()
    }

    private fun playFallbackBeep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 85)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
            Thread.sleep(220)
            tone.release()
        } catch (_: Exception) {
        }
    }

    fun stop(mediaPlayer: MediaPlayer?) {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
    }

    private fun playToneSequenceSync(notes: List<Pair<Int, Int>>) {
        for ((index, note) in notes.withIndex()) {
            if (note.first > 0 && note.second > 0) {
                DeviceStateTonePlayer.playBurstSync(note.first, note.second)
            } else if (note.second > 0) {
                Thread.sleep(note.second.toLong())
            }
            if (index < notes.lastIndex) Thread.sleep(70)
        }
    }
}