package com.superdl.launcher.callfilter

import android.content.Context
import android.telecom.TelecomManager

object CallFilterEngine {

    private val HIDDEN_NUMBER_TOKENS = setOf(
        "unknown",
        "private",
        "rejtett",
        "ismeretlen",
        "hidden",
        "anonymous",
        "withheld",
        "unavailable"
    )

    fun shouldBlock(
        context: Context,
        phoneNumber: String?,
        handlePresentation: Int = TelecomManager.PRESENTATION_ALLOWED
    ): Boolean {
        val normalized = phoneNumber?.let(CallFilterStore::normalizePhone).orEmpty()

        // A FEHÉRLISTA MINDENT FELÜLÍR — az időzített fókuszt is.
        // Aki rajta van, teljes Ne Zavarj alatt is átcsörög. Ez nem apróság:
        // a családtag vagy az orvos hívása életbevágó lehet.
        if (normalized.isNotBlank() && CallFilterStore.isWhitelisted(context, normalized)) {
            return false
        }
        if (normalized.isNotBlank() && CallFilterStore.isBlacklisted(context, normalized)) {
            return true
        }

        // REJTETT SZÁM: KÜLÖN KÉRDÉS, A MÓDTÓL FÜGGETLENÜL.
        //
        // Korábban ez a módokba volt beleszőve, és emiatt NEM VOLT olyan
        // állapot, amiben egy rejtett hívás átjött volna. Akit nem zavar,
        // annak sem volt választása. Most a saját kapcsolója dönt.
        if (isPrivateOrHidden(normalized, handlePresentation)) {
            return CallFilterStore.isHiddenBlocked(context)
        }

        // IDŐZÍTETT ÉS HELY ALAPÚ FÓKUSZ: ha épp érvényben van egy szabály
        // (pl. este tíztől reggel hatig, vagy „a munkahelyen vagy"), az
        // FELÜLÍRJA a kézzel beállított módot.
        //
        // HA MINDKETTŐ ÉRVÉNYBEN VAN, A SZIGORÚBB NYER: aki két szabályt is
        // beállított ugyanarra az időre, nyilván azt akarta, hogy akkor
        // tényleg ne zavarják.
        val mode = strictest(
            PlaceFocusStore.activeMode(context),
            FocusScheduleStore.activeMode(context)
        ) ?: CallFilterStore.getMode(context)

        return when (mode) {
            CallFilterMode.TOTAL_DND -> true
            CallFilterMode.PRIORITY_ONLY -> !CallContactLookup.isPriorityCaller(context, normalized)
            CallFilterMode.CONTACTS_ONLY -> !CallContactLookup.isKnownContact(context, normalized)
            CallFilterMode.ACCEPT_ALL -> false
        }
    }

    /**
     * MIÉRT SZŰRTÜK KI — a szűrt hívások listájába.
     *
     * Ezt csak MI tudjuk: a rendszer hívásnaplójában csak annyi látszik,
     * hogy elutasított hívás volt. Az ok nélkül a lista fele annyit ér.
     */
    fun blockReasonId(
        context: Context,
        phoneNumber: String?,
        handlePresentation: Int
    ): String {
        val normalized = phoneNumber?.let(CallFilterStore::normalizePhone).orEmpty()
        if (normalized.isNotBlank() && CallFilterStore.isBlacklisted(context, normalized)) {
            return "feketelista"
        }
        if (isPrivateOrHidden(normalized, handlePresentation)) return "rejtett"
        val mode = strictest(
            PlaceFocusStore.activeMode(context),
            FocusScheduleStore.activeMode(context)
        ) ?: CallFilterStore.getMode(context)
        return when (mode) {
            CallFilterMode.TOTAL_DND -> "nezavarj"
            CallFilterMode.PRIORITY_ONLY -> "reszleges"
            CallFilterMode.CONTACTS_ONLY -> "ismeretlen"
            CallFilterMode.ACCEPT_ALL -> "egyeb"
        }
    }

    /**
     * A szigorúbbik a kettő közül. A felsorolásban a legszigorúbb van elöl,
     * ezért a kisebb sorszám nyer.
     */
    private fun strictest(a: CallFilterMode?, b: CallFilterMode?): CallFilterMode? = when {
        a == null -> b
        b == null -> a
        a.ordinal <= b.ordinal -> a
        else -> b
    }

    fun isPrivateOrHidden(phoneNumber: String, handlePresentation: Int): Boolean {
        if (phoneNumber.isBlank()) return true
        if (phoneNumber.lowercase() in HIDDEN_NUMBER_TOKENS) return true
        return handlePresentation == TelecomManager.PRESENTATION_RESTRICTED ||
            handlePresentation == TelecomManager.PRESENTATION_UNKNOWN
    }

    fun speakBlockReason(context: Context, phoneNumber: String?, handlePresentation: Int): String {
        val normalized = phoneNumber?.let(CallFilterStore::normalizePhone).orEmpty()
        return when {
            CallFilterStore.getMode(context) == CallFilterMode.TOTAL_DND ->
                "Teljes Ne Zavarj mód. Hívás blokkolva."
            normalized.isNotBlank() && CallFilterStore.isBlacklisted(context, normalized) ->
                "Letiltott szám."
            CallFilterStore.getMode(context) == CallFilterMode.PRIORITY_ONLY ->
                "Részleges szűrés. Csak kedvenc és csillagozott hívások engedélyezettek."
            CallFilterStore.getMode(context) == CallFilterMode.CONTACTS_ONLY &&
                !CallContactLookup.isKnownContact(context, normalized) ->
                "Laza szűrés. Ismeretlen szám blokkolva."
            CallFilterStore.getMode(context) == CallFilterMode.ACCEPT_ALL &&
                isPrivateOrHidden(normalized, handlePresentation) ->
                "Rejtett számú hívás blokkolva."
            else -> "Hívás blokkolva."
        }
    }
}