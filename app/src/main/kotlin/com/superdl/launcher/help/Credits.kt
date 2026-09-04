package com.superdl.launcher.help

import com.superdl.launcher.legal.LegalSection

/**
 * KÖSZÖNET ÉS EGYÜTTMŰKÖDŐK.
 *
 * A köszönő sor Alph szavaiból van (2026-09-03): „magamnak kezdtem
 * készíteni, de mendenkinek fejezem be, akinek ez hasznos". Az együttműködők
 * listája bővíthető — egy új ember egy új sor, semmi más.
 */
object Credits {

    const val TITLE = "Köszönet és együttműködők"

    /** Név és szerep. A szerep az, amit a felhasználó hall a név után. */
    data class Contributor(val name: String, val role: String)

    val CONTRIBUTORS: List<Contributor> = listOf(
        Contributor("Hermann Tibor", "technikai tudás és rendszeres szakmai audit"),
        Contributor("Gerstenmaier György", "háttértámogatás")
    )

    fun sections(): List<LegalSection> {
        val list = mutableListOf(
            LegalSection(
                "Köszönet",
                "Ezt a programot magamnak kezdtem készíteni — de mindenkinek fejezem be, " +
                    "akinek hasznos. Nem lett volna belőle ennyi azok nélkül, akik " +
                    "tudásukkal, idejükkel és türelmükkel mellém álltak. Köszönöm."
            )
        )
        CONTRIBUTORS.forEach { c ->
            list += LegalSection(c.name, "${c.name}: ${c.role}.")
        }
        return list
    }
}
