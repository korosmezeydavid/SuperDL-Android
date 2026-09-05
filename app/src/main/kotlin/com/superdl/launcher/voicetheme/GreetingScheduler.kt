package com.superdl.launcher.voicetheme

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/**
 * A REGGELI ÉS ESTI KÖSZÖNÉS IDŐZÍTÉSE.
 *
 * MIÉRT KÜLÖN ÉBRESZTŐ ÉS NEM AZ ŐRSÉG PERCES CIKLUSA: az őrség csak akkor
 * fut, ha be van kapcsolva, és a perces ciklus altatás alatt csúszik. Egy
 * köszönés, ami néha elmarad, rosszabb, mint a semmi — mert a felhasználó
 * nem tudja eldönteni, hogy elromlott-e.
 *
 * A pontos ébresztő engedélyét a beállítás varázsló amúgy is kéri. Ha
 * mégsem lenne meg, pontatlan ébresztőre esünk vissza: néhány perc csúszás
 * egy köszönésnél nem tragédia, a NÉMASÁG viszont az lenne.
 */
object GreetingScheduler {

    private const val TAG = "SDL_VOICETHEME"

    const val EXTRA_WHICH = "which"
    const val WHICH_MORNING = "morning"
    const val WHICH_NIGHT = "night"

    private const val REQUEST_MORNING = 8801
    private const val REQUEST_NIGHT = 8802

    /** Mindkét köszönés újraütemezése a mai beállítások szerint. */
    fun rescheduleAll(context: Context) {
        schedule(context, WHICH_MORNING)
        schedule(context, WHICH_NIGHT)
    }

    fun schedule(context: Context, which: String) {
        val app = context.applicationContext
        val manager = app.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(app, which)

        val enabled = when (which) {
            WHICH_MORNING ->
                VoiceThemeStore.isEnabled(app) &&
                    VoiceThemeStore.isMorningEnabled(app) &&
                    // Feloldásra várunk, nem órára — nem kell ébresztő.
                    !VoiceThemeStore.isMorningOnUnlock(app)
            WHICH_NIGHT ->
                VoiceThemeStore.isEnabled(app) && VoiceThemeStore.isNightEnabled(app)
            else -> false
        }
        if (!enabled) {
            try {
                manager.cancel(pending)
            } catch (_: Exception) {
            }
            return
        }

        val minutes = if (which == WHICH_MORNING) {
            VoiceThemeStore.getMorningMinutes(app)
        } else {
            VoiceThemeStore.getNightMinutes(app)
        }
        val triggerAt = nextOccurrence(minutes)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    manager.canScheduleExactAlarms()
                if (exact) {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                } else {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                }
            } else {
                @Suppress("DEPRECATION")
                manager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            Log.i(TAG, "koszones utemezve: $which")
        } catch (e: Exception) {
            Log.w(TAG, "utemezes hiba ($which): ${e.message}")
        }
    }

    private fun pendingIntent(context: Context, which: String): PendingIntent {
        val intent = Intent(context, GreetingReceiver::class.java).apply {
            putExtra(EXTRA_WHICH, which)
        }
        val request = if (which == WHICH_MORNING) REQUEST_MORNING else REQUEST_NIGHT
        return PendingIntent.getBroadcast(
            context,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** A következő ilyen időpont: ma, ha még hátravan, különben holnap. */
    private fun nextOccurrence(totalMinutes: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, totalMinutes / 60)
            set(Calendar.MINUTE, totalMinutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }
}
