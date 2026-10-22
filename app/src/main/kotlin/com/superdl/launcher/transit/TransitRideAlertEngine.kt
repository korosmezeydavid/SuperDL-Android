package com.superdl.launcher.transit

import java.security.MessageDigest
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class TransitRideStatus { NOT_BOARDED, TRACKING, UNCERTAIN, ARRIVED }

/** A location sample supplied by a future Android integration. Times are wall-clock milliseconds. */
data class TransitLocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val timestampMs: Long
)

/** The two *Now* flags are one-time decisions, never persistent state. */
data class TransitRideUpdate(
    val status: TransitRideStatus,
    val currentStopIndex: Int?,
    val nextStopIndex: Int?,
    val stopsRemaining: Int?,
    val prepareToAlightNow: Boolean = false,
    val arrivedNow: Boolean = false
)

/** Primitive fields suitable for later local persistence; contains no location history. */
data class TransitRideCheckpoint(
    val routeId: String,
    val tripId: String,
    val sequenceSignature: String,
    val boardingStopIndex: Int,
    val destinationStopIndex: Int,
    val currentStopIndex: Int,
    val status: TransitRideStatus,
    val alertSent: Boolean,
    val arrivalSent: Boolean
)

/**
 * Conservative, side-effect-free-of-Android stop progress for one explicitly chosen GTFS trip.
 * Both indices refer to occurrences in [sequence.stops], not stop_id: circular trips may visit
 * a stop twice. The caller must explicitly invoke [board] after the passenger boards.
 *
 * Two fresh, accurate, unambiguous fixes at the next stop are needed to advance. The engine
 * never guesses past a skipped stop; [correctCurrentStop] lets the passenger resolve uncertainty.
 * It does not determine which vehicle the passenger boarded or whether this trip runs today.
 */
class TransitRideAlertEngine(
    val sequence: GtfsTripStopSequence,
    val boardingStopIndex: Int,
    val destinationStopIndex: Int
) {
    companion object {
        private const val MAX_FIX_AGE_MS = 15_000L
        private const val MAX_ACCURACY_M = 20.0
        private const val STOP_RADIUS_M = 35.0
        private const val AMBIGUITY_MARGIN_M = 12.0
        private const val MIN_CONFIRMATION_MS = 2_000L
        private const val MAX_CONFIRMATION_MS = 15_000L
        private const val MAX_PLAUSIBLE_SPEED_MPS = 45.0
        private const val EARTH_RADIUS_M = 6_371_000.0

        /** Rejects checkpoints from another trip, stop pattern, direction, or index selection. */
        fun restore(
            sequence: GtfsTripStopSequence,
            boardingStopIndex: Int,
            destinationStopIndex: Int,
            checkpoint: TransitRideCheckpoint
        ): TransitRideAlertEngine? {
            if (boardingStopIndex !in sequence.stops.indices ||
                destinationStopIndex !in sequence.stops.indices ||
                destinationStopIndex <= boardingStopIndex
            ) return null
            val engine = TransitRideAlertEngine(sequence, boardingStopIndex, destinationStopIndex)
            if (checkpoint.routeId != sequence.route.id ||
                checkpoint.tripId != sequence.trip.id ||
                checkpoint.sequenceSignature != engine.sequenceSignature() ||
                checkpoint.boardingStopIndex != boardingStopIndex ||
                checkpoint.destinationStopIndex != destinationStopIndex ||
                checkpoint.currentStopIndex !in boardingStopIndex..destinationStopIndex ||
                checkpoint.status == TransitRideStatus.NOT_BOARDED ||
                (checkpoint.status == TransitRideStatus.ARRIVED) !=
                    (checkpoint.currentStopIndex == destinationStopIndex) ||
                (checkpoint.status == TransitRideStatus.ARRIVED && !checkpoint.arrivalSent)
            ) return null
            engine.currentStopIndex = checkpoint.currentStopIndex
            engine.alertSent = checkpoint.alertSent
            engine.arrivalSent = checkpoint.arrivalSent
            // No recent GPS evidence survives a process restart. Keep progress, but require
            // new confirmation before presenting the session as confidently tracking.
            engine.status = if (checkpoint.status == TransitRideStatus.ARRIVED) {
                TransitRideStatus.ARRIVED
            } else TransitRideStatus.UNCERTAIN
            return engine
        }
    }

    init {
        require(boardingStopIndex in sequence.stops.indices) { "Invalid boarding stop index" }
        require(destinationStopIndex in sequence.stops.indices) { "Invalid destination stop index" }
        require(destinationStopIndex > boardingStopIndex) { "Destination must follow boarding" }
    }

    private var status = TransitRideStatus.NOT_BOARDED
    private var currentStopIndex: Int? = null
    private var alertSent = false
    private var arrivalSent = false
    private var candidateStopIndex: Int? = null
    private var candidateStartedMs: Long? = null
    private var lastReliableFix: TransitLocationFix? = null

    /** Explicit passenger confirmation. A one-stop ride warns immediately after boarding. */
    fun board(): TransitRideUpdate {
        if (status != TransitRideStatus.NOT_BOARDED) return snapshot()
        currentStopIndex = boardingStopIndex
        status = TransitRideStatus.TRACKING
        val prepare = destinationStopIndex == boardingStopIndex + 1
        if (prepare) alertSent = true
        return snapshot(prepare = prepare)
    }

    /**
     * Takes only the next stop into account. Low-quality, old, ambiguous, out-of-order, or
     * implausibly jumping samples set UNCERTAIN and never cause a stop or alert transition.
     */
    fun onLocation(fix: TransitLocationFix, observedAtMs: Long): TransitRideUpdate {
        if (status == TransitRideStatus.NOT_BOARDED || status == TransitRideStatus.ARRIVED) {
            return snapshot()
        }
        if (!isReliable(fix, observedAtMs) || isImplausibleJump(fix)) {
            return uncertain()
        }
        lastReliableFix = fix
        val current = currentStopIndex ?: return uncertain()
        val next = current + 1
        val distances = sequence.stops.map { call ->
            distanceMeters(fix.latitude, fix.longitude, call.stop.latitude, call.stop.longitude)
        }
        val nextDistance = distances[next]
        val nearNext = nextDistance + fix.accuracyMeters <= STOP_RADIUS_M
        val otherDistance = distances.withIndex()
            .filter { it.index != next }
            .minOfOrNull { it.value } ?: Double.POSITIVE_INFINITY
        val unambiguous = otherDistance - nextDistance > fix.accuracyMeters + AMBIGUITY_MARGIN_M

        if (nearNext && !unambiguous) return uncertain()
        if (!nearNext) {
            clearCandidate()
            // A selected trip can branch or cross itself. A fix at some *other* stop cannot
            // prove progress to the immediately next stop, even if its GPS accuracy is good.
            val nearest = distances.indices.minByOrNull { distances[it] } ?: return uncertain()
            return if (nearest != current &&
                distances[nearest] + fix.accuracyMeters <= STOP_RADIUS_M
            ) uncertain() else tracking()
        }

        val started = candidateStartedMs
        if (candidateStopIndex != next || started == null ||
            fix.timestampMs - started > MAX_CONFIRMATION_MS
        ) {
            candidateStopIndex = next
            candidateStartedMs = fix.timestampMs
            return tracking()
        }
        if (fix.timestampMs - started < MIN_CONFIRMATION_MS) return tracking()
        return advanceTo(next)
    }

    /** Explicit correction, including backward correction after an erroneous GPS advance. */
    fun correctCurrentStop(index: Int): TransitRideUpdate {
        require(status != TransitRideStatus.NOT_BOARDED) { "Boarding has not been confirmed" }
        require(index in boardingStopIndex..destinationStopIndex) { "Stop index outside this ride" }
        clearCandidate()
        lastReliableFix = null
        return advanceTo(index)
    }

    fun snapshot(): TransitRideUpdate = snapshot(prepare = false, arrived = false)

    /** Called when Android stops delivering location samples during an active ride. */
    fun markLocationUnavailable(): TransitRideUpdate =
        if (status == TransitRideStatus.NOT_BOARDED || status == TransitRideStatus.ARRIVED) {
            snapshot()
        } else uncertain()

    /** Returns null until [board] was explicitly confirmed. */
    fun checkpoint(): TransitRideCheckpoint? {
        val current = currentStopIndex ?: return null
        return TransitRideCheckpoint(
            routeId = sequence.route.id,
            tripId = sequence.trip.id,
            sequenceSignature = sequenceSignature(),
            boardingStopIndex = boardingStopIndex,
            destinationStopIndex = destinationStopIndex,
            currentStopIndex = current,
            status = status,
            alertSent = alertSent,
            arrivalSent = arrivalSent
        )
    }

    private fun advanceTo(index: Int): TransitRideUpdate {
        currentStopIndex = index
        clearCandidate()
        val prepare = index == destinationStopIndex - 1 && !alertSent
        if (prepare) alertSent = true
        val arrived = index == destinationStopIndex && !arrivalSent
        if (arrived) arrivalSent = true
        status = if (index == destinationStopIndex) TransitRideStatus.ARRIVED
        else TransitRideStatus.TRACKING
        return snapshot(prepare, arrived)
    }

    private fun tracking(): TransitRideUpdate {
        status = TransitRideStatus.TRACKING
        return snapshot()
    }

    private fun uncertain(): TransitRideUpdate {
        clearCandidate()
        status = TransitRideStatus.UNCERTAIN
        return snapshot()
    }

    private fun clearCandidate() {
        candidateStopIndex = null
        candidateStartedMs = null
    }

    private fun snapshot(prepare: Boolean, arrived: Boolean = false): TransitRideUpdate {
        val current = currentStopIndex
        return TransitRideUpdate(
            status = status,
            currentStopIndex = current,
            nextStopIndex = current?.takeIf { it < destinationStopIndex }?.plus(1),
            stopsRemaining = current?.let { destinationStopIndex - it },
            prepareToAlightNow = prepare,
            arrivedNow = arrived
        )
    }

    private fun isReliable(fix: TransitLocationFix, observedAtMs: Long): Boolean {
        if (!fix.latitude.isFinite() || fix.latitude !in -90.0..90.0 ||
            !fix.longitude.isFinite() || fix.longitude !in -180.0..180.0 ||
            !fix.accuracyMeters.isFinite() || fix.accuracyMeters < 0.0 ||
            fix.accuracyMeters > MAX_ACCURACY_M ||
            fix.timestampMs > observedAtMs || observedAtMs - fix.timestampMs > MAX_FIX_AGE_MS
        ) return false
        val previous = lastReliableFix
        return previous == null || fix.timestampMs > previous.timestampMs
    }

    private fun isImplausibleJump(fix: TransitLocationFix): Boolean {
        val previous = lastReliableFix ?: return false
        val elapsedSeconds = (fix.timestampMs - previous.timestampMs) / 1000.0
        if (elapsedSeconds <= 0.0) return true
        val distance = distanceMeters(
            previous.latitude, previous.longitude, fix.latitude, fix.longitude
        )
        // Allow each fix's uncertainty before judging the apparent speed.
        val definiteDistance = (distance - previous.accuracyMeters - fix.accuracyMeters).coerceAtLeast(0.0)
        return definiteDistance / elapsedSeconds > MAX_PLAUSIBLE_SPEED_MPS
    }

    private fun distanceMeters(latA: Double, lonA: Double, latB: Double, lonB: Double): Double {
        val dLat = Math.toRadians(latB - latA)
        val dLon = Math.toRadians(lonB - lonA)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(latA)) * cos(Math.toRadians(latB)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    private fun sequenceSignature(): String {
        val canonical = buildString {
            fun field(value: String) {
                append(value.length).append(':').append(value)
            }
            field(sequence.route.id)
            field(sequence.trip.id)
            sequence.stops.forEach { call ->
                field(call.stop.id)
                field(call.stopSequence.toString())
                field(java.lang.Double.doubleToLongBits(call.stop.latitude).toString())
                field(java.lang.Double.doubleToLongBits(call.stop.longitude).toString())
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        val hex = "0123456789abcdef"
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }
}
