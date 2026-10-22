package com.superdl.launcher.transit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.superdl.launcher.gps.GpsLocationHelper
import com.superdl.launcher.patrol.PatrolAnnouncer

/** A static timetable and GPS provide an approximate stop reminder, never vehicle tracking. */
data class TransitStopWatchStatus(
    val routeName: String,
    val destinationName: String,
    val stopNames: List<String>,
    val boardingStopIndex: Int,
    val destinationStopIndex: Int,
    val status: TransitRideStatus,
    val currentStopIndex: Int?,
    val nextStopIndex: Int?,
    val stopsRemaining: Int?,
    val serviceActive: Boolean,
    val locationPermissionGranted: Boolean
)

/**
 * Foreground GPS watcher for an explicitly chosen GTFS trip. The caller must choose the trip
 * and tap "Felszálltam" via [startRide]. The first reminder is issued only after the engine
 * confirms the previous stop; a poor or ambiguous fix makes the state UNCERTAIN instead.
 *
 * Register this non-exported class as a location foreground service in AndroidManifest.xml
 * before exposing the API in an Activity. No live vehicle, timetable, or ETA is inferred.
 */
class TransitStopWatchService : Service() {
    companion object {
        const val ACTION_STATE_CHANGED = "com.superdl.launcher.transit.STATE_CHANGED"
        private const val TAG = "SuperDL.TransitStopWatch"
        private const val ACTION_START = "com.superdl.launcher.transit.START"
        private const val ACTION_RESUME = "com.superdl.launcher.transit.RESUME"
        private const val ACTION_CORRECT = "com.superdl.launcher.transit.CORRECT"
        private const val ACTION_STOP = "com.superdl.launcher.transit.STOP"
        private const val EXTRA_STOP_INDEX = "stop_index"
        private const val CHANNEL_ID = "TRANSIT_STOP_WATCH_CHANNEL"
        private const val NOTIFICATION_ID = 7413
        private const val LOCATION_INTERVAL_MS = 2_000L
        private const val LOCATION_STALE_MS = 30_000L

        @Volatile private var serviceRunning = false

        /** Save the chosen static trip, then request a foreground service to confirm boarding. */
        fun startRide(
            context: Context,
            sequence: GtfsTripStopSequence,
            boardingStopIndex: Int,
            destinationStopIndex: Int
        ): Boolean {
            if (!GpsLocationHelper.hasPermission(context)) return false
            val previous = TransitRideSessionStore.load(context)
            val selected = TransitRideSession(
                sequence, boardingStopIndex, destinationStopIndex, checkpoint = null
            )
            if (!TransitRideSessionStore.save(context, selected)) return false
            return try {
                ContextCompat.startForegroundService(
                    context, Intent(context, TransitStopWatchService::class.java).setAction(ACTION_START)
                )
                broadcastState(context)
                true
            } catch (error: Exception) {
                Log.w(TAG, "Could not start foreground transit watcher", error)
                if (previous == null) TransitRideSessionStore.clear(context)
                else TransitRideSessionStore.save(context, previous)
                broadcastState(context)
                false
            }
        }

        /** An explicit stop removes the persistent active trip and shuts down location updates. */
        fun stopRide(context: Context): Boolean {
            val cleared = TransitRideSessionStore.clear(context)
            context.stopService(Intent(context, TransitStopWatchService::class.java))
            broadcastState(context)
            return cleared
        }

        /** Process/service interruption needs an explicit user decision to resume from a checkpoint. */
        fun resumeRide(context: Context): Boolean {
            val selected = TransitRideSessionStore.load(context) ?: return false
            val checkpoint = selected.checkpoint ?: return false
            if (checkpoint.status == TransitRideStatus.ARRIVED ||
                !GpsLocationHelper.hasPermission(context)
            ) return false
            return try {
                ContextCompat.startForegroundService(
                    context, Intent(context, TransitStopWatchService::class.java).setAction(ACTION_RESUME)
                )
                true
            } catch (error: Exception) {
                Log.w(TAG, "Could not resume foreground transit watcher", error)
                false
            }
        }

        /** UI may read this on resume and whenever [ACTION_STATE_CHANGED] is received. */
        fun readStatus(context: Context): TransitStopWatchStatus? {
            val session = TransitRideSessionStore.load(context) ?: return null
            val checkpoint = session.checkpoint
            if (checkpoint != null && TransitRideAlertEngine.restore(
                    session.sequence, session.boardingStopIndex, session.destinationStopIndex,
                    checkpoint
                ) == null
            ) return null
            val current = checkpoint?.currentStopIndex
            val status = when {
                checkpoint == null -> TransitRideStatus.NOT_BOARDED
                checkpoint.status == TransitRideStatus.ARRIVED -> TransitRideStatus.ARRIVED
                !serviceRunning -> TransitRideStatus.UNCERTAIN
                else -> checkpoint.status
            }
            return TransitStopWatchStatus(
                routeName = session.sequence.route.shortName.ifBlank {
                    session.sequence.route.longName.ifBlank { "Kiválasztott járat" }
                },
                destinationName = session.sequence.stops[session.destinationStopIndex].stop.name,
                stopNames = session.sequence.stops.map { it.stop.name },
                boardingStopIndex = session.boardingStopIndex,
                destinationStopIndex = session.destinationStopIndex,
                status = status,
                currentStopIndex = current,
                nextStopIndex = current?.takeIf { it < session.destinationStopIndex }?.plus(1),
                stopsRemaining = current?.let { session.destinationStopIndex - it },
                serviceActive = serviceRunning && status != TransitRideStatus.ARRIVED,
                locationPermissionGranted = GpsLocationHelper.hasPermission(context)
            )
        }

        /** Enqueues an explicit user correction; true means accepted for processing, not GPS proof. */
        fun requestManualCorrection(context: Context, stopIndex: Int): Boolean {
            val session = TransitRideSessionStore.load(context) ?: return false
            if (session.checkpoint == null ||
                stopIndex !in session.boardingStopIndex..session.destinationStopIndex ||
                !GpsLocationHelper.hasPermission(context)
            ) return false
            return try {
                ContextCompat.startForegroundService(
                    context, Intent(context, TransitStopWatchService::class.java)
                        .setAction(ACTION_CORRECT)
                        .putExtra(EXTRA_STOP_INDEX, stopIndex)
                )
                true
            } catch (error: Exception) {
                Log.w(TAG, "Could not request manual stop correction", error)
                false
            }
        }

        fun requestNextStop(context: Context): Boolean {
            val current = TransitRideSessionStore.load(context)?.checkpoint?.currentStopIndex
                ?: return false
            return requestManualCorrection(context, current + 1)
        }

        fun requestPreviousStop(context: Context): Boolean {
            val current = TransitRideSessionStore.load(context)?.checkpoint?.currentStopIndex
                ?: return false
            return requestManualCorrection(context, current - 1)
        }

        private fun broadcastState(context: Context) {
            context.sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(context.packageName))
        }
    }

    private var activeSession: TransitRideSession? = null
    private var engine: TransitRideAlertEngine? = null
    private var locationListener: LocationListener? = null
    private var uncertainAnnounced = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastLocationCallbackAtMs = 0L
    private val locationWatchdog = object : Runnable {
        override fun run() {
            val watch = engine ?: return
            val selected = activeSession ?: return
            if (lastLocationCallbackAtMs > 0L &&
                SystemClock.elapsedRealtime() - lastLocationCallbackAtMs >= LOCATION_STALE_MS
            ) {
                val before = watch.snapshot()
                val update = watch.markLocationUnavailable()
                if (update.status != before.status && persist(watch, selected)) {
                    announceDecision(update, before.status)
                }
            }
            mainHandler.postDelayed(this, 10_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!GpsLocationHelper.hasPermission(this)) {
            stopSelf()
            return
        }
        createNotificationChannel()
        try {
            startForeground(NOTIFICATION_ID, buildNotification(TransitRideStatus.UNCERTAIN, null))
            serviceRunning = true
        } catch (error: Exception) {
            Log.w(TAG, "Could not enter location foreground service", error)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!serviceRunning) return START_NOT_STICKY
        if (intent?.action == ACTION_STOP) {
            stopRide(this)
            return START_NOT_STICKY
        }
        val selected = TransitRideSessionStore.load(this) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val restored = selected.checkpoint?.let {
            TransitRideAlertEngine.restore(
                selected.sequence, selected.boardingStopIndex, selected.destinationStopIndex, it
            )
        }
        if (selected.checkpoint != null && restored == null) {
            TransitRideSessionStore.clear(this)
            broadcastState(this)
            stopSelf()
            return START_NOT_STICKY
        }
        val watch = restored ?: TransitRideAlertEngine(
            selected.sequence, selected.boardingStopIndex, selected.destinationStopIndex
        )
        engine = watch
        activeSession = selected
        val update = if (selected.checkpoint == null) watch.board() else watch.snapshot()
        if (!persist(watch, selected)) return START_NOT_STICKY

        if (intent?.action == ACTION_CORRECT) {
            val index = intent.getIntExtra(EXTRA_STOP_INDEX, -1)
            if (index !in selected.boardingStopIndex..selected.destinationStopIndex) return START_NOT_STICKY
            val before = watch.snapshot()
            val correction = watch.correctCurrentStop(index)
            if (!persist(watch, activeSession ?: selected)) return START_NOT_STICKY
            announceDecision(correction, before.status)
        } else if (selected.checkpoint == null) {
            PatrolAnnouncer.announce(
                this,
                "Megállófigyelés elindult. A helyzetet G P S alapján becsülöm; ellenőrizd a jármű kijelzőjét és bemondását.",
                withBeep = false, critical = true
            )
            announceDecision(update, TransitRideStatus.NOT_BOARDED)
        } else if (selected.checkpoint.status != TransitRideStatus.ARRIVED) {
            // A previous process' GPS confirmation is no longer fresh.
            uncertainAnnounced = true
            PatrolAnnouncer.announce(
                this, "A megállófigyelés újraindult. A helyzet most bizonytalan; friss G P S jelre várok.",
                withBeep = false, critical = true
            )
        }

        if (watch.snapshot().status == TransitRideStatus.ARRIVED) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (locationListener == null) {
            lastLocationCallbackAtMs = SystemClock.elapsedRealtime()
            locationListener = GpsLocationHelper.requestUpdates(this, LOCATION_INTERVAL_MS) {
                onLocation(it)
            }
            if (locationListener == null) {
                PatrolAnnouncer.announce(
                    this, "A megállófigyeléshez nem érkezik helyadat. A jelzéseket szüneteltetem.",
                    withBeep = false, critical = true
                )
                stopSelf()
                return START_NOT_STICKY
            }
            mainHandler.removeCallbacks(locationWatchdog)
            mainHandler.postDelayed(locationWatchdog, 10_000L)
        }
        // An interrupted journey is retained locally, but only the passenger can resume it.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(locationWatchdog)
        GpsLocationHelper.removeUpdates(this, locationListener)
        locationListener = null
        engine = null
        activeSession = null
        serviceRunning = false
        broadcastState(this)
        super.onDestroy()
    }

    private fun onLocation(location: Location) {
        lastLocationCallbackAtMs = SystemClock.elapsedRealtime()
        val watch = engine ?: return
        val selected = activeSession ?: return
        // A UI may have stopped/replaced the ride before this queued location callback ran.
        if (TransitRideSessionStore.load(this) != selected) return
        val before = watch.snapshot()
        val fix = TransitLocationFix(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble()
                else Double.POSITIVE_INFINITY,
            timestampMs = location.time
        )
        val update = watch.onLocation(fix, System.currentTimeMillis())
        if (!persist(watch, selected)) return
        announceDecision(update, before.status)
        if (update.status == TransitRideStatus.ARRIVED) stopSelf()
    }

    private fun persist(watch: TransitRideAlertEngine, selected: TransitRideSession): Boolean {
        val updated = selected.copy(checkpoint = watch.checkpoint())
        if (updated != selected && !TransitRideSessionStore.save(this, updated)) {
            PatrolAnnouncer.announce(
                this, "Nem sikerült menteni a megállófigyelés állapotát. A figyelést leállítom.",
                withBeep = true, critical = true
            )
            stopSelf()
            return false
        }
        activeSession = updated
        val snapshot = watch.snapshot()
        updateNotification(snapshot.status, snapshot.stopsRemaining)
        if (updated != selected) broadcastState(this)
        return true
    }

    private fun announceDecision(update: TransitRideUpdate, previousStatus: TransitRideStatus) {
        val selected = activeSession ?: return
        if (update.prepareToAlightNow) {
            val destination = selected.sequence.stops[selected.destinationStopIndex].stop.name
            PatrolAnnouncer.announce(
                this,
                "A kiválasztott útvonal szerint a cél előtti megállónál járhatsz. Készülj a leszálláshoz: $destination. Ellenőrizd a jármű kijelzőjét vagy bemondását.",
                withBeep = true, critical = true
            )
        }
        if (update.arrivedNow) {
            PatrolAnnouncer.announce(
                this,
                "A célmegálló közelében lehetsz. Ellenőrizd a pontos megállót a járművön.",
                withBeep = true, critical = true
            )
        }
        if (update.status == TransitRideStatus.UNCERTAIN &&
            previousStatus != TransitRideStatus.UNCERTAIN && !uncertainAnnounced
        ) {
            uncertainAnnounced = true
            PatrolAnnouncer.announce(
                this,
                "A G P S hely most bizonytalan. A megállójelzéseket szüneteltetem; ellenőrizd a jármű bemondását.",
                withBeep = false, critical = true
            )
        }
        if (update.status == TransitRideStatus.TRACKING) uncertainAnnounced = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, "Közlekedési megállófigyelő", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Kiválasztott járat megállóinak becsült figyelése GPS alapján"
                setShowBadge(false)
            }
        )
    }

    private fun updateNotification(status: TransitRideStatus, remaining: Int?) {
        getSystemService(NotificationManager::class.java)?.notify(
            NOTIFICATION_ID, buildNotification(status, remaining)
        )
    }

    private fun buildNotification(status: TransitRideStatus, remaining: Int?): Notification {
        val text = when (status) {
            TransitRideStatus.NOT_BOARDED -> "A kiválasztott járat indítása folyamatban"
            TransitRideStatus.TRACKING -> "GPS-becslés: ${remaining ?: "?"} megálló lehet hátra"
            TransitRideStatus.UNCERTAIN -> "Bizonytalan GPS; ellenőrizd a jármű bemondását"
            TransitRideStatus.ARRIVED -> "Célmegálló közelében lehetsz"
        }
        val stopIntent = Intent(this, TransitStopWatchService::class.java).setAction(ACTION_STOP)
        val stopAction = PendingIntent.getForegroundService(
            this, NOTIFICATION_ID, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Közlekedési megállófigyelő")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Figyelés leállítása", stopAction)
            .build()
    }
}
