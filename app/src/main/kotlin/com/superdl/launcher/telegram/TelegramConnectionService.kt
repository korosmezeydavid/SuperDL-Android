package com.superdl.launcher.telegram

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.superdl.launcher.BuildConfig
import com.superdl.launcher.R

/** A TDLib-kapcsolat látható háttérszolgáltatása. Üzenettartalmat nem naplóz és nem jelenít meg. */
class TelegramConnectionService : Service() {
    private var sounds: SoundPool? = null
    private var incomingSound = 0
    private val seenMessages = LinkedHashSet<String>()
    private val observer: (String) -> Unit = { json -> onTelegramUpdate(json) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(ONGOING_ID, ongoingNotification())
        sounds = SoundPool.Builder().setMaxStreams(2)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .build()
        incomingSound = try { sounds?.load(this, R.raw.snd_chat_incoming, 1) ?: 0 }
            catch (_: Exception) { 0 }
        TelegramSession.observe(observer)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !BuildConfig.FEATURE_TELEGRAM) {
            stopSelf()
            return START_NOT_STICKY
        }
        val result = TelegramSession.start(applicationContext)
        if (result != TdlibNativeTransport.StartResult.Started &&
            result != TdlibNativeTransport.StartResult.AlreadyRunning) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun onTelegramUpdate(json: String) {
        try {
            val key = TdlibJsonProtocol.incomingMessageKey(json) ?: return
            if (!seenMessages.add(key)) return
            if (seenMessages.size > 100) seenMessages.remove(seenMessages.first())
            if (!isMuted(this)) {
                try { sounds?.play(incomingSound, 1f, 1f, 1, 0, 1f) } catch (_: Exception) { }
            }
            if (Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                getSystemService(NotificationManager::class.java)
                    .notify(MESSAGE_ID, messageNotification())
            }
        } catch (_: Exception) {
            // A TDLib-válasz tartalmazhat személyes adatot; a kivételt sem naplózzuk.
        }
    }

    private fun openIntent(): PendingIntent = PendingIntent.getActivity(this, 0,
        Intent(this, TelegramActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun ongoingNotification(): Notification = NotificationCompat.Builder(this, STATUS_CHANNEL)
        .setSmallIcon(android.R.drawable.ic_dialog_email)
        .setContentTitle("SuperDL Telegram")
        .setContentText("A Telegram-kapcsolat a háttérben aktív.")
        .setContentIntent(openIntent())
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setSilent(true)
        .addAction(0, "Kapcsolat leállítása", PendingIntent.getService(this, 1,
            Intent(this, TelegramConnectionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .build()

    private fun messageNotification(): Notification = NotificationCompat.Builder(this, MESSAGE_CHANNEL)
        .setSmallIcon(android.R.drawable.ic_dialog_email)
        .setContentTitle("Új Telegram-üzenet")
        .setContentText("Nyisd meg a SuperDL Telegramot az elolvasáshoz.")
        .setContentIntent(openIntent())
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setSilent(true)
        .build()

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(STATUS_CHANNEL,
            "SuperDL Telegram háttérkapcsolat", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(MESSAGE_CHANNEL,
            "SuperDL Telegram üzenetek", NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(null, null) // A Csevejcenter saját hangját külön játsszuk le; nincs dupla csengés.
        })
    }

    override fun onDestroy() {
        TelegramSession.remove(observer)
        TelegramSession.stop()
        try { sounds?.release() } catch (_: Exception) { }
        sounds = null
        super.onDestroy()
    }

    companion object {
        private const val STATUS_CHANNEL = "superdl_telegram_status"
        private const val MESSAGE_CHANNEL = "superdl_telegram_messages"
        private const val ONGOING_ID = 76340
        private const val MESSAGE_ID = 76341
        const val ACTION_STOP = "com.superdl.launcher.telegram.STOP"
        private const val PREFS = "telegram_preferences"
        private const val MUTE_KEY = "mute_incoming_sound"

        fun isMuted(context: android.content.Context): Boolean =
            context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getBoolean(MUTE_KEY, false)

        fun setMuted(context: android.content.Context, muted: Boolean) {
            context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
                .putBoolean(MUTE_KEY, muted).apply()
        }
    }
}
