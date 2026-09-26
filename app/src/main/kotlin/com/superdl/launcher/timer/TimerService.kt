package com.superdl.launcher.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.superdl.launcher.patrol.PatrolAnnouncer

class TimerService : Service() {

    companion object {
        private const val CHANNEL_ID = "TIMER_CHANNEL"
        private const val NOTIFICATION_ID = 7301
    }

    private val handler = Handler(Looper.getMainLooper())
    private var announcedStart = false
    private var wakeLock: PowerManager.WakeLock? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            checkTimer()
            handler.postDelayed(this, 15_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        handler.post(tickRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = TimerStore.getActiveSession(this)
        if (session == null || session.isFinished()) {
            stopSelf()
            return START_NOT_STICKY
        }
        holdWakeLock(session)
        if (!announcedStart) {
            announcedStart = true
            PatrolAnnouncer.announce(
                this,
                TimerSpeech.speakStarted(
                    session.label,
                    session.durationMinutes,
                    session.announceIntervalMinutes
                )
            )
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        releaseWakeLock()
        super.onDestroy()
    }

    /**
     * MIÉRT: a lejárat Handler.postDelayed-re épül; kikapcsolt képernyőnél a
     * CPU alszik, és a bemondás némán elmarad vagy perceket késik. A zár a
     * hátralévő idő + 2 perc után magától is elenged.
     */
    private fun holdWakeLock(session: ActiveTimerSession) {
        try {
            val lock = wakeLock ?: (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SuperDL:Timer")
                .apply { setReferenceCounted(false) }
                .also { wakeLock = it }
            lock.acquire(session.remainingMinutes() * 60_000L + 2 * 60_000L)
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun checkTimer() {
        val session = TimerStore.getActiveSession(this) ?: run {
            stopSelf()
            return
        }
        if (session.isFinished()) {
            finishTimer(session)
            return
        }

        val elapsed = session.elapsedMinutes()
        val remaining = session.remainingMinutes()
        val interval = session.announceIntervalMinutes.coerceAtLeast(1)

        if (elapsed > 0 &&
            elapsed > session.lastAnnouncedElapsedMinutes &&
            elapsed % interval == 0
        ) {
            TimerStore.updateLastAnnounced(this, elapsed)
            PatrolAnnouncer.announce(
                this,
                TimerSpeech.speakProgress(session.label, elapsed, remaining)
            )
        }
    }

    private fun finishTimer(session: ActiveTimerSession) {
        PatrolAnnouncer.announce(this, TimerSpeech.speakFinished(session.label)) {
            TimerStore.clearActiveSession(this@TimerService)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Időzítő",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mentett időzítők futása"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val session = TimerStore.getActiveSession(this)
        val title = session?.label?.ifBlank { "Időzítő" } ?: "Időzítő"
        val text = session?.let {
            "Hátra: ${TimerSpeech.speakMinutes(it.remainingMinutes())}"
        } ?: "Futó időzítő"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}