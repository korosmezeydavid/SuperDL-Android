package com.superdl.launcher.sms

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

class SmsInboundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val source = intent.getStringExtra(EXTRA_SOURCE).orEmpty().ifBlank { "service" }
        val wakeLock = acquireWakeLock(this)
        val stored = try {
            SmsInboundHandler.handleIntent(applicationContext, Intent(intent), source)
        } catch (e: Exception) {
            SmsDebugLog.append(this, "SMS feldolgozás sikertelen ($source): ${e.message}")
            false
        }
        // A hang saját szálon szól. Ha a Service azonnal leáll, a háttérben
        // indított folyamat az értesítőhang vége előtt megszűnhet.
        Handler(Looper.getMainLooper()).postDelayed({
            releaseWakeLock(wakeLock)
            stopSelf(startId)
        }, if (stored) 10_000L else 0L)
        return START_NOT_STICKY
    }

    companion object {
        const val EXTRA_SOURCE = "sms_inbound_source"

        fun start(context: Context, intent: Intent, source: String) {
            val launch = Intent(context, SmsInboundService::class.java).apply {
                putExtra(EXTRA_SOURCE, source)
                intent.extras?.let { putExtras(it) }
                action = intent.action
                if (intent.data != null) data = intent.data
                intent.type?.let { type = it }
            }
            try {
                context.applicationContext.startService(launch)
            } catch (e: Exception) {
                SmsDebugLog.append(context, "Service indítás sikertelen ($source): ${e.message}")
                SmsInboundHandler.handleIntent(context.applicationContext, intent, "$source-sync")
            }
        }

        private fun acquireWakeLock(context: Context): PowerManager.WakeLock? =
            try {
                val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SuperDL:SmsInbound")?.apply {
                    setReferenceCounted(false)
                    acquire(30_000L)
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
}
