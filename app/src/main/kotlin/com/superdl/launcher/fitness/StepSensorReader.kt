package com.superdl.launcher.fitness

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * A LÉPÉSÉRZÉKELŐ figyelése.
 *
 * A telefon beépített lépésérzékelőjét használjuk — ez a rendszer szintjén,
 * energiatakarékosan számol, nem kell hozzá folyamatosan futó szolgáltatás.
 *
 * KORLÁT: nem minden telefonban van lépésérzékelő. Ha nincs, ezt megmondjuk,
 * és nem tettetjük, hogy működik.
 */
class StepSensorReader(private val context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val stepSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private var listener: ((Int) -> Unit)? = null

    /** Van-e egyáltalán lépésérzékelő ebben a telefonban? */
    fun isAvailable(): Boolean = stepSensor != null

    fun start(onSteps: (Int) -> Unit): Boolean {
        val sensor = stepSensor ?: return false
        listener = onSteps
        return try {
            sensorManager?.registerListener(
                this, sensor, SensorManager.SENSOR_DELAY_NORMAL
            ) == true
        } catch (e: Exception) {
            Log.w(TAG, "lepesszamlalo inditas hiba: ${e.message}")
            false
        }
    }

    fun stop() {
        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) {
        }
        listener = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val total = event?.values?.firstOrNull()?.toInt() ?: return
        val today = StepCounterStore.update(context, total)
        listener?.invoke(today)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val TAG = "SDL_STEPS"
    }
}
