package com.superdl.launcher.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.SystemClock
import android.os.Build
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

object AlertSoundPlayer {

    private const val TAG = "AlertSoundPlayer"

    const val DELAY_AFTER_WAKE_BEEP_MS = 700L
    const val DELAY_BEFORE_ALERT_UI_MS = 450L

    private val WAKE_DOUBLE_BEEP = listOf(
        1046 to 160,
        0 to 240,
        1046 to 160
    )

    private val DEFAULT_ALARM_SEQUENCE = listOf(
        880 to 220,
        0 to 200,
        1100 to 280,
        0 to 350,
        880 to 220
    )

    fun playAlertWakeBeep(context: Context? = null, force: Boolean = true) {
        if (context != null && !AlertSoundSettingsStore.shouldPlay(context, force)) return
        context?.let { ensureAlarmAudible(it) }
        playToneSequenceSync(context, WAKE_DOUBLE_BEEP, force = true)
    }

    fun resolveUri(context: Context, preset: AlertSoundPreset): Uri? {
        val type = preset.ringtoneType ?: return null
        return RingtoneManager.getDefaultUri(type)
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, type)
    }

    /**
     * EMLÉKEZTETŐK NÉMA MÓDBAN IS SZÓLNAK.
     *
     * Alph döntése (2026-09-26): „nagyon fontos a gyógyszer-emlékeztető és minden
     * emlékeztető normálisan szóljon néma módban is". A néma mód a program
     * apró hangjait (söprés, értesítés) csendesíti — egy bevenni való gyógyszert
     * vagy egy programot nem hallgathat el. Ezért ezeknél a kategóriáknál a
     * néma mód nem számít.
     */
    private val ALWAYS_AUDIBLE = setOf(
        AlertSoundCategory.MEDICATION,
        AlertSoundCategory.CALENDAR,
        AlertSoundCategory.ALARM_CLOCK
    )

    fun startLooping(context: Context, category: AlertSoundCategory): () -> Unit {
        val force = category in ALWAYS_AUDIBLE
        if (!AlertSoundSettingsStore.shouldPlay(context, force)) return {}
        AlertSoundStore.getToneUri(context, category)?.let { uri ->
            return startLoopingUri(context, category, uri, force)
        }
        val preset = AlertSoundStore.getPreset(context, category)
        return startLoopingPreset(context, preset, force)
    }

    private fun startLoopingUri(context: Context, category: AlertSoundCategory,
                                uri: Uri, force: Boolean): () -> Unit {
        if (force) ensureAlarmAudible(context)
        val running = AtomicBoolean(true)
        val thread = Thread({
            while (running.get() && !Thread.currentThread().isInterrupted) {
                if (!playSelectedSync(context, uri, category, running)) {
                    playToneSequenceSync(context, effectiveSequence(AlertSoundStore.getPreset(context, category)), force)
                }
                if (running.get()) sleepInterruptibly(700L)
            }
        }, "SuperDL-SelectedAlertLoop")
        thread.start()
        return { running.set(false); thread.interrupt() }
    }

    fun startLoopingPreset(
        context: Context,
        preset: AlertSoundPreset,
        force: Boolean = false
    ): () -> Unit {
        if (!AlertSoundSettingsStore.shouldPlay(context, force)) return {}
        ensureAlarmAudible(context)
        val sequence = effectiveSequence(preset)
        val running = AtomicBoolean(true)
        val thread = Thread(
            {
                while (running.get() && !Thread.currentThread().isInterrupted) {
                    playToneSequenceSync(context, sequence, force = force)
                    if (running.get() && !Thread.currentThread().isInterrupted) {
                        sleepInterruptibly(700L)
                    }
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
        val force = category in ALWAYS_AUDIBLE
        if (!AlertSoundSettingsStore.shouldPlay(context, force)) return
        if (category == AlertSoundCategory.SMS) {
            Thread({ playSmsOnSpeaker(context) }, "SuperDL-SmsSpeakerAlert").start()
            return
        }
        val uri = AlertSoundStore.getToneUri(context, category)
        if (uri == null) {
            preview(context, AlertSoundStore.getPreset(context, category))
            return
        }
        Thread({
            if (!playSelectedSync(context, uri, category, AtomicBoolean(true))) {
                playToneSequenceSync(context, effectiveSequence(AlertSoundStore.getPreset(context, category)), force)
            }
        }, "SuperDL-SelectedAlertOnce").start()
    }

    /** Az SMS saját lejátszót kap: más hangok és a beszéd útvonalát nem állítjuk át. */
    private fun playSmsOnSpeaker(context: Context) {
        // A telefon értesítési csatornája lehet 0-ra halkítva akkor is, ha a
        // felhasználó a SuperDL-ben külön SMS-hangot választott.
        ensureAlarmAudible(context)
        val preset = AlertSoundStore.getPreset(context, AlertSoundCategory.SMS)
        val uri = AlertSoundStore.getToneUri(context, AlertSoundCategory.SMS)
            ?: resolveUri(context, preset)
        if (uri != null && playSmsUriOnSpeaker(context, uri)) return
        val notes = effectiveSequence(preset)
        if (!playSmsSequenceOnSpeaker(context, notes)) {
            // Ha a gyártói hangútvonal elutasítja a célzott lejátszást,
            // maradjon legalább egy hallható rendszer-ébresztő hang.
            playToneSequenceSync(context, notes, force = true)
        }
    }

    private fun speaker(context: Context): AudioDeviceInfo? =
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

    private fun playSmsUriOnSpeaker(context: Context, uri: Uri): Boolean {
        var player: MediaPlayer? = null
        return try {
            player = MediaPlayer()
            player.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            player.setDataSource(context, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                speaker(context)?.let { player.setPreferredDevice(it) }
            }
            player.prepare()
            player.start()
            SystemClock.sleep(150L)
            val routed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                player.routedDevice else null
            if (routed != null && routed.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                Log.w(TAG, "SMS-hang nem a készülék hangszórójára került: ${routed.type}")
                return false
            }
            val deadline = SystemClock.elapsedRealtime() + 30_000L
            while (player.isPlaying && SystemClock.elapsedRealtime() < deadline) {
                sleepInterruptibly(100L)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "SMS-hang hangszórós lejátszása sikertelen", e)
            false
        } finally {
            try { player?.release() } catch (_: Exception) {}
        }
    }

    private fun playSmsSequenceOnSpeaker(context: Context, notes: List<Pair<Int, Int>>): Boolean {
        val sampleRate = 16_000
        val volume = toneVolume(context) / ToneGenerator.MAX_VOLUME.toDouble()
        val samples = ArrayList<Short>()
        for ((frequency, durationMs) in notes) {
            val count = sampleRate * durationMs.coerceAtLeast(0) / 1000
            for (i in 0 until count) {
                val edge = minOf(i, count - 1 - i, sampleRate / 100).toDouble() /
                    (sampleRate / 100)
                val wave = if (frequency > 0) kotlin.math.sin(
                    2.0 * Math.PI * frequency * i / sampleRate) else 0.0
                samples.add((wave * edge * volume * Short.MAX_VALUE).toInt().toShort())
            }
        }
        if (samples.isEmpty()) return false
        val data = ShortArray(samples.size) { samples[it] }
        var track: AudioTrack? = null
        try {
            track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(data.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            speaker(context)?.let { track.setPreferredDevice(it) }
            if (track.write(data, 0, data.size) != data.size) return false
            track.play()
            SystemClock.sleep(100L)
            val routed = track.routedDevice
            if (routed != null && routed.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                Log.w(TAG, "SMS tartalékhang nem a hangszóróra került: ${routed.type}")
                return false
            }
            sleepInterruptibly(data.size * 1000L / sampleRate + 100L)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Beépített SMS-hang hangszórós lejátszása sikertelen", e)
            return false
        } finally {
            try { track?.stop() } catch (_: Exception) {}
            try { track?.release() } catch (_: Exception) {}
        }
    }

    private fun playSelectedSync(context: Context, uri: Uri,
                                 category: AlertSoundCategory, running: AtomicBoolean): Boolean {
        var ringtone: android.media.Ringtone? = null
        return try {
            ringtone = RingtoneManager.getRingtone(context, uri) ?: return false
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(if (category in ALWAYS_AUDIBLE) AudioAttributes.USAGE_ALARM
                          else AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
            SystemClock.sleep(150L)
            val deadline = SystemClock.elapsedRealtime() + 30_000L
            while (running.get() && !Thread.currentThread().isInterrupted &&
                   ringtone.isPlaying && SystemClock.elapsedRealtime() < deadline) {
                sleepInterruptibly(100L)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "A választott értesítési hang nem játszható le", e)
            false
        } finally {
            try { ringtone?.stop() } catch (_: Exception) {}
        }
    }

    fun preview(context: Context, preset: AlertSoundPreset) {
        if (!AlertSoundSettingsStore.shouldPlay(context, force = true)) return
        ensureAlarmAudible(context)
        Thread({
            playToneSequenceSync(context, effectiveSequence(preset), force = true)
            tryPlaySystemRingtone(context, preset)
        }, "SuperDL-AlertPreview").start()
    }

    private fun effectiveSequence(preset: AlertSoundPreset): List<Pair<Int, Int>> =
        preset.toneSequence ?: DEFAULT_ALARM_SEQUENCE

    private fun tryPlaySystemRingtone(context: Context, preset: AlertSoundPreset) {
        if (preset.ringtoneType == null) return
        val uri = resolveUri(context, preset) ?: return
        try {
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                ringtone.streamType = AudioManager.STREAM_ALARM
            }
            ringtone.play()
        } catch (e: Exception) {
            Log.w(TAG, "Rendszer csengő nem játszható le", e)
        }
    }

    private fun ensureAlarmAudible(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val target = (max * AlertSoundSettingsStore.volumeScale(context))
                    .toInt()
                    .coerceIn(1, max)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Ébresztő hangerő beállítás sikertelen", e)
        }
    }

    private fun toneVolume(context: Context?): Int {
        val percent = context?.let { AlertSoundSettingsStore.getVolumePercent(it) } ?: 100
        return (percent * ToneGenerator.MAX_VOLUME / 100).coerceIn(40, ToneGenerator.MAX_VOLUME)
    }

    private fun playToneSequenceSync(
        context: Context?,
        notes: List<Pair<Int, Int>>,
        force: Boolean
    ) {
        if (context != null && !AlertSoundSettingsStore.shouldPlay(context, force)) return
        if (Thread.currentThread().isInterrupted) return
        val volume = toneVolume(context)
        for ((index, note) in notes.withIndex()) {
            if (Thread.currentThread().isInterrupted) return
            if (note.first > 0 && note.second > 0) {
                playAlarmBurst(note.first, note.second, volume)
            } else if (note.second > 0) {
                sleepInterruptibly(note.second.toLong())
            }
            if (index < notes.lastIndex) sleepInterruptibly(60L)
        }
    }

    private fun playAlarmBurst(freq: Int, durationMs: Int, volume: Int) {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, volume)
            val toneType = when {
                freq >= 1200 -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
                freq >= 900 -> ToneGenerator.TONE_PROP_BEEP2
                freq >= 600 -> ToneGenerator.TONE_PROP_BEEP
                else -> ToneGenerator.TONE_PROP_ACK
            }
            tone.startTone(toneType, durationMs.coerceIn(80, 2000))
            sleepInterruptibly((durationMs + 60).toLong())
            tone.release()
        } catch (e: Exception) {
            Log.w(TAG, "Beépített síp lejátszás sikertelen", e)
        }
    }

    private fun sleepInterruptibly(delayMs: Long) {
        if (delayMs <= 0L || Thread.currentThread().isInterrupted) return
        try {
            Thread.sleep(delayMs)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
