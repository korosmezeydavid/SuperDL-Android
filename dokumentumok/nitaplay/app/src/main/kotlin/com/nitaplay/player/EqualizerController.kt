package com.nitaplay.player

import android.content.Context
import android.media.audiofx.Equalizer
import android.util.Log
import com.nitaplay.data.AppPreferences

/**
 * Hangszínszabályzó: kész profilok + 5 kézi sáv.
 * Ha az eszköz nem támogatja, gracefully kikapcsol.
 */
class EqualizerController(private val context: Context) {
    private val prefs = AppPreferences(context)
    private var equalizer: Equalizer? = null
    var supported: Boolean = false
        private set

    /** millibel range for sliders */
    var bandLevelRange: ShortArray = shortArrayOf(-1500, 1500)
        private set

    fun attach(audioSessionId: Int) {
        release()
        try {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            supported = true
            bandLevelRange = eq.bandLevelRange
            applySaved()
        } catch (e: Exception) {
            Log.w(TAG, "Equalizer not supported", e)
            equalizer = null
            supported = false
        }
    }

    fun applySaved() {
        val eq = equalizer ?: return
        try {
            if (prefs.useCustomEq) {
                applyCustomBands(prefs.eqBands)
            } else {
                applyProfile(prefs.eqProfile)
            }
        } catch (e: Exception) {
            Log.w(TAG, "applySaved failed", e)
        }
    }

    fun applyProfile(name: String) {
        val eq = equalizer ?: return
        prefs.useCustomEq = false
        prefs.eqProfile = name
        try {
            if (name == AppPreferences.EQ_OFF) {
                eq.enabled = false
                return
            }
            eq.enabled = true
            val gains = profileGains(name)
            applyGainsToBands(eq, gains)
        } catch (e: Exception) {
            Log.w(TAG, "applyProfile failed", e)
        }
    }

    fun applyCustomBands(gainsDb: FloatArray) {
        val eq = equalizer ?: return
        prefs.useCustomEq = true
        prefs.eqBands = gainsDb
        try {
            eq.enabled = true
            applyGainsToBands(eq, gainsDb)
        } catch (e: Exception) {
            Log.w(TAG, "applyCustomBands failed", e)
        }
    }

    fun getBandCount(): Int = try {
        equalizer?.numberOfBands?.toInt() ?: 5
    } catch (_: Exception) {
        5
    }

    fun getCurrentGainsDb(): FloatArray {
        val eq = equalizer ?: return prefs.eqBands
        return try {
            val n = minOf(5, eq.numberOfBands.toInt())
            FloatArray(5) { i ->
                if (i < n) eq.getBandLevel(i.toShort()) / 100f else 0f
            }
        } catch (_: Exception) {
            prefs.eqBands
        }
    }

    private fun applyGainsToBands(eq: Equalizer, gainsDb: FloatArray) {
        val n = minOf(5, eq.numberOfBands.toInt())
        val min = bandLevelRange[0]
        val max = bandLevelRange[1]
        for (i in 0 until n) {
            val mB = (gainsDb.getOrElse(i) { 0f } * 100f).toInt()
                .coerceIn(min.toInt(), max.toInt()).toShort()
            eq.setBandLevel(i.toShort(), mB)
        }
    }

    /** Approximate named profiles as 5-band dB gains */
    private fun profileGains(name: String): FloatArray = when (name) {
        "Normál" -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
        "Pop" -> floatArrayOf(-1.5f, 2.5f, 4f, 2f, -1f)
        "Rock" -> floatArrayOf(4f, 2.5f, -1.5f, 2f, 4f)
        "Jazz" -> floatArrayOf(3f, 1.5f, -1f, 1.5f, 3f)
        "Klasszikus" -> floatArrayOf(4f, 2f, -1f, 2.5f, 3.5f)
        "Basszus-erősítés" -> floatArrayOf(6f, 4f, 1f, 0f, 0f)
        "Beszéd" -> floatArrayOf(-2f, 1f, 4f, 3f, 0f)
        else -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (_: Exception) {
        }
        equalizer = null
        supported = false
    }

    companion object {
        private const val TAG = "NitaPlay.EQ"
    }
}
