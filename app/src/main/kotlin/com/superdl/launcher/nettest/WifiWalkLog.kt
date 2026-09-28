package com.superdl.launcher.nettest

/**
 * WI-FI BEJÁRÁS NAPLÓJA — a windowsos `nettest.JelNaplo` hű átirata.
 *
 * Felhasználói kérés (Windows, 2026-08-20): mesh-hálózat építése közben
 * látni, hol milyen erős még a wifi. Az ember közben JÁRKÁL a lakásban, és
 * nem a képernyőt nézi — vakon pedig végképp nem. Ezért a figyelő
 * folyamatosan mér, a változást KIMONDJA, a helyszíneket meg lehet jelölni
 * („konyha"), és a végén megmondja, hova érdemes még egy mesh-egység.
 *
 * Android-mentes: az időzítést, a hangot és a bemondást a MainActivity
 * intézi; ez csak számol és szöveget ad — így JVM-en tesztelhető.
 */
class WifiWalkLog(changeThreshold: Int = 3) {

    /** Egy mérés: (dBm, jelminőség-százalék — a telefonon mindig 0). */
    data class Reading(val dbm: Int, val percent: Int)

    /** Egy megjelölt hely. */
    data class Place(val name: String, val dbm: Int, val percent: Int)

    private val _readings = mutableListOf<Reading>()
    private val _places = mutableListOf<Place>()
    val readings: List<Reading> get() = _readings
    val places: List<Place> get() = _places

    val threshold: Int = maxOf(1, changeThreshold)
    private var lastSpoken: Int? = null

    /**
     * Új mérés. Igaz, ha ÉRDEMES kimondani (elég nagyot változott).
     *
     * Miért kell küszöb: a jel másodpercenként ingadozik 1-2 dBm-et. Ha minden
     * rezdülést bemondanánk, a program folyamatosan beszélne, és a felhasználó
     * nem hallaná a lényeget.
     */
    fun add(dbm: Int, percent: Int = 0, canSpeak: Boolean = true): Boolean {
        _readings += Reading(dbm, percent)
        val last = lastSpoken
        if (last == null || kotlin.math.abs(dbm - last) >= threshold) {
            // TELEFONOS KIEGÉSZÍTÉS: ha most nem mondhatjuk ki (épp beszél a
            // program, vagy a hely nevét diktálod), a viszonyítási alap
            // MARAD — így a változás elhangzik, amint újra szóhoz jutunk.
            // (Az alapértelmezés a Windows-viselkedés.)
            if (!canSpeak) return false
            lastSpoken = dbm
            return true
        }
        return false
    }

    fun best(): Int = _readings.maxOfOrNull { it.dbm } ?: 0
    fun weakest(): Int = _readings.minOfOrNull { it.dbm } ?: 0

    /** Átlag, egészre kerekítve (Python `round`: páros felé). */
    fun average(): Int {
        if (_readings.isEmpty()) return 0
        return NetTestText.pyRoundInt(_readings.sumOf { it.dbm.toDouble() } / _readings.size).toInt()
    }

    /** A mostani helyszín megjelölése az UTOLSÓ mérés értékével. Mérés nélkül nem jelöl. */
    fun mark(name: String?): Place? {
        val last = _readings.lastOrNull() ?: return null
        val place = Place((name ?: "").trim().ifEmpty { "névtelen pont" }, last.dbm, last.percent)
        _places += place
        return place
    }

    fun placeText(p: Place): String = "${p.name}: ${p.dbm} dBm – ${NetTestText.signalGrade(p.dbm).first}"

    /** = JelNaplo.osszefoglalo — soronként ('\n'), ahogy a Windows is adja. */
    fun summary(): String {
        if (_readings.isEmpty()) return "Nem történt mérés."
        val out = mutableListOf(
            "${_readings.size} mérés. Legerősebb: ${best()} dBm, leggyengébb: ${weakest()} dBm, átlag: ${average()} dBm."
        )
        if (_places.isNotEmpty()) {
            out += "Megjelölt helyek:"
            _places.forEach { out += "  " + placeText(it) }
            val weak = _places.filter { it.dbm < -72 }
            out += if (weak.isNotEmpty()) {
                "Ezeken a helyeken gyenge a jel, ide érdemes még egy mesh-egységet tenni: " +
                    weak.joinToString(", ") { it.name } + "."
            } else {
                "A megjelölt helyeken mindenhol legalább elfogadható a jel."
            }
        }
        return out.joinToString("\n")
    }

    /**
     * = JelNaplo.mentheto_szoveg — a bejárás jegyzőkönyve. A `time` a
     * telefonos kiegészítés (üresen a Windows-szöveggel betűre egyezik): a
     * megosztott szövegben jó látni, mikor készült.
     */
    fun saveText(network: String = "", time: String = ""): String {
        val out = mutableListOf("SuperDL – Wi-Fi jelerősség-bejárás")
        if (network.isNotBlank()) out += "Hálózat: $network"
        if (time.isNotBlank()) out += "Időpont: $time"
        out += ""
        out += summary()
        out += ""
        out += "Minden mérés (dBm):"
        out += _readings.joinToString(", ") { it.dbm.toString() }
        return out.joinToString("\n")
    }
}
