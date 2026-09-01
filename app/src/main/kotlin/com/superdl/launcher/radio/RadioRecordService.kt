package com.superdl.launcher.radio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.superdl.launcher.MainActivity

/**
 * IDŐZÍTETT RÁDIÓFELVÉTEL — előtér-szolgáltatásként.
 *
 * MIÉRT ELŐTÉR-SZOLGÁLTATÁS: egy félórás felvételt a rendszer háttérben
 * bármikor megölne. Az előtér-szolgáltatás értesítéssel jár, és ezt így is
 * kell: a felhasználó lássa (és hallja a képernyőolvasóval), hogy a telefonja
 * ÉPPEN FELVESZ. Egy némán rögzítő telefon nem lenne rendben.
 *
 * A LEÁLLÍTÁS KÉTFÉLE: a beállított idő letelik, vagy a felhasználó leállítja
 * az értesítésből. Mindkét úton lezárjuk a fájlt — félbehagyott, nulla bájtos
 * felvétel nem maradhat.
 */
class RadioRecordService : Service() {

    private var recorder: RadioRecorder? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null
    private var entryName: String = "Rádió"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finishRecording(stoppedByUser = true)
            return START_NOT_STICKY
        }

        val id = intent?.getIntExtra(EXTRA_ID, -1) ?: -1
        val entry = if (id >= 0) RadioScheduleStore.get(this, id) else null
        if (entry == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        entryName = entry.stationName

        startForeground(NOTIF_ID, buildNotification(entry))

        // Már fut egy felvétel? Ne indítsunk másodikat ugyanarra.
        if (recorder?.isRecording == true) return START_STICKY

        val rec = RadioRecorder(this)
        recorder = rec
        val station = RadioStation(
            id = "schedule_${entry.id}",
            name = entry.stationName,
            streamUrl = entry.streamUrl
        )
        val started = rec.start(station)
        if (!started) {
            Log.w(TAG, "a felvetel nem indult el: ${entry.stationName}")
            stopSelf()
            return START_NOT_STICKY
        }

        // Az időzített leállítás.
        val runnable = Runnable { finishRecording(stoppedByUser = false) }
        stopRunnable = runnable
        handler.postDelayed(runnable, entry.durationMinutes * 60_000L)
        return START_STICKY
    }

    private fun finishRecording(stoppedByUser: Boolean) {
        stopRunnable?.let { handler.removeCallbacks(it) }
        stopRunnable = null
        val file = try {
            recorder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "stop hiba: ${e.message}")
            null
        }
        recorder = null
        notifyResult(file != null, stoppedByUser)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {
        }
        stopSelf()
    }

    /**
     * AZ EREDMÉNYRŐL SZÓLNI KELL. Aki időzített felvételt kért, az számít rá.
     * Ha némán elmaradt, csak napok múlva derülne ki — akkor, amikor keresi.
     */
    private fun notifyResult(ok: Boolean, stoppedByUser: Boolean) {
        val text = when {
            ok && stoppedByUser -> "$entryName felvétele leállítva és elmentve."
            ok -> "$entryName felvétele kész. A Rádió felvételek menüpontban hallgathatod meg."
            else -> "$entryName felvétele nem sikerült. Lehet, hogy nem volt internet."
        }
        try {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            ensureChannel(manager)
            manager.notify(
                NOTIF_RESULT_ID,
                baseNotification("Rádiófelvétel", text)
                    .setAutoCancel(true)
                    .build()
            )
        } catch (e: Exception) {
            Log.w(TAG, "ertesites hiba: ${e.message}")
        }
    }

    private fun buildNotification(entry: RadioScheduleEntry): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager != null) ensureChannel(manager)

        val stopIntent = Intent(this, RadioRecordService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return baseNotification(
            "Rádiófelvétel folyamatban",
            "${entry.stationName} — ${entry.durationMinutes} perc"
        )
            .setOngoing(true)
            .addAction(0, "Leállítás", stopPending)
            .build()
    }

    private fun baseNotification(title: String, text: String) =
        androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Rádiófelvétel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Időzített rádiófelvétel állapota"
            }
        )
    }

    override fun onDestroy() {
        // Biztonsági háló: ha a rendszer öl meg minket, akkor is zárjuk a fájlt.
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        recorder = null
        stopRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SuperDL.RadioRecSvc"
        private const val CHANNEL_ID = "superdl_radio_record"
        private const val NOTIF_ID = 7301
        private const val NOTIF_RESULT_ID = 7302
        const val EXTRA_ID = "schedule_id"
        const val ACTION_STOP = "com.superdl.launcher.RADIO_RECORD_STOP"

        /** Fut-e éppen időzített felvétel (a menüben ki tudjuk mondani). */
        fun stopIntent(context: Context): Intent =
            Intent(context, RadioRecordService::class.java).apply { action = ACTION_STOP }
    }
}
