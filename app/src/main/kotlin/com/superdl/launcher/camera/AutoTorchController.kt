package com.superdl.launcher.camera

import androidx.camera.core.Camera
import com.superdl.launcher.tools.FlashlightState

/**
 * AUTOMATIKUS LÁMPA A FELISMERŐKHÖZ.
 *
 * A HIBA, AMIT EZ JAVÍT (Péter, 2026-09-06): „Felismerők: sötétben nem
 * kapcsolja be a lámpát, így semmit nem tud felismerni. Kamera sem használ
 * vakut a sötétben, így fekete fotó készül."
 *
 * ── AMI ÉRDEKES EBBEN: A MEGOLDÁS MÁR MEGVOLT ─────────────────────────────
 *
 * A bankjegyfelismerőben évek óta ott van ugyanez, `BanknoteTorchController`
 * néven: fényerőt mér, küszöb alatt bekapcsol, fölötte kikapcsol. Csak épp
 * oda volt kötve, egyetlen felismerőhöz. A szövegfelismerő, a
 * környezetpásztázó és a kamera semmit nem tudott róla.
 *
 * Ez ugyanaz az alkatrész, a bankjegyes mérési típusától függetlenné téve,
 * hogy bármelyik kamerás funkció használhassa.
 *
 * ── MIÉRT NEM EGYSZERŰEN „SÖTÉTBEN KAPCSOLD BE" ───────────────────────────
 *
 * Két küszöb van, nem egy, és ez szándékos. Egyetlen küszöbnél a lámpa a
 * határ közelében villogni kezdene: bekapcsol, ettől világosabb lesz a kép,
 * ezért kikapcsol, ettől sötétebb lesz, ezért bekapcsol. A két küszöb közötti
 * sáv (0,26 és 0,40) ezt a hintázást akadályozza meg, a várakozási idő pedig
 * azt, hogy másodpercenként kattogjon.
 *
 * Vakon a villogó lámpa nem látszik — de a felismerés minden bekapcsolásnál
 * újraindulna, és a felhasználó csak annyit tapasztalna, hogy „nem akar
 * összejönni".
 */
class AutoTorchController {

    private var camera: Camera? = null
    private var torchEnabled = false
    private var lastToggleAt = 0L

    fun attach(camera: Camera) {
        this.camera = camera
    }

    fun isTorchOn(): Boolean = torchEnabled

    /**
     * @param meanLuminance a képkocka átlagos fényereje, 0 és 1 között.
     * @return igaz, ha a lámpa MOST be van kapcsolva.
     */
    fun update(meanLuminance: Float): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastToggleAt < TOGGLE_COOLDOWN_MS) return torchEnabled

        when {
            !torchEnabled && meanLuminance < TORCH_ON_BELOW -> {
                setTorch(true)
                lastToggleAt = now
            }
            torchEnabled && meanLuminance > TORCH_OFF_ABOVE -> {
                setTorch(false)
                lastToggleAt = now
            }
        }
        return torchEnabled
    }

    /** Kézi bekapcsolás — például fényképezés előtt, mérés nélkül. */
    fun forceOn() {
        if (!torchEnabled) {
            setTorch(true)
            lastToggleAt = System.currentTimeMillis()
        }
    }

    fun release() {
        setTorch(false)
        camera = null
    }

    private fun setTorch(enabled: Boolean) {
        try {
            camera?.cameraControl?.enableTorch(enabled)
            torchEnabled = enabled
            // A zseblámpa-menüpont ugyanezt az állapotot olvassa, ezért
            // szinkronban kell tartani — különben a menü azt hinné, hogy ki
            // van kapcsolva, és a felhasználó egy már égő lámpát „kapcsolna
            // be" (ami valójában lekapcsolná).
            FlashlightState.isOn = enabled
        } catch (_: Exception) {
            torchEnabled = false
        }
    }

    companion object {
        /** Ez alatt kapcsolunk BE. */
        const val TORCH_ON_BELOW = 0.26f

        /** Ez fölött kapcsolunk KI. A kettő közötti sáv akadályozza a hintázást. */
        const val TORCH_OFF_ABOVE = 0.40f

        /** Két kapcsolás között ennyit várunk. */
        const val TOGGLE_COOLDOWN_MS = 900L

        /**
         * EGY KÉPKOCKA ÁTLAGOS FÉNYEREJE, 0 és 1 között.
         *
         * A CameraX YUV_420_888 formátumban ad képet: az első sík (Y) MAGA a
         * fényerő, csatornánkénti számolás nélkül. Ezért nem kell a képet
         * RGB-re alakítani — ez a mérés a legolcsóbb, amit kamerán lehet.
         *
         * Mintavételezünk, nem minden képpontot nézünk: egy felismerőnél
         * másodpercenként több képkocka jön, és a teljes végigolvasás
         * fölöslegesen melegítené a telefont.
         */
        fun meanLuminance(image: androidx.camera.core.ImageProxy, sampleStep: Int = 16): Float = try {
            val plane = image.planes.firstOrNull()
            if (plane == null) {
                0.5f
            } else {
                val buffer = plane.buffer
                buffer.rewind()
                val size = buffer.remaining()
                if (size <= 0) {
                    0.5f
                } else {
                    var sum = 0L
                    var count = 0
                    var i = 0
                    while (i < size) {
                        sum += (buffer.get(i).toInt() and 0xFF)
                        count++
                        i += sampleStep
                    }
                    if (count == 0) 0.5f else (sum.toFloat() / count / 255f)
                }
            }
        } catch (_: Exception) {
            // Ha nem tudjuk megmérni, NEM kapcsolgatunk vaktában: a 0,5 a két
            // küszöb között van, tehát ez a válasz mindent változatlanul hagy.
            0.5f
        }
    }
}
