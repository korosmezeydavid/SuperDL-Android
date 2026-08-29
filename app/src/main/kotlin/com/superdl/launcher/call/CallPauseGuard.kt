package com.superdl.launcher.call

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log

/**
 * HÍVÁS ALATTI NÉMÍTÁS — egységes megoldás minden lejátszóhoz.
 *
 * MIÉRT KELL: ha csörög a telefon vagy beszélgetsz, a felolvasás és a
 * lejátszás beleszól — ez nemcsak zavaró, hanem használhatatlanná teszi a
 * hívást. Vak felhasználónál különösen: nem tudja gyorsan "megkeresni és
 * lenyomni" a szünet gombot.
 *
 * MŰKÖDÉS:
 *  - CSÖRGÉSKOR és BESZÉLGETÉS közben: azonnal szünet
 *  - A HÍVÁS UTÁN: magától folytatja — DE CSAK AKKOR, ha a hívás előtt
 *    tényleg ment. Ha a felhasználó állította meg, marad állva.
 *
 * Egy helyen van megírva, hogy MINDEN lejátszó ugyanúgy viselkedjen:
 * könyvolvasó, rádió, podcast, YouTube, zene.
 */
class CallPauseGuard(
    context: Context,
    private val isPlaying: () -> Boolean,
    private val onPause: () -> Unit,
    private val onResume: () -> Unit
) {
    private val watcher = CallStateWatcher(context) { state -> handleState(state) }

    /** Mi állítottuk-e meg — csak ilyenkor folytatjuk magunktól. */
    private var pausedByCall = false

    fun register() {
        try {
            watcher.register()
        } catch (e: Exception) {
            Log.w(TAG, "hivas-figyeles inditas hiba: ${e.message}")
        }
    }

    fun unregister() {
        try {
            watcher.unregister()
        } catch (_: Exception) {
        }
        pausedByCall = false
    }

    private fun handleState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING,
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (!pausedByCall && isPlaying()) {
                    pausedByCall = true
                    Log.i(TAG, "hivas -> lejatszas szunetel")
                    onPause()
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (pausedByCall) {
                    pausedByCall = false
                    Log.i(TAG, "hivas vege -> lejatszas folytatodik")
                    onResume()
                }
            }
        }
    }

    companion object {
        private const val TAG = "SDL_CALL"
    }
}
