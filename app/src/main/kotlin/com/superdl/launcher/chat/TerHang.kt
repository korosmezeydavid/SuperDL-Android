package com.superdl.launcher.chat

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.cos
import kotlin.math.sin

/**
 * Csevejcenter – TÉRBELI (sztereó) hang-motor a telefonon (a PC `terhang.py`
 * párja). Minden résztvevő a sztereó tér más pontjáról szól (bal–közép–jobb),
 * így a hallgató a hang IRÁNYÁBÓL tudja, KI beszél – vakoknak külön élmény.
 *
 * 16 kHz mono, 20 ms-os kockák. Rögzítés: AudioRecord (RECORD_AUDIO jog kell).
 * Lejátszás: AudioTrack (sztereó). A hálózati réteg a `fogad(nev, pcm)`-mel
 * táplálja a keverőt, a saját mikrofon-kockát a `start` callbackje adja ki.
 */
const val FS = 16000
const val BLOKK = 320                 // 20 ms @ 16 kHz (minta/kocka, mono)

private fun panSample(s: Int, ang: Double): Pair<Int, Int> {
    val l = (s * cos(ang)).toInt()
    val r = (s * sin(ang)).toInt()
    return l to r
}

/** Résztvevő-nevekhez pan-pozíciók (-1..+1), névsor szerint egyenletesen. */
fun ulesek(nevek: List<String>): Map<String, Float> {
    val tiszta = nevek.filter { it.isNotBlank() }.distinct().sortedBy { it.lowercase() }
    if (tiszta.isEmpty()) return emptyMap()
    if (tiszta.size == 1) return mapOf(tiszta[0] to 0f)
    return tiszta.mapIndexed { i, n -> n to (-1f + 2f * i / (tiszta.size - 1)) }.toMap()
}

/**
 * Több résztvevő mono hang-kockáit panorámázva sztereóvá keveri. Résztvevőnként
 * jitter-puffer; a `kimenet(n)` legyárt n minta kevert INTERLEAVED sztereót
 * (PCM16 ShortArray, hossza 2*n). Tiszta logika, eszköz nélkül tesztelhető.
 */
class Kevero {
    private val pufferek = HashMap<String, ArrayDeque<ShortArray>>()
    private val panMap = HashMap<String, Float>()
    private val lock = Any()
    private val maxPuffer = 25         // ~0,5 s résztvevőnként

    fun setUlesek(map: Map<String, Float>) {
        synchronized(lock) { panMap.clear(); panMap.putAll(map) }
    }

    fun add(nev: String, mono: ShortArray) {
        synchronized(lock) {
            val dq = pufferek.getOrPut(nev) { ArrayDeque() }
            if (dq.size >= maxPuffer) dq.removeFirst()
            dq.addLast(mono)
        }
    }

    fun elenged(nev: String) {
        synchronized(lock) { pufferek.remove(nev) }
    }

    /** n mintányi kevert INTERLEAVED sztereó (PCM16), lágy klipp-védelemmel. */
    fun kimenet(n: Int = BLOKK): ShortArray {
        val accL = IntArray(n)
        val accR = IntArray(n)
        synchronized(lock) {
            for ((nev, dq) in pufferek) {
                if (dq.isEmpty()) continue
                val mono = dq.removeFirst()
                val pan = panMap[nev] ?: 0f
                val ang = (pan.coerceIn(-1f, 1f) + 1f) * (Math.PI / 4.0)
                val cl = cos(ang); val cr = sin(ang)
                val m = minOf(n, mono.size)
                for (i in 0 until m) {
                    val s = mono[i].toInt()
                    accL[i] += (s * cl).toInt()
                    accR[i] += (s * cr).toInt()
                }
            }
        }
        // csúcs keresése a lágy klippeléshez
        var peak = 0
        for (i in 0 until n) {
            val a = if (accL[i] < 0) -accL[i] else accL[i]
            val b = if (accR[i] < 0) -accR[i] else accR[i]
            if (a > peak) peak = a
            if (b > peak) peak = b
        }
        val skala = if (peak > 32767) 32767.0 / peak else 1.0
        val out = ShortArray(2 * n)
        for (i in 0 until n) {
            out[2 * i] = (accL[i] * skala).toInt().coerceIn(-32768, 32767).toShort()
            out[2 * i + 1] = (accR[i] * skala).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }
}

/** PCM16 ShortArray (mono) -> bájtok (little-endian) a hálózati küldéshez. */
fun shortsToBytes(s: ShortArray, len: Int = s.size): ByteArray {
    val b = ByteArray(len * 2)
    for (i in 0 until len) {
        b[2 * i] = (s[i].toInt() and 0xFF).toByte()
        b[2 * i + 1] = ((s[i].toInt() shr 8) and 0xFF).toByte()
    }
    return b
}

/** Bájtok (PCM16 little-endian) -> mono ShortArray. */
fun bytesToShorts(b: ByteArray): ShortArray {
    val n = b.size / 2
    val s = ShortArray(n)
    for (i in 0 until n) {
        val lo = b[2 * i].toInt() and 0xFF
        val hi = b[2 * i + 1].toInt()
        s[i] = ((hi shl 8) or lo).toShort()
    }
    return s
}

class TerbeliHang {
    val kevero = Kevero()

    @Volatile private var running = false
    @Volatile private var muted = false
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var capThread: Thread? = null
    private var playThread: Thread? = null

    fun setUlesek(map: Map<String, Float>) = kevero.setUlesek(map)
    fun fogad(nev: String, pcm: ByteArray) {
        try { kevero.add(nev, bytesToShorts(pcm)) } catch (_: Exception) { }
    }
    fun elenged(nev: String) = kevero.elenged(nev)
    fun nemit(b: Boolean) { muted = b }

    /** Rögzítés + lejátszás indítása. `onFrame` minden ~20 ms-os mono PCM16
     * kockát megkap (a hálózat elküldi). RECORD_AUDIO jog kell. */
    @SuppressLint("MissingPermission")
    fun indit(onFrame: (ByteArray) -> Unit) {
        running = true
        val inMin = AudioRecord.getMinBufferSize(
            FS, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, FS,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(inMin, BLOKK * 2 * 4))
        record = rec
        val outMin = AudioTrack.getMinBufferSize(
            FS, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val trk = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(FS)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build())
            .setBufferSizeInBytes(maxOf(outMin, BLOKK * 2 * 2 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = trk

        rec.startRecording()
        trk.play()

        capThread = Thread {
            val buf = ShortArray(BLOKK)
            while (running) {
                val n = try { rec.read(buf, 0, BLOKK) } catch (e: Exception) { -1 }
                if (n <= 0) continue
                if (muted) continue
                try { onFrame(shortsToBytes(buf, n)) } catch (_: Exception) { }
            }
        }.also { it.isDaemon = true; it.start() }

        playThread = Thread {
            while (running) {
                val mix = kevero.kimenet(BLOKK)
                try { trk.write(mix, 0, mix.size) } catch (_: Exception) { }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun leallit() {
        running = false
        capThread?.interrupt(); playThread?.interrupt()
        try { record?.stop(); record?.release() } catch (_: Exception) { }
        try { track?.stop(); track?.release() } catch (_: Exception) { }
        record = null; track = null
        try { Log.i("CsevejTerHang", "hang leállítva") } catch (_: Exception) { }
    }
}
