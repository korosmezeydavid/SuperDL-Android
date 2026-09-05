package com.superdl.launcher.voicetheme

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * A BESZÉDTÉMA HANGJAINAK FELVÉTELE A TELEFONON.
 *
 * MIÉRT SAJÁT ÉS NEM A DIKTAFON: a diktafon hosszú felvételekhez való,
 * előtér-szolgáltatással és saját állapottal. Itt hat rövid mondat kell,
 * egymás után, azonnali visszajátszással — és nem szabad összeakadnia a
 * diktafon állapotával, ha valaki épp rögzít valamit.
 *
 * A formátum m4a (AAC): a telefon maga tömöríti, tehát egy három
 * másodperces mondat 30-40 kilobájt. Ez azért számít, mert a kész téma
 * base64-ben utazik a katalógusban — egy WAV-os téma többmegás lenne.
 */
object VoiceThemeRecorder {

    private var recorder: MediaRecorder? = null
    private var target: File? = null

    var isRecording: Boolean = false
        private set

    /**
     * Ide kerül a felvétel: a MEGNEVEZETT TÉMA saját mappájába.
     *
     * MIÉRT NEM EGY KÖZÖS HELYRE (Alph, 2026-09-05): ha minden felvétel
     * ugyanoda menne, a második téma felülvágná az elsőt. Márpedig teljesen
     * ésszerű, hogy valakinek több saját témája legyen — egy komoly és egy
     * vicces —, és mindkettőt megtartsa.
     */
    fun fileFor(context: Context, themeId: String, event: VoiceEvent): File =
        File(themeDir(context, themeId), "${event.baseName}.m4a")

    fun themeDir(context: Context, themeId: String): File =
        File(VoiceThemePlayer.themesRoot(context), themeId).apply { mkdirs() }

    /**
     * Felvétel indítása. A hívónak előbb meg kell szereznie a mikrofon
     * engedélyt.
     * @return igaz, ha elindult
     */
    fun start(context: Context, themeId: String, event: VoiceEvent): Boolean {
        if (isRecording) return false
        // ÚJ FELVÉTELNÉL A RÉGI TÖRLŐDIK, minden kiterjesztésben. Enélkül egy
        // korábbi wav „elé kerülhetne" az új m4a-nak, és a felhasználó a régit
        // hallaná — kideríthetetlen lenne, miért.
        val dir = themeDir(context, themeId)
        for (ext in VoiceEvent.EXTENSIONS) {
            try {
                File(dir, "${event.baseName}.$ext").delete()
            } catch (_: Exception) {
            }
        }
        val output = fileFor(context, themeId, event)
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(64000)
            rec.setAudioSamplingRate(44100)
            rec.setOutputFile(output.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            target = output
            isRecording = true
            true
        } catch (_: Exception) {
            try {
                rec.reset()
                rec.release()
            } catch (_: Exception) {
            }
            output.delete()
            recorder = null
            target = null
            isRecording = false
            false
        }
    }

    /**
     * Felvétel lezárása.
     * @return a mentett fájl, vagy null ha nem sikerült / üres lett
     */
    fun stop(): File? {
        val rec = recorder ?: return null
        val output = target
        return try {
            rec.stop()
            rec.release()
            recorder = null
            isRecording = false
            target = null
            // A pár száz bájtos fájl néma vagy félbemaradt felvétel.
            if (output != null && output.length() > 1000) output else {
                output?.delete()
                null
            }
        } catch (_: Exception) {
            try {
                rec.release()
            } catch (_: Exception) {
            }
            recorder = null
            isRecording = false
            output?.delete()
            target = null
            null
        }
    }

    fun cancel() {
        val rec = recorder ?: return
        try {
            rec.reset()
            rec.release()
        } catch (_: Exception) {
        }
        target?.delete()
        recorder = null
        target = null
        isRecording = false
    }

    /** Felső határ a behozott fájlra — sajátnak bőven elég. */
    private const val MAX_IMPORT_BYTES = 5 * 1024 * 1024

    /**
     * MEGLÉVŐ HANGFÁJL BEHOZATALA a felvétel helyett.
     *
     * Nem mindenki mondatot akar: van, aki hanghatást tenne a töltőre, van,
     * akinek a gépén már kész a felvétel. A rendszer fájlválasztója minden
     * tárhelyet lát, mi csak átmásoljuk a téma mappájába.
     *
     * A KITERJESZTÉS a mi kulcsunk a fájl megtalálásához — a lejátszó a
     * TARTALOMBÓL ismeri fel a formátumot, tehát elég, ha a kiterjesztés a
     * mi listánkban szerepel. Ezért a mime alapján képezzük le.
     *
     * @return a mentett fájl, vagy null ha nem sikerült
     */
    fun importFromUri(
        context: Context,
        themeId: String,
        event: VoiceEvent,
        uri: android.net.Uri
    ): File? {
        return try {
            val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
            val ext = when {
                mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
                mime.contains("ogg") -> "ogg"
                mime.contains("opus") -> "opus"
                mime.contains("wav") -> "wav"
                mime.contains("aac") -> "aac"
                else -> "m4a"
            }
            val dir = themeDir(context, themeId)
            for (e in VoiceEvent.EXTENSIONS) {
                try {
                    File(dir, "${event.baseName}.$e").delete()
                } catch (_: Exception) {
                }
            }
            val out = File(dir, "${event.baseName}.$ext")
            var written = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        written += n
                        if (written > MAX_IMPORT_BYTES) {
                            output.flush()
                            out.delete()
                            return null
                        }
                        output.write(buffer, 0, n)
                    }
                }
            } ?: return null
            if (out.length() < 1000) {
                out.delete()
                null
            } else {
                out
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * A RÉGI, MAPPA NÉLKÜLI FELVÉTELEK ÁTKÖLTÖZTETÉSE.
     *
     * Aki korábban kézzel másolta a hangokat a `files/elena/` mappába (vagy
     * az első változattal vett fel), annak a fájljai ott vannak, és MINDIG
     * nyernének a most felvett témák fölött. Ez érthetetlen lenne: felveszi
     * az új témát, aktiválja, és mégis a régit hallja.
     *
     * Ezért az első alkalommal áthelyezzük őket egy rendes, megnevezett
     * témába — semmi nem vész el, csak a helyére kerül.
     *
     * @return hány fájl költözött; 0 ha nem volt mit költöztetni
     */
    fun migrateLegacyClips(context: Context, themeId: String): Int {
        val from = VoiceThemePlayer.ownDir(context)
        val to = themeDir(context, themeId)
        var moved = 0
        for (event in VoiceEvent.entries) {
            for (ext in VoiceEvent.EXTENSIONS) {
                val src = File(from, "${event.baseName}.$ext")
                if (!src.exists() || src.length() <= 1000) continue
                try {
                    val dst = File(to, "${event.baseName}.$ext")
                    if (src.renameTo(dst)) {
                        moved++
                    } else {
                        // Más köteten a rename nem megy — másolunk, aztán törlünk.
                        src.copyTo(dst, overwrite = true)
                        src.delete()
                        moved++
                    }
                } catch (_: Exception) {
                }
            }
        }
        return moved
    }

    /** Van-e még régi, mappa nélküli felvétel. */
    fun hasLegacyClips(context: Context): Boolean {
        val dir = VoiceThemePlayer.ownDir(context)
        for (event in VoiceEvent.entries) {
            for (ext in VoiceEvent.EXTENSIONS) {
                val f = File(dir, "${event.baseName}.$ext")
                if (f.exists() && f.length() > 1000) return true
            }
        }
        return false
    }
}
