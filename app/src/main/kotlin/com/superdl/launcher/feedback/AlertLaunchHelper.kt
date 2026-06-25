package com.superdl.launcher.feedback

import android.util.Log

/**
 * Fokozatos riasztás-indítás: először könnyű beep-beep, majd késleltetve a teljes menü.
 * Csökkenti az egyidejű terhelést (FGS + Activity + TTS), ami befagyást okozhat.
 * Hiba esetén azonnali tartalék-indítás – csendes meghibásodás tilos.
 */
object AlertLaunchHelper {

    private const val TAG = "AlertLaunchHelper"

    fun launchStaged(
        onAfterWakeBeep: () -> Unit,
        onShowAlertUi: () -> Unit,
        onComplete: () -> Unit = {}
    ) {
        Thread(
            {
                try {
                    AlertSoundPlayer.playAlertWakeBeep()
                    Thread.sleep(AlertSoundPlayer.DELAY_AFTER_WAKE_BEEP_MS)
                    onAfterWakeBeep()
                    Thread.sleep(AlertSoundPlayer.DELAY_BEFORE_ALERT_UI_MS)
                    onShowAlertUi()
                } catch (e: Exception) {
                    Log.e(TAG, "Fokozatos riasztás meghiúsult, tartalék-indítás", e)
                    launchImmediateFallback(onAfterWakeBeep, onShowAlertUi)
                } finally {
                    onComplete()
                }
            },
            "SuperDL-AlertLaunch"
        ).start()
    }

    private fun launchImmediateFallback(
        onAfterWakeBeep: () -> Unit,
        onShowAlertUi: () -> Unit
    ) {
        try {
            AlertSoundPlayer.playAlertWakeBeep()
        } catch (e: Exception) {
            Log.e(TAG, "Tartalék beep sem sikerült", e)
        }
        try {
            onAfterWakeBeep()
        } catch (e: Exception) {
            Log.e(TAG, "Tartalék szolgáltatás-indítás sikertelen", e)
        }
        try {
            onShowAlertUi()
        } catch (e: Exception) {
            Log.e(TAG, "Tartalék UI-indítás sikertelen", e)
        }
    }
}