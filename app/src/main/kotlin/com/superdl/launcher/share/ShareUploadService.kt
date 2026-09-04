package com.superdl.launcher.share

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.superdl.launcher.MainActivity
import com.superdl.launcher.patrol.PatrolAnnouncer
import java.io.File

/**
 * A FELTÖLTÉST ÉLETBEN TARTÓ SZOLGÁLTATÁS.
 *
 * MIÉRT NEM ELÉG EGY SZÁL AZ ABLAKBAN: egy négygigás fájl feltöltése percekig
 * tart. Közben a képernyő elalszik, a felhasználó félreteszi a telefont — és
 * az Android az ablakkal együtt a szálat is megállítja. A felhasználó annyit
 * tapasztalna, hogy „elindult, aztán semmi".
 *
 * Ugyanaz a két ébrentartó kell, mint a WiFi portálnál: a rádió és a
 * PROCESSZOR is maradjon ébren, különben az átvitel a felénél megáll.
 */
class ShareUploadService : Service() {

    companion object {
        const val EXTRA_PATH = "share_path"
        const val EXTRA_TARGET = "share_target"
        const val ACTION_CANCEL = "com.superdl.launcher.share.UPLOAD_CANCEL"

        private const val CHANNEL_ID = "SHARE_UPLOAD_CHANNEL"
        private const val NOTIFICATION_ID = 8802

        /** Négy óra bőven elég bármilyen feltöltéshez; ennél tovább nem tartjuk ébren a gépet. */
        private const val WAKE_LOCK_TIMEOUT_MS = 4 * 60 * 60 * 1000L

        /** A megnyitott ablak feliratkozik ide; ha nincs ablak, a hang megy a bemondón. */
        @Volatile
        var listener: Listener? = null

        @Volatile
        var runningFileName: String = ""
            private set

        @Volatile
        var lastPercent: Int = 0
            private set

        val isRunning: Boolean get() = runningFileName.isNotBlank()

        interface Listener {
            fun onProgress(pct: Int)
            fun onDone(result: CloudUploader.Result, entry: ShareEntry?)
        }

        fun start(context: Context, file: File, target: CloudTarget) {
            val intent = Intent(context, ShareUploadService::class.java).apply {
                putExtra(EXTRA_PATH, file.absolutePath)
                putExtra(EXTRA_TARGET, target.id)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            CloudUploader.cancel()
            context.startService(
                Intent(context, ShareUploadService::class.java).apply { action = ACTION_CANCEL }
            )
        }
    }

    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            CloudUploader.cancel()
            return START_NOT_STICKY
        }
        val path = intent?.getStringExtra(EXTRA_PATH)
        val targetId = intent?.getStringExtra(EXTRA_TARGET)
        val file = path?.let { File(it) }
        val target = targetId?.let { CloudTargets.byId(it) }
        if (file == null || !file.isFile || target == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (isRunning) {
            // Egyszerre EGY feltöltés. Kettő párhuzamosan vakon
            // követhetetlen lenne, és a haladás-bemondások egymásra beszélnének.
            PatrolAnnouncer.announce(
                applicationContext,
                "Már fut egy feltöltés: $runningFileName. Várd meg, vagy szakítsd meg.",
                withBeep = false,
                softChime = false
            )
            return START_NOT_STICKY
        }

        runningFileName = file.name
        lastPercent = 0
        startForeground(NOTIFICATION_ID, buildNotification(file.name, 0))
        acquireLocks()

        Thread {
            val result = CloudUploader.upload(file, target) { pct ->
                lastPercent = pct
                listener?.onProgress(pct)
                if (pct % 10 == 0) updateNotification(file.name, pct)
            }
            val entry = if (result.ok) {
                ShareEntry(
                    id = System.currentTimeMillis().toString(),
                    fileName = file.name,
                    sizeBytes = file.length(),
                    providerId = target.id,
                    providerName = target.name,
                    spokenProvider = target.spokenName,
                    url = result.url,
                    deleteToken = result.deleteToken,
                    uploadedAt = System.currentTimeMillis(),
                    expiresAt = result.expiresAtMillis,
                    oneTime = target.oneTimeDownload
                ).also { ShareHistoryStore.add(applicationContext, it) }
            } else {
                null
            }
            if (result.ok) copyToClipboard(result.url)
            val l = listener
            if (l != null) {
                l.onDone(result, entry)
            } else {
                // Nincs nyitva az ablak — akkor is meg kell tudnia, mi lett.
                PatrolAnnouncer.announce(
                    applicationContext, result.message, withBeep = false, softChime = result.ok
                )
            }
            runningFileName = ""
            releaseLocks()
            stopForegroundCompat()
            stopSelf()
        }.start()
        return START_NOT_STICKY
    }

    /**
     * A LINK A VÁGÓLAPRA — és ezt a szolgáltatás teszi meg, nem az ablak.
     *
     * MIÉRT ITT: a felhasználó kérése az volt, hogy a link AZONNAL a
     * vágólapon legyen. Ha az ablakra bíznánk, és közben más appra váltott,
     * a link elveszne, és a feltöltésnek nem lenne értelme.
     */
    private fun copyToClipboard(url: String) {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("SuperDL megosztás", url))
        } catch (_: Exception) {
        }
    }

    private fun acquireLocks() {
        try {
            val pm = applicationContext.getSystemService(Context.POWER_SERVICE)
                as android.os.PowerManager
            wakeLock = pm.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK, "SuperDL:ShareUpload"
            ).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        } catch (_: Exception) {
        }
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE)
                as android.net.wifi.WifiManager
            wifiLock = wm.createWifiLock(
                android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SuperDL:ShareUpload"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        wakeLock = null
        try {
            wifiLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        wifiLock = null
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        releaseLocks()
        runningFileName = ""
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Fájl feltöltése",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Fájl feltöltése ideiglenes tárhelyre"
                    setShowBadge(false)
                }
            )
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(name: String, percent: Int): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Feltöltés: $name")
            .setContentText("$percent százalék")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, false)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(name: String, percent: Int) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(name, percent))
        } catch (_: Exception) {
        }
    }
}
