package com.superdl.launcher.report

import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HIBAJELENTÉS ÖSSZEÁLLÍTÁSA.
 *
 * MIÉRT KELL PILLANATKÉP, HA VAN ÖSSZEOMLÁS-NAPLÓ:
 * A legbosszantóbb hibák NEM összeomlások. A 2026-08-16-i eset megmutatta:
 * a PIN segéd és a képernyőolvasó "összeakadt" a zárolási képernyőn — a
 * program nem omlott össze, tehát a napló ÜRES maradt, a rendszernapló pedig
 * mire megnéztük, felülíródott. Egy vak felhasználó ilyenkor csak annyit tud
 * mondani: "nem működik".
 *
 * Ezért a jelentés RÖGZÍTI AZ ÁLLAPOTOT abban a pillanatban, amikor a
 * felhasználó jelenti a hibát: milyen készülék, mi van bekapcsolva, milyen
 * beállítások élnek.
 *
 * ADATVÉDELEM: a jelentés se névjegyet, se üzenetet, se helyzetet, se
 * jelszót nem tartalmaz. Csak a program állapotát és a felhasználó SAJÁT
 * LEÍRÁSÁT.
 *
 * EGYETLEN KIVÉTEL, és ez szándékos: az „UTOLSÓ YOUTUBE-PRÓBÁLKOZÁS"
 * szakaszban benne van a videó azonosítója. Enélkül nem tudjuk újrajátszani
 * az esetet, és nem derül ki, hogy a hiba minden videónál jelentkezik-e,
 * vagy csak a korhatáros, régiózárt, beágyazás-tiltott darabokon. A jelentés
 * csak akkor indul útnak, ha a felhasználó maga elküldi — és előtte
 * végighallgathatja.
 */
object BugReport {

    /**
     * A jelentés szövege.
     * @param userDescription amit a felhasználó elmondott a hibáról
     */
    fun build(context: Context, userDescription: String): String = buildString {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        appendLine("SUPER DL — HIBAJELENTÉS")
        appendLine("========================================")
        appendLine("Ideje: $now")
        appendLine()

        appendLine("A FELHASZNÁLÓ LEÍRÁSA:")
        appendLine(userDescription.ifBlank { "(nem adott meg leírást)" })
        appendLine()

        appendLine("PROGRAM:")
        appendLine("  verzió: ${appVersion(context)}")
        appendLine("  változat: ${if (isDebugBuild(context)) "fejlesztői" else "kiadási"}")
        appendLine()

        appendLine("KÉSZÜLÉK:")
        appendLine("  gyártó: ${Build.MANUFACTURER}")
        appendLine("  típus: ${Build.MODEL}")
        appendLine("  Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()

        appendLine("ÁLLAPOT A JELENTÉS PILLANATÁBAN:")
        appendLine("  kisegítő szolgáltatások: ${accessibilityState(context)}")
        appendLine("  beszéd hangcsatornája: ${speechChannel(context)}")
        appendLine("  biztonságos mód: ${safeMode()}")
        appendLine("  elszigetelt háttérhibák: ${backgroundFailures(context)}")
        appendLine()

        val crash = crashLogTail(context)
        if (crash.isNotBlank()) {
            appendLine("UTOLSÓ ÖSSZEOMLÁS (technikai):")
            appendLine(crash)
        } else {
            appendLine("ÖSSZEOMLÁS-NAPLÓ: üres (nem volt összeomlás)")
        }

        // AMI NEM OMLIK ÖSSZE, AZ IS LEHET HIBA.
        //
        // A 2026-09-11-i eset: az 1.63.6-os YouTube-összeomlást megjavítottuk,
        // és a jelentésből LÁTSZOTT is, hogy már nem omlik össze — a tesztelő
        // mégis pontosan ugyanazt tapasztalta. A takarékos módnak ugyanis két
        // külön zsákutcája van, és a jelentésből egyik sem derült ki.
        //
        // Ez a szakasz azért van, hogy a következő kör ne találgatás legyen.
        val youtube = com.superdl.launcher.youtube.YoutubeDiagnostics.report(context)
        if (youtube.isNotBlank()) {
            appendLine()
            appendLine("UTOLSÓ YOUTUBE-PRÓBÁLKOZÁS:")
            appendLine(youtube.trimEnd())
        }
    }

    /** Rövid tárgy az e-mailhez — így a levelek rendezhetők. */
    fun subject(context: Context): String =
        "Super DL hibajelentés — ${appVersion(context)} — ${Build.MODEL}"

    // ── Az egyes adatok ─────────────────────────────────────────────────────

    private fun appVersion(context: Context): String = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: "ismeretlen"
    } catch (_: Exception) {
        "ismeretlen"
    }

    private fun isDebugBuild(context: Context): Boolean =
        context.packageName.endsWith(".debug")

    /**
     * MELYIK kisegítő szolgáltatások futnak.
     * Ez a legfontosabb adat: a tegnapi hibát pontosan ez árulta volna el.
     */
    private fun accessibilityState(context: Context): String = try {
        val enabled = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val parts = mutableListOf<String>()
        if (enabled.contains("ScreenReaderService")) parts += "Super DL olvasó"
        if (enabled.contains("KeyguardPinAccessibilityService")) parts += "PIN segéd"
        if (enabled.contains("talkback", ignoreCase = true)) parts += "TalkBack"
        // Más gyártók olvasói is fontosak: ütközhetnek a mienkkel.
        val others = enabled.split(':').count { it.isNotBlank() } - parts.size
        if (others > 0) parts += "$others egyéb"
        if (parts.isEmpty()) "egy sem" else parts.joinToString(", ")
    } catch (_: Exception) {
        "nem olvasható"
    }

    private fun speechChannel(context: Context): String = try {
        if (com.superdl.launcher.tts.TtsSettingsStore.getSpeechChannel(context) ==
            com.superdl.launcher.tts.TtsSettingsStore.CHANNEL_ACCESSIBILITY
        ) "kisegítő" else "média"
    } catch (_: Exception) {
        "ismeretlen"
    }

    private fun safeMode(): String = try {
        if (com.superdl.launcher.crash.StartupGuard.isSafeMode) "IGEN" else "nem"
    } catch (_: Exception) {
        "ismeretlen"
    }

    private fun backgroundFailures(context: Context): String = try {
        com.superdl.launcher.crash.CrashLogHandler.backgroundFailureCount(context).toString()
    } catch (_: Exception) {
        "?"
    }

    /**
     * AZ UTOLSÓ ÖSSZEOMLÁS — A FEJLÉCÉVEL EGYÜTT.
     *
     * A KORÁBBI VÁLTOZAT HIBÁS VOLT, ÉS EZ DRÁGÁN DERÜLT KI. Egyszerűen az
     * utolsó 25 SORT vágta ki a fájlból — csakhogy egy stack trace hosszabb
     * ennél, tehát a `=== dátum ===` fejléc MINDIG lemaradt. Így a jelentés
     * egy időpont nélküli nyomot mutatott, ami úgy nézett ki, mintha az
     * imént történt volna.
     *
     * 2026-09-01-én ez egy hetekkel korábbi, MÁR JAVÍTOTT bankjegy-hiba
     * nyomát tette be egy friss jelentésbe, és a hibakeresés első fél órája
     * rossz nyomon ment el — miközben a valódi hiba (a névjegy-szinkron
     * összeomlása) egészen máshol volt.
     *
     * Ez nem szépséghiba: egy vak tesztelő nem tudja ellenőrizni a jelentés
     * tartalmát, azt küldi el, amit a program összerak. Ha a program
     * félrevezet, a tesztelő is félrevezet, akaratlanul.
     *
     * Mostantól a BEJEGYZÉS ELEJÉTŐL vágunk, és ha a nyom régi vagy más
     * verzióból való, azt KIMONDJUK.
     */
    private fun crashLogTail(context: Context): String = try {
        val file = java.io.File(context.filesDir, "crash_log.txt")
        if (!file.exists()) "" else {
            val lines = file.readLines()
            // Az utolsó bejegyzés kezdete. A fejléc alakja: "=== dátum | verzió: x ==="
            val start = lines.indexOfLast { it.startsWith("=== ") }
            val entry = if (start >= 0) lines.drop(start) else lines.takeLast(30)
            val header = entry.firstOrNull().orEmpty()
            val warning = stalenessWarning(context, header)
            (if (warning.isBlank()) entry else listOf(warning) + entry)
                .take(32)
                .joinToString("\n")
        }
    } catch (_: Exception) {
        ""
    }

    /**
     * FIGYELMEZTETÉS, HA A NYOM NEM MOSTANI.
     *
     * Két külön ok, és mindkettő számít:
     *  - MÁS VERZIÓ: akkor a hiba lehet, hogy már javítva van.
     *  - RÉGI (7 napnál idősebb): akkor valószínűleg nem ahhoz van köze,
     *    amit a felhasználó most tapasztalt.
     *
     * Inkább jelezzünk feleslegesen, mint hogy egyszer is elhallgassuk:
     * a fölösleges figyelmeztetés egy mondat, az elhallgatott elavultság
     * fél nap keresés a rossz helyen.
     */
    private fun stalenessWarning(context: Context, header: String): String {
        val reasons = mutableListOf<String>()

        val version = Regex("verzió:\\s*([^=\\s]+)").find(header)?.groupValues?.getOrNull(1)
        if (version == null) {
            reasons += "verzió ismeretlen (régi naplóformátum)"
        } else if (version != appVersion(context).substringBefore(" ")) {
            reasons += "MÁS VERZIÓBAN történt ($version), lehet, hogy már javítva van"
        }

        val stamp = Regex("===\\s*(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})")
            .find(header)?.groupValues?.getOrNull(1)
        if (stamp != null) {
            try {
                val format = java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    java.util.Locale.getDefault()
                )
                val whenMs = format.parse(stamp)?.time ?: 0L
                val days = (System.currentTimeMillis() - whenMs) / 86_400_000L
                if (days >= 7) reasons += "$days napja történt"
            } catch (_: Exception) {
            }
        }

        return if (reasons.isEmpty()) {
            ""
        } else {
            "  FIGYELEM: ez a nyom nem feltétlenül a most jelzett hibáé — " +
                reasons.joinToString("; ") + "."
        }
    }
}
