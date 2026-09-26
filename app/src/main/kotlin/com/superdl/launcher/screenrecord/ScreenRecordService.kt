package com.superdl.launcher.screenrecord

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.superdl.launcher.files.RecordingsDirs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A KÉPERNYŐFELVÉTEL SZOLGÁLTATÁSA.
 *
 * MIÉRT KELL EGYÁLTALÁN SZOLGÁLTATÁS: a rendszer a képernyő tükrözését csak
 * akkor engedi, ha egy előtérben futó szolgáltatás tartja. Enélkül a
 * felvétel a program elhagyásakor azonnal leállna — vagyis pont akkor,
 * amikor a hiba megtörténik.
 *
 * A SORREND KÖTÖTT ÉS NEM CSERÉLHETŐ FEL: előbb kell előtérbe lépni, és
 * CSAK UTÁNA szabad elkérni a tükrözést. Fordítva az újabb Androidok
 * kivétellel megölik a programot.
 */
class ScreenRecordService : Service() {

    companion object {
        private const val CHANNEL_ID = "KEPERNYOFELVETEL"
        private const val NOTIFICATION_ID = 7700

        private const val ACTION_START = "com.superdl.launcher.KEPFELVETEL_INDIT"
        private const val ACTION_STOP = "com.superdl.launcher.KEPFELVETEL_ALLJ"
        private const val EXTRA_CODE = "kod"
        private const val EXTRA_DATA = "adat"

        /** A leállás eredménye: a hívó ebből tudja, mit mondjon. */
        var lastResultFile: File? = null
            private set

        var lastError: String? = null
            private set

        /** Igaz, ha a hangra hiába vártunk, és kép-only lett a felvétel. */
        var lastAudioGaveUp: Boolean = false
            private set

        fun startIntent(context: Context, resultCode: Int, data: Intent): Intent =
            Intent(context, ScreenRecordService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenRecordService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }

    private var pipeline: ScreenRecordPipeline? = null
    private var projection: MediaProjection? = null
    private val handler = Handler(Looper.getMainLooper())

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            // A FELHASZNÁLÓ A RENDSZER FELÜLETÉN IS LEÁLLÍTHATJA. Ilyenkor
            // nem hagyunk félbevágott fájlt: rendesen lezárjuk.
            handler.post { finishRecording() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                finishRecording()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startForegroundTyped()
                beginRecording(intent)
                return START_NOT_STICKY
            }
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    private fun beginRecording(intent: Intent) {
        if (ScreenRecordStore.isRecording) return
        lastError = null
        lastResultFile = null

        val code = intent.getIntExtra(EXTRA_CODE, 0)
        @Suppress("DEPRECATION")
        val data: Intent? = intent.getParcelableExtra(EXTRA_DATA)
        if (data == null) {
            fail("Nem kaptam meg a képernyő engedélyét.")
            return
        }

        val manager = getSystemService(MediaProjectionManager::class.java)
        val mp = try {
            manager?.getMediaProjection(code, data)
        } catch (t: Throwable) {
            null
        }
        if (mp == null) {
            fail("A rendszer nem adta oda a képernyőt.")
            return
        }
        projection = mp
        try {
            mp.registerCallback(projectionCallback, handler)
        } catch (_: Exception) {
        }

        val file = createOutputFile()
        val p = ScreenRecordPipeline(
            context = applicationContext,
            outputFile = file,
            withMic = ScreenRecordStore.isMicEnabled(this),
            withDeviceAudio = ScreenRecordStore.isDeviceAudioEnabled(this)
        )
        if (!p.start(mp)) {
            // A LÉPÉSNAPLÓT IS KIMONDJUK. Enélkül a „nem sikerült" pont
            // annyit ér, mint a csend.
            fail(
                "Eddig jutott: ${p.lastStep}. ${p.lastError ?: ""}".trim(),
                p.lastError
            )
            return
        }
        pipeline = p
        ScreenRecordStore.noteStarted(file, p.micActive, p.deviceAudioActive)
        updateNotification()

        // AZ INDULÁST KI KELL MONDANI.
        //
        // Az első éles próbánál ez hiányzott, és emiatt nem lehetett
        // megkülönböztetni azt, hogy „elindult" attól, hogy „elindult volna".
        // A felhasználó a rendszer párbeszéde után nem kapott SEMMILYEN
        // visszajelzést, és csak a leállításnál derült ki, hogy nem ment.
        val mibol = when {
            p.micActive && p.deviceAudioActive -> "A hangod és a telefon hangja is rákerül."
            p.micActive -> "Csak a te hangod kerül rá, a telefon hangját nem kaptam meg."
            p.deviceAudioActive -> "Csak a telefon hangja kerül rá, a mikrofont nem kaptam meg."
            else -> "FIGYELEM: hang nélkül veszek fel, egyik hangforrást sem kaptam meg."
        }
        speak("Felvétel elindult. $mibol")
    }

    private fun finishRecording() {
        val p = pipeline
        pipeline = null
        val file = ScreenRecordStore.currentFile
        ScreenRecordStore.noteStopped()

        try { projection?.unregisterCallback(projectionCallback) } catch (_: Exception) {}
        projection = null

        if (p != null) {
            val ok = p.stop()
            lastResultFile = if (ok) file else null
            if (!ok) lastError = p.speakDiagnosis()
            lastAudioGaveUp = p.audioGaveUp
            if (ok && file != null) {
                // Hogy a galéria és a számítógép is azonnal lássa.
                try {
                    MediaScannerConnection.scanFile(
                        applicationContext, arrayOf(file.absolutePath), arrayOf("video/mp4"), null
                    )
                } catch (_: Exception) {
                }
            }
        }
        stopForegroundCompat()
        stopSelf()
    }

    /**
     * A KUDARC NEM LEHET NÉMA.
     *
     * Ez a program kimondott szabálya, és az első éles próbánál pont ezt
     * szegtem meg: a felvétel elbukott az indításnál, a program nem szólt
     * semmit, és a felhasználó azt hitte, fut. A leállításnál kapott csak egy
     * „most nem megy felvétel" mondatot — abból nem derül ki semmi.
     *
     * Mostantól a hiba MEGNEVEZI magát, és a program KIMONDJA, akkor is, ha
     * közben már nem a SuperDL van előtérben.
     */
    private fun fail(message: String, details: String? = null) {
        lastError = if (details.isNullOrBlank()) message else "$message ($details)"
        ScreenRecordStore.noteStopped()
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        speak("A képernyőfelvétel nem indult el. $message")
        stopForegroundCompat()
        stopSelf()
    }

    private fun speak(text: String) {
        try {
            com.superdl.launcher.patrol.PatrolAnnouncer.announce(
                applicationContext, text, critical = true
            )
        } catch (_: Throwable) {
        }
    }

    /**
     * MIÉRT: a kétparaméteres startForeground a manifest összes típusát kéri
     * (mediaProjection|microphone); Android 14-en mikrofon-engedély nélkül ez
     * SecurityException és összeomlás. A mikrofon típust csak megadott
     * engedély mellett kérjük.
     */
    private fun startForegroundTyped() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification())
            return
        }
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        try {
            startForeground(NOTIFICATION_ID, buildNotification(), types)
        } catch (_: Exception) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {
        }
    }

    private fun createOutputFile(): File {
        val dir = RecordingsDirs.screen(applicationContext)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale("hu")).format(Date())
        var file = File(dir, "kepernyo-$stamp.mp4")
        var i = 2
        while (file.exists() && i < 100) {
            file = File(dir, "kepernyo-$stamp-$i.mp4")
            i++
        }
        return file
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Képernyőfelvétel",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Képernyőfelvétel folyamatban"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Képernyőfelvétel")
            .setContentText("Felvétel folyamatban")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun updateNotification() {
        try {
            getSystemService(NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID, buildNotification())
        } catch (_: Exception) {
        }
    }
}
