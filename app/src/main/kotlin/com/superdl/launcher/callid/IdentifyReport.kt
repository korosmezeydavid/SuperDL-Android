package com.superdl.launcher.callid

/**
 * A „Szám azonosítása" eredménye és a felolvasott szöveg.
 *
 * A négy réteg sorrendje: 1. névjegyek, 2. OpenStreetMap cégadat,
 * 3. a szám leírása (libphonenumber), 4. kézi keresés a felhasználó
 * kezdeményezésére. Itt csak a szöveg születik — tiszta Kotlin, hogy a
 * mondatokat gép mellett is ellenőrizni lehessen.
 *
 * @param contactName ha a szám már a névjegyek közt van (akár más alakban)
 * @param candidates az összes OSM-találat (a felolvasás ebből legfeljebb hármat mond)
 */
data class IdentifyReport(
    val info: NumberDescriber.Info,
    val contactName: String?,
    val candidates: List<PhoneIndexEntry>
) {
    /** A menübe (mentésre) kínált találatok — ugyanaz a három, amit felolvasunk. */
    val offered: List<PhoneIndexEntry> get() = candidates.take(MAX_SPOKEN)

    fun summary(): String {
        if (info.kind == NumberDescriber.Kind.HIDDEN) return info.description
        contactName?.takeIf { it.isNotBlank() }?.let {
            return "Ez a szám már a névjegyeid között van: $it. ${info.description}"
        }
        // Rövid és érvénytelen számnál a „nevet nem találtam" magyarázat
        // félrevezető volna (ezeknek nincs tulajdonosa a tudakozóban): a
        // leírás maga a válasz.
        if (info.kind == NumberDescriber.Kind.SHORT || info.kind == NumberDescriber.Kind.INVALID) {
            return info.description
        }
        if (candidates.isNotEmpty()) {
            val listed = offered.joinToString("; ") { it.speak() }
            val more = candidates.size - offered.size
            val tail = if (more > 0) ", és még $more" else ""
            return "Nyilvános cégadat (OpenStreetMap): $listed$tail. " +
                "$OSM_CAVEAT ${info.description}"
        }
        return "$NO_RESULT_PREFIX Amit tudok: ${info.description}"
    }

    companion object {
        const val MAX_SPOKEN = 3
        const val OSM_CAVEAT = "OpenStreetMap közösségi adat, lehet elavult."
        const val NO_RESULT_PREFIX = "Nevet nem találtam. Ez a szám nincs a névjegyeid között, " +
            "és a nyilvános cégadatokban sem szerepel. Magánszemélyek mobilszáma csak a " +
            "nemzeti tudakozóban kereshető, és csak ha a tulajdonosa hozzájárult."
    }
}

/**
 * Az eredmény utáni műveletek. A két kereső csak a KÉZI utat nyitja meg:
 * a szám a vágólapra kerül, a honlap megnyílik, a beillesztés a tiéd.
 * Automatikusan semmi nem megy el a telefonról.
 */
sealed class NumberIdentifyAction(val label: String) {
    class SaveContact(val candidate: PhoneIndexEntry) :
        NumberIdentifyAction("Mentés a névjegyek közé: ${candidate.name}")
    object NationalDirectory : NumberIdentifyAction("Keresés a nemzeti tudakozóban")
    object Comments : NumberIdentifyAction("Hozzászólások keresése (telefonszam-tudakozo.hu)")
    object Repeat : NumberIdentifyAction("Eredmény újra")
    object Back : NumberIdentifyAction("Vissza a hívásnaplóhoz")

    companion object {
        /**
         * Csak a honlapokat nyitjuk meg, keresőcímet NEM rakunk össze: annak
         * az oldalnak a felhasználási feltételeit nem néztük át, és a szám így
         * csak akkor megy el, ha te magad beilleszted.
         */
        const val NATIONAL_DIRECTORY_URL = "https://www.telekom.hu/lakossagi/tudakozo"
        const val COMMENTS_URL = "https://www.telefonszam-tudakozo.hu/"

        fun forReport(report: IdentifyReport): List<NumberIdentifyAction> {
            val list = mutableListOf<NumberIdentifyAction>()
            // Mentést csak akkor kínálunk, ha még nincs a névjegyek közt.
            if (report.contactName.isNullOrBlank()) {
                report.offered.forEach { list.add(SaveContact(it)) }
            }
            // Rejtett és rövid számot a tudakozókban sincs értelme keresni.
            val searchable = report.info.kind != NumberDescriber.Kind.HIDDEN &&
                report.info.kind != NumberDescriber.Kind.SHORT
            if (searchable) {
                list.add(NationalDirectory)
                list.add(Comments)
            }
            list.add(Repeat)
            list.add(Back)
            return list
        }
    }
}
