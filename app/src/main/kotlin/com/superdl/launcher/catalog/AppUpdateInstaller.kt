package com.superdl.launcher.catalog

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * A FRISSÍTÉS LETÖLTÉSE ÉS TELEPÍTÉSE.
 *
 * MIÉRT KELL: a SuperDL nem a Google Play-ről érkezik, tehát nincs automatikus
 * frissítés. Enélkül a tesztelőknek kézzel kellene fájlokat keresgélniük —
 * vaknak ez kimondottan nehéz. Így viszont a telefon SZÓL, ha van újabb, és
 * egy mozdulattal letölti és felajánlja a telepítést.
 *
 * BIZTONSÁG: a telepítést MINDIG a rendszer végzi, a felhasználó megerősítésével.
 * Az alkalmazás semmit nem telepít magától.
 */
object AppUpdateInstaller {

    private const val TAG = "SDL_UPDATE"

    /** Van-e engedélyünk telepítőt indítani? */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** A telepítési engedély beállításának megnyitása. */
    fun openInstallPermissionSettings(context: Context): Boolean = try {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                android.net.Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
        }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "engedely-beallitas hiba: ${e.message}")
        false
    }

    /** A letöltött telepítők helye. */
    private fun downloadDir(context: Context): File =
        File(context.filesDir, "frissites").apply { mkdirs() }

    /**
     * A frissítés LETÖLTÉSE. HÁTTÉRSZÁLRÓL hívandó.
     *
     * @param onProgress százalékos állapot (0-100) — hosszú letöltésnél
     *                   érdemes bemondani, hogy ne tűnjön elakadtnak
     * @return a letöltött fájl, vagy null ha nem sikerült
     */
    fun download(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit = {}
    ): File? {
        if (url.isBlank()) return null
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "SuperDL")
            }
            if (conn.responseCode != 200) {
                Log.w(TAG, "letoltes valasz: ${conn.responseCode}")
                return null
            }
            val total = conn.contentLength.toLong()
            val target = File(downloadDir(context), "superdl-frissites.apk")
            if (target.exists()) target.delete()

            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastPercent = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val percent = ((downloaded * 100) / total).toInt()
                            // Csak minden 10 százaléknál jelzünk, hogy ne
                            // árasszuk el a felhasználót.
                            if (percent >= lastPercent + 10) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            Log.i(TAG, "frissites letoltve: ${target.length()} bajt")
            target
        } catch (e: Exception) {
            Log.w(TAG, "letoltes hiba: ${e.message}")
            null
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * A LEGUTÓBBI HIBA OKA, emberi nyelven.
     *
     * MIÉRT KELL: eddig a telepítés kudarcából csak annyi jutott el a
     * felhasználóhoz, hogy „a telepítő nem indítható" — az igazi ok
     * (`IllegalArgumentException` a FileProvider-ből) egy `catch`-ben
     * csendben elveszett. Egy vak tesztelő ebből semmit nem tud kezdeni,
     * és a fejlesztő sem, amíg elő nem veszi a naplót.
     */
    @Volatile
    var lastInstallError: String? = null
        private set

    /**
     * A TELEPÍTŐ elindítása. A rendszer kéri a megerősítést a felhasználótól.
     */
    fun install(context: Context, apk: File): Boolean {
        lastInstallError = null

        if (!apk.exists() || apk.length() <= 0L) {
            lastInstallError = "A letöltött fájl hiányzik vagy üres."
            Log.w(TAG, "telepito: hianyzo vagy ures fajl: ${apk.absolutePath}")
            return false
        }
        if (!canInstall(context)) {
            lastInstallError =
                "Nincs engedélyed telepítésre. A Beállítások, Program frissítése " +
                    "pontban a program megnyitja neked ezt a kapcsolót."
            return false
        }

        val uri = try {
            FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", apk
            )
        } catch (e: Exception) {
            // EZ VOLT A HIBA 2026-09-02-ig: a letöltés helye nem szerepelt a
            // res/xml/file_paths.xml-ben, ezért a FileProvider elutasította.
            lastInstallError =
                "A telepítőfájlt nem sikerült átadni a rendszernek. " +
                    "Ez a program hibája, kérlek jelentsd."
            Log.w(TAG, "FileProvider hiba (${apk.absolutePath}): ${e.message}", e)
            return false
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            lastInstallError = "A rendszer telepítője nem indult el: ${e.message}"
            Log.w(TAG, "telepito inditas hiba: ${e.message}", e)
            false
        }
    }

    /** A letöltött telepítő törlése (sikeres frissítés után). */
    fun cleanUp(context: Context) {
        try {
            downloadDir(context).listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {
        }
    }
}
