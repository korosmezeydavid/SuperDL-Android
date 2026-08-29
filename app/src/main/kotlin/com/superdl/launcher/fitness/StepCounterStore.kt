package com.superdl.launcher.fitness

import android.content.Context
import java.util.Calendar

/**
 * LÉPÉSSZÁMLÁLÓ — a napi adatok tárolása és számítása.
 *
 * MŰKÖDÉS: a telefon beépített lépésérzékelője az UTOLSÓ ÚJRAINDÍTÁS ÓTA
 * összes lépést adja vissza, nem a napit. Ezért eltároljuk a nap eleji
 * állást, és abból számoljuk a mai lépéseket. Ha a telefon újraindul, az
 * érzékelő nulláról kezd — ezt is kezeljük.
 */
object StepCounterStore {

    private const val PREFS = "superdl_steps"
    private const val KEY_DAY = "day"
    private const val KEY_BASE = "base_count"
    private const val KEY_TODAY = "today_steps"
    private const val KEY_GOAL = "daily_goal"
    private const val KEY_HEIGHT = "height_cm"
    private const val KEY_WEIGHT = "weight_kg"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun todayKey(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * Új érzékelő-adat feldolgozása.
     * @param sensorTotal az érzékelő szerinti összes lépés
     * @return a MAI lépésszám
     */
    fun update(context: Context, sensorTotal: Int): Int {
        val p = prefs(context)
        val today = todayKey()
        val savedDay = p.getInt(KEY_DAY, -1)
        var base = p.getInt(KEY_BASE, -1)

        if (savedDay != today || base < 0) {
            // Új nap: a mostani állás lesz a kiindulópont.
            base = sensorTotal
            p.edit().putInt(KEY_DAY, today).putInt(KEY_BASE, base).apply()
        }
        if (sensorTotal < base) {
            // A telefon újraindult, az érzékelő nulláról kezdte.
            base = 0
            p.edit().putInt(KEY_BASE, 0).apply()
        }
        val steps = (sensorTotal - base).coerceAtLeast(0)
        p.edit().putInt(KEY_TODAY, steps).apply()
        return steps
    }

    fun todaySteps(context: Context): Int = prefs(context).getInt(KEY_TODAY, 0)

    // ── SZEMÉLYES ADATOK a pontosabb számításhoz ────────────────────────────

    fun getHeightCm(context: Context): Int = prefs(context).getInt(KEY_HEIGHT, 170)
    fun setHeightCm(context: Context, cm: Int) {
        prefs(context).edit().putInt(KEY_HEIGHT, cm.coerceIn(120, 220)).apply()
    }

    fun getWeightKg(context: Context): Int = prefs(context).getInt(KEY_WEIGHT, 75)
    fun setWeightKg(context: Context, kg: Int) {
        prefs(context).edit().putInt(KEY_WEIGHT, kg.coerceIn(30, 200)).apply()
    }

    fun getDailyGoal(context: Context): Int = prefs(context).getInt(KEY_GOAL, 6000)
    fun setDailyGoal(context: Context, goal: Int) {
        prefs(context).edit().putInt(KEY_GOAL, goal.coerceIn(1000, 30000)).apply()
    }
}
