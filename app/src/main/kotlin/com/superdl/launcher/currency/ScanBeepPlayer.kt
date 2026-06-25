package com.superdl.launcher.currency

import android.media.AudioManager
import android.media.ToneGenerator
import java.io.Closeable

class ScanBeepPlayer : Closeable {

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)

    fun playScanStart() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 130)
    }

    override fun close() {
        toneGenerator.release()
    }
}