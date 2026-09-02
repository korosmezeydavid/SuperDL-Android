package com.superdl.launcher.crash

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogHandler {

    private const val TAG = "SuperDL.Crash"
    private const val LOG_FILE = "crash_log.txt"
    private const val MAX_LOG_BYTES = 256 * 1024

    /** Már telepítve van-e — a kétszeres telepítés láncot építene. */
    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                appendCrash(appContext, thread, throwable)
            } catch (e: Exception) {
                // A naplóírás elbukhat: feloldás ELŐTT a tároló még
                // titkosított. Ez NEM baj — az elszigetelés a fontos, a
                // naplózás csak segítség.
                Log.e(TAG, "Failed to write crash log", e)
            }

            // HÁTTÉRSZÁL-VÉDELEM.
            //
            // A SuperDL 70-nél is több háttérszálat indít (hálózat, fájl,
            // felismerés). Ha EGY ilyen szálon hiba keletkezik, az alapból az
            // EGÉSZ folyamatot megöli — a launcher eltűnik, a vak felhasználó
            // pedig ott marad felület nélkül.
            //
            // Egy háttérművelet hibája viszont NEM teszi működésképtelenné a
            // felületet: a szál meghal, a program megy tovább. Ezért itt NEM
            // adjuk tovább a hibát a rendszernek — a szál csendben leáll.
            //
            // A FŐ SZÁLNÁL ez nem járható út: ott a felület állapota már
            // sérült lehet, ezért ott hagyjuk a rendszert dolgozni.
            // (A fő szál gesztus-hibáit amúgy is elkapja a gesztus-pajzs.)
            val isMainThread = thread === android.os.Looper.getMainLooper().thread
            if (!isMainThread) {
                Log.w(
                    TAG,
                    "Hatterszal hiba ELSZIGETELVE (${thread.name}): " +
                        "${throwable.javaClass.simpleName}. A program tovabb fut."
                )
                markBackgroundFailure(appContext)
                return@setDefaultUncaughtExceptionHandler
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Feljegyezzük, hogy volt elszigetelt háttérhiba.
     * A felhasználót NEM szakítjuk félbe miatta — lehet, hogy épp telefonál
     * vagy olvas. A diagnosztikában viszont lekérdezhető.
     */
    private fun markBackgroundFailure(context: Context) {
        try {
            val prefs = context.getSharedPreferences("superdl_crash", Context.MODE_PRIVATE)
            val count = prefs.getInt("background_failures", 0) + 1
            prefs.edit()
                .putInt("background_failures", count)
                .putLong("last_background_failure", System.currentTimeMillis())
                .apply()
        } catch (_: Exception) {
        }
    }

    /** Hány háttérhibát szigeteltünk el (a diagnosztikához). */
    fun backgroundFailureCount(context: Context): Int = try {
        context.getSharedPreferences("superdl_crash", Context.MODE_PRIVATE)
            .getInt("background_failures", 0)
    } catch (_: Exception) {
        0
    }

    fun readRecent(context: Context, maxChars: Int = 8000): String {
        val file = logFile(context)
        if (!file.exists()) return ""
        val text = file.readText()
        return if (text.length <= maxChars) text else text.takeLast(maxChars)
    }

    private fun appendCrash(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        // A VERZIÓ IS BEKERÜL A FEJLÉCBE.
        //
        // Enélkül egy hetekkel korábbi, MÁR JAVÍTOTT összeomlás úgy néz ki a
        // hibajelentésben, mintha az imént történt volna — és a keresés arra
        // a hibára megy el, ami már nincs. Pontosan ez történt 2026-09-01-én:
        // egy 1.54.9-es bankjegy-hiba nyoma jelent meg egy 1.57.0-s
        // jelentésben, és az első fél óra rossz nyomon ment el.
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
        val entry = buildString {
            appendLine("=== $timestamp | verzió: $version ===")
            appendLine("Thread: ${thread.name}")
            append(sw.toString())
            appendLine()
        }
        val file = logFile(context)
        file.parentFile?.mkdirs()
        file.appendText(entry)
        trimIfNeeded(file)
        Log.e(TAG, "Uncaught exception on ${thread.name}", throwable)
    }

    private fun logFile(context: Context): File =
        File(context.filesDir, LOG_FILE)

    private fun trimIfNeeded(file: File) {
        if (file.length() <= MAX_LOG_BYTES) return
        val text = file.readText()
        file.writeText(text.takeLast(MAX_LOG_BYTES))
    }
}