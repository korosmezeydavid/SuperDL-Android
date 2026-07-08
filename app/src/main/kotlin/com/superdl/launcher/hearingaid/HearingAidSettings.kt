package com.superdl.launcher.hearingaid

data class HearingAidSettings(
    val masterGain: Float = 1.5f,
    val micGain: Float = 1.2f,
    val bassGain: Float = 1.0f,
    val midGain: Float = 1.0f,
    val trebleGain: Float = 1.0f,
    val balance: BalanceMode = BalanceMode.BOTH
) {
    enum class BalanceMode { LEFT, BOTH, RIGHT }

    fun speakSummary(): String = buildString {
        append("Hallás erősítő. ")
        append("Fő erősítés: ${percent(masterGain)}. ")
        append("Mikrofon: ${percent(micGain)}. ")
        append("Mély: ${percent(bassGain)}. ")
        append("Közép: ${percent(midGain)}. ")
        append("Magas: ${percent(trebleGain)}. ")
        append("Balansz: ${balance.speakHu()}.")
    }

    private fun percent(value: Float): String = "${(value * 100).toInt()} százalék"

    companion object {
        const val GAIN_MIN = 0.5f
        const val GAIN_MAX = 3.0f
        const val GAIN_STEP = 0.25f
    }
}

fun HearingAidSettings.BalanceMode.speakHu(): String = when (this) {
    HearingAidSettings.BalanceMode.LEFT -> "bal fül"
    HearingAidSettings.BalanceMode.BOTH -> "mindkét fül"
    HearingAidSettings.BalanceMode.RIGHT -> "jobb fül"
}