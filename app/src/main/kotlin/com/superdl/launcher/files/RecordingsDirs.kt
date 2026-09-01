package com.superdl.launcher.files

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * A FELVÉTELEK NYILVÁNOS HELYE.
 *
 * A BAJ, AMIT ORVOSOL: a rádió- és diktafon-felvételek eddig a program saját
 * mappájába kerültek (Android/data/...). Az Android 11 óta oda SEMMILYEN
 * fájlkezelő nem lát be — sem a miénk, sem a gyári, sem a számítógép USB-n.
 * A felvételek tehát ott voltak, csak épp elérhetetlenül: a felhasználó a
 * saját hangfelvételét nem tudta se átmásolni, se elküldeni.
 *
 * A MEGOLDÁS: nyilvános, mindenki által látott hely.
 *
 *     /Recordings/Radio      – rádiófelvételek
 *     /Recordings/Diktafon   – diktafon-felvételek
 *
 * A "Recordings" nem véletlen név: az Android 12 óta ez a rendszer SAJÁT
 * felvétel-mappája (Environment.DIRECTORY_RECORDINGS), tehát a gyári appok
 * és a médiatár is odavalónak ismerik.
 *
 * TARTALÉK ÚT: a nyilvános mappa létrehozásához teljes fájlhozzáférés kell.
 * Ha nincs meg, a régi (saját) mappát adjuk vissza — így a felvétel akkor is
 * elkészül, csak nehezebben érhető el. Amint az engedély megvan, a következő
 * hívás átköltözteti a régi felvételeket, és onnantól a nyilvános hely él.
 *
 * SEMMI NEM VÉSZ EL: a költöztetés MÁSOL, és csak sikeres másolás után töröl.
 */
object RecordingsDirs {

    private const val TAG = "SuperDL.RecDirs"
    private const val ROOT = "Recordings"

    /** Amit már megpróbáltunk átköltöztetni ebben a futásban (ne fusson újra és újra). */
    private val migrated = java.util.Collections.synchronizedSet(HashSet<String>())

    /** Rádiófelvételek: /Recordings/Radio */
    fun radio(context: Context): File = resolve(
        context = context,
        subFolder = "Radio",
        legacy = File(context.getExternalFilesDir(null), "radio_recordings")
    )

    /** Diktafon-felvételek: /Recordings/Diktafon */
    fun dictaphone(context: Context): File = resolve(
        context = context,
        subFolder = "Diktafon",
        legacy = File(context.getExternalFilesDir(null), "ProfiDiktafon")
    )

    /** A régi (saját mappás) helyek — a portál ezeket is nézze, amíg van bennük valami. */
    fun legacyRadio(context: Context): File =
        File(context.getExternalFilesDir(null), "radio_recordings")

    fun legacyDictaphone(context: Context): File =
        File(context.getExternalFilesDir(null), "ProfiDiktafon")

    /** Ember számára kimondható útvonal, a bemondásokhoz. */
    fun speakPath(dir: File): String {
        val root = Environment.getExternalStorageDirectory().absolutePath
        val rel = dir.absolutePath.removePrefix(root).trimStart('/')
        return if (rel.isBlank()) "fő tárhely" else rel.replace('/', ' ') + " mappa"
    }

    private fun resolve(context: Context, subFolder: String, legacy: File): File {
        val public = File(File(Environment.getExternalStorageDirectory(), ROOT), subFolder)
        val usable = try {
            (public.exists() || public.mkdirs()) && public.canWrite()
        } catch (e: Exception) {
            Log.w(TAG, "nyilvanos mappa nem hozhato letre: ${e.message}")
            false
        }
        if (!usable) return ensure(legacy)
        migrateOnce(context, legacy, public)
        return public
    }

    private fun ensure(dir: File): File {
        try {
            if (!dir.exists()) dir.mkdirs()
        } catch (_: Exception) {
        }
        return dir
    }

    /**
     * A régi mappa tartalmának átköltöztetése — futásonként egyszer.
     * Előbb másol, és csak sikeres másolás után törli az eredetit.
     */
    private fun migrateOnce(context: Context, from: File, to: File) {
        val key = from.absolutePath
        if (!migrated.add(key)) return
        try {
            if (!from.exists() || !from.isDirectory) return
            val files = from.listFiles()?.filter { it.isFile } ?: return
            if (files.isEmpty()) return
            var moved = 0
            files.forEach { src ->
                try {
                    var target = File(to, src.name)
                    var i = 2
                    while (target.exists() && i < 1000) {
                        val base = src.name.substringBeforeLast('.', src.name)
                        val ext = src.name.substringAfterLast('.', "")
                        target = File(to, if (ext.isBlank()) "$base ($i)" else "$base ($i).$ext")
                        i++
                    }
                    src.copyTo(target, overwrite = false)
                    if (target.exists() && target.length() == src.length()) {
                        src.delete()
                        moved++
                        try {
                            MediaScannerConnection.scanFile(
                                context, arrayOf(target.absolutePath), null, null
                            )
                        } catch (_: Exception) {
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "koltoztetes hiba (${src.name}): ${e.message}")
                }
            }
            if (moved > 0) Log.i(TAG, "$moved felvetel atkoltoztetve ide: ${to.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "koltoztetes hiba: ${e.message}")
        }
    }
}
