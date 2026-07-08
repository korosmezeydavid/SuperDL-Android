package com.superdl.launcher.currency

class BanknoteConsensusFilter(
    private val windowSize: Int = 4,
    private val requiredAgreements: Int = 3
) {
    private val recent = ArrayDeque<BanknoteClassificationResult?>(windowSize)

    fun submit(
        frameDecision: BanknoteFrameGate.Decision,
        result: BanknoteClassificationResult?
    ): BanknoteClassificationResult? {
        val candidate = when {
            frameDecision.isEmptySlot -> null
            result == null -> null
            result.isReliable(strictColor = false) -> result
            else -> null
        }

        if (recent.size >= windowSize) recent.removeFirst()
        recent.addLast(candidate)

        if (recent.size < windowSize) return null

        val reliable = recent.filterNotNull()
        if (reliable.size < requiredAgreements) return null

        val counts = reliable.groupingBy { it.denomination }.eachCount()
        val winner = counts.maxByOrNull { it.value } ?: return null
        if (winner.value < requiredAgreements) return null

        return reliable
            .filter { it.denomination == winner.key }
            .maxByOrNull { it.confidence }
    }

    fun reset() {
        recent.clear()
    }
}