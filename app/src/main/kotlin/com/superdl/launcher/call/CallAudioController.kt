package com.superdl.launcher.call

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log

/**
 * HÍVÁS HANGVEZÉRLÉS — kihangosítás és mikrofon-némítás.
 *
 * MIÉRT KELLETT ÁTÍRNI:
 * A korábbi megoldás az AudioManager.isSpeakerphoneOn tulajdonságot állította.
 * Ez Android 12 (API 31) FÖLÖTT MÁR NEM MŰKÖDIK megbízhatóan — a rendszer
 * elavultnak jelölte, és sok készüléken egyszerűen nincs hatása. Ezért nem
 * kapcsolt ki a kihangosítás, hiába kérted.
 *
 * HÁROM SZINTEN próbálkozunk, a legmegbízhatóbbtól a tartalékig:
 *
 *  1. TELECOM (a legjobb): ha a SuperDL az alapértelmezett telefon alkalmazás,
 *     a rendszer ENGEDI, hogy közvetlenül állítsuk a hangutat és a némítást.
 *     Ez minden Android-verzión helyesen működik.
 *  2. KOMMUNIKÁCIÓS ESZKÖZ (Android 12+): a hangot a beépített hangszóróra
 *     vagy a fülhallgatóra irányítjuk.
 *  3. RÉGI MÓDSZER (Android 11 és alatta): a hagyományos kapcsoló.
 *
 * Minden lépést NAPLÓZUNK, és az eredményt VISSZAOLVASSUK — így kiderül, ha egy
 * készüléken egyik módszer sem működne.
 */
object CallAudioController {

    private const val TAG = "SDL_CALL"

    /** A futó hívás-szolgáltatás, ha a SuperDL az alapértelmezett telefon app. */
    @Volatile
    private var inCallService: InCallService? = null

    /** A rendszer által jelentett VALÓS állapot. */
    @Volatile
    var speakerOn: Boolean = false
        private set

    @Volatile
    var muted: Boolean = false
        private set

    fun attach(service: InCallService) {
        inCallService = service
        Log.i(TAG, "hangvezerles: Telecom szolgaltatas csatlakoztatva")
    }

    fun detach() {
        inCallService = null
    }

    /** A rendszer jelezte, hogy változott a hangállapot. */
    fun onAudioStateChanged(speakerOn: Boolean, muted: Boolean) {
        this.speakerOn = speakerOn
        this.muted = muted
        Log.i(TAG, "hangallapot: kihangositas=$speakerOn nemitas=$muted")
    }

    /**
     * KIHANGOSÍTÁS be/ki.
     * @return sikerült-e ténylegesen átállítani
     */
    fun setSpeakerphone(context: Context, enabled: Boolean): Boolean {
        // 1. TELECOM — a legmegbízhatóbb út.
        inCallService?.let { service ->
            return try {
                service.setAudioRoute(
                    if (enabled) CallAudioState.ROUTE_SPEAKER
                    else CallAudioState.ROUTE_EARPIECE
                )
                speakerOn = enabled
                Log.i(TAG, "kihangositas Telecom uton: $enabled")
                true
            } catch (e: Exception) {
                Log.w(TAG, "Telecom hangut hiba: ${e.message}")
                fallbackSpeaker(context, enabled)
            }
        }
        return fallbackSpeaker(context, enabled)
    }

    private fun fallbackSpeaker(context: Context, enabled: Boolean): Boolean {
        val audio = try {
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        } catch (_: Exception) {
            return false
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // 2. Android 12+: a kommunikációs eszköz kiválasztása.
                if (enabled) {
                    val speaker = audio.availableCommunicationDevices.firstOrNull {
                        it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speaker != null) {
                        val ok = audio.setCommunicationDevice(speaker)
                        speakerOn = ok
                        Log.i(TAG, "kihangositas kommunikacios eszkozzel: $ok")
                        return ok
                    }
                    Log.w(TAG, "nincs beepitett hangszoro a kommunikacios eszkozok kozt")
                    return false
                } else {
                    audio.clearCommunicationDevice()
                    speakerOn = false
                    Log.i(TAG, "kihangositas kikapcsolva (kommunikacios eszkoz torolve)")
                    return true
                }
            }
            // 3. Android 11 és alatta: a hagyományos kapcsoló.
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = enabled
            @Suppress("DEPRECATION")
            val actual = audio.isSpeakerphoneOn
            speakerOn = actual
            Log.i(TAG, "kihangositas regi modszerrel: kert=$enabled tenyleges=$actual")
            actual == enabled
        } catch (e: Exception) {
            Log.w(TAG, "kihangositas hiba: ${e.message}")
            false
        }
    }

    /**
     * MIKROFON NÉMÍTÁS be/ki.
     *
     * A Telecom út itt is a megbízhatóbb: a rendszer így TÉNYLEGESEN elnémítja
     * a hívást. Az AudioManager-es némítás csak tartalék, mert egyes
     * készülékeken a hívás mikrofonjára nincs hatása.
     */
    fun setMicrophoneMute(context: Context, mutedRequested: Boolean): Boolean {
        inCallService?.let { service ->
            return try {
                service.setMuted(mutedRequested)
                muted = mutedRequested
                Log.i(TAG, "nemitas Telecom uton: $mutedRequested")
                true
            } catch (e: Exception) {
                Log.w(TAG, "Telecom nemitas hiba: ${e.message}")
                fallbackMute(context, mutedRequested)
            }
        }
        return fallbackMute(context, mutedRequested)
    }

    private fun fallbackMute(context: Context, mutedRequested: Boolean): Boolean = try {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.isMicrophoneMute = mutedRequested
        val actual = audio.isMicrophoneMute
        muted = actual
        Log.i(TAG, "nemitas regi modszerrel: kert=$mutedRequested tenyleges=$actual")
        actual == mutedRequested
    } catch (e: Exception) {
        Log.w(TAG, "nemitas hiba: ${e.message}")
        false
    }

    /** Minden visszaállítása a hívás végén. */
    fun reset(context: Context) {
        try {
            setSpeakerphone(context, false)
            setMicrophoneMute(context, false)
        } catch (_: Exception) {
        }
        speakerOn = false
        muted = false
    }
}
