package com.superdl.launcher.catalog

/**
 * Egy modul a SuperDL katalógusból.
 *
 * FONTOS ELV: a modul mindig ADAT, soha nem futtatható kód. A "motor" (pl. a
 * kvíz-játék) a SuperDL-ben van, a tartalom jön a katalógusból. Így egy új
 * kvízhez NEM kell új alkalmazás-verzió, és a Google Play szabályzatát sem
 * sértjük (az tiltja a futásidejű kód-letöltést).
 */
data class CatalogModule(
    val id: String,
    val name: String,
    val type: ModuleType,
    val version: Int,
    val description: String,
    val sizeBytes: Long,
    val filePath: String,
    val minAppVersion: String,
    /**
     * A katalógusban MEGADOTT kategória azonosítója (pl. "jatekok").
     * Ha üres, a modul típusából következtetünk — így a régi katalógusok is
     * működnek.
     */
    val categoryId: String = ""
) {
    /** Felolvasható összefoglaló a listához. */
    fun speakSummary(installedVersion: Int?): String {
        val state = when {
            installedVersion == null -> "nincs letöltve"
            installedVersion < version -> "frissítés érhető el"
            else -> "letöltve"
        }
        return "$name. ${type.label}. $state. ${speakSize()}."
    }

    fun speakSize(): String = when {
        sizeBytes < 1024 -> "$sizeBytes bájt"
        sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} kilobájt"
        else -> "${sizeBytes / 1024 / 1024} megabájt"
    }
}

/**
 * A modulok NAGY CSOPORTJAI a katalógusban.
 *
 * Ömlesztve minden modul egy listában áttekinthetetlen lenne — főleg ha
 * idővel több tucat lesz. Előbb csoportot választasz, aztán modult.
 */
enum class CatalogCategory(val label: String) {
    GAMES("Játékok és szórakozás"),
    LEARNING("Tanulás és tudás"),
    MEDIA("Média és rádió"),
    EVERYDAY("Mindennapi segítség"),
    LOOKS("Hangzás és megjelenés"),
    OTHER("Egyéb");

    companion object {
        /**
         * Melyik csoportba tartozik egy modul?
         * ELSŐSORBAN a katalógusban megadott kategória számít — így te döntöd
         * el, mi hova kerüljön, és később át is sorolhatsz valamit anélkül,
         * hogy az alkalmazást módosítani kellene.
         */
        fun of(module: CatalogModule): CatalogCategory {
            if (module.categoryId.isNotBlank()) {
                byId(module.categoryId)?.let { return it }
            }
            return of(module.type)
        }

        fun byId(id: String): CatalogCategory? = when (id.lowercase()) {
            "jatekok", "játékok", "games" -> GAMES
            "tanulas", "tanulás", "learning" -> LEARNING
            "media", "média" -> MEDIA
            "mindennapi", "everyday" -> EVERYDAY
            "megjelenes", "megjelenés", "looks" -> LOOKS
            "egyeb", "egyéb", "other" -> OTHER
            else -> null
        }

        /** Melyik csoportba tartozik egy modul-típus? (tartalék) */
        fun of(type: ModuleType): CatalogCategory = when (type) {
            ModuleType.QUIZ, ModuleType.WORD_GAME -> GAMES
            ModuleType.GUIDE -> LEARNING
            ModuleType.RADIO_PACK -> MEDIA
            ModuleType.RECIPES, ModuleType.TEXT_BANK -> EVERYDAY
            ModuleType.SOUND_THEME -> LOOKS
            ModuleType.EXTERNAL_APP -> OTHER
            ModuleType.UNKNOWN -> OTHER
        }
    }
}

/** A támogatott modul-típusok. Mindegyikhez van "motor" a SuperDL-ben. */
enum class ModuleType(val key: String, val label: String) {
    QUIZ("quiz", "kvíz játék"),
    WORD_GAME("wordgame", "szójáték"),
    SOUND_THEME("soundtheme", "hangkészlet"),
    RADIO_PACK("radiopack", "rádiócsomag"),
    GUIDE("guide", "útmutató"),
    RECIPES("recipes", "receptek"),
    TEXT_BANK("textbank", "szövegtár-készlet"),
    EXTERNAL_APP("externalapp", "külön alkalmazás"),
    UNKNOWN("unknown", "ismeretlen típus");

    companion object {
        fun fromKey(key: String?): ModuleType =
            entries.firstOrNull { it.key.equals(key, true) } ?: UNKNOWN
    }
}
