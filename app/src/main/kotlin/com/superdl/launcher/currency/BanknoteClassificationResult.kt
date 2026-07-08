package com.superdl.launcher.currency

import android.graphics.RectF

enum class BanknotePipelineMode {
    TWO_STAGE,
    ROI_FALLBACK,
    FULL_FRAME_FALLBACK
}

data class BanknoteClassificationResult(
    val denomination: BanknoteDenomination,
    val confidence: Float,
    val secondBestConfidence: Float = 0f,
    val noneConfidence: Float = 0f,
    val colorVerdict: BanknoteColorVerifier.Verdict = BanknoteColorVerifier.Verdict.NEUTRAL,
    val detectionConfidence: Float = 0f,
    val pipelineMode: BanknotePipelineMode = BanknotePipelineMode.FULL_FRAME_FALLBACK,
    val detectionBox: RectF? = null
) {
    val fusedConfidence: Float
        get() = when (pipelineMode) {
            BanknotePipelineMode.TWO_STAGE ->
                (confidence * 0.72f) + (detectionConfidence.coerceAtLeast(0f) * 0.28f)
            else -> confidence
        }
    fun isReliable(
        threshold: Float = CONFIDENCE_THRESHOLD,
        margin: Float = MIN_TOP_MARGIN,
        strictColor: Boolean = true
    ): Boolean {
        val score = fusedConfidence
        if (score < threshold) return false
        if (pipelineMode == BanknotePipelineMode.TWO_STAGE && detectionConfidence < DETECTION_THRESHOLD) return false
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
        const val CONFIDENCE_THRESHOLD = 0.52f
        const val DETECTION_THRESHOLD = 0.55f
        const val MIN_TOP_MARGIN = 0.10f
        const val NONE_COMPETE_MARGIN = 0.06f
        const val MANUAL_CONFIDENCE_THRESHOLD = 0.45f
        const val MANUAL_TOP_MARGIN = 0.08f
    }
}