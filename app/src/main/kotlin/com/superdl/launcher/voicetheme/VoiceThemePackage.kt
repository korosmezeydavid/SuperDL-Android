package com.superdl.launcher.voicetheme

import android.content.Context
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * A BESZÉDTÉMA CSOMAG — egyetlen JSON fájl, a hangok base64-ben.
 *
 * MIÉRT PONT ÍGY, ÉS MIÉRT NEM ZIP:
 *
 * A katalógus letöltője (`CatalogClient.downloadModule`) SZÖVEGET tölt le,
 * JSON-ként ellenőrzi, és szövegként menti. Binárisat nem tud kezelni. Ha a
 * téma is JSON, akkor a katalógus kódjához HOZZÁ SEM KELL NYÚLNI: a téma
 * pontosan olyan modul lesz, mint egy kvíz vagy egy receptcsomag — adat,
 * nem kód.
 *
 * A második ok gyakorlati: vakon EGY fájlt kezelni sokkal egyszerűbb, mint
 * egy mappát vagy egy zipet. És ugyanaz a fájl megy a barátnak, a közösbe és
 * a letöltőnek — amit készítesz, pontosan az, amit a katalógus terjeszt.
 *
 * MÉRET: hat klip m4a-ban 150-300 kB, base64-gyel 200-400 kB. Kevesebb, mint
 * egy fénykép.
 */
object VoiceThemePackage {

    private const val TAG = "SDL_VOICETHEME"

    /** Egy klip felső határa a csomagban. E fölött nem csomagolunk. */
    private const val MAX_CLIP_BYTES = 600 * 1024

    /** A teljes csomag felső határa — a katalógust is ez védi. */
    private const val MAX_PACKAGE_BYTES = 4 * 1024 * 1024

    data class ImportResult(
        val ok: Boolean,
        val id: String = "",
        val name: String = "",
        val author: String = "",
        val clipCount: Int = 0,
        val error: String = ""
    )

    // ── ÍRÁS ────────────────────────────────────────────────────────────────

    /**
     * A saját felvételekből csomagot készít.
     *
     * @return a kész fájl, vagy null ha egyetlen hang sincs felvéve
     */
    fun export(
        context: Context,
        id: String,
        name: String,
        author: String,
        description: String = "",
        language: String = "hu"
    ): File? {
        val themeId = safeId(id)
        val sounds = JSONObject()
        var count = 0
        var total = 0L
        for (event in VoiceEvent.entries) {
            val file = findClip(context, themeId, event) ?: continue
            if (file.length() > MAX_CLIP_BYTES) {
                Log.w(TAG, "tul nagy klip, kihagyva: ${event.id} (${file.length()})")
                continue
            }
            val bytes = try {
                file.readBytes()
            } catch (_: Exception) {
                continue
            }
            total += bytes.size
            if (total > MAX_PACKAGE_BYTES) {
                Log.w(TAG, "csomag meret tullepve, tobbi klip kihagyva")
                break
            }
            sounds.put(
                event.id,
                JSONObject()
                    .put("formatum", file.extension.lowercase())
                    .put("adat", Base64.encodeToString(bytes, Base64.NO_WRAP))
            )
            count++
        }
        if (count == 0) return null

        val root = JSONObject()
            .put("id", safeId(id))
            .put("nev", name)
            .put("szerzo", author)
            .put("nyelv", language)
            .put("verzio", 1)
            .put("leiras", description)
            .put("sajat_hang", true)
            .put("hangok", sounds)

        return try {
            val dir = File(context.getExternalFilesDir(null), "hangtemak").apply { mkdirs() }
            val out = File(dir, "tema-${safeId(id)}.json")
            out.writeText(root.toString(), Charsets.UTF_8)
            Log.i(TAG, "tema csomagolva: ${out.name}, $count hang, $total bajt")
            out
        } catch (e: Exception) {
            Log.w(TAG, "csomagolas hiba: ${e.message}")
            null
        }
    }

    /** Hány eseményhez van hang egy témában — a csomagolás előtt megmondjuk. */
    fun clipCount(context: Context, themeId: String): Int =
        VoiceEvent.entries.count { findClip(context, safeId(themeId), it) != null }

    private fun findClip(context: Context, themeId: String, event: VoiceEvent): File? {
        val dir = File(VoiceThemePlayer.themesRoot(context), themeId)
        for (ext in VoiceEvent.EXTENSIONS) {
            val file = File(dir, "${event.baseName}.$ext")
            if (file.exists() && file.length() > 1000) return file
        }
        return null
    }

    /** Ékezet nélküli mappanév a téma nevéből — kívülről is kell. */
    fun idFromName(name: String): String = safeId(name)

    // ── OLVASÁS ─────────────────────────────────────────────────────────────

    /**
     * Csomag telepítése a `hangtemak/<azonosito>/` mappába.
     *
     * IDEGEN ADAT: a fájl bárhonnan jöhet, ezért minden lépés ellenőrzött, és
     * hiba esetén udvarias visszautasítás jön, nem összeomlás.
     */
    fun install(context: Context, text: String): ImportResult {
        return try {
            if (text.length > MAX_PACKAGE_BYTES * 2) {
                return ImportResult(false, error = "A csomag túl nagy.")
            }
            val root = JSONObject(text)
            val id = safeId(root.optString("id"))
            if (id.isBlank()) return ImportResult(false, error = "A csomagnak nincs azonosítója.")
            val sounds = root.optJSONObject("hangok")
                ?: return ImportResult(false, error = "A csomagban nincsenek hangok.")

            val dir = File(VoiceThemePlayer.themesRoot(context), id).apply { mkdirs() }
            var count = 0
            for (event in VoiceEvent.entries) {
                val entry = sounds.optJSONObject(event.id) ?: continue
                val format = entry.optString("formatum", "m4a").lowercase()
                if (format !in VoiceEvent.EXTENSIONS) continue
                val data = entry.optString("adat")
                if (data.isBlank()) continue
                val bytes = try {
                    Base64.decode(data, Base64.DEFAULT)
                } catch (_: Exception) {
                    continue
                }
                if (bytes.size < 1000 || bytes.size > MAX_CLIP_BYTES) continue
                // A régi kiterjesztések törlése, hogy ne maradjon kísértet.
                for (ext in VoiceEvent.EXTENSIONS) {
                    try {
                        File(dir, "${event.baseName}.$ext").delete()
                    } catch (_: Exception) {
                    }
                }
                File(dir, "${event.baseName}.$format").writeBytes(bytes)
                count++
            }
            if (count == 0) {
                return ImportResult(false, error = "A csomagban egyetlen használható hang sem volt.")
            }
            ImportResult(
                ok = true,
                id = id,
                name = root.optString("nev", id),
                author = root.optString("szerzo", ""),
                clipCount = count
            )
        } catch (e: Exception) {
            Log.w(TAG, "telepites hiba: ${e.message}")
            ImportResult(false, error = "Ezt a fájlt nem sikerült beszédtémaként értelmezni.")
        }
    }

    fun installFromFile(context: Context, file: File): ImportResult = try {
        install(context, file.readText(Charsets.UTF_8))
    } catch (_: Exception) {
        ImportResult(false, error = "A fájl nem olvasható.")
    }

    /** Ékezet és szóköz nélküli azonosító — mappanévnek és fájlnévnek is jó. */
    private fun safeId(raw: String): String {
        val ascii = raw.trim().lowercase()
            .replace('á', 'a').replace('é', 'e').replace('í', 'i')
            .replace('ó', 'o').replace('ö', 'o').replace('ő', 'o')
            .replace('ú', 'u').replace('ü', 'u').replace('ű', 'u')
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        return ascii.take(40)
    }
}
