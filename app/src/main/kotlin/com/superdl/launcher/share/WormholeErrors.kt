package com.superdl.launcher.share

/**
 * A WORMHOLE ANGOL HIBÁIBÓL MAGYAR, CSELEKVÉSRE FOGHATÓ OK.
 *
 * Ez a tábla a windowsos `p2p.py` `_HIBA_MINTAK` listájának testvére, és
 * SZÁNDÉKOSAN ugyanazokat a mondatokat mondja. Aki a gépen már találkozott
 * ezzel a hibával, az a telefonon ugyanazt a magyarázatot kapja — két
 * különböző megfogalmazás ugyanarra a bajra két különböző hibának látszana.
 *
 * A protokoll angolul beszél. A felhasználó nem. Egy „key confirmation
 * failed" üzenet önmagában nem segít senkin; az segít, hogy „rossz vagy
 * elgépelt kód".
 */
object WormholeErrors {

    private val PATTERNS: List<Pair<Regex, String>> = listOf(
        Regex("key confirmation failed|wrongpassword|scary|corrupt", RegexOption.IGNORE_CASE) to
            "rossz vagy elgépelt kód — pontosan a küldő kódját kell beírni.",
        Regex("timed out|timeout|took too long|no response", RegexOption.IGNORE_CASE) to
            "időtúllépés — a másik gép nem kapcsolódott be időben. Indítsátok " +
            "egyszerre, és a fogadó azonnal írja be a kódot, mert a kód lejár.",
        Regex("already (been )?(used|claimed)|nameplate.*claimed|crowded", RegexOption.IGNORE_CASE) to
            "ezt a kódot már felhasználták, vagy lejárt. Kérj új kódot, és úgy próbáld.",
        Regex(
            "refused|unreachable|failed to connect|getaddrinfo|name or service|" +
                "temporary failure|websocket|connection.?error|network is unreachable|" +
                "ssl|certificate|handshake|could not connect|no route|dial tcp|i/o timeout",
            RegexOption.IGNORE_CASE
        ) to
            "nem érhető el a közvetítő szerver — valószínűleg a hálózat, a tűzfal " +
            "vagy egy V P N blokkolja. Próbáljátok másik hálózatról, például " +
            "mobilnetről.",
        Regex(
            "no such file|not found|permission denied|disk|no space|read-only",
            RegexOption.IGNORE_CASE
        ) to
            "fájl- vagy tárhelyhiba — nézd meg a fájlt, a szabad helyet és a " +
            "jogosultságokat.",
        Regex("context canceled|canceled|cancelled", RegexOption.IGNORE_CASE) to
            "megszakítottad."
    )

    /**
     * Emberi ok az angol üzenetből. Üres, ha semmi ismerőset nem látunk —
     * olyankor a hívó a NYERS üzenetet mondja be.
     *
     * MIÉRT NEM NYELJÜK EL AZ ISMERETLENT: ha egy ismeretlen hibára némán
     * „nem sikerült"-et mondanánk, a felhasználó nem tudná jelenteni sem.
     * Inkább hangozzon el érthetetlenül, mint sehogy.
     */
    fun friendly(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) return ""
        for ((pattern, message) in PATTERNS) {
            if (pattern.containsMatchIn(text)) return message
        }
        return "a hálózat jelzése: " + text.take(160)
    }
}
