package com.superdl.launcher.route

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.superdl.launcher.gps.GpsLocationHelper
import com.superdl.launcher.gps.GpsRadarMath
import com.superdl.launcher.patrol.PatrolAnnouncer

class GpsRouteRecorderService : Service() {

    companion object {
        private const val CHANNEL_ID = "GPS_ROUTE_RECORD_CHANNEL"
        private const val NOTIFICATION_ID = 7410
        private const val UPDATE_INTERVAL_MS = 1_500L
        private const val MIN_POINT_DISTANCE_M = 3
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val workerThread = HandlerThread("SuperDL-RouteRecord").apply { start() }
    private val workerHandler = Handler(workerThread.looper)
    private var locationListener: LocationListener? = null
    private val eventDetector = RouteEventDetector()
    private var announcedStart = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!GpsRouteSession.isRecording) {
            stopSelf()
            return
        }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        eventDetector.reset()
        locationListener = GpsLocationHelper.requestUpdates(this, UPDATE_INTERVAL_MS) { location ->
            workerHandler.post { onLocationUpdate(location) }
        }
        if (locationListener == null) {
            GpsRouteSession.clearRecording()
            PatrolAnnouncer.announce(this, "A G P S helyfigyelés nem indult el. Ellenőrizd a helyhozzáférést.", withBeep = true, critical = true)
            stopSelf()
        } else {
            mainHandler.postDelayed({
                if (GpsRouteSession.isRecording && !GpsRouteSession.isVoiceNoteRecording && synchronized(GpsRouteSession.recordingLock) { GpsRouteSession.points.isEmpty() }) {
                    PatrolAnnouncer.announce(this, "Még várok pontos G P S jelre. Menj szabad ég alá.", withBeep = false, critical = true)
                }
            }, 15_000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!GpsRouteSession.isRecording) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!announcedStart) {
            announcedStart = true
            PatrolAnnouncer.announce(
                this,
                "Útvonal rögzítés elindult: ${GpsRouteSession.recordingName}.",
                withBeep = true,
                critical = true
            )
        }
        // A felvétel csak memóriában él; processzhalál után nincs mit helyreállítani.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        workerHandler.removeCallbacksAndMessages(null)
        workerThread.quitSafely()
        GpsLocationHelper.removeUpdates(this, locationListener)
        locationListener = null
        super.onDestroy()
    }

    private fun onLocationUpdate(location: Location) {
        val counts = synchronized(GpsRouteSession.recordingLock) {
            if (!GpsRouteSession.isRecording) return
            // A helyjelölőnek az álló helyzetben kapott friss fix is kell.
            GpsRouteSession.lastRecordingLocation = Location(location)
            if (!GpsRouteFixQuality.isUsable(location)) return
            val previousPoint = GpsRouteSession.points.lastOrNull()
            if (previousPoint != null) {
                val moved = GpsRadarMath.distanceMeters(
                    previousPoint.latitude,
                    previousPoint.longitude,
                    location.latitude,
                    location.longitude
                )
                if (moved < MIN_POINT_DISTANCE_M) return
            }

            val accuracyM = if (location.hasAccuracy() && location.accuracy > 0f) {
                location.accuracy.toInt()
            } else {
                null
            }
            val bearing = previousPoint?.let {
                GpsRadarMath.bearingDegrees(
                    it.latitude,
                    it.longitude,
                    location.latitude,
                    location.longitude
                )
            }
            val point = RoutePoint(
                latitude = location.latitude,
                longitude = location.longitude,
                timestampMs = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
                accuracyM = accuracyM,
                bearing = bearing
            )
            GpsRouteSession.points.add(point)

            if (GpsRouteSession.points.size == 1) {
                GpsRouteSession.events.add(
                    RouteEvent(
                        type = RouteEventType.START,
                        latitude = point.latitude,
                        longitude = point.longitude,
                        timestampMs = point.timestampMs
                    )
                )
            }

            eventDetector.refreshCrossings(location.latitude, location.longitude)
            GpsRouteSession.events.addAll(eventDetector.onLocation(location, previousPoint))
            GpsRouteSession.points.size to GpsRouteSession.events.size
        }

        mainHandler.post { updateNotification(counts.first, counts.second) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GPS Útvonal rögzítés",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Saját útvonal felvétele GPS-sel"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS Útvonal rögzítés")
            .setContentText(GpsRouteSession.recordingName.ifBlank { "Felvétel folyamatban" })
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun updateNotification(pointCount: Int, eventCount: Int) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS Útvonal rögzítés")
            .setContentText("${GpsRouteSession.recordingName} • $pointCount pont • $eventCount esemény")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }
}
