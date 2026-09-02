package com.superdl.launcher.email

/**
 * MIT LEHET KEZDENI EGY ELOLVASOTT LEVÉLLEL.
 *
 * MIÉRT KELLETT: eddig az „E-mail írása" külön menüpont volt, a levelek
 * olvasásától messze. Vagyis pont akkor NEM tudtál válaszolni, amikor épp
 * elolvastad a levelet: ki kellett lépni, megkeresni az írás menüpontot,
 * és ott újra bediktálni a címzettet — akinek a levele az imént szólt a
 * füledbe. Látó ember erre rákattint; vaknak ez öt fölösleges lépés.
 *
 * Mostantól az elolvasott levélnél egy jobbra söprés előhozza ezt a
 * menüt, és a válasz CÍMZETTJE ÉS TÁRGYA MAGÁTÓL KITÖLTŐDIK.
 */
enum class EmailAction(val label: String) {

    REPLY("Válasz a feladónak"),

    FORWARD("Továbbítás"),

    READ_AGAIN("Levél újraolvasása"),

    /** A feladó címének mentése, hogy legközelebb ne kelljen diktálni. */
    SAVE_SENDER("Feladó mentése a címjegyzékbe"),

    NEW_MAIL("Új levél írása"),

    BACK("Vissza a levélhez");

    companion object {
        val all: List<EmailAction> = entries.toList()

        /**
         * A VÁLASZ TÁRGYA. A szabvány szerint „Re:" előtag — de csak egyszer.
         * A „Re: Re: Re:" lánc egy hosszú levelezésben felolvasva
         * elviselhetetlen, és semmit nem tesz hozzá.
         */
        fun replySubject(original: String): String {
            val trimmed = original.trim()
            val hasPrefix = trimmed.startsWith("Re:", ignoreCase = true)
            return if (hasPrefix) trimmed else "Re: $trimmed"
        }

        /** A továbbítás tárgya, ugyanezzel a logikával. */
        fun forwardSubject(original: String): String {
            val trimmed = original.trim()
            val hasPrefix = trimmed.startsWith("Fwd:", ignoreCase = true) ||
                trimmed.startsWith("Fw:", ignoreCase = true)
            return if (hasPrefix) trimmed else "Fwd: $trimmed"
        }

        /**
         * A FELADÓ CÍME a fejlécből. A fejléc alakja általában
         * `Géza <mezeig79@gmail.com>`, de lehet puszta cím is.
         *
         * Üres string, ha nem sikerül — a hívónak EZT kell kimondania,
         * nem egy értelmetlen címre küldeni a választ.
         */
        fun senderAddress(from: String): String {
            val raw = from.trim()
            val inBrackets = raw.substringAfter("<", "").substringBefore(">", "").trim()
            val candidate = if (inBrackets.isNotBlank()) inBrackets else raw
            return if (candidate.contains("@") && !candidate.contains(" ")) candidate else ""
        }

        /** A feladó emberi neve a címjegyzékbe mentéshez. */
        fun senderLabel(from: String): String {
            val name = from.trim().substringBefore("<").trim().trim('"').trim()
            return if (name.isNotBlank()) name else senderAddress(from)
        }

        /**
         * A TOVÁBBÍTOTT LEVÉL TÖRZSE — az eredeti idézve.
         *
         * Vakon a hosszú idézet a felolvasásban fárasztó, ezért az eredetit
         * egy világos, KIMONDHATÓ elválasztó vezeti be, nem a szokásos
         * „>" jelek, amiket a beszédmotor amúgy sem tud értelmesen olvasni.
         */
        fun forwardBody(mail: ImapMail, ownText: String): String = buildString {
            if (ownText.isNotBlank()) {
                append(ownText.trim())
                append("\n\n")
            }
            append("--- Továbbított levél ---\n")
            append("Feladó: ${mail.from}\n")
            append("Tárgy: ${mail.subject}\n")
            append("Dátum: ${mail.date}\n\n")
            append(mail.body)
        }
    }
}
