package com.superdl.launcher.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log

/**
 * FÜLHALLGATÓ KIHÚZÁSA — a lejátszás azonnal megáll.
 *
 * MIÉRT KELL: ha kihúzod a fülhallgatót (vagy megszakad a Bluetooth-kapcsolat),
 * a hang alapból ÁTVÁLT A HANGSZÓRÓRA, és teljes hangerőn szólni kezd. Ez
 * buszon, váróban, munkahelyen kellemetlen — és vak felhasználónál külön baj,
 * mert nem tudja egy pillantással megkeresni és lenyomni a szünet gombot.
 *
 * Az Android küld egy jelzést, MIELŐTT átvált a hangszóróra. Erre kell
 * reagálni: minden rendes médialejátszó megteszi.
 *
 * FONTOS: csak SZÜNETELTETÜNK, nem állítunk le. Ha visszadugod a fülhallgatót,
 * ott folytathatod, ahol abbahagytad.
 */
class HeadphoneUnplugGuard(
    private val context: Context,
    private val onPause: () -> Unit
) {
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.i(TAG, "fulhallgato kihuzva -> lejatszas szunetel")
                try {
                    onPause()
                } catch (e: Exception) {
                    Log.w(TAG, "szuneteltetes hiba: ${e.message}")
                }
            }
        }
    }

    fun register() {
        if (registered) return
        try {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            registered = true
        } catch (e: Exception) {
            Log.w(TAG, "figyelo inditas hiba: ${e.message}")
        }
    }

    fun unregister() {
        if (!registered) return
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
        registered = false
    }

    companion object {
        private const val TAG = "SDL_MEDIA"
    }
}

/**
 * HANGFÓKUSZ — udvarias együttélés a többi alkalmazással.
 *
 * MIÉRT KELL: ha egy másik alkalmazás megszólal (navigáció bemondja a
 * kanyart, érkezik egy hangüzenet, más zenét indít), a mi lejátszónk alapból
 * TOVÁBB SZÓL, és a kettő egymásra beszél. Vak felhasználónál ez különösen
 * rossz: pont az a mondat vész el, amit hallania kellene.
 *
 * A rádió, a podcast és a YouTube eddig NEM kért hangfókuszt — a zene és a
 * hangoskönyv igen. Ez egységesíti őket.
 */
class AudioFocusGuard(
    context: Context,
    private val onPause: () -> Unit,
    private val onResume: () -> Unit
) {
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var request: android.media.AudioFocusRequest? = null
    private var pausedByFocusLoss = false

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pausedByFocusLoss = true
                try {
                    onPause()
                } catch (_: Exception) {
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // CSAK akkor folytatjuk, ha MI állítottuk meg a fókusz miatt.
                // Ha a felhasználó nyomott szünetet, maradjon állva.
                if (pausedByFocusLoss) {
                    pausedByFocusLoss = false
                    try {
                        onResume()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    /** @return sikerült-e megszerezni a hangot */
    fun request(): Boolean {
        val manager = audioManager ?: return true
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val attrs = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val req = android.media.AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN
                ).setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(listener)
                    .build()
                request = req
                manager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                manager.requestAudioFocus(
                    listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.w("SDL_MEDIA", "hangfokusz keres hiba: ${e.message}")
            true   // ne akadályozzuk a lejátszást, ha a kérés maga hibázik
        }
    }

    fun release() {
        val manager = audioManager ?: return
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                request?.let { manager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                manager.abandonAudioFocus(listener)
            }
        } catch (_: Exception) {
        }
        request = null
        pausedByFocusLoss = false
    }
}
