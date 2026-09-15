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
     * 1. ÚT — A FELHASZNÁLÓ SAJÁT LEVELEZŐJÉVEL, A JELENTÉS CSATOLMÁNYKÉNT.
     *
     * MIÉRT CSATOLMÁNY, ÉS MIÉRT NEM A LEVÉL TÖRZSE (2026-09-15):
     *
     * Kiss István jelentése CSONKÁN érkezett meg: a fejléc és a részletes
     * napló ELSŐ SORA jött át, utána semmi. Az egész szerepkör-rész, a
     * kisegítő szolgáltatások és a tételek listája — vagyis pont az, amiért
     * a napló készül — eltűnt útközben. Ugyanez történt korábban Géza
     * jelentésével is; akkor a menü-útra fogtuk, de most kiderült, hogy a
     * varázsló-úton is elveszik.
     *
     * A hosszú szöveg a levelezőig egy `Intent` extrájában utazik, és ott
     * bárhol elcsonkulhat: a rendszer korlátja, a levelező szerkesztője, a
     * beillesztés. Nincs hibaüzenet, nincs jelzés — a felhasználó elküldi,
     * mi meg csak egy fejlécet kapunk, és semmit nem tudunk kezdeni vele.
     *
     * Ezért mostantól: a jelentés FÁJLBA kerül, a fájl megy CSATOLMÁNYKÉNT,
     * a levél törzsébe pedig csak egy rövid kísérő szöveg. Egy csatolt
     * fájlt semmi nem vág el félbe. A mentés akkor is megtörténik, ha a
     * levelező meg sem nyílik — így a jelentés a telefonon marad, és a
     * WiFi portálról bármikor letölthető.
     *
     * @return sikerült-e megnyitni levelezőt
     */
    fun sendWithMailApp(context: Context, report: String, subject: String): Boolean {
        // ELŐBB A MENTÉS. Ez a biztonsági másolat: ha a levelező elszáll,
        // ha a felhasználó meggondolja magát, ha a szöveg elveszik — a
        // jelentés akkor is megvan a telefonon.
        val file = saveToFile(context, report)
        val uri = file?.let { shareUri(context, it) }

        // CSATOLMÁNNYAL, ha van fájl és a rendszer át tudja adni.
        if (uri != null && sendMailWithAttachment(context, uri, subject)) return true

        // VÉGSŐ TARTALÉK: a régi út, a szöveg a levél törzsében. Csonka
        // jelentés is több a semminél — de a mentett fájl akkor is megvan.
        return try {
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
    }

    /**
     * A csatolmányos levél — LEHETŐLEG EGYENESEN A LEVELEZŐBE.
     *
     * MIÉRT NEM EGYSZERŰEN EGY VÁLASZTÓ: a menüpont azt ígéri, hogy „a saját
     * levelezőmmel". Ha e helyett egy tucat alkalmazás listája ugrik fel,
     * azt vakon végig kell hallgatni és el kell találni benne a jót — pedig
     * a felhasználó már választott egyszer. Ezért előbb MEGKERESSÜK, melyik
     * program kezeli a leveleket ezen a telefonon, és egyenesen annak adjuk
     * át. Csak ha nincs ilyen, akkor jön a választó.
     */
    private fun sendMailWithAttachment(context: Context, uri: Uri, subject: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(TARGET_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, BODY_NOTE)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        mailAppPackage(context)?.let { pkg ->
            try {
                context.startActivity(Intent(intent).setPackage(pkg))
                return true
            } catch (e: Exception) {
                android.util.Log.w(TAG, "levelezo ($pkg) inditas hiba: ${e.message}")
            }
        }
        return try {
            val chooser = Intent.createChooser(intent, "Hibajelentés küldése")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            android.util.Log.w(TAG, "csatolmanyos kuldes hiba: ${e.message}")
            false
        }
    }

    /** Melyik program kezeli a leveleket ezen a telefonon. */
    private fun mailAppPackage(context: Context): String? = try {
        val probe = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        context.packageManager
            .resolveActivity(probe, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
            ?.takeIf { it.isNotBlank() && !it.contains("android.internal") }
    } catch (e: Exception) {
        android.util.Log.w(TAG, "levelezo keresese hiba: ${e.message}")
        null
    }

    /** A mentett jelentés átadható címe. Null, ha a rendszer nem engedi. */
    private fun shareUri(context: Context, file: File): Uri? = try {
        androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
    } catch (e: Exception) {
        android.util.Log.w(TAG, "fileprovider hiba: ${e.message}")
        null
    }

    private const val BODY_NOTE =
        "A Super DL hibajelentése a csatolt fájlban van.\n" +
            "(A szöveg azért csatolmány, mert a levél törzsében elcsonkulhat.)"

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
        // ITT IS CSATOLMÁNY, ugyanazért: a hosszú szöveg útközben elcsonkul.
        // Ha a fájl valamiért nem készül el, marad a szöveg — csonkán is
        // többet ér, mint a semmi.
        val uri = saveToFile(context, report)?.let { shareUri(context, it) }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            if (uri != null) {
                putExtra(Intent.EXTRA_TEXT, BODY_NOTE)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                putExtra(Intent.EXTRA_TEXT, report)
            }
        }
        val chooser = Intent.createChooser(intent, "Hibajelentés küldése")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
