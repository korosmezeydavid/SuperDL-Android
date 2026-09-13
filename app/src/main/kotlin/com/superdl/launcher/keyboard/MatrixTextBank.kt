package com.superdl.launcher.keyboard

import android.content.Context
import com.superdl.launcher.textbank.TextBankStore

/**
 * SZÖVEGTÁR — előre elmentett szövegek a mátrix billentyűzet gombjain.
 *
 * MIÉRT KELL: egy hosszú e-mail cím, egy számlaszám vagy egy gyakori válasz
 * betűnkénti bevitele lassú, és pont ott a legnagyobb az elgépelés esélye, ahol
 * a legnehezebb ellenőrizni. Ha egyszer eltároltad, onnantól EGY mozdulat.
 *
 * FELÉPÍTÉS: ugyanaz a 3x4-es rács, mint a betűbevitelnél — 1-től 9-ig, majd
 * csillag, nulla, kettőskereszt. Nem kell új mozdulatot tanulni: ugyanúgy
 * leteszed az ujjad, csúsztatsz, felengeded.
 *
 * ────────────────────────────────────────────────────────────────────────────
 * FIGYELEM — EZ MÁR CSAK EGY AJTÓ, NEM A TÁR.
 *
 * A tartalom mostantól a KÖZÖS szövegtárban él (`textbank/TextBankStore`), amit
 * az SMS-küldés, a diktálás és a WiFi portál is lát. Ez az osztály csak
 * lefordítja a gomb-alapú használatot a közös tár nyelvére.
 *
 * MIÉRT: két tár ugyanarra a célra azt jelentené, hogy a felhasználó soha nem
 * tudja, melyikben van a számlaszám. A billentyűzeten SEMMI nem változik.
 * ────────────────────────────────────────────────────────────────────────────
 */
object MatrixTextBank {

    /** A gombhoz tartozó szöveg, vagy null ha üres. */
    fun get(context: Context, slot: MatrixKey): String? =
        TextBankStore.forSlot(context, slot.name)?.text?.takeIf { it.isNotBlank() }

    fun set(context: Context, slot: MatrixKey, text: String) {
        TextBankStore.setForSlot(context, slot.name, text)
    }

    fun clear(context: Context, slot: MatrixKey) {
        TextBankStore.clearSlot(context, slot.name)
    }

    /**
     * Hány GOMBHOZ KÖTÖTT hely van kitöltve.
     *
     * Szándékosan nem a teljes szövegtár mérete: a billentyűzeten csak azok
     * érhetők el, amik gombon ülnek, és félrevezető lenne huszonhármat
     * bemondani, ha csak négy van kéznél.
     */
    fun count(context: Context): Int =
        MatrixKey.entries.count { get(context, it) != null }

    /**
     * A gomb tartalmának RÖVID bemondása navigáláskor.
     * Hosszú szövegnél csak az elejét mondjuk, különben a végigcsúsztatás
     * elviselhetetlenül lassú lenne.
     */
    fun speakPreview(context: Context, slot: MatrixKey): String {
        val text = get(context, slot)
            ?: return "${slot.label}: üres"
        val preview = if (text.length > 40) text.take(40) + "…" else text
        return "${slot.label}: $preview"
    }

    /** A gombokra kötött helyek felolvasható listája. */
    fun speakAll(context: Context): String {
        val filled = MatrixKey.entries.mapNotNull { slot ->
            get(context, slot)?.let { text ->
                val preview = if (text.length > 30) text.take(30) + "…" else text
                "${slot.label}: $preview"
            }
        }
        return if (filled.isEmpty()) {
            "Egy gombra sincs szöveg kötve. A billentyűzeten két ujjal háromszor " +
                "koppintva nyithatod meg a szövegtárat, és ott töltheted fel — " +
                "vagy a WiFi portál Szövegtár lapján, begépelve."
        } else {
            "${filled.size} gombra kötött szöveg. ${filled.joinToString(". ")}"
        }
    }
}
