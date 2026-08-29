package com.superdl.launcher.fitness

import android.content.Context

/**
 * A LÉPÉSEKBŐL SZÁMÍTOTT ADATOK.
 *
 * ŐSZINTÉN A PONTOSSÁGRÓL: ezek BECSLÉSEK, nem mérések. A lépéshossz a
 * testmagasságból, a kalória a testsúlyból és a megtett távolságból számol.
 * Egy sportóra pontosabb — de a nagyságrend és a napi trend követésére ez
 * bőven elég, és nem kell hozzá semmilyen külön eszköz.
 */
object FitnessCalculator {

    /**
     * Lépéshossz méterben, a testmagasságból.
     * Bevett becslés: a magasság 41,5 százaléka.
     */
    fun strideMeters(heightCm: Int): Double = heightCm * 0.415 / 100.0

    /** Megtett távolság méterben. */
    fun distanceMeters(steps: Int, heightCm: Int): Double = steps * strideMeters(heightCm)

    /**
     * Elégetett kalória (kilokalória).
     *
     * A gyaloglás energiaigénye jó közelítéssel a TESTSÚLY és a TÁVOLSÁG
     * szorzata: körülbelül 0,5 kilokalória testsúly-kilogrammonként és
     * kilométerenként.
     */
    fun calories(steps: Int, heightCm: Int, weightKg: Int): Double {
        val km = distanceMeters(steps, heightCm) / 1000.0
        return km * weightKg * 0.5
    }

    /** Felolvasható távolság. */
    fun speakDistance(meters: Double): String = when {
        meters < 1000 -> "${meters.toInt()} méter"
        else -> {
            val km = meters / 1000.0
            "%.1f kilométer".format(km).replace('.', ',')
        }
    }

    /** Felolvasható sebesség kilométer per órában. */
    fun speakSpeed(metersPerSecond: Float): String {
        if (metersPerSecond <= 0.2f) return "állsz"
        val kmh = metersPerSecond * 3.6f
        val pace = when {
            kmh < 3f -> "lassú séta"
            kmh < 5f -> "séta"
            kmh < 7f -> "gyors séta"
            kmh < 12f -> "futás"
            kmh < 30f -> "kerékpár"
            else -> "jármű"
        }
        return "%.1f kilométer per óra, $pace".format(kmh).replace('.', ',')
    }

    /** A napi összefoglaló felolvasható alakja. */
    fun speakSummary(context: Context): String {
        val steps = StepCounterStore.todaySteps(context)
        val height = StepCounterStore.getHeightCm(context)
        val weight = StepCounterStore.getWeightKg(context)
        val goal = StepCounterStore.getDailyGoal(context)

        if (steps == 0) {
            return "Ma még nincs rögzített lépés. A számláló a háttérben dolgozik."
        }
        val distance = distanceMeters(steps, height)
        val kcal = calories(steps, height, weight)
        val percent = (steps * 100 / goal.coerceAtLeast(1)).coerceAtMost(999)

        val goalPart = if (steps >= goal) {
            "A napi célt teljesítetted!"
        } else {
            "Ez a napi cél $percent százaléka, még ${goal - steps} lépés van hátra."
        }
        return "Ma $steps lépés. ${speakDistance(distance)}. " +
            "Körülbelül ${kcal.toInt()} kalória. $goalPart"
    }
}
