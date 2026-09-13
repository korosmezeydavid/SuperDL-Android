package com.superdl.launcher.home

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/**
 * A NAPI ELLENŐRZÉS ÜTEMEZÉSE.
 *
 * EZ NEM GEOKERÍTÉS. Egy geokerítés folyamatosan figyel, háttér-helyzet
 * engedélyt kér, és eszi az akkumulátort. Itt viszont egy HATÁRIDŐ van, ami
 * ELLENŐRIZ: naponta egyszer, a megadott percben felébred, körülnéz pár
 * másodpercre, és utána megint alszik. Ezért nem kell háttér-helyzet engedély,
 * nem kell folyamatosan futó szolgáltatás, és nem kell a Play Services sem.
 */
object HomeWatchScheduler {

    private const val TAG = "SDL_OTTHON"
    private const val REQUEST = 80001

    const val ACTION_FIRE = "com.superdl.launcher.home.HOME_WATCH_FIRE"

    fun reschedule(context: Context) {
        cancel(context)
        if (!HomeWatchSettings.isEnabled(context)) return
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val at = nextTriggerMillis(context)
        try {
            if (canScheduleExact(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Pontos ébresztő nélkül is ütemezünk. Egy késve elvégzett
                // ellenőrzés jobb, mint az elmaradt.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context))
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, at, pendingIntent(context))
            }
            Log.i(TAG, "otthon-ellenorzes utemezve: $at")
        } catch (e: Exception) {
            Log.w(TAG, "utemezes hiba: ${e.message}")
        }
    }

    fun cancel(context: Context) {
        try {
            context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
        } catch (e: Exception) {
            Log.w(TAG, "utemezes torles hiba: ${e.message}")
        }
    }

    fun nextTriggerMillis(context: Context): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, HomeWatchSettings.hour(context))
            set(Calendar.MINUTE, HomeWatchSettings.minute(context))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun canScheduleExact(context: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        } else {
            true
        }
    } catch (_: Exception) {
        true
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, HomeWatchReceiver::class.java).apply { action = ACTION_FIRE }
        return PendingIntent.getBroadcast(
            context, REQUEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/**
 * AMIKOR ELJÖTT AZ IDŐ.
 *
 * A fogadó NEM dolgozik: elindítja az előtér-szolgáltatást, és BEÜTEMEZI A
 * KÖVETKEZŐ NAPOT. A következő nap beütemezése azért van itt, a munka ELŐTT,
 * mert ha bármi elszáll a szolgáltatásban, a holnapi ellenőrzés akkor is
 * álljon készen.
 */
class HomeWatchReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HomeWatchScheduler.ACTION_FIRE) return
        val app = context.applicationContext

        try {
            HomeWatchScheduler.reschedule(app)
        } catch (t: Throwable) {
            Log.w("SDL_OTTHON", "kovetkezo nap utemezese hiba: ${t.message}")
        }

        if (!HomeWatchSettings.isEnabled(app)) return

        try {
            val service = Intent(app, HomeWatchService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(service)
            } else {
                app.startService(service)
            }
        } catch (t: Throwable) {
            Log.w("SDL_OTTHON", "ellenorzes inditas hiba: ${t.message}")
        }
    }
}
