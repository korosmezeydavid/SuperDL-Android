package com.superdl.launcher.environment

/**
 * A felismerhető tárgykategóriák.
 *
 * A `searchWords` a HANGOS KERESÉSHEZ kell: ezekre a szavakra ismerjük fel,
 * hogy a felhasználó mit keres. Szándékosan a beszélt nyelv szavai vannak
 * benne (nem a COCO-címkék), toldalék nélküli tőalakban — a toldalékokat a
 * `fromSpoken()` szó eleji egyezése kezeli.
 */
enum class ObjectCategory(
    val id: String,
    val hungarianName: String,
    val cocoLabels: Set<String>,
    val searchWords: Set<String> = emptySet()
) {
    DOOR("door", "Ajtó", emptySet()),
    CHAIR(
        "chair",
        "Szék",
        setOf("chair", "couch", "bench"),
        setOf("szék", "fotel", "kanapé", "pad", "ülés", "ülőhely")
    ),
    TABLE(
        "table",
        "Asztal",
        setOf("dining table"),
        setOf("asztal")
    ),
    PERSON(
        "person",
        "Személy",
        setOf("person"),
        setOf("ember", "személy", "valaki", "férfi")
    ),
    FLOOR_OBJECT(
        "floor_object",
        "Földön lévő tárgy",
        setOf(
            "bottle",
            "cup",
            "book",
            "backpack",
            "handbag",
            "suitcase",
            "laptop",
            "umbrella"
        ),
        setOf(
            "tárgy", "üveg", "palack", "pohár", "bögre", "könyv", "táska",
            "hátizsák", "bőrönd", "laptop", "esernyő"
        )
    ),
    PHONE(
        "phone",
        "Telefon",
        setOf("cell phone"),
        setOf("telefon", "mobil")
    ),
    ANIMAL(
        "animal",
        "Állat",
        setOf("dog", "cat", "bird", "horse", "sheep", "cow"),
        setOf("állat", "kutya", "macska", "madár", "birka", "tehén")
    ),
    VEHICLE(
        "vehicle",
        "Jármű",
        setOf("car", "bus", "truck", "bicycle", "motorcycle", "train"),
        setOf(
            "jármű", "autó", "kocsi", "busz", "teherautó", "bicikli",
            "kerékpár", "motor", "vonat"
        )
    ),
    SCREEN(
        "screen",
        "Képernyő",
        setOf("tv"),
        setOf("képernyő", "tévé", "televízió", "monitor")
    ),
    BED(
        "bed",
        "Ágy",
        setOf("bed"),
        setOf("ágy")
    ),
    PLANT(
        "plant",
        "Növény",
        setOf("potted plant"),
        setOf("növény", "virág", "cserép", "pálma")
    );

    companion object {
        private val labelToCategory: Map<String, ObjectCategory> =
            entries.flatMap { category ->
                category.cocoLabels.map { label -> label.lowercase() to category }
            }.toMap()

        fun fromCocoLabel(label: String): ObjectCategory? =
            labelToCategory[label.trim().lowercase()]

        fun fromId(id: String): ObjectCategory? =
            entries.firstOrNull { it.id == id }

        /**
         * AMIT TÉNYLEG MEG TUDUNK KERESNI.
         *
         * Figyelem: az AJTÓ kategóriának ÜRES a `cocoLabels` halmaza, tehát a
         * felismerő SOHA nem ad vissza ajtót. A kitekintőben ez nem tűnt fel,
         * mert ott csak annyi a következménye, hogy nem hangzik el. A
         * kereséshez viszont végzetes lenne felajánlani: a felhasználó
         * percekig forgatná a telefont valami után, ami sosem jöhet meg.
         *
         * Ezért a keresés CSAK azokat a kategóriákat kínálja, amelyekhez
         * tartozik legalább egy felismerhető címke. Egy szűk, de megbízható
         * lista jobb, mint egy bőkezű ígéret.
         */
        fun searchable(): List<ObjectCategory> =
            entries.filter { it.cocoLabels.isNotEmpty() && it.searchWords.isNotEmpty() }

        /** Felolvasható felsorolás: „szék, asztal, személy…" */
        fun searchableList(): String =
            searchable().joinToString(", ") { it.hungarianName.lowercase() }

        /**
         * A kimondott szöveghez tartozó kategória.
         *
         * A magyar toldalékok miatt nem elég a pontos egyezés („széket
         * keresek"), ezért mindkét irányban megengedjük a szó eleji egyezést,
         * de csak három betűtől — különben az „az" és a „hol" mindenre
         * illeszkedne.
         */
        fun fromSpoken(spoken: String): ObjectCategory? {
            val szavak = spoken.lowercase()
                .split(Regex("[^\\p{L}]+"))
                .filter { it.length >= 3 }
            if (szavak.isEmpty()) return null
            for (kategoria in searchable()) {
                for (szinonima in kategoria.searchWords) {
                    for (szo in szavak) {
                        if (szo.startsWith(szinonima) || szinonima.startsWith(szo)) {
                            return kategoria
                        }
                    }
                }
            }
            return null
        }
    }
}
