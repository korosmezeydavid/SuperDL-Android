package com.superdl.launcher.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * AZ EMLÉKEZTETŐK ÜTEMEZÉSE.
 *
 * Ugyanaz a gépezet, ami az ébresztőt és az időzített SMS-t is hajtja: pontos
 * ébresztő a rendszertől, és újraindítás után visszatesszük mindet. Enélkül
 * egy éjszakai újraindítás vagy egy Xiaomi-féle háttér-takarítás csendben
 * elnyelné az egészet — és a felhasználó abban a hitben élne, hogy szólni fog.
 */
object LaterReminderScheduler {

    private const val TAG = "SDL_EMLEK"
    private const val REQUEST_BASE = 90000

    const val ACTION_FIRE = "com.superdl.launcher.reminder.LATER_FIRE"
    const val EXTRA_ID = "emlekezteto_id"

    fun scheduleAll(context: Context) {
        LaterReminderStore.all(context).forEach { schedule(context, it) }
    }

    fun schedule(context: Context, entry: LaterReminder) {
        val app = context.applicationContext
        val manager = app.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(app, entry.id)
        try {
            if (canScheduleExact(app) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.dueAt, pi)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Pontos ébresztő nélkül is ütemezünk. Egy késve megszólaló
                // emlékeztető jobb, mint az elmaradt.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.dueAt, pi)
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, entry.dueAt, pi)
            }
        } catch (e: Exception) {
            Log.w(TAG, "utemezes hiba (${entry.id}): ${e.message}")
        }
    }

    fun cancel(context: Context, id: Int) {
        try {
            context.applicationContext.getSystemService(AlarmManager::class.java)
                ?.cancel(pendingIntent(context.applicationContext, id))
        } catch (e: Exception) {
            Log.w(TAG, "torles hiba ($id): ${e.message}")
        }
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

    private fun pendingIntent(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, LaterReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_BASE + id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/**
 * AMIKOR ELJÖTT AZ IDŐ.
 *
 * A fogadó nem dolgozik: kinyitja az emlékeztető képernyőt. Ott lehet
 * azonnal visszahívni vagy halasztani — ez a lényeg. Egy emlékeztető, ami
 * csak bemondja a nevet, és utána újra meg kell keresni a számot, félkész.
 *
 * Minden lépés külön védőhálóban, és `Throwable`, nem `Exception`: egy
 * háttéreseményben eldobott hiba az EGÉSZ programot bezárja.
 */
class LaterReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LaterReminderScheduler.ACTION_FIRE) return
        val id = intent.getIntExtra(LaterReminderScheduler.EXTRA_ID, -1)
        if (id < 0) return
        val app = context.applicationContext

        try {
            val entry = LaterReminderStore.get(app, id) ?: return
            LaterReminderAlertActivity.launch(app, entry)
        } catch (t: Throwable) {
            Log.w("SDL_EMLEK", "emlekezteto hiba ($id): ${t.message}")
        }
    }
}
