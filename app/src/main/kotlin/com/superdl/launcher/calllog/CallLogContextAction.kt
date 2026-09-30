package com.superdl.launcher.calllog

import android.content.Context
import com.superdl.launcher.contacts.ContactHelper

enum class CallLogContextAction(val label: String) {
    CALL("Hívás indítása"),
    SEND_SMS("SMS küldés"),
    COPY_NUMBER("Szám másolása"),
    /**
     * SZÁM AZONOSÍTÁSA — csak legális, helyi forrásból: névjegyek,
     * OpenStreetMap cégindex, a számterv (libphonenumber). A tudakozós
     * keresést csak kézzel kínálja fel; a szám magától nem megy el sehova.
     */
    IDENTIFY_NUMBER("Szám azonosítása"),
    /**
     * EMLÉKEZTETÉS KÉSŐBBRE. Ami ide kerül, az a „Visszahívandók" listára
     * megy — nem az ébresztők közé. A visszahívás nem ébresztő.
     */
    REMIND_LATER("Emlékeztetés később"),
    SAVE_CONTACT("Mentés névjegyként"),
    ADD_FAVORITE("Hozzáadás a Kedvencekhez"),
    /**
     * FEHÉRLISTÁRA. Aki rajta van, MINDEN szűrésen átjön — Teljes Ne Zavarj
     * módban is. Eddig sehogy nem lehetett felvenni rá senkit.
     */
    ADD_WHITELIST("Fehérlistára — mindig engedd át"),
    BLOCK_NUMBER("Telefonszám letiltása");

    companion object {
        fun forEntry(context: Context, entry: CallLogEntry): List<CallLogContextAction> {
            val actions = mutableListOf(CALL, SEND_SMS, COPY_NUMBER)
            // Mindig ott van: rejtett számra is kimondja, hogy az nem azonosítható.
            actions.add(IDENTIFY_NUMBER)
            if (entry.number.isNotBlank()) actions.add(REMIND_LATER)
            if (!ContactHelper.isKnownNumber(context, entry.number)) {
                actions.add(SAVE_CONTACT)
            }
            actions.add(ADD_FAVORITE)
            if (entry.number.isNotBlank() &&
                !com.superdl.launcher.callfilter.CallFilterStore
                    .isWhitelisted(context, entry.number)
            ) {
                actions.add(ADD_WHITELIST)
            }
            if (entry.number.isNotBlank()) {
                actions.add(BLOCK_NUMBER)
            }
            return actions
        }
    }
}