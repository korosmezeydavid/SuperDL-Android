package com.superdl.launcher.transit

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** One selected, static GTFS trip and its local progress. No coordinates from the passenger are stored. */
data class TransitRideSession(
    val sequence: GtfsTripStopSequence,
    val boardingStopIndex: Int,
    val destinationStopIndex: Int,
    val checkpoint: TransitRideCheckpoint?
)

/** Bounded binary format so the Android store needs no network, GTFS download, or JSON library. */
object TransitRideSessionCodec {
    private const val VERSION = 1
    private const val MAX_STOPS = 1_000
    const val MAX_BYTES = 512_000

    fun encode(session: TransitRideSession): ByteArray {
        require(isValidSelection(session)) { "Invalid selected GTFS trip" }
        require(session.checkpoint == null || validCheckpoint(session)) { "Invalid ride checkpoint" }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(VERSION)
            val route = session.sequence.route
            val trip = session.sequence.trip
            out.writeUTF(route.id)
            out.writeUTF(route.shortName)
            out.writeUTF(route.longName)
            out.writeOptionalInt(route.type)
            out.writeUTF(trip.id)
            out.writeUTF(trip.routeId)
            out.writeOptionalString(trip.serviceId)
            out.writeOptionalString(trip.headsign)
            out.writeOptionalInt(trip.directionId)
            out.writeInt(session.sequence.stops.size)
            session.sequence.stops.forEach { call ->
                out.writeUTF(call.stop.id)
                out.writeUTF(call.stop.name)
                out.writeDouble(call.stop.latitude)
                out.writeDouble(call.stop.longitude)
                out.writeInt(call.stopSequence)
                out.writeOptionalString(call.arrivalTime)
                out.writeOptionalString(call.departureTime)
            }
            out.writeInt(session.boardingStopIndex)
            out.writeInt(session.destinationStopIndex)
            val checkpoint = session.checkpoint
            out.writeBoolean(checkpoint != null)
            if (checkpoint != null) {
                out.writeUTF(checkpoint.routeId)
                out.writeUTF(checkpoint.tripId)
                out.writeUTF(checkpoint.sequenceSignature)
                out.writeInt(checkpoint.boardingStopIndex)
                out.writeInt(checkpoint.destinationStopIndex)
                out.writeInt(checkpoint.currentStopIndex)
                out.writeUTF(checkpoint.status.name)
                out.writeBoolean(checkpoint.alertSent)
                out.writeBoolean(checkpoint.arrivalSent)
            }
        }
        return bytes.toByteArray().also {
            require(it.size <= MAX_BYTES) { "Selected trip exceeds local storage limit" }
        }
    }

    /** Returns null for a truncated, stale-version, inconsistent, or corrupted private record. */
    fun decode(bytes: ByteArray): TransitRideSession? {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readInt() != VERSION) return null
                val route = GtfsRoute(
                    input.readUTF(), input.readUTF(), input.readUTF(), input.readOptionalInt()
                )
                val trip = GtfsTrip(
                    input.readUTF(), input.readUTF(), input.readOptionalString(),
                    input.readOptionalString(), input.readOptionalInt()
                )
                val count = input.readInt()
                if (count !in 2..MAX_STOPS) return null
                val stops = ArrayList<GtfsStopCall>(count)
                repeat(count) {
                    val stop = GtfsStop(
                        input.readUTF(), input.readUTF(), input.readDouble(), input.readDouble()
                    )
                    stops.add(GtfsStopCall(
                        stop, input.readInt(), input.readOptionalString(), input.readOptionalString()
                    ))
                }
                val sequence = GtfsTripStopSequence(route, trip, stops)
                val boarding = input.readInt()
                val destination = input.readInt()
                val checkpoint = if (input.readBoolean()) TransitRideCheckpoint(
                    routeId = input.readUTF(),
                    tripId = input.readUTF(),
                    sequenceSignature = input.readUTF(),
                    boardingStopIndex = input.readInt(),
                    destinationStopIndex = input.readInt(),
                    currentStopIndex = input.readInt(),
                    status = TransitRideStatus.valueOf(input.readUTF()),
                    alertSent = input.readBoolean(),
                    arrivalSent = input.readBoolean()
                ) else null
                val session = TransitRideSession(sequence, boarding, destination, checkpoint)
                if (input.available() != 0 || !isValidSelection(session) ||
                    (checkpoint != null && !validCheckpoint(session))
                ) null else session
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun isValidSelection(session: TransitRideSession): Boolean {
        val sequence = session.sequence
        if (sequence.route.id.isBlank() || sequence.trip.id.isBlank() ||
            sequence.trip.routeId != sequence.route.id ||
            sequence.stops.size !in 2..MAX_STOPS ||
            session.boardingStopIndex !in sequence.stops.indices ||
            session.destinationStopIndex !in sequence.stops.indices ||
            session.destinationStopIndex <= session.boardingStopIndex
        ) return false
        var previousSequence = Int.MIN_VALUE
        for (call in sequence.stops) {
            if (call.stop.id.isBlank() || call.stopSequence <= previousSequence ||
                !call.stop.latitude.isFinite() || call.stop.latitude !in -90.0..90.0 ||
                !call.stop.longitude.isFinite() || call.stop.longitude !in -180.0..180.0
            ) return false
            previousSequence = call.stopSequence
        }
        return true
    }

    private fun validCheckpoint(session: TransitRideSession): Boolean {
        val checkpoint = session.checkpoint ?: return true
        if (checkpoint.currentStopIndex >= session.destinationStopIndex - 1 &&
            !checkpoint.alertSent
        ) return false
        if (checkpoint.currentStopIndex == session.destinationStopIndex &&
            !checkpoint.arrivalSent
        ) return false
        return TransitRideAlertEngine.restore(
            session.sequence, session.boardingStopIndex, session.destinationStopIndex, checkpoint
        ) != null
    }

    private fun DataOutputStream.writeOptionalString(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private fun DataInputStream.readOptionalString(): String? =
        if (readBoolean()) readUTF() else null

    private fun DataOutputStream.writeOptionalInt(value: Int?) {
        writeBoolean(value != null)
        if (value != null) writeInt(value)
    }

    private fun DataInputStream.readOptionalInt(): Int? =
        if (readBoolean()) readInt() else null
}
