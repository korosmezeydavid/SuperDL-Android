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
 * ADATVÉDELEM: a jelentés SEMMILYEN személyes adatot nem tartalmaz —
 * se névjegyet, se üzenetet, se helyzetet, se jelszót. Csak a program
 * állapotát és a felhasználó SAJÁT LEÍRÁSÁT.
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

    /** Az összeomlás-napló UTOLSÓ része — a teljes fájl túl hosszú lenne. */
    private fun crashLogTail(context: Context): String = try {
        val file = java.io.File(context.filesDir, "crash_log.txt")
        if (!file.exists()) "" else {
            val lines = file.readLines()
            lines.takeLast(25).joinToString("\n")
        }
    } catch (_: Exception) {
        ""
    }
}
