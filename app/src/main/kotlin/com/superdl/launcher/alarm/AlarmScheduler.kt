package com.superdl.launcher.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.superdl.launcher.settings.PermissionGuideTexts
import com.superdl.launcher.settings.PermissionGuideType
import java.util.Calendar

object AlarmScheduler {

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return manager.canScheduleExactAlarms()
    }

    fun exactAlarmGuideSpeech(): String =
        PermissionGuideTexts.sections(PermissionGuideType.EXACT_ALARM)
            .joinToString(" ") { it.body }

    fun schedule(context: Context, entry: AlarmEntry) {
        if (!entry.enabled) return
        val triggerAt = nextTriggerMillis(entry.hour, entry.minute)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, entry.id)
            putExtra(AlarmReceiver.EXTRA_LABEL, entry.label)
            putExtra(AlarmReceiver.EXTRA_HOUR, entry.hour)
            putExtra(AlarmReceiver.EXTRA_MINUTE, entry.minute)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            entry.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setExactAlarm(context, triggerAt, pendingIntent)
    }

    fun setExactAlarm(context: Context, triggerAt: Long, pendingIntent: PendingIntent): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return try {
            if (canScheduleExact(context)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    @Suppress("DEPRECATION")
                    manager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
                true
            } else {
                scheduleInexactAlarm(manager, triggerAt, pendingIntent)
                false
            }
        } catch (_: SecurityException) {
            try {
                scheduleInexactAlarm(manager, triggerAt, pendingIntent)
                false
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun scheduleInexactAlarm(
        manager: AlarmManager,
        triggerAt: Long,
        pendingIntent: PendingIntent
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(context: Context, alarmId: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(pendingIntent)
    }

    fun rescheduleAll(context: Context) {
        AlarmStore.getEnabled(context).forEach { schedule(context, it) }
    }

    fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return cal.timeInMillis
    }

    fun millisUntil(entry: AlarmEntry): Long =
        nextTriggerMillis(entry.hour, entry.minute) - System.currentTimeMillis()
}