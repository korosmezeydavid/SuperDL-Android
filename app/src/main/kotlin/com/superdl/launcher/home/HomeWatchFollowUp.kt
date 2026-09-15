package com.superdl.launcher.home

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * A FRISSÍTŐ ÜZENET ÜTEMEZÉSE.
 *
 * MIÉRT KELL: az első üzenet egyetlen pontot ad a segítőnek. Aki elindul
 * megkeresni, negyedóra múlva már máshol van — és a segítő addigra a régi
 * ponthoz ér. Egy frissítés ezt oldja meg.
 *
 * MIÉRT VAN SZIGORÚ FELSŐ KORLÁT: aki éjjel háromszor kap riasztást ugyanarról
 * az emberről, negyedszerre már nem nézi meg. A védőháló akkor ér valamit, ha
 * a címzett komolyan veszi, tehát nem szabad elhasználni a figyelmét.
 * Ezért legfeljebb `MAX_FOLLOWUPS` frissítés megy ki egy riasztás után.
 */
object HomeWatchFollowUp {

    private const val TAG = "SDL_OTTHON"
    private const val REQUEST = 80002

    const val ACTION_FIRE = "com.superdl.launcher.home.HOME_WATCH_FOLLOWUP"

    /** Beütemezi a következő frissítést, ha még jár belőle. */
    fun schedule(context: Context) {
        val app = context.applicationContext
        if (!HomeWatchSettings.isFollowUpEnabled(app)) return
        if (HomeWatchSettings.followUpsSent(app) >= HomeWatchSettings.MAX_FOLLOWUPS) return
        val manager = app.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + HomeWatchSettings.FOLLOWUP_DELAY_MS
        try {
            if (HomeWatchScheduler.canScheduleExact(app) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            ) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(app))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(app))
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, at, pendingIntent(app))
            }
            Log.i(TAG, "frissites utemezve: $at")
        } catch (e: Exception) {
            Log.w(TAG, "frissites utemezes hiba: ${e.message}")
        }
    }

    /**
     * Lemondja a függő frissítést. Ez akkor kell, ha a felhasználó leállította
     * a riasztást, vagy közben hazaért — ilyenkor a frissítés már csak
     * fölösleges ijedelem lenne.
     */
    fun cancel(context: Context) {
        try {
            context.applicationContext.getSystemService(AlarmManager::class.java)
                ?.cancel(pendingIntent(context.applicationContext))
        } catch (e: Exception) {
            Log.w(TAG, "frissites torles hiba: ${e.message}")
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, HomeWatchFollowUpReceiver::class.java)
            .apply { action = ACTION_FIRE }
        return PendingIntent.getBroadcast(
            context, REQUEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/**
 * AMIKOR ELJÖTT A FRISSÍTÉS IDEJE.
 *
 * A fogadó nem dolgozik: elindítja a szolgáltatást frissítés módban. Az
 * ellenőrzés ott fut le újra — MERT KÖZBEN HAZAÉRHETETT. Ilyenkor nem megy
 * ki semmi, csak a napló jegyzi fel.
 */
class HomeWatchFollowUpReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HomeWatchFollowUp.ACTION_FIRE) return
        val app = context.applicationContext
        if (!HomeWatchSettings.isEnabled(app)) return
        if (!HomeWatchSettings.isFollowUpEnabled(app)) return
        try {
            val service = Intent(app, HomeWatchService::class.java)
                .putExtra(HomeWatchService.EXTRA_FOLLOWUP, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(service)
            } else {
                app.startService(service)
            }
        } catch (t: Throwable) {
            Log.w("SDL_OTTHON", "frissites inditas hiba: ${t.message}")
        }
    }
}
