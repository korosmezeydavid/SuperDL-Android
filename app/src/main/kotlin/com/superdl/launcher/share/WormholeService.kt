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
import com.superdl.launcher.files.FileManagerHelper
import com.superdl.launcher.patrol.PatrolAnnouncer
import java.io.File

/**
 * GÉPRŐL GÉPRE FÁJLKÜLDÉS — magic-wormhole.
 *
 * Ugyanaz a protokoll, amit a windowsos SuperDL p2p modulja használ, tehát a
 * telefon és a gép EGYMÁSNAK tud küldeni: a küldő kap egy szó-kódot, a fogadó
 * beírja, és a fájl végpontok között titkosítva megy át.
 *
 * AMIT NEM ÍGÉRÜNK TÚL: ha a két készülék nem talál egymásra közvetlenül, az
 * adat egy TOVÁBBÍTÓ szerveren folyik át — titkosítva, de nem közvetlenül.
 * A windowsos súgó pontosan ezt mondja; ugyanezt mondjuk itt is.
 *
 * Előtérben futó szolgáltatás, mert egy nagy fájl percekig megy, és közben a
 * képernyő elalszik.
 */
class WormholeService : Service() {

    companion object {
        const val EXTRA_MODE = "wh_mode"
        const val EXTRA_PATH = "wh_path"
        const val EXTRA_CODE = "wh_code"
        const val MODE_SEND = "send"
        const val MODE_RECEIVE = "receive"
        const val ACTION_CANCEL = "com.superdl.launcher.share.WH_CANCEL"

        private const val CHANNEL_ID = "WORMHOLE_CHANNEL"
        private const val NOTIFICATION_ID = 8803
        private const val WAKE_LOCK_TIMEOUT_MS = 4 * 60 * 60 * 1000L

        @Volatile
        var listener: Listener? = null

        @Volatile
        var currentCode: String = ""
            private set

        @Volatile
        var busyWith: String = ""
            private set

        val isRunning: Boolean get() = busyWith.isNotBlank()

        interface Listener {
            fun onCode(code: String)
            fun onProgress(pct: Int)
            fun onDone(ok: Boolean, message: String)
        }

        /** Ide érkeznek a kóddal fogadott fájlok. */
        fun receiveDir(): File {
            val dir = File(FileManagerHelper.rootDir(), "SuperDL/Fogadott")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        fun send(context: Context, file: File) = launch(context) {
            it.putExtra(EXTRA_MODE, MODE_SEND)
            it.putExtra(EXTRA_PATH, file.absolutePath)
        }

        fun receive(context: Context, code: String) = launch(context) {
            it.putExtra(EXTRA_MODE, MODE_RECEIVE)
            it.putExtra(EXTRA_CODE, code)
        }

        private fun launch(context: Context, fill: (Intent) -> Unit) {
            val intent = Intent(context, WormholeService::class.java).also(fill)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            try {
                whmobile.Whmobile.cancel()
            } catch (_: Throwable) {
            }
            context.startService(
                Intent(context, WormholeService::class.java).apply { action = ACTION_CANCEL }
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
            try {
                whmobile.Whmobile.cancel()
            } catch (_: Throwable) {
            }
            return START_NOT_STICKY
        }
        val mode = intent?.getStringExtra(EXTRA_MODE)
        if (mode == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (isRunning) {
            PatrolAnnouncer.announce(
                applicationContext,
                "Már fut egy átvitel: $busyWith. Várd meg, vagy szakítsd meg.",
                withBeep = false, softChime = false
            )
            return START_NOT_STICKY
        }

        val file = intent.getStringExtra(EXTRA_PATH)?.let { File(it) }
        val code = intent.getStringExtra(EXTRA_CODE).orEmpty().trim()
        if (mode == MODE_SEND && (file == null || !file.isFile)) {
            finishWith(false, "Ez a fájl már nem érhető el.")
            return START_NOT_STICKY
        }
        if (mode == MODE_RECEIVE && code.isBlank()) {
            finishWith(false, "Nem kaptam kódot, ezért nincs mit fogadni.")
            return START_NOT_STICKY
        }

        busyWith = if (mode == MODE_SEND) file!!.name else "fogadás"
        currentCode = ""
        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                if (mode == MODE_SEND) "Küldés: ${file!!.name}" else "Fájl fogadása", 0
            )
        )
        acquireLocks()

        val cb = object : whmobile.Callback {
            override fun onCode(c: String) {
                currentCode = c
                listener?.onCode(c) ?: PatrolAnnouncer.announce(
                    applicationContext, "A küldési kód: $c", withBeep = false, softChime = true
                )
            }

            override fun onProgress(pct: Long) {
                val p = pct.toInt().coerceIn(0, 100)
                if (p % 10 == 0) updateNotification(busyWith, p)
                listener?.onProgress(p)
            }

            override fun onDone(ok: Boolean, message: String) {
                val text = if (ok) {
                    if (mode == MODE_SEND) {
                        "A fájl átment a másik gépre."
                    } else {
                        val name = File(message).name
                        "A fájl megérkezett: $name. Helye: SuperDL, Fogadott mappa."
                    }
                } else {
                    val ok2 = WormholeErrors.friendly(message)
                    if (mode == MODE_SEND) {
                        "A küldés nem fejeződött be." + if (ok2.isNotBlank()) " Ok: $ok2" else ""
                    } else {
                        "A fogadás nem sikerült." + if (ok2.isNotBlank()) " Ok: $ok2" else ""
                    }
                }
                finishWith(ok, text)
            }
        }

        try {
            if (mode == MODE_SEND) {
                whmobile.Whmobile.send(file!!.absolutePath, cb)
            } else {
                whmobile.Whmobile.receive(code, receiveDir().absolutePath, cb)
            }
        } catch (t: Throwable) {
            finishWith(
                false,
                "A gépről gépre küldés nem indult el ezen a telefonon. " +
                    "Ok: ${t.javaClass.simpleName}, ${t.message}"
            )
        }
        return START_NOT_STICKY
    }

    private fun finishWith(ok: Boolean, message: String) {
        val l = listener
        if (l != null) {
            l.onDone(ok, message)
        } else {
            PatrolAnnouncer.announce(
                applicationContext, message, withBeep = false, softChime = ok
            )
        }
        busyWith = ""
        currentCode = ""
        releaseLocks()
        stopForegroundCompat()
        stopSelf()
    }

    private fun acquireLocks() {
        try {
            val pm = applicationContext.getSystemService(Context.POWER_SERVICE)
                as android.os.PowerManager
            wakeLock = pm.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK, "SuperDL:Wormhole"
            ).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        } catch (_: Exception) {
        }
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE)
                as android.net.wifi.WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wm.createWifiLock(
                android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SuperDL:Wormhole"
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
        busyWith = ""
        currentCode = ""
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Fájl gépről gépre", NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Fájlküldés és fogadás kóddal"
                    setShowBadge(false)
                }
            )
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(title: String, percent: Int): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("$percent százalék")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, false)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(title: String, percent: Int) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(title, percent))
        } catch (_: Exception) {
        }
    }
}
