package com.superdl.launcher.catalog

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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.superdl.launcher.patrol.PatrolAnnouncer

/**
 * A KATALÓGUS-MODULOK LETÖLTÉSE — ELŐTÉR-SZOLGÁLTATÁSBAN.
 *
 * A HIBA, AMI EZT KIKÉNYSZERÍTETTE (szanyuka1975, Xiaomi M2103K19G,
 * Android 13, 1.63.8): „a játékok nem töltöttek le". A szerveren minden
 * rendben volt — mind a 31 modul HTTP 200-zal felelt.
 *
 * A LETÖLTÉS EDDIG NYERS `Thread`-BEN FUTOTT AZ ACTIVITY-BEN, és az eredmény
 * a `postWhenAlive`-on ment át:
 *
 *     mainHandler.post {
 *         if (isFinishing || isDestroyed) return@post      // ← ITT VESZETT EL
 *         block()
 *     }
 *
 * Ha az Activity a letöltés vége előtt eltűnt — a felhasználó kilépett, vagy
 * a MIUI leállította a programot —, a `return@post` ELDOBTA AZ EGÉSZ
 * EREDMÉNYT: nincs beszéd, nincs napló, nincs újrapróbálás. A fájl akár le is
 * jöhetett a lemezre; a felhasználó soha nem tudta meg.
 *
 * És épp ennél a telefonnál állt élesben a feltétel: a jelentésében a
 * „Korlátlan háttérfutás" tétele `kihagyva` volt, két próbálkozás után.
 * Xiaomin enélkül a processz háttérbe kerülve leáll.
 *
 * EZ VOLT A KATALÓGUS AZ EGYETLEN HÁLÓZATI FUNKCIÓ SZOLGÁLTATÁS NÉLKÜL. A
 * programban tucatnyi `startForegroundService` van (YouTube, időzítő,
 * megosztás, diktafon, rádiófelvétel) — a modul-letöltés maradt ki.
 *
 * MIÉRT NEM WorkManager: a projektben egyetlen sor WorkManager sincs, és egy
 * új függőség új kockázat (lásd az 1.63.6 összeomlását, amit egy régi
 * támogató könyvtár okozott). Az előtér-szolgáltatás a ház bevált mintája.
 *
 * MIÉRT A `PatrolAnnouncer` MONDJA BE AZ EREDMÉNYT: mert az akkor is
 * megszólal, amikor a felhasználó már nincs a programban — saját ébrentartó
 * zárral, saját beszédmotorral. Pontosan az az eset, ami eddig néma volt.
 */
class CatalogDownloadService : Service() {

    companion object {
        private const val TAG = "SDL_CATALOG"
        private const val CHANNEL_ID = "CATALOG_DOWNLOAD_CHANNEL"
        private const val NOTIFICATION_ID = 8815

        /**
         * A HOSSZÚ CSEND HATÁRA. Ennyi után szólunk, hogy még dolgozunk.
         *
         * MIÉRT KELL: a hálózati időtúllépés húsz másodperc. Addig vakon a
         * csend és a lefagyás megkülönböztethetetlen — és a felhasználó
         * joggal lép ki, amivel régen maga okozta, hogy az eredményt már ne
         * hallja meg.
         */
        private const val STILL_WORKING_MS = 7_000L

        private const val EXTRA_ID = "modul_id"
        private const val EXTRA_NAME = "modul_nev"
        private const val EXTRA_DESC = "modul_leiras"
        private const val EXTRA_PATH = "modul_fajl"
        private const val EXTRA_VERSION = "modul_verzio"
        private const val EXTRA_TYPE = "modul_tipus"
        private const val EXTRA_MIN_APP = "modul_min_app"
        private const val EXTRA_CATEGORY = "modul_kategoria"
        private const val EXTRA_AUTHOR = "modul_szerzo"
        private const val EXTRA_SIZE = "modul_meret"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context, module: CatalogModule) {
            val intent = Intent(context, CatalogDownloadService::class.java).apply {
                putExtra(EXTRA_ID, module.id)
                putExtra(EXTRA_NAME, module.name)
                putExtra(EXTRA_DESC, module.description)
                putExtra(EXTRA_PATH, module.filePath)
                putExtra(EXTRA_VERSION, module.version)
                putExtra(EXTRA_TYPE, module.type.key)
                putExtra(EXTRA_MIN_APP, module.minAppVersion)
                putExtra(EXTRA_CATEGORY, module.categoryId)
                putExtra(EXTRA_AUTHOR, module.author)
                putExtra(EXTRA_SIZE, module.sizeBytes)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Ha a szolgáltatás nem indítható (pl. a rendszer épp tiltja),
                // AKKOR IS mondjuk meg — a néma kudarc a legrosszabb kimenet.
                Log.w(TAG, "letolto szolgaltatas nem indithato: ${e.message}")
                PatrolAnnouncer.announce(
                    context.applicationContext,
                    "A letöltés nem indult el. Nyisd meg a programot, és próbáld újra.",
                    critical = true
                )
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val module = intent?.let { readModule(it) }
        if (module == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // ELŐTÉRBE AZONNAL. A rendszer néhány másodpercet ad rá; ha csak a
        // letöltés után hívnánk meg, a szolgáltatás elhasalna.
        startForegroundSafely(buildNotification("Letöltés: ${module.name}"))
        isRunning = true

        val stillWorking = Runnable {
            if (isRunning) {
                PatrolAnnouncer.announce(
                    applicationContext,
                    "Még töltöm: ${module.name}. Várj.",
                    withBeep = false,
                    critical = true
                )
            }
        }
        handler.postDelayed(stillWorking, STILL_WORKING_MS)

        worker = Thread {
            val error = try {
                CatalogClient.downloadModule(this, module)
            } catch (t: Throwable) {
                // Throwable, nem Exception: egy háttérlépésben eldobott Error
                // ugyanúgy némaságot csinálna, és ez a program egyik
                // kimondott szabálya.
                Log.w(TAG, "letoltes vegzetes hiba: ${t.message}")
                "A letöltés váratlan hibával állt le."
            }
            handler.removeCallbacks(stillWorking)
            isRunning = false

            val message = if (error != null) {
                error
            } else {
                val what = module.description.ifBlank { module.type.label }
                "Letöltve: ${module.name}. $what"
            }
            // MINDIG MEGSZÓLALUNK. Ez az egész javítás lényege: a letöltésnek
            // legyen hangja akkor is, ha a felhasználó közben kilépett.
            PatrolAnnouncer.announce(applicationContext, message, critical = true)

            handler.post { stopSelf() }
        }
        worker?.start()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    // ── Segédek ─────────────────────────────────────────────────────────────

    private fun readModule(intent: Intent): CatalogModule? {
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val path = intent.getStringExtra(EXTRA_PATH).orEmpty()
        if (id.isBlank() || path.isBlank()) return null
        return CatalogModule(
            id = id,
            name = intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "Névtelen modul" },
            type = ModuleType.fromKey(intent.getStringExtra(EXTRA_TYPE)),
            version = intent.getIntExtra(EXTRA_VERSION, 1),
            description = intent.getStringExtra(EXTRA_DESC).orEmpty(),
            sizeBytes = intent.getLongExtra(EXTRA_SIZE, 0L),
            filePath = path,
            minAppVersion = intent.getStringExtra(EXTRA_MIN_APP).orEmpty().ifBlank { "1.0.0" },
            categoryId = intent.getStringExtra(EXTRA_CATEGORY).orEmpty(),
            author = intent.getStringExtra(EXTRA_AUTHOR).orEmpty()
        )
    }

    private fun startForegroundSafely(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "startForeground hiba: ${e.message}")
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Super DL — modul letöltése")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Modul letöltése",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Amíg egy kérdéssor vagy más modul letöltődik"
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        } catch (e: Exception) {
            Log.w(TAG, "csatorna hiba: ${e.message}")
        }
    }
}
