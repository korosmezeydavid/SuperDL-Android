package com.superdl.launcher.voicetheme

import android.content.Context
import android.util.Log
import java.io.File

/**
 * AZ ELENA TÉMA BE VAN ÉPÍTVE A PROGRAMBA.
 *
 * MIÉRT: Elena nem egy letölthető extra, hanem a beszédtémák ALAPJA. Aki
 * bekapcsolja a funkciót, annak azonnal szólnia kell valaminek — különben
 * egy üres kapcsolót kap, és nem érti, mire jó az egész. Ezért a hat hang
 * az alkalmazás részeként utazik (`assets/hangtemak/elena/`), és az első
 * indításkor a helyére kerül.
 *
 * MÉRET: a hat hang m4a-ban összesen kb. 210 kilobájt. Ennyit megér.
 *
 * VISSZAÁLLÍTHATÓ: ha a felhasználó felülvette Elena valamelyik hangját,
 * a `restore()` visszahozza a gyárit — ez az a biztonsági háló, ami miatt
 * nyugodtan lehet kísérletezni.
 */
object VoiceThemeAssets {

    private const val TAG = "SDL_VOICETHEME"

    /** A beépített téma azonosítója. Ez SOHA nem tűnhet el. */
    const val BUILT_IN_ID = "elena"
    const val BUILT_IN_NAME = "Elena"

    private const val ASSET_DIR = "hangtemak/elena"

    /**
     * Kicsomagolja a beépített témát, ha még nincs a helyén.
     * Olcsó: ha minden fájl megvan, azonnal visszatér.
     */
    fun ensureInstalled(context: Context) {
        try {
            val dir = File(VoiceThemePlayer.themesRoot(context), BUILT_IN_ID).apply { mkdirs() }
            var copied = 0
            for (event in VoiceEvent.entries) {
                // Ha a felhasználó saját hangot tett ide, NEM írjuk felül.
                if (VoiceThemePlayer.clipIn(context, BUILT_IN_ID, event) != null) continue
                if (copyAsset(context, event, dir)) copied++
            }
            if (copied > 0) {
                VoiceThemeStore.setThemeName(context, BUILT_IN_ID, BUILT_IN_NAME)
                Log.i(TAG, "beepitett Elena tema kicsomagolva: $copied hang")
            }
        } catch (e: Exception) {
            Log.w(TAG, "beepitett tema kicsomagolas hiba: ${e.message}")
        }
    }

    /** A gyári Elena-hangok visszaállítása, a felülvetteket is felülírva. */
    fun restore(context: Context): Int {
        val dir = File(VoiceThemePlayer.themesRoot(context), BUILT_IN_ID).apply { mkdirs() }
        var copied = 0
        for (event in VoiceEvent.entries) {
            for (ext in VoiceEvent.EXTENSIONS) {
                try {
                    File(dir, "${event.baseName}.$ext").delete()
                } catch (_: Exception) {
                }
            }
            if (copyAsset(context, event, dir)) copied++
        }
        VoiceThemeStore.setThemeName(context, BUILT_IN_ID, BUILT_IN_NAME)
        return copied
    }

    private fun copyAsset(context: Context, event: VoiceEvent, dir: File): Boolean = try {
        context.assets.open("$ASSET_DIR/${event.baseName}.m4a").use { input ->
            File(dir, "${event.baseName}.m4a").outputStream().use { output ->
                input.copyTo(output)
            }
        }
        true
    } catch (_: Exception) {
        // Nem minden eseményhez KELL beépített hang — ami nincs, az nincs.
        false
    }
}
