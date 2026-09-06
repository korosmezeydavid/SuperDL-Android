package com.superdl.launcher.lock.keyguard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.UserManager
import android.util.Log
import com.superdl.launcher.tts.TtsManager
import java.io.File

/**
 * BESZÉD A ZÁRKÉPERNYŐN — AKKOR IS, HA A BESZÉDMOTOR MÉG NEM ÉL.
 *
 * A HIBA, AMIT EZ JAVÍT (Lőrincz Richárd felvétele, 2026-09-06,
 * Galaxy S24 Ultra, Android 16):
 *
 *   „újraindulás után hiába van bekapcsolva a PIN segéd, hiába van
 *    bekapcsolva a SuperDL képernyőolvasó, amíg be nem írom ELŐSZÖR a
 *    PIN kódomat, addig NEM BESZÉL EGYIK SEM. Semmi. És csak a
 *    TalkBackkel tudom beírni a PIN kódot."
 *
 * MIÉRT TÖRTÉNT: a rendszer beszédmotorja (Google vagy Samsung TTS) NEM
 * Direct Boot képes. Az első feloldásig az Android el sem indítja a
 * nem Direct Boot képes alkalmazásokat — így a beszédmotort sem. A
 * TalkBack azért beszél, mert saját, beépített úton kerüli meg ezt.
 *
 * A PIN segéd maga rendben elindult: a szolgáltatás `directBootAware`, a
 * beállításait az eszköz-védett tárolóból olvassa, a billentyűzet meg is
 * jelent. Csak épp NÉMÁN — vagyis pont abban a pillanatban hallgatott el,
 * amikor a legnagyobb szükség lett volna rá: a saját telefonod
 * feloldásánál.
 *
 * A MEGOLDÁS: a zárképernyő szövegkészlete ZÁRT HALMAZ. Tíz számjegy, két
 * gomb, és néhány rögzített mondat. Ez előre felvehető, és a
 * `MediaPlayer` — a beszédmotorral ellentétben — semmilyen külső
 * alkalmazástól nem függ. Az `assets` pedig az APK része, tehát
 * titkosított fázisban is olvasható.
 *
 * MIKOR MELYIK: a feloldás UTÁN a rendes beszédmotor szól, mert az szebb
 * és a felhasználó saját hangbeállításait követi. A beépített klipek CSAK
 * az első feloldás előtt szólalnak meg — ott, ahol eddig némaság volt.
 */
class KeyguardVoice(private val context: Context) {

    private companion object {
        const val TAG = "SDL_PINASSIST"
        const val ASSET_DIR = "zarhang"
    }

    /** A rendes beszédmotor. Feloldás után ez szól. */
    var tts: TtsManager? = null

    private var player: MediaPlayer? = null

    /** Ami még lejátszásra vár — a klipek egymás után szólalnak meg. */
    private val queue = ArrayDeque<String>()

    /**
     * FEL VAN-E OLDVA A KÉSZÜLÉK.
     *
     * Ez dönti el, melyik úton beszélünk. Nem azt nézzük, hogy a
     * beszédmotor „működik-e" — arra nincs megbízható kérdés, és egy
     * félig bekötött motor némán nyel el mindent. A feloldottság viszont
     * egyértelmű, és pontosan azt a határt jelöli, ahol a motor
     * elérhetetlen.
     */
    private fun unlocked(): Boolean = try {
        val um = context.getSystemService(Context.USER_SERVICE) as? UserManager
        um?.isUserUnlocked ?: true
    } catch (_: Exception) {
        // Ha nem tudjuk megállapítani, a beépített hangot választjuk:
        // az legfeljebb kevésbé szép, de MEGSZÓLAL.
        false
    }

    /**
     * Egy megszólalás.
     *
     * @param keys a beépített klipek kulcsai, ebben a sorrendben
     * @param text ugyanaz emberi mondatként, a beszédmotornak
     */
    fun say(keys: List<String>, text: String) {
        if (unlocked()) {
            val engine = tts
            if (engine != null) {
                try {
                    engine.speak(text)
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "beszedmotor hiba, beepitett hangra valtok: ${e.message}")
                }
            }
        }
        playClips(keys)
    }

    fun say(key: String, text: String) = say(listOf(key), text)

    /**
     * PRÓBA: a beépített hangok KÉNYSZERÍTETT lejátszása.
     *
     * MIÉRT KELL: ezek a klipek csak a bekapcsolás utáni, feloldás előtti
     * percben szólalnak meg — egyébként a rendes beszédmotor beszél. Ez az
     * egyetlen pillanat viszont pont az, amit a felhasználó a legkevésbé tud
     * kényelmesen kipróbálni: ott áll a zárt telefonnal a kezében.
     *
     * Ezért van egy menüpont, ami feloldott állapotban is ezeket játssza le.
     * Így meg lehet hallgatni, mielőtt éles helyzetben számítana rájuk.
     */
    fun playBuiltInDemo() {
        playClips(
            listOf(
                "bevezeto", "sz1", "sz2", "sz3",
                "torles", "megerosites",
                "beirva", "db3", "szamjegy",
                "elkuldve"
            )
        )
    }

    /** Megvan-e egyáltalán a beépített készlet ebben a változatban. */
    fun hasBuiltInClips(): Boolean = try {
        (context.assets.list(ASSET_DIR)?.size ?: 0) > 0
    } catch (_: Exception) {
        false
    }

    /** A beírt számjegyek darabszáma — „három számjegy beírva". */
    fun countKeys(count: Int): List<String> = when {
        count <= 0 -> listOf("nincs_szamjegy")
        count <= 16 -> listOf("db$count", "szamjegy")
        // Tizenhat fölött nem mondunk számot: nincs rá klip, és egy PIN
        // úgysem szokott ilyen hosszú lenni. A tény viszont elhangzik.
        else -> listOf("szamjegy")
    }

    /** A billentyűzet egy tételének kulcsa. */
    fun itemKey(label: String): String = when (label) {
        "0" -> "sz0"
        "1" -> "sz1"
        "2" -> "sz2"
        "3" -> "sz3"
        "4" -> "sz4"
        "5" -> "sz5"
        "6" -> "sz6"
        "7" -> "sz7"
        "8" -> "sz8"
        "9" -> "sz9"
        "Törlés" -> "torles"
        "Megerősítés" -> "megerosites"
        else -> ""
    }

    // ── Lejátszás ───────────────────────────────────────────────────────

    private fun playClips(keys: List<String>) {
        val usable = keys.filter { it.isNotBlank() }
        if (usable.isEmpty()) return
        stop()
        queue.clear()
        queue.addAll(usable)
        playNext()
    }

    private fun playNext() {
        val key = queue.removeFirstOrNull() ?: return
        val file = clipFile(key)
        if (file == null) {
            // Ami nincs meg, azt átugorjuk — a mondat többi része szóljon.
            playNext()
            return
        }
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    release2()
                    playNext()
                }
                setOnErrorListener { _, _, _ ->
                    release2()
                    playNext()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "zarhang lejatszas hiba ($key): ${e.message}")
            release2()
            playNext()
        }
    }

    private fun release2() {
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }

    fun stop() {
        queue.clear()
        release2()
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
    }

    /**
     * A KLIP FÁJLKÉNT — mert a MediaPlayer az assetsből csak
     * `AssetFileDescriptor`-ral tud olvasni, és az OEM-eken néha
     * megbízhatatlan. Ezért az első kéréskor kimásoljuk az ESZKÖZ-VÉDETT
     * gyorsítótárba: az titkosított fázisban is írható és olvasható,
     * ellentétben a szokásos gyorsítótárral.
     */
    private fun clipFile(key: String): File? {
        val dir = cacheDir() ?: return null
        val out = File(dir, "$key.m4a")
        if (out.exists() && out.length() > 300) return out
        return try {
            context.assets.open("$ASSET_DIR/$key.m4a").use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            if (out.length() > 300) out else null
        } catch (e: Exception) {
            Log.w(TAG, "zarhang kicsomagolas hiba ($key): ${e.message}")
            null
        }
    }

    private fun cacheDir(): File? = try {
        val base = try {
            context.createDeviceProtectedStorageContext() ?: context
        } catch (_: Exception) {
            context
        }
        File(base.cacheDir, ASSET_DIR).apply { mkdirs() }
    } catch (e: Exception) {
        Log.w(TAG, "zarhang gyorsitotar hiba: ${e.message}")
        null
    }
}
