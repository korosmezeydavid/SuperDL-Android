package com.superdl.launcher.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.superdl.launcher.patrol.PatrolAnnouncer

/**
 * AZ IDŐZÍTETT ÜZENETEK ÜTEMEZÉSE.
 *
 * Ugyanaz a gépezet, ami az ébresztőt és a gyógyszer-emlékeztetőt is hajtja:
 * pontos ébresztő a rendszertől, és újraindítás után visszatesszük mindet.
 *
 * A KÜLDÉS NEM KÉRDEZ. Alph kérése szó szerint: „itt nem kérdezett meg a
 * rendszer, hanem egyszerűen fogta, kiküldte." Ez nem kényelem, hanem a
 * funkció lényege: az üzenetnek akkor is el kell mennie, amikor nem tudsz
 * válaszolni — mert alszol, vagy mert baj van.
 *
 * Ebből következik a fordítottja is: a TÖRLÉS legyen könnyű és ismerhető.
 * Ezért van „Időzített üzeneteim" menüpont, hátralévő idővel.
 */
object ScheduledSmsScheduler {

    private const val TAG = "SDL_SMS"
    private const val REQUEST_BASE = 70000

    fun scheduleAll(context: Context) {
        ScheduledSmsStore.pending(context).forEach { schedule(context, it) }
    }

    fun schedule(context: Context, entry: ScheduledSms) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, entry.id)
        try {
            if (canScheduleExact(context)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    manager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, entry.triggerAt, pi
                    )
                } else {
                    @Suppress("DEPRECATION")
                    manager.setExact(AlarmManager.RTC_WAKEUP, entry.triggerAt, pi)
                }
            } else {
                // PONTOS ÉBRESZTŐ NÉLKÜL IS ÜTEMEZÜNK, csak pontatlanul.
                // Egy késve kiment üzenet jobb, mint egy ki nem ment.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.triggerAt, pi)
                } else {
                    manager.set(AlarmManager.RTC_WAKEUP, entry.triggerAt, pi)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "idozites hiba (${entry.id}): ${e.message}")
        }
    }

    fun cancel(context: Context, id: Int) {
        try {
            val manager = context.getSystemService(AlarmManager::class.java) ?: return
            manager.cancel(pendingIntent(context, id))
        } catch (e: Exception) {
            Log.w(TAG, "idozites torles hiba ($id): ${e.message}")
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
        val intent = Intent(context, ScheduledSmsReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    const val ACTION_FIRE = "com.superdl.launcher.sms.SCHEDULED_SMS_FIRE"
    const val EXTRA_ID = "idozitett_sms_id"
}

/**
 * AMIKOR ELJÖTT AZ IDŐ.
 *
 * MINDEN LÉPÉS KÜLÖN VÉDŐHÁLÓBAN, és `Throwable`, nem `Exception` — ez a
 * program kimondott szabálya, mert egy háttéreseményben eldobott hiba az
 * EGÉSZ programot bezárja.
 */
class ScheduledSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ScheduledSmsScheduler.ACTION_FIRE) return
        val id = intent.getIntExtra(ScheduledSmsScheduler.EXTRA_ID, -1)
        if (id < 0) return

        val pending = goAsync()
        Thread {
            try {
                fire(context, id)
            } catch (t: Throwable) {
                Log.w("SDL_SMS", "idozitett sms hiba ($id): ${t.message}")
                try {
                    ScheduledSmsStore.markDone(context, id, "Váratlan hiba miatt nem ment el.")
                } catch (_: Throwable) {
                }
            } finally {
                try {
                    pending.finish()
                } catch (_: Throwable) {
                }
            }
        }.start()
    }

    private fun fire(context: Context, id: Int) {
        val entry = ScheduledSmsStore.get(context, id) ?: return
        if (!entry.isPending) return

        SmsSendReceiver.clearLastError()
        val report = SmsHelper.sendToMany(context, entry.recipients(), entry.message)

        val outcome = if (report.failedTo.isEmpty()) {
            "Elment."
        } else if (report.sentTo.isEmpty()) {
            "NEM ment el."
        } else {
            "Részben ment el: nem kapta meg ${report.failedTo.joinToString(", ")}."
        }
        ScheduledSmsStore.markDone(context, id, outcome)
        SmsOutcomeStore.note(
            context,
            entry.whoText(),
            if (report.anySent) SmsOutcomeStore.State.SENDING else SmsOutcomeStore.State.FAILED,
            if (report.anySent) null else "az időzített üzenet nem indult el"
        )

        // CSAK AKKOR SZÓLUNK, HA BAJ VAN.
        //
        // A sikeres küldés az elvárt eset — azt bejelenteni hajnali négykor
        // zavaró lenne, és pont az alvást verné fel, amiért az egészet
        // beállították. A KUDARCOT viszont ki KELL mondani, mert a felhasználó
        // abban a hiszemben él, hogy az üzenet elment.
        if (report.failedTo.isNotEmpty()) {
            PatrolAnnouncer.announce(
                context.applicationContext,
                "Figyelem: az időzített üzenet nem ment el neki: " +
                    "${report.failedTo.joinToString(", ")}. ${report.speak()}",
                critical = true
            )
        }
    }
}
