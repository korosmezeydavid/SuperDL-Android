package com.superdl.launcher.telegram

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Rövid, helyi Opus/OGG hangüzenet (régebbi Androidon M4A tartalék). */
internal class TelegramVoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAt = 0L
    val isRecording: Boolean get() = recorder != null

    fun start(): Boolean {
        if (recorder != null) return false
        val dir = File(context.cacheDir, "telegram_voice").apply { mkdirs() }
        val opus = Build.VERSION.SDK_INT >= 29
        val file = File.createTempFile("voice_", if (opus) ".ogg" else ".m4a", dir)
        val candidate = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context)
            else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            candidate.setAudioSource(MediaRecorder.AudioSource.MIC)
            candidate.setOutputFormat(if (opus) MediaRecorder.OutputFormat.OGG
                else MediaRecorder.OutputFormat.MPEG_4)
            candidate.setAudioEncoder(if (opus) MediaRecorder.AudioEncoder.OPUS
                else MediaRecorder.AudioEncoder.AAC)
            candidate.setAudioChannels(1)
            candidate.setAudioEncodingBitRate(if (opus) 32000 else 96000)
            candidate.setAudioSamplingRate(if (opus) 48000 else 44100)
            candidate.setOutputFile(file.absolutePath)
            candidate.prepare()
            candidate.start()
            output = file
            recorder = candidate
            startedAt = System.currentTimeMillis()
            true
        } catch (_: Exception) {
            try { candidate.release() } catch (_: Exception) { }
            file.delete()
            false
        }
    }

    fun stop(): Pair<File, Int>? {
        val current = recorder ?: return null
        val file = output
        recorder = null
        output = null
        return try {
            current.stop()
            current.release()
            if (file == null || file.length() == 0L) { file?.delete(); null }
            else file to ((System.currentTimeMillis() - startedAt) / 1000L).toInt().coerceAtLeast(1)
        } catch (_: Exception) {
            try { current.release() } catch (_: Exception) { }
            file?.delete()
            null
        }
    }

    fun cancel() {
        val current = recorder
        recorder = null
        try { current?.stop() } catch (_: Exception) { }
        try { current?.release() } catch (_: Exception) { }
        output?.delete()
        output = null
    }
}
