package com.superdl.launcher.transit

import java.io.BufferedReader
import java.io.PushbackReader
import java.io.Reader

/** A static GTFS stop, identified by stop_id rather than by its display name. */
data class GtfsStop(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)

data class GtfsRoute(
    val id: String,
    val shortName: String,
    val longName: String,
    val type: Int?
)

data class GtfsTrip(
    val id: String,
    val routeId: String,
    val serviceId: String?,
    val headsign: String?,
    val directionId: Int?
)

/** One visit to a stop. A circular trip may visit the same stop more than once. */
data class GtfsStopCall(
    val stop: GtfsStop,
    val stopSequence: Int,
    val arrivalTime: String?,
    val departureTime: String?
)

/** A timetable pattern, not a live vehicle position or a prediction. */
data class GtfsTripStopSequence(
    val route: GtfsRoute,
    val trip: GtfsTrip,
    val stops: List<GtfsStopCall>
)

class GtfsFormatException(message: String) : IllegalArgumentException(message)

/**
 * Reads the four relevant files of a static GTFS feed without Android APIs, network access,
 * credentials, or a dependency on the order of rows in stop_times.txt. The caller owns the
 * Readers and supplies UTF-8 decoding. Only stops and stop_times for selected trips are kept.
 *
 * A route can contain several directions and stop patterns. [forRoute] deliberately returns
 * every matching trip; callers must choose a specific trip before using a stop sequence for
 * a destination alert. service_id is retained, but calendar validity is not resolved here.
 */
object GtfsStopSequenceParser {

    fun listRoutes(routesCsv: Reader): List<GtfsRoute> =
        readRoutes(routesCsv).values.sortedWith(compareBy<GtfsRoute> { it.shortName }.thenBy { it.id })

    fun forTrip(
        stopsCsv: Reader,
        routesCsv: Reader,
        tripsCsv: Reader,
        stopTimesCsv: Reader,
        tripId: String
    ): GtfsTripStopSequence? {
        val routes = readRoutes(routesCsv)
        val trip = readTrips(tripsCsv, tripId = tripId)[tripId] ?: return null
        return buildSequences(stopsCsv, stopTimesCsv, routes, listOf(trip)).single()
    }

    fun forRoute(
        stopsCsv: Reader,
        routesCsv: Reader,
        tripsCsv: Reader,
        stopTimesCsv: Reader,
        routeId: String,
        directionId: Int? = null
    ): List<GtfsTripStopSequence> {
        val routes = readRoutes(routesCsv)
        if (routeId !in routes) return emptyList()
        val trips = readTrips(tripsCsv, routeId = routeId).values.filter {
            directionId == null || it.directionId == directionId
        }
        if (trips.isEmpty()) return emptyList()
        return buildSequences(stopsCsv, stopTimesCsv, routes, trips)
    }

    /** Returns one representative trip for each distinct direction and ordered stop-id pattern. */
    fun forRoutePatterns(
        stopsCsv: Reader,
        routesCsv: Reader,
        tripsCsv: Reader,
        stopTimesCsv: Reader,
        routeId: String
    ): List<GtfsTripStopSequence> {
        val routes = readRoutes(routesCsv)
        if (routeId !in routes) return emptyList()
        val trips = readTrips(tripsCsv, routeId = routeId).values.toList()
        if (trips.isEmpty()) return emptyList()
        return buildSequences(stopsCsv, stopTimesCsv, routes, trips, distinctPatterns = true)
    }

    private fun readRoutes(reader: Reader): Map<String, GtfsRoute> {
        val routes = linkedMapOf<String, GtfsRoute>()
        readTable(reader, "routes.txt", setOf("route_id")) { row ->
            val id = row.required("route_id")
            val type = row.value("route_type").takeIf { it.isNotEmpty() }?.toIntOrNull()
                ?: if (row.value("route_type").isNotEmpty()) {
                    throw row.error("invalid route_type")
                } else null
            if (routes.put(id, GtfsRoute(id, row.value("route_short_name"),
                    row.value("route_long_name"), type)) != null) {
                throw row.error("duplicate route_id: $id")
            }
        }
        return routes
    }

    private fun readTrips(
        reader: Reader,
        routeId: String? = null,
        tripId: String? = null
    ): Map<String, GtfsTrip> {
        val trips = linkedMapOf<String, GtfsTrip>()
        readTable(reader, "trips.txt", setOf("trip_id", "route_id")) { row ->
            val id = row.required("trip_id")
            val rowRouteId = row.required("route_id")
            if ((routeId != null && rowRouteId != routeId) || (tripId != null && id != tripId)) {
                return@readTable
            }
            val directionRaw = row.value("direction_id")
            val direction = if (directionRaw.isEmpty()) null else directionRaw.toIntOrNull()
                ?.takeIf { it == 0 || it == 1 }
                ?: throw row.error("invalid direction_id: $directionRaw")
            if (trips.put(id, GtfsTrip(
                    id = id,
                    routeId = rowRouteId,
                    serviceId = row.value("service_id").ifEmpty { null },
                    headsign = row.value("trip_headsign").ifEmpty { null },
                    directionId = direction
                )) != null) {
                throw row.error("duplicate trip_id: $id")
            }
        }
        return trips
    }

    private data class RawStopCall(
        val stopId: String,
        val sequence: Int,
        val arrivalTime: String?,
        val departureTime: String?
    )

    private fun buildSequences(
        stopsCsv: Reader,
        stopTimesCsv: Reader,
        routes: Map<String, GtfsRoute>,
        selectedTrips: List<GtfsTrip>,
        distinctPatterns: Boolean = false
    ): List<GtfsTripStopSequence> {
        val callsByTrip = selectedTrips.associate { it.id to mutableListOf<RawStopCall>() }
        readTable(stopTimesCsv, "stop_times.txt", setOf("trip_id", "stop_id", "stop_sequence")) { row ->
            val calls = callsByTrip[row.value("trip_id")] ?: return@readTable
            val sequence = row.required("stop_sequence").toIntOrNull()
                ?.takeIf { it >= 0 } ?: throw row.error("invalid stop_sequence")
            calls.add(RawStopCall(
                stopId = row.required("stop_id"),
                sequence = sequence,
                arrivalTime = row.value("arrival_time").ifEmpty { null },
                departureTime = row.value("departure_time").ifEmpty { null }
            ))
        }

        val representativeTrips = if (distinctPatterns) {
            data class PatternKey(val direction: Int?, val stopIds: List<String>)
            val seen = hashSetOf<PatternKey>()
            selectedTrips.sortedBy { it.id }.filter { trip ->
                val calls = callsByTrip.getValue(trip.id).sortedBy { it.sequence }
                if (calls.isEmpty()) {
                    throw GtfsFormatException("stop_times.txt: no stops for trip ${trip.id}")
                }
                if (calls.zipWithNext().any { (a, b) -> a.sequence == b.sequence }) {
                    throw GtfsFormatException("stop_times.txt: duplicate stop_sequence for trip ${trip.id}")
                }
                seen.add(PatternKey(trip.directionId, calls.map { it.stopId }))
            }
        } else selectedTrips
        val neededStops = representativeTrips.asSequence()
            .flatMap { callsByTrip.getValue(it.id).asSequence() }.map { it.stopId }.toSet()
        val stops = linkedMapOf<String, GtfsStop>()
        readTable(stopsCsv, "stops.txt", setOf("stop_id", "stop_name", "stop_lat", "stop_lon")) { row ->
            val id = row.value("stop_id")
            if (id !in neededStops) return@readTable
            val latitude = row.required("stop_lat").toDoubleOrNull()
                ?.takeIf { it in -90.0..90.0 } ?: throw row.error("invalid stop_lat for $id")
            val longitude = row.required("stop_lon").toDoubleOrNull()
                ?.takeIf { it in -180.0..180.0 } ?: throw row.error("invalid stop_lon for $id")
            if (stops.put(id, GtfsStop(id, row.required("stop_name"), latitude, longitude)) != null) {
                throw row.error("duplicate stop_id: $id")
            }
        }

        return representativeTrips.sortedBy { it.id }.map { trip ->
            val route = routes[trip.routeId]
                ?: throw GtfsFormatException("trips.txt: trip ${trip.id} refers to missing route ${trip.routeId}")
            val rawCalls = callsByTrip.getValue(trip.id).sortedBy { it.sequence }
            if (rawCalls.isEmpty()) {
                throw GtfsFormatException("stop_times.txt: no stops for trip ${trip.id}")
            }
            if (rawCalls.zipWithNext().any { (a, b) -> a.sequence == b.sequence }) {
                throw GtfsFormatException("stop_times.txt: duplicate stop_sequence for trip ${trip.id}")
            }
            GtfsTripStopSequence(route, trip, rawCalls.map { call ->
                val stop = stops[call.stopId] ?: throw GtfsFormatException(
                    "stop_times.txt: trip ${trip.id} refers to missing stop ${call.stopId}"
                )
                GtfsStopCall(stop, call.sequence, call.arrivalTime, call.departureTime)
            })
        }
    }

    private class CsvRow(
        private val table: String,
        private val rowNumber: Int,
        private val columns: Map<String, Int>,
        private val values: List<String>
    ) {
        fun value(name: String): String = columns[name]?.let { values.getOrNull(it) }.orEmpty().trim()
        fun required(name: String): String = value(name).ifEmpty {
            throw error("blank $name")
        }
        fun error(message: String): GtfsFormatException =
            GtfsFormatException("$table row $rowNumber: $message")
    }

    private fun readTable(
        reader: Reader,
        table: String,
        requiredColumns: Set<String>,
        onRow: (CsvRow) -> Unit
    ) {
        val csv = CsvRows(reader, table)
        val headers = csv.next()?.mapIndexed { index, name ->
            if (index == 0) name.removePrefix("\uFEFF").trim() else name.trim()
        } ?: throw GtfsFormatException("$table: empty file")
        if (headers.size != headers.toSet().size) {
            throw GtfsFormatException("$table: duplicate column name")
        }
        val indexes = headers.withIndex().associate { it.value to it.index }
        val missing = requiredColumns - indexes.keys
        if (missing.isNotEmpty()) {
            throw GtfsFormatException("$table: missing columns ${missing.sorted().joinToString()}")
        }
        while (true) {
            val values = csv.next() ?: break
            if (values.all { it.isBlank() }) continue
            onRow(CsvRow(table, csv.rowNumber, indexes, values))
        }
    }

    /** RFC 4180-style quoted fields, including escaped quotes and embedded newlines. */
    private class CsvRows(reader: Reader, private val table: String) {
        private val input = PushbackReader(BufferedReader(reader), 1)
        var rowNumber: Int = 0
            private set

        fun next(): List<String>? {
            val row = mutableListOf<String>()
            val field = StringBuilder()
            var quoted = false
            var afterQuote = false
            var sawCharacter = false
            while (true) {
                val current = input.read()
                if (current == -1) {
                    if (quoted) throw GtfsFormatException("$table row ${rowNumber + 1}: unterminated quoted field")
                    if (!sawCharacter) return null
                    row.add(field.toString())
                    rowNumber++
                    return row
                }
                sawCharacter = true
                val char = current.toChar()
                if (quoted) {
                    if (char == '"') {
                        val next = input.read()
                        if (next == '"'.code) field.append('"')
                        else {
                            quoted = false
                            afterQuote = true
                            if (next != -1) input.unread(next)
                        }
                    } else field.append(char)
                    continue
                }
                when (char) {
                    '"' -> {
                        if (field.isNotEmpty() || afterQuote) {
                            throw GtfsFormatException("$table row ${rowNumber + 1}: unexpected quote")
                        }
                        quoted = true
                    }
                    ',' -> {
                        row.add(field.toString())
                        field.setLength(0)
                        afterQuote = false
                    }
                    '\r', '\n' -> {
                        if (char == '\r') {
                            val next = input.read()
                            if (next != '\n'.code && next != -1) input.unread(next)
                        }
                        row.add(field.toString())
                        rowNumber++
                        return row
                    }
                    else -> {
                        if (afterQuote) {
                            if (!char.isWhitespace()) {
                                throw GtfsFormatException("$table row ${rowNumber + 1}: text after closing quote")
                            }
                        } else field.append(char)
                    }
                }
            }
        }
    }
}
