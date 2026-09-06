package com.superdl.launcher.voicetheme

import android.content.Context
import android.util.Log
import com.superdl.launcher.share.CloudTargets
import com.superdl.launcher.share.CloudUploader
import java.io.File

/**
 * „BEKÜLDÖM A KÖZÖSBE" — a saját téma útja a katalógusig.
 *
 * MIÉRT NEM AUTOMATIKUS, ÉS MIÉRT NEM KELL HOZZÁ KISZOLGÁLÓ:
 *
 * Egy „töltsd fel, és megjelenik mindenkinél" gomb azt jelentené, hogy
 * bárki hangja bekerül bárki telefonjába emberi szem (fül) nélkül. Ehhez
 * kiszolgáló, fiókok, moderálás és felelősség kellene — és az első
 * kellemetlen csomagnál kiderülne, hogy nincs rá fék.
 *
 * Ehelyett a lánc ennyi: a program feltölti a kész téma-fájlt egy
 * ideiglenes tárhelyre (ugyanarra, amit a megosztás is használ), összeállít
 * egy KÉSZ üzenetet a fejlesztőnek, és a felhasználó egy söpréssel elküldi.
 * A fejlesztő meghallgatja, és ő teszi közzé. Ezt a program KI IS MONDJA:
 * a beküldés nem közzététel.
 *
 * A cél a `0x0.st`, mert harminc napig él, nem tűnik el az első letöltés
 * után, és utólag törölhető róla a fájl.
 */
object VoiceThemeSubmit {

    private const val TAG = "SDL_VOICETHEME"

    /** Ide töltünk fel. Nem egyszeri letöltésű, és sokáig él. */
    private const val TARGET_ID = "zerox"

    data class Result(
        val ok: Boolean,
        /** Felolvasható mondat — sikernél is, hibánál is. */
        val message: String,
        /** A kész levél szövege, ha sikerült. */
        val letter: String = "",
        val url: String = ""
    )

    /**
     * A NYILATKOZAT, amit a beküldés előtt hallani kell.
     *
     * Nem jogi szöveg, hanem az, amit tényleg tudni kell: a hang a tiéd, és
     * onnantól bárki letöltheti.
     */
    const val DECLARATION =
        "Mielőtt beküldöd, két dolgot kérek. Az első: a felvétel a sajátod " +
            "legyen — a te hangod, vagy olyan hang, amit szabadon továbbadhatsz. " +
            "A második: ha bekerül a katalógusba, onnantól bárki letöltheti és " +
            "használhatja. Ha ez rendben van, söpörj jobbra. Ha meggondolnád, " +
            "söpörj balra — a téma akkor is megmarad a telefonodon, és " +
            "egyenként bárkinek elküldheted a Beszédtéma megosztása ponttal."

    /**
     * Feltöltés és levél-összeállítás. HÁTTÉRSZÁLRÓL hívandó.
     *
     * @param file a kész téma-csomag (`tema-<azonosito>.json`)
     */
    fun upload(
        context: Context,
        file: File,
        themeId: String,
        name: String,
        author: String,
        onProgress: (Int) -> Unit = {}
    ): Result {
        val target = CloudTargets.byId(TARGET_ID)
            ?: return Result(false, "A feltöltési célt nem találom a programban.")
        if (!file.isFile || file.length() <= 0L) {
            return Result(false, "A téma fájlja eltűnt. Csomagold újra.")
        }
        val res = try {
            CloudUploader.upload(file, target, onProgress)
        } catch (t: Throwable) {
            Log.w(TAG, "bekuldes feltoltes hiba: ${t.message}")
            return Result(false, "A feltöltés megszakadt. Ok: ${t.javaClass.simpleName}.")
        }
        if (!res.ok || res.url.isBlank()) {
            // A CloudUploader üzenete már emberi nyelvű, ne írjuk felül.
            return Result(false, res.message)
        }
        val letter = buildLetter(context, themeId, name, author, file, res.url)
        return Result(
            ok = true,
            message = "Feltöltve. Most elküldheted a beküldést a fejlesztőnek. " +
                "Ez még NEM közzététel: ő meghallgatja, és ő dönt róla.",
            letter = letter,
            url = res.url
        )
    }

    /** A tárgy — hogy a fejlesztő postájában egy szempillantás alatt kilátszik. */
    fun subject(name: String): String = "SuperDL beszédtéma beküldés: $name"

    /**
     * A KÉSZ LEVÉL.
     *
     * Minden benne van, amiből a fejlesztő dönteni tud, és amiből a
     * katalógus-sor összeáll — nem kell visszakérdeznie semmit.
     */
    private fun buildLetter(
        context: Context,
        themeId: String,
        name: String,
        author: String,
        file: File,
        url: String
    ): String {
        val count = VoiceThemePackage.clipCount(context, themeId)
        val events = VoiceEvent.entries.filter {
            VoiceThemePlayer.clipIn(context, themeId, it) != null
        }
        val missing = VoiceEvent.entries.filterNot { it in events }
        return buildString {
            appendLine("BESZÉDTÉMA BEKÜLDÉS A KÖZÖS KATALÓGUSBA")
            appendLine("========================================")
            appendLine()
            appendLine("A téma neve: $name")
            appendLine("Szerző: ${author.ifBlank { "(nem adta meg)" }}")
            appendLine("Azonosító: $themeId")
            appendLine("Nyelv: hu")
            appendLine("Hangok száma: $count / ${VoiceEvent.entries.size}")
            appendLine("Van hang ehhez: ${events.joinToString(", ") { it.label }}")
            if (missing.isNotEmpty()) {
                appendLine("Nincs hang ehhez: ${missing.joinToString(", ") { it.label }}")
                appendLine("  (ezeknél a beépített Elena szólal meg)")
            }
            appendLine("Méret: ${CloudTargets.sizeText(file.length())}")
            appendLine()
            appendLine("LETÖLTÉS:")
            appendLine(url)
            appendLine()
            appendLine("NYILATKOZAT: a beküldő megerősítette, hogy a felvétel a sajátja,")
            appendLine("és hozzájárul ahhoz, hogy a katalógusból bárki letölthesse.")
            appendLine()
            appendLine("KATALÓGUS-SOR JAVASLAT (mobil-katalogus.json):")
            appendLine("{")
            appendLine("  \"id\": \"tema_$themeId\",")
            appendLine("  \"nev\": \"$name\",")
            appendLine("  \"tipus\": \"soundtheme\",")
            appendLine("  \"kategoria\": \"megjelenes\",")
            appendLine("  \"szerzo\": \"$author\",")
            appendLine("  \"verzio\": 1,")
            appendLine("  \"meret\": ${file.length()},")
            appendLine("  \"fajl\": \"temak/tema-$themeId.json\"")
            appendLine("}")
        }
    }
}
