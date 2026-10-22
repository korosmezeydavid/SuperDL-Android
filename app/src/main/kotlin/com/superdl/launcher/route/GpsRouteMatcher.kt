package com.superdl.launcher.route

import com.superdl.launcher.gps.GpsRadarMath

data class RouteMatch(
    val pointIndex: Int,
    val distanceToRouteM: Int,
    val nextEvent: RouteEvent?,
    val nextEventIndex: Int,
    val distanceToNextEventM: Int?,
    val reversed: Boolean = false,
    val eventPointIndices: List<Int> = emptyList()
)

object GpsRouteMatcher {

    private const val MAX_OFF_ROUTE_M = 35
    private const val SHORT_ROUTE_ENDPOINT_RADIUS_M = 15
    private const val MIN_ENDPOINT_SEPARATION_M = 15f

    fun match(
        route: GpsRouteRecording,
        latitude: Double,
        longitude: Double,
        reversed: Boolean = false,
        announcedEventIndices: Set<Int> = emptySet()
    ): RouteMatch {
        val points = route.points
        if (points.isEmpty()) {
            return RouteMatch(-1, Int.MAX_VALUE, null, -1, null, reversed)
        }

        var closestIndex = 0
        var closestDistance = Int.MAX_VALUE
        for (i in points.indices) {
            val distance = GpsRadarMath.distanceMeters(
                latitude,
                longitude,
                points[i].latitude,
                points[i].longitude
            )
            if (distance < closestDistance) {
                closestDistance = distance
                closestIndex = i
            }
        }

        val eventIndices = route.events.mapIndexed { eventIndex, event ->
            Triple(eventIndex, event, nearestPointIndex(points, event.latitude, event.longitude))
        }
        val nextIndex = selectNextEventIndex(
            eventIndices.map { it.third },
            route.events.map { it.type },
            closestIndex,
            reversed,
            announcedEventIndices
        )
        val next = nextIndex?.let(eventIndices::get)

        val nextEvent = next?.second
        val nextEventIndex = next?.first ?: -1
        val distanceToNextEvent = next?.let { (_, event, eventPointIndex) ->
            if (eventPointIndex == closestIndex) {
                GpsRadarMath.distanceMeters(latitude, longitude, event.latitude, event.longitude)
            } else {
                // A légvonal túl korán jelezne egy visszakanyarodó utcánál.
                var distance = GpsRadarMath.distanceMeters(
                    latitude, longitude, points[closestIndex].latitude, points[closestIndex].longitude
                )
                val from = minOf(closestIndex, eventPointIndex)
                val to = maxOf(closestIndex, eventPointIndex)
                for (index in from until to) {
                    distance += GpsRadarMath.distanceMeters(
                        points[index].latitude, points[index].longitude,
                        points[index + 1].latitude, points[index + 1].longitude
                    )
                }
                distance + GpsRadarMath.distanceMeters(
                    points[eventPointIndex].latitude, points[eventPointIndex].longitude,
                    event.latitude, event.longitude
                )
            }
        }

        return RouteMatch(
            pointIndex = closestIndex,
            distanceToRouteM = closestDistance,
            nextEvent = nextEvent,
            nextEventIndex = nextEventIndex,
            distanceToNextEventM = distanceToNextEvent,
            reversed = reversed,
            eventPointIndices = eventIndices.map { it.third }
        )
    }

    fun isOnRoute(match: RouteMatch): Boolean =
        match.pointIndex >= 0 && match.distanceToRouteM <= MAX_OFF_ROUTE_M

    /** Az azonos útvonalpontra eső jelölők se nyomják el egymást. */
    fun selectNextEventIndex(
        eventPointIndices: List<Int>,
        eventTypes: List<RouteEventType>,
        closestPointIndex: Int,
        reversed: Boolean,
        announcedEventIndices: Set<Int> = emptySet()
    ): Int? {
        if (closestPointIndex < 0 || eventPointIndices.size != eventTypes.size) return null
        val eligible = eventPointIndices.indices.filter { index ->
            val point = eventPointIndices[index]
            val ahead = if (reversed) point <= closestPointIndex else point >= closestPointIndex
            ahead && index !in announcedEventIndices &&
                !(reversed && (eventTypes[index] == RouteEventType.NOTE || eventTypes[index] == RouteEventType.STOP)) &&
                !(!reversed && eventTypes[index] == RouteEventType.START)
        }
        return if (reversed) {
            eligible.maxWithOrNull(compareBy<Int> { eventPointIndices[it] }.thenBy { it })
        } else {
            eligible.minWithOrNull(compareBy<Int> { eventPointIndices[it] }.thenBy { it })
        }
    }

    /** Azonos helyen több jelölőt egyetlen helyadat mellett kell felolvasni. */
    fun pendingEventsAtSamePoint(
        eventPointIndices: List<Int>,
        eventTypes: List<RouteEventType>,
        selectedIndex: Int,
        reversed: Boolean,
        announcedEventIndices: Set<Int>
    ): List<Int> {
        if (selectedIndex !in eventPointIndices.indices || eventPointIndices.size != eventTypes.size) return emptyList()
        val point = eventPointIndices[selectedIndex]
        return eventPointIndices.indices.filter { index ->
            eventPointIndices[index] == point && index !in announcedEventIndices &&
                !(reversed && (eventTypes[index] == RouteEventType.NOTE || eventTypes[index] == RouteEventType.STOP)) &&
                !(!reversed && eventTypes[index] == RouteEventType.START)
        }
    }

    fun approachThreshold(distanceM: Int): Int? = when {
        distanceM <= 10 -> 10
        distanceM <= 20 -> 20
        distanceM <= 50 -> 50
        else -> null
    }

    /**
     * A two- or three-point route cannot produce enough index changes for trend
     * detection. Infer its initial direction only at an unambiguous endpoint.
     * Distances are from the current, already validated location fix.
     */
    fun initialShortRouteDirection(
        pointCount: Int,
        distanceToStartM: Int,
        distanceToEndM: Int,
        accuracyM: Float
    ): GpsRouteTravelDirection {
        if (pointCount !in 2..3 || !accuracyM.isFinite() || accuracyM <= 0f ||
            distanceToStartM < 0 || distanceToEndM < 0
        ) return GpsRouteTravelDirection.UNKNOWN

        val separation = maxOf(MIN_ENDPOINT_SEPARATION_M, accuracyM * 2f)
        return when {
            distanceToStartM <= SHORT_ROUTE_ENDPOINT_RADIUS_M &&
                distanceToEndM - distanceToStartM >= separation -> GpsRouteTravelDirection.FORWARD
            distanceToEndM <= SHORT_ROUTE_ENDPOINT_RADIUS_M &&
                distanceToStartM - distanceToEndM >= separation -> GpsRouteTravelDirection.REVERSE
            else -> GpsRouteTravelDirection.UNKNOWN
        }
    }

    /** A célközelséget a valódi végponttól és a fix hibahatárától számoljuk. */
    fun isDestinationReached(
        pointCount: Int,
        pointIndex: Int,
        distanceToGoalM: Int,
        accuracyM: Float,
        reversed: Boolean
    ): Boolean {
        if (pointCount < 2 || pointIndex !in 0 until pointCount || distanceToGoalM < 0 ||
            !accuracyM.isFinite() || accuracyM <= 0f || distanceToGoalM + accuracyM > 15f
        ) return false
        return if (reversed) {
            if (pointCount <= 3) pointIndex == 0 else pointIndex <= 1
        } else {
            if (pointCount <= 3) pointIndex == pointCount - 1 else pointIndex >= pointCount - 2
        }
    }

    private fun nearestPointIndex(points: List<RoutePoint>, latitude: Double, longitude: Double): Int {
        var closestIndex = 0
        var closestDistance = Int.MAX_VALUE
        for (i in points.indices) {
            val distance = GpsRadarMath.distanceMeters(
                latitude,
                longitude,
                points[i].latitude,
                points[i].longitude
            )
            if (distance < closestDistance) {
                closestDistance = distance
                closestIndex = i
            }
        }
        return closestIndex
    }
}
