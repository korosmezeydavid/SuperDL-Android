package com.superdl.launcher.settings

import android.content.Context
import com.superdl.launcher.callfilter.CallFilterMode
import com.superdl.launcher.callfilter.CallFilterStore
import com.superdl.launcher.feedback.AlertSoundSettingsStore
import com.superdl.launcher.patrol.PatrolStore

/**
 * TÖBB ÁLLÁSÚ BEÁLLÍTÁSOK — VÁLASZTÓVAL, NEM KÖRBEFORGATÁSSAL.
 *
 * A HIBA, AMIT EZ JAVÍT (Alph, 2026-09-17):
 *
 * Ezeket eddig a jobbra söprés KÖRBEFORGATTA, és minden lépés AZONNAL
 * életbe lépett. A hívásszűrőnél ez nem kényelmetlenség, hanem valódi kár:
 * ha a „Mindent fogad"-ról a „Laza"-ra akartál menni, útközben átmentél a
 * „Teljes Ne Zavarj"-on — és ha épp akkor csörgött valaki, azt a telefon
 * eldobta. A felhasználó egy beállítást akart állítani, nem hívást
 * elutasítani.
 *
 * Mostantól a jobbra söprés MENÜT NYIT. Fel-le válogatsz, jobbra
 * érvényesíted. Közben semmi nem lép életbe.
 *
 * A MENÜ A JELENLEGI ÁLLÁSON NYÍLIK. Aki beállítást nyit, tudni akarja,
 * hol tart most — nem a lista elejére akar esni.
 */
enum class ChoiceSetting(val title: String) {

    CALL_FILTER_MODE("Hívás szűrő mód"),
    HIDDEN_CALLS("Rejtett számú hívások"),
    FILTER_ANNOUNCE("Szűrt hívás bemondása"),
    FILTERED_IN_LOG("Szűrt hívások a hívásnaplóban"),
    ALERT_VOLUME("Csengőhang hangerő"),
    TIME_INTERVAL("Idő bemondás gyakorisága"),
    BATTERY_FIRST_ALERT("Első akkumulátor figyelmeztetés");

    /** A választható állások, felolvasható alakban. */
    fun labels(context: Context): List<String> = when (this) {
        CALL_FILTER_MODE -> CallFilterMode.entries.map { it.menuLabel }
        HIDDEN_CALLS -> listOf("Tiltva", "Átengedve")
        FILTER_ANNOUNCE -> CallFilterStore.AnnounceMode.entries.map { it.label }
        FILTERED_IN_LOG -> listOf("Látszik", "Rejtve")
        ALERT_VOLUME -> AlertSoundSettingsStore.VOLUME_STEPS.map { "$it százalék" }
        TIME_INTERVAL -> PatrolStore.TIME_INTERVALS.map { "$it perc" }
        BATTERY_FIRST_ALERT ->
            com.superdl.launcher.battery.BatteryPatrolLogic.FIRST_ALERT_LEVELS
                .map { "$it százalék" }
    }

    /** Melyik álláson van most. */
    fun currentIndex(context: Context): Int = when (this) {
        CALL_FILTER_MODE -> CallFilterMode.entries.indexOf(CallFilterStore.getMode(context))
        HIDDEN_CALLS -> if (CallFilterStore.isHiddenBlocked(context)) 0 else 1
        FILTER_ANNOUNCE ->
            CallFilterStore.AnnounceMode.entries.indexOf(CallFilterStore.announceMode(context))
        FILTERED_IN_LOG -> if (CallFilterStore.isFilteredHiddenInLog(context)) 1 else 0
        ALERT_VOLUME ->
            AlertSoundSettingsStore.VOLUME_STEPS
                .indexOf(AlertSoundSettingsStore.getVolumePercent(context))
        TIME_INTERVAL ->
            PatrolStore.TIME_INTERVALS.indexOf(PatrolStore.getTimeIntervalMinutes(context))
        BATTERY_FIRST_ALERT ->
            com.superdl.launcher.battery.BatteryPatrolLogic.FIRST_ALERT_LEVELS
                .indexOf(PatrolStore.getFirstAlertPercent(context))
    }.coerceAtLeast(0)

    /**
     * Beállítja a választott állást.
     * @return amit a program utána KIMOND — nem elég a „beállítva",
     *         a felhasználónak hallania kell, mi lett belőle.
     */
    fun apply(context: Context, index: Int): String = when (this) {
        CALL_FILTER_MODE -> {
            val mode = CallFilterMode.entries[index]
            CallFilterStore.setMode(context, mode)
            mode.speakLabel
        }
        HIDDEN_CALLS -> {
            val block = index == 0
            CallFilterStore.setHiddenBlocked(context, block)
            if (block) {
                "Rejtett számú hívások tiltva."
            } else {
                "Rejtett számú hívások átengedve. Aki nem mutatja a számát, az is elér."
            }
        }
        FILTER_ANNOUNCE -> {
            val mode = CallFilterStore.AnnounceMode.entries[index]
            CallFilterStore.setAnnounceMode(context, mode)
            mode.speakLabel
        }
        // MIÉRT KELL KIMONDANI, HOGY NEM VÉSZ EL: aki a „rejtve"-t választja,
        // annak tudnia kell, hol találja meg mégis, ha keresné.
        FILTERED_IN_LOG -> {
            val hide = index == 1
            CallFilterStore.setFilteredHiddenInLog(context, hide)
            if (hide) {
                "Szűrt hívások a hívásnaplóban rejtve. A nem fogadott hívások között és " +
                    "a visszahívási kérdésekben sem szerepelnek. A Szűrt hívások " +
                    "listájában továbbra is megtalálod őket."
            } else {
                "Szűrt hívások a hívásnaplóban látszanak."
            }
        }
        ALERT_VOLUME -> {
            val value = AlertSoundSettingsStore.VOLUME_STEPS[index]
            AlertSoundSettingsStore.setVolumePercent(context, value)
            "Csengőhang hangerő: $value százalék."
        }
        TIME_INTERVAL -> {
            val value = PatrolStore.TIME_INTERVALS[index]
            PatrolStore.setTimeIntervalMinutes(context, value)
            "Idő bemondás gyakorisága: $value perc."
        }
        BATTERY_FIRST_ALERT -> {
            val value = com.superdl.launcher.battery.BatteryPatrolLogic.FIRST_ALERT_LEVELS[index]
            PatrolStore.setFirstAlertPercent(context, value)
            "Első akkumulátor figyelmeztetés: $value százalék. " +
                "Onnantól kétszázalékonként ismétlem, amíg töltőre nem teszed."
        }
    }

    /** Kell-e hangminta a beállítás után (a hangerőnél igen). */
    val previewSound: Boolean get() = this == ALERT_VOLUME
}
