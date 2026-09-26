package com.superdl.launcher.calendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import com.superdl.launcher.feedback.AlertLaunchHelper

class CalendarAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CALENDAR_ALARM = "com.superdl.launcher.CALENDAR_ALARM"
        const val EXTRA_EVENT_ID = "calendar_event_id"
        const val EXTRA_TITLE = "calendar_title"
        const val EXTRA_BEGIN_MS = "calendar_begin_ms"
        const val EXTRA_END_MS = "calendar_end_ms"
        const val EXTRA_TRIGGER_MS = "calendar_trigger_ms"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CALENDAR_ALARM) return
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        if (eventId < 0L) return

        val appContext = context.applicationContext
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val beginMs = intent.getLongExtra(EXTRA_BEGIN_MS, 0L)
        val endMs = intent.getLongExtra(EXTRA_END_MS, 0L)

        // MIÉRT: a riasztás a beütemezéskori állapotot hordozza. Ha a programot
        // azóta törölték vagy áthelyezték, a régi riasztás mégis megszólalt,
        // rossz időben. Előbb továbbgörgetjük az ütemezést (az új időpont így
        // biztosan beáll), aztán az elavult riasztást csendben eldobjuk.
        try {
            CalendarReminderScheduler.rescheduleUpcoming(appContext)
        } catch (_: Exception) {
        }
        if (isStaleInstance(appContext, eventId, beginMs, title)) return

        val pendingResult = goAsync()
        val wakeLock = acquireWakeLock(appContext)

        AlertLaunchHelper.launchStaged(
            onAfterWakeBeep = {
                val serviceIntent = Intent(appContext, CalendarAlarmService::class.java).apply {
                    putExtra(CalendarAlarmService.EXTRA_TITLE, title)
                    putExtra(EXTRA_EVENT_ID, eventId)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_BEGIN_MS, beginMs)
                    putExtra(EXTRA_END_MS, endMs)
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        appContext.startForegroundService(serviceIntent)
                    } else {
                        appContext.startService(serviceIntent)
                    }
                } catch (_: Exception) {
                }
            },
            onShowAlertUi = {
                val launch = Intent(appContext, CalendarAlertActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_EVENT_ID, eventId)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_BEGIN_MS, beginMs)
                    putExtra(EXTRA_END_MS, endMs)
                }
                try {
                    appContext.startActivity(launch)
                } catch (_: Exception) {
                }
            },
            onComplete = {
                releaseWakeLock(wakeLock)
                pendingResult.finish()
            }
        )
    }

    private fun isStaleInstance(context: Context, eventId: Long, beginMs: Long, title: String): Boolean {
        // Engedély vagy adat nélkül nem tudjuk ellenőrizni: inkább szóljon.
        if (beginMs <= 0L || title.isBlank()) return false
        if (!CalendarHelper.hasReadPermission(context)) return false
        return try {
            CalendarHelper.getInstancesBetween(context, beginMs, beginMs + 1L)
                .none { it.eventId == eventId && it.begin == beginMs }
        } catch (_: Exception) {
            false
        }
    }

    private fun acquireWakeLock(context: Context): PowerManager.WakeLock? =
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SuperDL:CalendarAlarm"
            ).apply {
                setReferenceCounted(false)
                acquire(90_000L)
            }
        } catch (_: Exception) {
            null
        }

    private fun releaseWakeLock(wakeLock: PowerManager.WakeLock?) {
        try {
            if (wakeLock?.isHeld == true) wakeLock.release()
        } catch (_: Exception) {
        }
    }
}