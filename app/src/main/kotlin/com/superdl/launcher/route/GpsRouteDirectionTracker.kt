package com.superdl.launcher.route

/** The travel direction inferred from successive positions on a recorded route. */
enum class GpsRouteTravelDirection {
    UNKNOWN,
    FORWARD,
    REVERSE
}

/**
 * Infers direction from small changes in the nearest recorded point index.
 *
 * A single GPS jitter does not reverse a confirmed direction. Two consecutive
 * opposite observations make it unknown, so the guide can withhold directional
 * cues; four opposite observations confirm the reversal. An abrupt index jump is never used
 * as direction evidence and starts a new observation window instead.
 *
 * This class has no Android dependency. Create one tracker for each guidance
 * session and call [reset] when that session ends or the route changes.
 */
class GpsRouteDirectionTracker(
    private val confirmObservations: Int = 3,
    private val uncertaintyObservations: Int = 2,
    private val reverseObservations: Int = 4,
    private val maxIndexStep: Int = 4
) {
    init {
        require(confirmObservations > 0)
        require(uncertaintyObservations > 0)
        require(reverseObservations >= maxOf(confirmObservations, uncertaintyObservations))
        require(maxIndexStep > 0)
    }

    var direction: GpsRouteTravelDirection = GpsRouteTravelDirection.UNKNOWN
        private set

    private var lastIndex: Int? = null
    private var lastConfirmedDirection = GpsRouteTravelDirection.UNKNOWN
    private var candidateDirection = GpsRouteTravelDirection.UNKNOWN
    private var candidateObservations = 0

    fun observe(pointIndex: Int): GpsRouteTravelDirection {
        if (pointIndex < 0) {
            reset()
            return direction
        }

        val previous = lastIndex
        lastIndex = pointIndex
        if (previous == null) return direction

        val delta = pointIndex.toLong() - previous.toLong()
        if (delta == 0L) return direction
        if (kotlin.math.abs(delta) > maxIndexStep) {
            // Rebase at the new point, but do not infer movement from a GPS jump.
            clearEvidence()
            return direction
        }

        val observedDirection = if (delta > 0) {
            GpsRouteTravelDirection.FORWARD
        } else {
            GpsRouteTravelDirection.REVERSE
        }
        candidateObservations = if (candidateDirection == observedDirection) {
            (candidateObservations + 1).coerceAtMost(reverseObservations)
        } else {
            candidateDirection = observedDirection
            1
        }

        when {
            direction == observedDirection -> Unit
            direction != GpsRouteTravelDirection.UNKNOWN -> {
                when {
                    candidateObservations >= reverseObservations -> confirm(observedDirection)
                    candidateObservations >= uncertaintyObservations -> direction = GpsRouteTravelDirection.UNKNOWN
                }
            }
            else -> {
                val needed = if (lastConfirmedDirection != GpsRouteTravelDirection.UNKNOWN &&
                    observedDirection != lastConfirmedDirection
                ) reverseObservations else confirmObservations
                if (candidateObservations >= needed) confirm(observedDirection)
            }
        }
        return direction
    }

    fun reset() {
        lastIndex = null
        clearEvidence()
    }

    private fun clearEvidence() {
        direction = GpsRouteTravelDirection.UNKNOWN
        lastConfirmedDirection = GpsRouteTravelDirection.UNKNOWN
        candidateDirection = GpsRouteTravelDirection.UNKNOWN
        candidateObservations = 0
    }

    private fun confirm(value: GpsRouteTravelDirection) {
        direction = value
        lastConfirmedDirection = value
    }
}
