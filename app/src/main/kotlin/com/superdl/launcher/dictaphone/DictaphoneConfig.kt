package com.superdl.launcher.dictaphone

data class DictaphoneConfig(
    val format: DictaphoneFormat = DictaphoneFormat.WAV,
    val sampleRate: DictaphoneSampleRate = DictaphoneSampleRate.RATE_44K,
    val bitrate: DictaphoneBitrate = DictaphoneBitrate.B192,
    val channels: DictaphoneChannels = DictaphoneChannels.MONO,
    val noiseSuppressionEnabled: Boolean = false
) {
    fun speakSummary(): String {
        val noise = if (noiseSuppressionEnabled) "zajszűrés be" else "zajszűrés ki, nyers hang"
        val ratePart = if (format.isCompressed()) {
            "${format.speakSummary()}, ${sampleRate.speakSummary()}, ${bitrate.speakSummary()}, ${channels.speakSummary()}, $noise"
        } else {
            "${format.speakSummary()}, ${sampleRate.speakSummary()}, ${channels.speakSummary()}, $noise"
        }
        return ratePart
    }
}