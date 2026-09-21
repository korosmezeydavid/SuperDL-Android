package com.superdl.launcher.home

import java.util.Calendar

/**
 * A KEDVES MONDAT, AMIKOR AZ ESTI ELLENŐRZÉS OTTHON TALÁL.
 *
 * MIÉRT VAN EZ (Alph kérése, 2026-09-21): az otthon-figyelés eddig
 * SZÁNDÉKOSAN néma volt a jó kimenetelre. Az elv jó volt — ne beszéljen
 * feleslegesen —, de kiderült, hogy egy rövid, meleg mondat nem
 * fecsegés: ez az EGYETLEN visszajelzés arról, hogy a védőháló tényleg
 * lefutott és tényleg működik. Aki soha nem hall semmit, az nem tudja,
 * hogy a figyelés él-e egyáltalán.
 *
 * MIÉRT TÖBBFÉLE MONDAT: ugyanaz a mondat minden este, egy éven át,
 * háromszázhatvanötször — az már zaj. A napszak és a véletlen együtt
 * elég ahhoz, hogy emberi maradjon.
 *
 * MIÉRT RÖVID: éjjel tíz körül hangzik el. Egy fél mondat elég.
 */
object HomeGreeting {

    private val ESTE = listOf(
        "Örülök, hogy otthon vagy. Jó pihenést!",
        "Otthon vagy, minden rendben. Szép estét!",
        "Megvagy, hazaértél. Kellemes estét!",
        "Itthon vagy. Pihend ki a napot!"
    )

    private val KESO_ESTE = listOf(
        "Örülök, hogy otthon vagy. Jó éjszakát!",
        "Hazaértél. Aludj jól!",
        "Otthon vagy, minden rendben. Nyugodt éjszakát!",
        "Megvagy. Jó pihenést, jó éjszakát!"
    )

    private val NAPPAL = listOf(
        "Otthon vagy, minden rendben.",
        "Megvagy, itthon vagy. Szép napot!",
        "Örülök, hogy otthon vagy."
    )

    /**
     * @param hour a mostani óra (0–23); tesztelhetőség miatt átadható
     */
    fun text(hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): String {
        val lista = when (hour) {
            in 21..23, in 0..4 -> KESO_ESTE
            in 17..20 -> ESTE
            else -> NAPPAL
        }
        return lista.random()
    }
}
