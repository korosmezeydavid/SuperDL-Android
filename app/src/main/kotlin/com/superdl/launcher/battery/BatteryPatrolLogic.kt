package com.superdl.launcher.battery

object BatteryPatrolLogic {

    /**
     * Az ELSŐ figyelmeztetés választható szintjei.
     *
     * MIÉRT LETT ÁLLÍTHATÓ: eddig fixen 20%-nál szólalt meg először, és ezt
     * senki nem tudta megváltoztatni. Van, akinek ez késő (nagy fogyasztás,
     * hosszú út), és van, akinek korai.
     */
    val FIRST_ALERT_LEVELS = listOf(5, 10, 15, 20, 25, 30, 40, 50)

    const val DEFAULT_FIRST_ALERT = 20

    /**
     * A figyelmeztetési létra az első szinttől lefelé, kettesével, 2%-ig.
     *
     * MIÉRT LÉTRA ÉS NEM EGYETLEN JELZÉS: vakon egyetlen figyelmeztetés
     * kevés — épp lehet a zsebben a telefon, vagy szólhat a rádió. A
     * kétszázalékos lépcső addig ismétel, amíg tényleg tudomásul veszik.
     */
    fun thresholdsFor(firstAlert: Int): List<Int> {
        val start = FIRST_ALERT_LEVELS.firstOrNull { it == firstAlert } ?: DEFAULT_FIRST_ALERT
        return generateSequence(start) { it - 2 }.takeWhile { it >= 2 }.toList()
    }

    /** Visszafelé kompatibilis alapérték (a régi hívások miatt). */
    val THRESHOLDS = thresholdsFor(DEFAULT_FIRST_ALERT)

    fun thresholdToAlert(level: Int, lastAlerted: Int, firstAlert: Int = DEFAULT_FIRST_ALERT): Int? {
        for (threshold in thresholdsFor(firstAlert)) {
            if (level <= threshold && lastAlerted > threshold) return threshold
        }
        return null
    }

    /**
     * Mikor nullázzuk a figyelmeztetés-állapotot: töltéskor, vagy ha a szint
     * az első küszöb fölé emelkedett (két százalék ráhagyással, hogy a
     * lebegő érték ne kapcsolgasson).
     */
    fun shouldReset(level: Int, isCharging: Boolean, firstAlert: Int = DEFAULT_FIRST_ALERT): Boolean =
        isCharging || level > firstAlert + 2

    fun speakMessage(threshold: Int): String {
        val urgency = when {
            threshold <= 4 -> "Kritikus! "
            threshold <= 10 -> "Sürgős figyelmeztetés! "
            else -> "Figyelem! "
        }
        return "$urgency Az akkumulátor töltöttsége $threshold százalék. Csatlakoztasd a töltőt!"
    }
}