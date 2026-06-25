package com.superdl.launcher.feedback

import android.media.RingtoneManager

enum class AlertSoundPreset(
    val label: String,
    val ringtoneType: Int? = null,
    val toneSequence: List<Pair<Int, Int>>? = null
) {
    ALARM(
        "Ébresztő hang",
        RingtoneManager.TYPE_ALARM
    ),
    RINGTONE(
        "Csengőhang",
        RingtoneManager.TYPE_RINGTONE
    ),
    NOTIFICATION(
        "Értesítés hang",
        RingtoneManager.TYPE_NOTIFICATION
    ),
    BELL(
        "Csengő – három hang",
        toneSequence = listOf(523 to 120, 659 to 120, 784 to 180)
    ),
    SOFT_CHIME(
        "Lágy csengő",
        toneSequence = listOf(880 to 100, 1175 to 150)
    ),
    DOUBLE_BEEP(
        "Dupla síp",
        toneSequence = listOf(880 to 80, 0 to 60, 880 to 80)
    );

    companion object {
        val selectable: List<AlertSoundPreset> = entries.toList()
    }
}