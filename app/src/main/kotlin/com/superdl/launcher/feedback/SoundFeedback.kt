package com.superdl.launcher.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.util.EnumMap

class SoundFeedback(context: Context) {

    private val appContext = context.applicationContext
    private val loaded = EnumMap<SoundType, Boolean>(SoundType::class.java)
    private val soundPool: SoundPool
    private val soundIds = EnumMap<SoundType, Int>(SoundType::class.java)

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(attrs)
            .build()
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                soundIds.entries.firstOrNull { it.value == sampleId }?.key?.let {
                    loaded[it] = true
                }
            }
        }
        SoundType.entries.forEach { type ->
            loaded[type] = false
            soundIds[type] = soundPool.load(appContext, type.resId, 1)
        }
    }

    fun play(type: SoundType, volume: Float = 1f) {
        val id = soundIds[type] ?: return
        if (loaded[type] != true) return
        soundPool.play(id, volume, volume, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }
}