package com.superdl.launcher.voicetheme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar

/**
 * A KÖSZÖNÉS MEGSZÓLALTATÁSA, majd újraütemezés holnapra.
 *
 * FONTOS RÉSZLET — AZ ÉJSZAKAI CSEND ITT NEM ÉRVÉNYES:
 * a „jó éjszakát" természeténél fogva éjszakai. Ha az őrség csendjére
 * hallgatna, a funkció bekapcsolva is néma maradna, és senki nem értené,
 * miért. A napi keret sem vonatkozik rá: a felhasználó erre az egy
 * mondatra kifejezetten időpontot állított.
 */
class GreetingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val which = intent.getStringExtra(GreetingScheduler.EXTRA_WHICH)
        try {
            when (which) {
                GreetingScheduler.WHICH_MORNING -> {
                    if (VoiceThemeStore.isMorningEnabled(context)) {
                        VoiceThemePlayer.announce(
                            context, VoiceEvent.MORNING, countsAgainstQuota = false
                        )
                        VoiceThemeStore.setMorningLastDay(
                            context, Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                        )
                    }
                }
                GreetingScheduler.WHICH_NIGHT -> {
                    if (VoiceThemeStore.isNightEnabled(context)) {
                        VoiceThemePlayer.announce(
                            context, VoiceEvent.NIGHT, countsAgainstQuota = false
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SDL_VOICETHEME", "koszones hiba: ${e.message}")
        }
        // Holnapra újra — az ébresztő egyszeri, magától nem ismétlődik.
        try {
            which?.let { GreetingScheduler.schedule(context, it) }
        } catch (_: Exception) {
        }
    }
}
