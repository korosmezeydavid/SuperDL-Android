package com.superdl.launcher.youtube

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AZ UTOLSÓ YOUTUBE-PRÓBÁLKOZÁS NYOMA.
 *
 * A HIBA, AMI EZT KIKÉNYSZERÍTETTE (Mezei Géza, Xiaomi M2103K19G, Android 13,
 * 1.63.8): az 1.63.6-os összeomlást megjavítottuk, és a jelentésből látszott
 * is, hogy a program már nem omlik össze — a felhasználó viszont pontosan
 * ugyanazt tapasztalta: „a verziószám megemelkedett, de a YouTube-nál nem
 * változott semmi".
 *
 * AZ OK: a takarékos módnak KÉT külön zsákutcája van, és mindkettő ugyanúgy
 * hangzott. Az egyik az, hogy a feloldó egyetlen címet sem talált; a másik az,
 * hogy talált, de a YouTube elutasította a lejátszást. A kettő GYÖKERESEN
 * más javítást kíván — az elsőnél a feloldó szorul cserére, a másodiknál a
 * kliens-álca (PO token). A jelentésből viszont egyik sem látszott.
 *
 * MIÉRT NEM ELÉG A LOGCAT: a `resolveFromInnerTube` eddig is naplózta minden
 * kliens válaszát `android.util.Log`-gal. Csakhogy a logcat a fejlesztő
 * gépéhez van kötve. Egy vak tesztelő a másik városban nem tud logcatot
 * húzni, és mire a telefon a kezünkbe kerülne, a gyűrűs puffer rég
 * felülírta. Az az adat tehát LÉTEZETT, csak soha nem jutott el hozzánk.
 *
 * Ezért a nyom most a tárolóba kerül, és a hibajelentés viszi magával.
 *
 * ADATVÉDELEM: a nyom a videó azonosítóját is tartalmazza. Ez SZÁNDÉKOS és
 * ez az egyetlen ilyen adat: enélkül nem tudjuk újrajátszani az esetet, és
 * nem derül ki, hogy a hiba minden videónál jelentkezik-e, vagy csak a
 * korhatáros, régiózárt, beágyazás-tiltott darabokon. A jelentést a
 * felhasználó maga küldi el, és a küldés előtt végig meghallgathatja.
 */
object YoutubeDiagnostics {

    private const val PREFS = "youtube_diag"
    private const val KEY_TRACE = "last_trace"

    /** Egy sor sem lehet hosszabb ennél — a jelentést ember olvassa. */
    private const val MAX_LINE = 200

    /**
     * MIÉRT VAN FELSŐ HATÁR A SOROKON: a feloldó öt klienst, tizenöt Piped- és
     * több Invidious-tükröt is végigkérdezhet. Korlát nélkül a jelentés
     * hosszabb lenne, mint a felhasználó leírása — és akkor senki nem olvassa
     * el egyiket sem.
     */
    private const val MAX_LINES = 40

    private val lines = mutableListOf<String>()
    private var videoId: String = ""
    private var startedAt: Long = 0L

    /** Új próbálkozás kezdete. Az előző nyom ilyenkor esik ki. */
    @Synchronized
    fun begin(videoId: String) {
        this.videoId = videoId
        startedAt = System.currentTimeMillis()
        lines.clear()
    }

    /**
     * Egy lépés eredménye — kliens neve, HTTP-kód, hány használható cím.
     *
     * Ez fut a feloldó hálózati szálán is, ezért szinkronizált: a
     * `resolveStreamUrls` több klienst kérdez egymás után, és a jövőben
     * párhuzamosan is kérdezhetne.
     */
    @Synchronized
    fun note(line: String) {
        if (lines.size >= MAX_LINES) return
        lines += line.take(MAX_LINE)
    }

    /**
     * A VÉGEREDMÉNY, ÉS A KIÍRÁS PILLANATA.
     *
     * Itt írunk tárolóba, nem a `note()`-ban: a nyom akkor ér valamit,
     * amikor eldőlt, hogy szól-e a hang. Így egy próbálkozás egy írás, nem
     * negyven — és a nyom azelőtt kerül biztonságba, hogy a szolgáltatás
     * három másodperc múlva leállítaná magát.
     */
    @Synchronized
    fun finish(context: Context, outcome: String) {
        val text = buildString {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(if (startedAt > 0L) startedAt else System.currentTimeMillis()))
            appendLine("ideje: $stamp")
            appendLine("videó: ${videoId.ifBlank { "(ismeretlen)" }}")
            appendLine("eltelt: ${System.currentTimeMillis() - startedAt} ezredmásodperc")
            appendLine("kimenetel: $outcome")
            if (lines.isNotEmpty()) {
                appendLine("lépések:")
                lines.forEach { appendLine("  $it") }
            }
        }
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TRACE, text)
                .apply()
        } catch (_: Exception) {
        }
    }

    /** A hibajelentésnek. Üres szöveg, ha még nem volt próbálkozás. */
    fun report(context: Context): String = try {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TRACE, "")
            .orEmpty()
    } catch (_: Exception) {
        ""
    }
}
