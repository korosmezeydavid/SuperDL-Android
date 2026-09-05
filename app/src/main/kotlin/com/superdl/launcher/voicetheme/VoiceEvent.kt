package com.superdl.launcher.voicetheme

/**
 * AZ ESEMÉNYEK, AMIKHEZ SAJÁT HANG RENDELHETŐ.
 *
 * MI EZ AZ EGÉSZ: a telefon néhány visszatérő pillanatát (merülés, töltés,
 * reggel, este) nem muszáj gépi mondattal megúszni. Ha van hozzá felvett
 * hang, azt játsszuk le — így a program „valakivé" válik, nem marad a
 * rendszer újabb üzenete.
 *
 * AZ ALAPELV, AMI MINDENT ELDÖNT:
 *
 *     Bármelyik esemény témázható. Az információ viszont ÉLI TÚL.
 *
 * Ahol a semleges változat számot mondott (töltöttség), ott a témás
 * változat lejátssza a klipet ÉS kimondja a tényt is. Nem azért, mert a
 * vicces hang baj — hanem mert az „éhes vagyok" önmagában nem árulja el,
 * hogy tíz perc van hátra vagy két óra. A humor a téma szerzőjének dolga,
 * a szám a miénk.
 */
enum class VoiceEvent(
    val id: String,
    /** A fájl neve KITERJESZTÉS NÉLKÜL. Ékezet nélkül, mert sok gépen gond. */
    val baseName: String,
    /** Ahogy a beállításokban felolvassuk. */
    val label: String,
    /**
     * Amit a program mond, ha nincs felvett hang.
     * Üres = ehhez az eseményhez a hívó maga mondja a szöveget (merülés:
     * a százalék), tehát némaság itt sem lesz.
     */
    val fallback: String
) {
    BATTERY_LOW("battery_low", "battery_low", "Merüléskor", ""),
    BATTERY_FULL("battery_full", "battery_full", "Feltöltve", "Az akkumulátor feltöltött."),
    CHARGER_IN("charger_in", "charger_in", "Töltő bedugva", "Töltő csatlakoztatva."),
    CHARGER_OUT_LOW(
        "charger_out_low", "charger_out_low",
        "Töltő kihúzva, alacsony töltöttségnél",
        "A töltő ki lett húzva, és az akkumulátor még alacsony."
    ),
    MORNING("morning", "morning", "Jó reggelt", "Jó reggelt!"),
    NIGHT("night", "night", "Jó éjszakát", "Jó éjszakát!");

    companion object {
        /**
         * Elfogadott kiterjesztések. Nem kötjük ki a WAV-ot: aki telefonnal
         * vesz fel, könnyen m4a-t vagy mp3-at kap, és ne kelljen konvertálnia.
         */
        val EXTENSIONS = listOf("wav", "mp3", "m4a", "ogg", "opus", "aac")

        fun byId(id: String?): VoiceEvent? = entries.firstOrNull { it.id == id }
    }
}
