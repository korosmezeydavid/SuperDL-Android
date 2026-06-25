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
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.superdl.launcher.gps.GpsLocationHelper
import com.superdl.launcher.patrol.PatrolAnnouncer

class GpsRouteGuideService : Service() {

    companion object {
        private const val CHANNEL_ID = "GPS_ROUTE_GUIDE_CHANNEL"
        private const val NOTIFICATION_ID = 7411
        private const val UPDATE_INTERVAL_MS = 2_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var locationListener: LocationListener? = null
    private var announcedStart = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        locationListener = GpsLocationHelper.requestUpdates(this, UPDATE_INTERVAL_MS) { location ->
            onLocationUpdate(location)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!GpsRouteSession.isGuiding || GpsRouteSession.activeRoute == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!announcedStart) {
            announcedStart = true
            val routeName = GpsRouteSession.activeRoute?.name ?: "Útvonal"
            PatrolAnnouncer.announce(
                this,
                "Útvonal követés elindult: $routeName.",
                withBeep = true
            )
        }
        return START_STICKY
    }

    override fun onDestroy() {
        GpsLocationHelper.removeUpdates(this, locationListener)
        locationListener = null
        super.onDestroy()
    }

    private fun onLocationUpdate(location: Location) {
        val route = GpsRouteSession.activeRoute ?: return
        GpsRouteSession.lastLocation = location

        val match = GpsRouteMatcher.match(route, location.latitude, location.longitude)
        val nextEvent = match.nextEvent ?: run {
            checkRouteCompletion(route, match)
            return
        }

        if (match.nextEventIndex <= GpsRouteSession.lastAnnouncedEventIndex) return
        val distance = match.distanceToNextEventM ?: return
        val thresholds = listOf(50, 20, 10)
        for (threshold in thresholds) {
            if (distance <= threshold) {
                val last = GpsRouteSession.lastApproachThreshold
                if (last == null || last > threshold) {
                    GpsRouteSession.lastApproachThreshold = threshold
                    val message = buildApproachMessage(nextEvent, threshold)
                    PatrolAnnouncer.announce(this, message, withBeep = threshold <= 20)
                    if (threshold <= 10) {
                        GpsRouteSession.lastAnnouncedEventIndex = match.nextEventIndex
                        GpsRouteSession.lastApproachThreshold = null
                    }
                }
                break
            }
        }

        handler.post {
            updateNotification(route.name, nextEvent, distance)
        }
    }

    private fun checkRouteCompletion(route: GpsRouteRecording, match: RouteMatch) {
        if (route.points.isEmpty()) return
        val distanceToEnd = match.distanceToRouteM
        if (match.pointIndex >= route.points.lastIndex - 1 && distanceToEnd <= 15) {
            PatrolAnnouncer.announce(this, "Útvonal vége elérve.", withBeep = true)
            GpsRouteStore.stopGuidance(this)
            stopSelf()
        }
    }

    private fun buildApproachMessage(event: RouteEvent, thresholdM: Int): String {
        val action = when (event.type) {
            RouteEventType.TURN_LEFT -> "fordulj balra"
            RouteEventType.TURN_RIGHT -> "fordulj jobbra"
            RouteEventType.TURN_SLIGHT -> "enyhe kanyar"
            RouteEventType.U_TURN -> "fordulj vissza"
            RouteEventType.CROSSING -> "kereszteződés"
            RouteEventType.WAYPOINT -> event.label ?: "út pont"
            RouteEventType.START -> "indulás"
            RouteEventType.STOP -> "megállás"
        }
        return when (thresholdM) {
            50 -> "50 méter múlva $action."
            20 -> "20 méter múlva $action."
            else -> "Most $action."
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GPS Útvonal követés",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mentett útvonal hangos követése"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val routeName = GpsRouteSession.activeRoute?.name ?: "Útvonal követés"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS Útvonal követés")
            .setContentText(routeName)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(routeName: String, nextEvent: RouteEvent, distanceM: Int) {
        val label = when (nextEvent.type) {
            RouteEventType.TURN_LEFT -> "balra"
            RouteEventType.TURN_RIGHT -> "jobbra"
            RouteEventType.TURN_SLIGHT -> "enyhe kanyar"
            RouteEventType.U_TURN -> "visszafordulás"
            RouteEventType.CROSSING -> "kereszteződés"
            RouteEventType.WAYPOINT -> nextEvent.label ?: "út pont"
            RouteEventType.START -> "indulás"
            RouteEventType.STOP -> "megállás"
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS Útvonal követés")
            .setContentText("$routeName • $distanceM m • $label")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }
}