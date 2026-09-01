package com.superdl.launcher.radio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Az időzített felvétel ébresztője.
 *
 * MIÉRT NEM ITT VESZÜNK FEL: egy BroadcastReceiver néhány másodpercet kap,
 * utána a rendszer megölheti. Egy félórás felvétel ide nem fér bele. Ezért a
 * receiver csak ELINDÍTJA az előtér-szolgáltatást, ami már futhat sokáig.
 */
class RadioScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        try {
            when (intent?.action) {
                ACTION_START -> {
                    val id = intent.getIntExtra(EXTRA_ID, -1)
                    if (id < 0) return
                    val entry = RadioScheduleStore.get(context, id) ?: return
                    if (!entry.enabled) return
                    startService(context, id)
                    // A KÖVETKEZŐ ALKALOM AZONNAL: ha ezt a felvétel végére
                    // hagynánk, egy közben megölt szolgáltatás után az egész
                    // ismétlődés némán megszűnne.
                    RadioScheduleScheduler.scheduleNextOccurrence(context, entry)
                }
                // Újraindítás után az ébresztők elvesznek — újra be kell tenni őket.
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                "android.intent.action.QUICKBOOT_POWERON" ->
                    RadioScheduleScheduler.rescheduleAll(context)
            }
        } catch (e: Exception) {
            Log.w(TAG, "onReceive hiba: ${e.message}")
        }
    }

    private fun startService(context: Context, id: Int) {
        val serviceIntent = Intent(context, RadioRecordService::class.java).apply {
            putExtra(RadioRecordService.EXTRA_ID, id)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    companion object {
        private const val TAG = "SuperDL.RadioSched"
        const val ACTION_START = "com.superdl.launcher.RADIO_SCHEDULE_START"
        const val EXTRA_ID = "schedule_id"
    }
}
