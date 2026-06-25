package com.superdl.launcher.currency

data class BanknoteClassificationResult(
    val denomination: BanknoteDenomination,
    val confidence: Float,
    val secondBestConfidence: Float = 0f,
    val noneConfidence: Float = 0f,
    val colorVerdict: BanknoteColorVerifier.Verdict = BanknoteColorVerifier.Verdict.NEUTRAL
) {
    fun isReliable(
        threshold: Float = CONFIDENCE_THRESHOLD,
        margin: Float = MIN_TOP_MARGIN,
        strictColor: Boolean = true
    ): Boolean {
        if (confidence < threshold) return false
        if (confidence - secondBestConfidence < margin) return false
        if (noneConfidence >= confidence - NONE_COMPETE_MARGIN) return false
        if (strictColor && colorVerdict == BanknoteColorVerifier.Verdict.DISAGREE) return false
        return true
    }

    fun isReliableForManualCheck(): Boolean =
        isReliable(
            threshold = MANUAL_CONFIDENCE_THRESHOLD,
            margin = MANUAL_TOP_MARGIN,
            strictColor = false
        )

    companion object {
        const val CONFIDENCE_THRESHOLD = 0.42f
        const val MIN_TOP_MARGIN = 0.05f
        const val NONE_COMPETE_MARGIN = 0.04f
        const val MANUAL_CONFIDENCE_THRESHOLD = 0.38f
        const val MANUAL_TOP_MARGIN = 0.04f
    }
}