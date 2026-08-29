package com.superdl.launcher.report

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A HIBAJELENTÉS ELJUTTATÁSA A FEJLESZTŐHÖZ — három úton.
 *
 * MIÉRT HÁROM: a tesztelők nagyon eltérő tudásúak. Van, aki soha nem állított
 * be e-mailt a telefonján, és van, aki hálózat nélküli helyen próbálja. Ha
 * csak EGY utat kínálnánk, a jelentések fele elveszne — pedig pont az a
 * jelentés a legértékesebb, amit egy kevésbé gyakorlott felhasználó küld.
 */
object BugReportSender {

    /**
     * Ide érkeznek a jelentések.
     *
     * UGYANAZ a cím, mint a Névjegyben — egyetlen helyről vesszük, hogy ne
     * lehessen kétféle elérhetőség a programban.
     */
    const val TARGET_EMAIL = com.superdl.launcher.legal.LegalTexts.DEVELOPER_EMAIL

    /**
     * 1. ÚT — A FELHASZNÁLÓ SAJÁT LEVELEZŐJÉVEL.
     *
     * EZ A LEGEGYSZERŰBB, ÉS EZ AZ ALAPÉRTELMEZETT. Megnyitjuk azt a
     * levelezőt, ami már be van állítva a telefonon (Gmail vagy bármi más),
     * ELŐRE KITÖLTVE: címzett, tárgy, teljes szöveg.
     *
     * A tesztelőnek NEM kell jelszót megadnia, fiókot beállítania, semmit —
     * csak elküldeni a kész levelet.
     *
     * @return sikerült-e megnyitni levelezőt
     */
    fun sendWithMailApp(context: Context, report: String, subject: String): Boolean = try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(TARGET_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, report)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        android.util.Log.w(TAG, "levelezo megnyitas hiba: ${e.message}")
        false
    }

    /**
     * 2. ÚT — MENTÉS FÁJLBA, a WiFi portálról letölthető helyre.
     *
     * MIÉRT KELL: hálózat nélkül is működik, és aki nem akar levelezni, az
     * a portálról letöltheti és elküldheti, ahogy neki kényelmes. A fájl
     * ott marad, tehát később is előkereshető.
     *
     * @return a mentett fájl, vagy null ha nem sikerült
     */
    fun saveToFile(context: Context, report: String): File? = try {
        val dir = File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS),
            "hibajelentesek"
        ).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
        val file = File(dir, "superdl-hiba-$stamp.txt")
        // ATOMI ÍRÁS: előbb ideiglenes fájlba, majd átnevezés. Így nem
        // keletkezhet félkész jelentés, ha közben megszakad valami.
        val temp = File(dir, "superdl-hiba-$stamp.tmp")
        temp.writeText(report, Charsets.UTF_8)
        if (temp.renameTo(file)) file else temp
    } catch (e: Exception) {
        android.util.Log.w(TAG, "jelentes mentes hiba: ${e.message}")
        null
    }

    /**
     * 3. ÚT — MEGOSZTÁS bármivel.
     *
     * Aki nem levelezővel, hanem üzenetküldővel (WhatsApp, Messenger, Viber)
     * küldené el, annak ez az út. Sok vak felhasználónak ez a megszokott
     * csatorna, és sokkal kényelmesebb neki, mint az e-mail.
     */
    fun share(context: Context, report: String, subject: String): Boolean = try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, report)
        }
        val chooser = Intent.createChooser(intent, "Hibajelentés küldése")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        true
    } catch (e: Exception) {
        android.util.Log.w(TAG, "megosztas hiba: ${e.message}")
        false
    }

    /** Hány mentett jelentés vár a telefonon. */
    fun savedCount(context: Context): Int = try {
        File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS),
            "hibajelentesek"
        ).listFiles()?.count { it.name.endsWith(".txt") } ?: 0
    } catch (_: Exception) {
        0
    }

    private const val TAG = "SDL_REPORT"
}
