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
import com.superdl.launcher.gps.VoiceNoteRecorder
import com.superdl.launcher.patrol.PatrolAnnouncer

class GpsRouteGuideService : Service() {

    companion object {
        private const val CHANNEL_ID = "GPS_ROUTE_GUIDE_CHANNEL"
        private const val NOTIFICATION_ID = 7411
        private const val UPDATE_INTERVAL_MS = 2_000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val workerThread = HandlerThread("SuperDL-RouteGuide").apply { start() }
    private val workerHandler = Handler(workerThread.looper)

    private var locationListener: LocationListener? = null
    private var announcedStart = false
    private var uncertainFixAnnounced = false
    private var offRouteAnnounced = false
    private var stableFixCount = 0
    private var stableOnRouteCount = 0
    @Volatile private var hasUsableFix = false
    private val directionTracker = GpsRouteDirectionTracker()
    private var lastStableDirection = GpsRouteTravelDirection.UNKNOWN
    private var shortRouteDirection = GpsRouteTravelDirection.UNKNOWN
    private var trackedRouteId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!GpsRouteSession.isGuiding || GpsRouteSession.activeRoute == null) {
            stopSelf()
            return
        }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        locationListener = GpsLocationHelper.requestUpdates(this, UPDATE_INTERVAL_MS) { location ->
            workerHandler.post { onLocationUpdate(location) }
        }
        if (locationListener == null) {
            GpsRouteSession.clearGuidance()
            PatrolAnnouncer.announce(this, "A G P S útmutatás nem indult el, mert nincs helyadat.", withBeep = true, critical = true)
            stopSelf()
        } else {
            mainHandler.postDelayed({
                if (GpsRouteSession.isGuiding && !hasUsableFix) {
                    PatrolAnnouncer.announce(this, "Még várok pontos G P S jelre. Az útmutatás addig nem ad kanyarjelzést.", withBeep = false, critical = true)
                }
            }, 15_000L)
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
                "Útvonal követés elindult: $routeName. A haladás irányát néhány lépés után állapítom meg; addig nincs kanyarjelzés.",
                withBeep = true,
                critical = true
            )
        }
        // Az aktív útvonal csak memóriában él; üres sessiont nem indítunk újra.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        workerHandler.removeCallbacksAndMessages(null)
        GpsLocationHelper.removeUpdates(this, locationListener)
        locationListener = null
        workerThread.quitSafely()
        super.onDestroy()
    }

    private fun onLocationUpdate(location: Location) {
        val route = GpsRouteSession.activeRoute ?: return
        if (trackedRouteId != route.id) {
            trackedRouteId = route.id
            directionTracker.reset()
            lastStableDirection = GpsRouteTravelDirection.UNKNOWN
            shortRouteDirection = GpsRouteTravelDirection.UNKNOWN
        }
        GpsRouteSession.lastLocation = location

        if (!GpsRouteFixQuality.isUsable(location)) {
            stableFixCount = 0
            if (!uncertainFixAnnounced) {
                uncertainFixAnnounced = true
                PatrolAnnouncer.announce(this, "A G P S hely most pontatlan. Az útvonal-emlékeztetőket átmenetileg szüneteltetem.", withBeep = false, critical = true)
            }
            return
        }
        hasUsableFix = true
        if (uncertainFixAnnounced && ++stableFixCount >= 3) uncertainFixAnnounced = false

        val routePosition = GpsRouteMatcher.match(route, location.latitude, location.longitude)
        if (!GpsRouteMatcher.isOnRoute(routePosition)) {
            stableOnRouteCount = 0
            if (!offRouteAnnounced) {
                offRouteAnnounced = true
                PatrolAnnouncer.announce(this, "A mentett útvonaltól eltérő helyen vagy. A jelölők felolvasását szüneteltetem.", withBeep = true, critical = true)
            }
            return
        }
        if (offRouteAnnounced && ++stableOnRouteCount >= 3) offRouteAnnounced = false

        val direction = updateGuidanceDirection(route, location, routePosition.pointIndex)
        if (direction == GpsRouteTravelDirection.UNKNOWN) return
        val reversed = direction == GpsRouteTravelDirection.REVERSE
        val match = GpsRouteMatcher.match(
            route,
            location.latitude,
            location.longitude,
            reversed,
            GpsRouteSession.announcedEventIndices
        )
        val nextEvent = match.nextEvent ?: run {
            checkRouteCompletion(route, match, location)
            return
        }

        val distance = match.distanceToNextEventM ?: return
        val threshold = GpsRouteMatcher.approachThreshold(distance)
        if (threshold != null) {
            val group = GpsRouteMatcher.pendingEventsAtSamePoint(
                match.eventPointIndices,
                route.events.map { it.type },
                match.nextEventIndex,
                reversed,
                GpsRouteSession.announcedEventIndices
            )
            val newWarnings = group.filter { index ->
                val previous = GpsRouteSession.approachThresholds[index]
                previous == null || previous > threshold
            }
            if (newWarnings.isNotEmpty()) {
                // Egyetlen bemondás a közös ponthoz, így két GPS-fix közt sem vész el jegyzet.
                val message = newWarnings.joinToString(" ") { index ->
                    buildApproachMessage(route.events[index], threshold, reversed)
                }
                val voiceNotes = newWarnings.mapNotNull { index ->
                    route.events[index].voiceNotePath
                }
                PatrolAnnouncer.announce(
                    this, message, withBeep = threshold <= 20, critical = true,
                    onDone = if (voiceNotes.isEmpty()) null else {{
                        if (GpsRouteSession.activeRoute?.id == route.id && GpsRouteSession.isGuiding) {
                            playRouteVoiceNotes(voiceNotes, 0)
                        }
                    }}
                )
                newWarnings.forEach { index ->
                    GpsRouteSession.approachThresholds[index] = threshold
                    if (threshold <= 10 || route.events[index].type == RouteEventType.NOTE) {
                        GpsRouteSession.announcedEventIndices.add(index)
                    }
                }
            }
        }

        // A helyfigyelő álló helyzetben nem feltétlenül küld még egy fixet.
        // Ha a cél-eseményt ezen a fixen jelentettük be, már most zárjuk le.
        val destinationType = if (reversed) RouteEventType.START else RouteEventType.STOP
        val destinationIndex = route.events.indexOfLast { it.type == destinationType }
        if (destinationIndex >= 0 && destinationIndex in GpsRouteSession.announcedEventIndices &&
            checkRouteCompletion(route, match, location)
        ) {
            return
        }

        mainHandler.post {
            updateNotification(route.name, nextEvent, distance, reversed)
        }
    }

    private fun updateGuidanceDirection(
        route: GpsRouteRecording,
        location: Location,
        pointIndex: Int
    ): GpsRouteTravelDirection {
        val shortRoute = route.points.size in 2..3
        val previous = if (shortRoute) shortRouteDirection else directionTracker.direction
        val direction = if (shortRoute) {
            if (shortRouteDirection == GpsRouteTravelDirection.UNKNOWN) {
                val start = route.points.first()
                val end = route.points.last()
                shortRouteDirection = GpsRouteMatcher.initialShortRouteDirection(
                    route.points.size,
                    GpsRadarMath.distanceMeters(location.latitude, location.longitude, start.latitude, start.longitude),
                    GpsRadarMath.distanceMeters(location.latitude, location.longitude, end.latitude, end.longitude),
                    location.accuracy
                )
            }
            shortRouteDirection
        } else {
            directionTracker.observe(pointIndex)
        }
        if (direction == GpsRouteTravelDirection.UNKNOWN) {
            if (previous != GpsRouteTravelDirection.UNKNOWN) {
                PatrolAnnouncer.announce(
                    this,
                    "Az útvonal haladási iránya bizonytalan. A kanyarjelzéseket szüneteltetem.",
                    withBeep = false,
                    critical = true
                )
            }
            return direction
        }
        GpsRouteSession.guidanceReversed = direction == GpsRouteTravelDirection.REVERSE
        if (direction != lastStableDirection) {
            GpsRouteSession.announcedEventIndices.clear()
            GpsRouteSession.approachThresholds.clear()
            lastStableDirection = direction
            val message = if (direction == GpsRouteTravelDirection.REVERSE) {
                "Visszafelé haladsz az útvonalon. A személyes jegyzeteket nem olvasom visszafelé."
            } else {
                "Előrefelé haladsz az útvonalon. A jelölők felolvasása indul."
            }
            PatrolAnnouncer.announce(this, message, withBeep = direction == GpsRouteTravelDirection.REVERSE, critical = true)
        }
        return direction
    }

    private fun checkRouteCompletion(route: GpsRouteRecording, match: RouteMatch, location: Location): Boolean {
        if (route.points.isEmpty()) return false
        val goal = if (match.reversed) route.points.first() else route.points.last()
        val distanceToGoal = GpsRadarMath.distanceMeters(
            location.latitude, location.longitude, goal.latitude, goal.longitude
        )
        val reachedEnd = GpsRouteMatcher.isDestinationReached(
            route.points.size, match.pointIndex, distanceToGoal, location.accuracy, match.reversed
        )
        if (reachedEnd) {
            val message = if (match.reversed) {
                "Az útvonal kezdete a közelben lehet. Ellenőrizd a pontos helyet."
            } else {
                "Az útvonal vége a közelben lehet. Ellenőrizd a pontos helyet."
            }
            PatrolAnnouncer.announce(this, message, withBeep = true, critical = true)
            GpsRouteStore.stopGuidance(this)
            mainHandler.post { stopSelf() }
        }
        return reachedEnd
    }

    private fun buildApproachMessage(event: RouteEvent, thresholdM: Int, reversed: Boolean): String {
        if (event.type == RouteEventType.NOTE) {
            if (!event.voiceNotePath.isNullOrBlank()) return "Saját hangjegyzet a közelben."
            return "Saját útvonal-emlékeztető a közelben: ${event.label.orEmpty()}."
        }
        val action = when (event.type) {
            RouteEventType.TURN_LEFT -> if (reversed) "jobb kanyar" else "bal kanyar"
            RouteEventType.TURN_RIGHT -> if (reversed) "bal kanyar" else "jobb kanyar"
            RouteEventType.TURN_SLIGHT -> "enyhe kanyar"
            RouteEventType.U_TURN -> "visszafordulás"
            RouteEventType.CROSSING -> "kereszteződés"
            RouteEventType.WAYPOINT -> event.label ?: "út pont"
            RouteEventType.NOTE -> event.label ?: "megjegyzés"
            RouteEventType.START -> if (reversed) "az útvonal kezdete" else "az útvonal indulása"
            RouteEventType.STOP -> if (reversed) "az útvonal indulása" else "az útvonal vége"
        }
        val prefix = if (reversed) "Visszafelé. " else ""
        return when (thresholdM) {
            50 -> "${prefix}Mentett útvonal: nagyjából 50 méteren belül $action."
            20 -> "${prefix}Mentett útvonal: $action a közelben."
            else -> "${prefix}Mentett útvonal: $action itt a közelben. A pontos helyet ellenőrizd."
        }
    }

    private fun playRouteVoiceNotes(paths: List<String>, index: Int) {
        if (index >= paths.size || !GpsRouteSession.isGuiding) return
        VoiceNoteRecorder.play(paths[index]) {
            mainHandler.post { playRouteVoiceNotes(paths, index + 1) }
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

    private fun updateNotification(
        routeName: String,
        nextEvent: RouteEvent,
        distanceM: Int,
        reversed: Boolean
    ) {
        val label = when (nextEvent.type) {
            RouteEventType.TURN_LEFT -> if (reversed) "jobbra" else "balra"
            RouteEventType.TURN_RIGHT -> if (reversed) "balra" else "jobbra"
            RouteEventType.TURN_SLIGHT -> "enyhe kanyar"
            RouteEventType.U_TURN -> "visszafordulás"
            RouteEventType.CROSSING -> "kereszteződés"
            RouteEventType.WAYPOINT -> nextEvent.label ?: "út pont"
            RouteEventType.NOTE -> "saját megjegyzés"
            RouteEventType.START -> if (reversed) "megállás" else "indulás"
            RouteEventType.STOP -> if (reversed) "indulás" else "megállás"
        }
        val direction = if (reversed) "vissza • " else ""
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS Útvonal követés")
            .setContentText("$routeName • $direction$distanceM m • $label")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }
}
