package com.superdl.launcher.currency

class BanknoteScanDebouncer(
    private val cooldownMs: Long = 3200L,
    private val absenceFramesRequired: Int = 6
) {
    private var billInFrame = false
    private var absenceStreak = 0
    private var lastAnnouncedDenomination: BanknoteDenomination? = null
    private var lastAnnouncedAt: Long = 0L

    fun onAbsentFrame(): BillPresenceEvent {
        if (!billInFrame) {
            absenceStreak = 0
            return BillPresenceEvent.NONE
        }
        absenceStreak++
        if (absenceStreak >= absenceFramesRequired) {
            billInFrame = false
            absenceStreak = 0
            lastAnnouncedDenomination = null
            lastAnnouncedAt = 0L
            return BillPresenceEvent.REMOVED
        }
        return BillPresenceEvent.STILL_SCANNING
    }

    fun onDetected(result: BanknoteClassificationResult, now: Long = System.currentTimeMillis()): ScanDecision {
        if (!result.isReliable(strictColor = false)) {
            return when (onAbsentFrame()) {
                BillPresenceEvent.REMOVED -> ScanDecision.BillRemoved
                else -> ScanDecision.Ignored
            }
        }

        absenceStreak = 0
        val entered = !billInFrame
        billInFrame = true

        if (entered) {
            return ScanDecision.Announce(result, playEntryBeep = true)
        }

        val sameAsLast = result.denomination == lastAnnouncedDenomination
        val withinCooldown = now - lastAnnouncedAt < cooldownMs
        if (sameAsLast && withinCooldown) {
            return ScanDecision.Ignored
        }

        return ScanDecision.Announce(result, playEntryBeep = false)
    }

    fun markAnnounced(denomination: BanknoteDenomination, now: Long = System.currentTimeMillis()) {
        lastAnnouncedDenomination = denomination
        lastAnnouncedAt = now
    }

    fun reset() {
        billInFrame = false
        absenceStreak = 0
        lastAnnouncedDenomination = null
        lastAnnouncedAt = 0L
    }

    enum class BillPresenceEvent {
        NONE,
        STILL_SCANNING,
        REMOVED
    }

    sealed class ScanDecision {
        data object Ignored : ScanDecision()
        data object BillRemoved : ScanDecision()
        data class Announce(
            val result: BanknoteClassificationResult,
            val playEntryBeep: Boolean
        ) : ScanDecision()
    }
}