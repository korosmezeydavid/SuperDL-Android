package com.superdl.launcher.medialabel

/**
 * EGY FELVÉTEL MŰVELETEI.
 *
 * MIÉRT MŰVELET-MENÜ, ÉS NEM MOZDULATOK: négy mozdulat van, és a
 * lépkedés kettőt már elvisz. A program máshol is ezt a mintát használja
 * (névjegyek, üzenetek) — a jobbra megnyitja a műveleteket.
 */
enum class MediaAction(val label: String) {
    PLAY_LABEL("Hangcímke lejátszása"),
    RECORD_LABEL("Hangcímke felvétele"),
    REPLACE_LABEL("Hangcímke cseréje"),
    DELETE_LABEL("Hangcímke törlése"),
    SHARE("Megosztás"),
    BACK("Vissza a felvételekhez");

    companion object {
        /**
         * A felkínált műveletek a tétel ÁLLAPOTÁTÓL függenek.
         *
         * Nincs értelme „hangcímke lejátszása" pontot mutatni ott, ahol nincs
         * címke — a vakon végighallgatott üres menüpont időpocsékolás.
         */
        fun forItem(hasLabel: Boolean): List<MediaAction> =
            if (hasLabel) {
                listOf(PLAY_LABEL, REPLACE_LABEL, SHARE, DELETE_LABEL, BACK)
            } else {
                listOf(RECORD_LABEL, SHARE, BACK)
            }
    }
}
