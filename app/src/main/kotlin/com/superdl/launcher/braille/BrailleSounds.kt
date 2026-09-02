package com.superdl.launcher.braille

import android.content.Context
import android.media.AudioManager

/**
 * BILLENTYŰHANGOK A BRAILLE-BEVITELHEZ.
 *
 * MIÉRT A RENDSZER SAJÁT HANGJAI, ÉS NEM SAJÁT HANGFÁJLOK:
 *
 * Az Android minden készüléken hoz egy billentyűhang-készletet, amit MINDEN
 * billentyűzet használ. Ha ugyanezt szólaltatjuk meg, akkor
 *
 *  - a hang **ismerős** lesz: pontosan olyan, mint a telefon többi
 *    billentyűzetén, tehát nem kell hozzászokni;
 *  - a felhasználó **rendszerbeállítása érvényesül** — aki kikapcsolta a
 *    billentyűhangot, annál csendben marad, és nem kell külön kapcsolót
 *    csinálnunk hozzá;
 *  - nem növeli a program méretét, és nem kell hangfájlt karbantartani.
 *
 * A HANGERŐ SZÁNDÉKOSAN HALK (`VOLUME`). A Braille-írásnál a beszéd a
 * fontos: a kattanás csak azt erősítse meg, hogy a koppintás MEGTÖRTÉNT.
 * Ha túl hangos, elnyomja a felolvasott betűt — és akkor pont a lényeg vész
 * el.
 *
 * MINDEN HÍVÁS BURKOLT. Egy néma vagy hibás hangrendszer sosem viheti el az
 * írást: a hang kényelem, a beírt betű a funkció.
 */
class BrailleSounds(context: Context) {

    companion object {
        /** Halk: a beszéd alá, nem fölé. */
        private const val VOLUME = 0.35f
    }

    private val audio: AudioManager? = try {
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    } catch (_: Throwable) {
        null
    }

    private fun play(effect: Int, volume: Float = VOLUME) {
        try {
            audio?.playSoundEffect(effect, volume)
        } catch (_: Throwable) {
        }
    }

    /** Ujj ért a kijelzőhöz — „megvagy". Ez szól a legtöbbször, ezért a legrövidebb. */
    fun fingerDown() = play(AudioManager.FX_KEYPRESS_STANDARD)

    /** Beíródott egy karakter. Halk kattanás a betű kimondása ELŐTT. */
    fun charWritten() = play(AudioManager.FX_KEY_CLICK)

    fun space() = play(AudioManager.FX_KEYPRESS_SPACEBAR)

    fun delete() = play(AudioManager.FX_KEYPRESS_DELETE)

    /** Nem ismerjük fel a cellát. Más hang, hogy tudd: nem írtál semmit. */
    fun invalid() = play(AudioManager.FX_KEYPRESS_INVALID, 0.5f)

    fun enter() = play(AudioManager.FX_KEYPRESS_RETURN)
}
