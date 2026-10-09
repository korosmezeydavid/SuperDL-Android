package com.superdl.launcher.patrol

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.superdl.launcher.catalog.CatalogClient
import com.superdl.launcher.catalog.CatalogStore
import com.superdl.launcher.catalog.ModuleType
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * BESZÉLŐ ÓRA — ÉLŐ HANGON, KLIPEKBŐL.
 *
 * A katalógusból letölthető „beszélő óra" modul egy JSON, benne m4a klipek
 * base64-ben (a beszédtémák mintájára):
 *   oraHH   — „Most tizennégy óra van."    (egész órakor)
 *   elejeHH — „Most tizennégy óra"
 *   percMM  — „harminc perc"               (05, 10, … 55)
 *   van     — „van."
 * Öt perces időpontra: eleje + szünet + perc + szünet + van. Más percre nincs
 * klip — ott a hívó a felolvasóra esik vissza.
 *
 * MIÉRT PCM-RE BONTJUK: három m4a egymás után lejátszva a lejátszók közti
 * váltás kiszámíthatatlan szünetet adna („Most tizennégy óra ... harminc").
 * Telepítéskor egyszer visszafejtjük nyers hangmintára, bemondáskor pedig egy
 * darabban, pontos szünetekkel játsszuk le.
 *
 * MIÉRT NEM FÜGG A BESZÉDMOTORTÓL: az óránkénti bemondás rántotta magával a
 * beszédet (android-nema-beszed-melyvizsgalat). A klip ezt a kockázatot
 * kiveszi: ha a motor beragad, az óra akkor is szól.
 */
object TalkingClock {

    private const val TAG = "SuperDL.TalkingClock"
    private const val MAX_MODULE_BYTES = 8L * 1024 * 1024
    private const val MAX_CLIP_BYTES = 600 * 1024
    private val KEY_OK = Regex("^(ora\\d\\d|eleje\\d\\d|perc\\d\\d|van)$")

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()

    private data class Pack(
        val dir: File,
        val rate: Int,
        val gapHourMinute: Int,
        val gapMinuteVan: Int
    )

    @Volatile private var cached: Pack? = null
    @Volatile private var cachedKey: String? = null

    /** Melyik klipek kellenek ehhez az időponthoz — null, ha nincs rá klip. */
    fun clipsFor(hour: Int, minute: Int): List<String>? = when {
        hour !in 0..23 || minute !in 0..59 -> null
        minute == 0 -> listOf("ora%02d".format(hour))
        minute % 5 == 0 -> listOf("eleje%02d".format(hour), "perc%02d".format(minute), "van")
        else -> null
    }

    /** Gyors, főszálról is hívható: van-e letöltött óra-modul, és erre a percre van-e klip. */
    fun canSay(context: Context, hour: Int, minute: Int): Boolean =
        clipsFor(hour, minute) != null && installedId(context) != null

    private fun installedId(context: Context): String? = try {
        CatalogStore.installedIds(context, ModuleType.TALKING_CLOCK).sorted().firstOrNull()
    } catch (_: Throwable) {
        null
    }

    /** Új vagy törölt modul után: a következő bemondás újra betölti. */
    fun invalidate() {
        synchronized(lock) {
            cached = null
            cachedKey = null
        }
    }

    /**
     * Kicsomagolás (base64 → m4a → nyers hangminta). HÁTTÉRSZÁLRÓL hívandó —
     * a letöltés végén egyszer lefut, hogy az első bemondásnak ne kelljen várnia.
     */
    fun prepare(context: Context): Boolean = pack(context) != null

    private fun pack(context: Context): Pack? {
        val id = installedId(context) ?: return null
        val version = CatalogStore.installedVersion(context, id) ?: return null
        val key = "$id-v$version"
        synchronized(lock) {
            if (key == cachedKey) cached?.let { return it }
            val dir = File(context.filesDir, "beszelo_ora/$id")
            // A „-2" a kibontás módja: ha változik, a régi kibontás magától újra fut.
            val marker = File(dir, "kesz-v$version-2.txt")
            val p = if (marker.isFile) readMarker(dir, marker) else unpack(context, id, dir, marker)
            cached = p
            cachedKey = if (p != null) key else null
            return p
        }
    }

    private fun readMarker(dir: File, marker: File): Pack? = try {
        val o = JSONObject(marker.readText())
        Pack(dir, o.getInt("rate"), o.optInt("gap1", 50), o.optInt("gap2", 15))
    } catch (e: Throwable) {
        Log.w(TAG, "marker hibas", e)
        null
    }

    private fun unpack(context: Context, id: String, dir: File, marker: File): Pack? {
        return try {
            val file = CatalogClient.moduleFile(context, id)
            if (!file.isFile || file.length() > MAX_MODULE_BYTES) return null
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val sounds = root.getJSONObject("hangok")
            dir.deleteRecursively()
            dir.mkdirs()
            val tmp = File(dir, "_klip.m4a")
            var rate = 0
            var count = 0
            for (name in sounds.keys()) {
                if (!KEY_OK.matches(name)) continue
                val b64 = sounds.optString(name)
                if (b64.length > MAX_CLIP_BYTES * 4 / 3 + 8) continue
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                if (bytes.size < 512) continue
                tmp.writeBytes(bytes)
                val decoded = decode(tmp) ?: continue
                if (rate == 0) rate = decoded.second
                if (decoded.second != rate) {
                    Log.w(TAG, "$name: eltero mintavetel (${decoded.second}), kihagyva")
                    continue
                }
                File(dir, "$name.pcm").writeBytes(trimTail(decoded.first, decoded.second))
                count++
            }
            tmp.delete()
            if (count == 0 || rate == 0) return null
            val gap1 = root.optInt("szunet_ora_perc_ms", 50).coerceIn(0, 400)
            val gap2 = root.optInt("szunet_perc_van_ms", 15).coerceIn(0, 400)
            marker.writeText(JSONObject().put("rate", rate).put("gap1", gap1).put("gap2", gap2).toString())
            Log.i(TAG, "kicsomagolva: $id, $count klip, $rate Hz")
            Pack(dir, rate, gap1, gap2)
        } catch (e: Throwable) {
            Log.w(TAG, "kicsomagolas hiba", e)
            null
        }
    }

    /** m4a → 16 bites mono hangminta (little-endian) + mintavételi frekvencia. */
    private fun decode(file: File): Pair<ByteArray, Int>? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(format, null, null, 0)
            c.start()
            val out = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var guard = 0
            while (guard++ < 20000) {
                if (!inputDone) {
                    val inIdx = c.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = c.getInputBuffer(inIdx)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            c.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = c.dequeueOutputBuffer(info, 10_000)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = c.outputFormat
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else if (outIdx >= 0) {
                    val buf = c.getOutputBuffer(outIdx)!!
                    if (info.size > 0) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val chunk = ByteArray(info.size)
                        buf.get(chunk)
                        out.write(if (channels > 1) firstChannel(chunk, channels) else chunk)
                    }
                    c.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            val bytes = out.toByteArray()
            if (bytes.isEmpty()) null else Pair(bytes, rate)
        } catch (e: Throwable) {
            Log.w(TAG, "visszafejtes hiba: ${file.name}", e)
            null
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            try { extractor.release() } catch (_: Throwable) {}
        }
    }

    /**
     * A visszafejtett AAC végén ~20 ms töltelék-csend van. Három klipnél ez
     * a szüneteket érezhetően megnyújtaná — levágjuk, 5 ms-ot hagyva, hogy a
     * gépen gyártott mondatokkal egyformán szóljon (telefonon mérve).
     */
    private fun trimTail(pcm: ByteArray, rate: Int): ByteArray {
        var last = pcm.size / 2 - 1
        while (last > 0) {
            val v = (pcm[2 * last].toInt() and 0xff) or (pcm[2 * last + 1].toInt() shl 8)
            if (kotlin.math.abs(v.toShort().toInt()) > 300) break
            last--
        }
        val keep = ((last + 1 + rate * 5 / 1000) * 2).coerceAtMost(pcm.size)
        return if (keep >= 2) pcm.copyOf(keep) else pcm
    }

    private fun firstChannel(chunk: ByteArray, channels: Int): ByteArray {
        val frames = chunk.size / (2 * channels)
        val out = ByteArray(frames * 2)
        for (i in 0 until frames) {
            out[2 * i] = chunk[2 * i * channels]
            out[2 * i + 1] = chunk[2 * i * channels + 1]
        }
        return out
    }

    /** Az összefűzött mondat nyers hangmintája — null, ha valami hiányzik. */
    fun render(context: Context, hour: Int, minute: Int): Pair<ByteArray, Int>? {
        val names = clipsFor(hour, minute) ?: return null
        val p = pack(context) ?: return null
        val parts = names.map { File(p.dir, "$it.pcm") }
        if (parts.any { !it.isFile }) return null
        val out = ByteArrayOutputStream()
        parts.forEachIndexed { i, f ->
            if (i == 1) out.write(ByteArray(p.rate * p.gapHourMinute / 1000 * 2))
            if (i == 2) out.write(ByteArray(p.rate * p.gapMinuteVan / 1000 * 2))
            out.write(f.readBytes())
        }
        return Pair(out.toByteArray(), p.rate)
    }

    /**
     * Bemondás. Az `onDone(true)` ha elhangzott, `onDone(false)` ha nem sikerült
     * — ilyenkor a hívó a felolvasóval mondja. Az `onDone` MINDIG lefut, egyszer,
     * a főszálon.
     */
    fun play(context: Context, hour: Int, minute: Int, onDone: (Boolean) -> Unit) {
        val app = context.applicationContext
        Thread({
            var ok = false
            var track: AudioTrack? = null
            try {
                val r = render(app, hour, minute)
                if (r != null) {
                    val (pcm, rate) = r
                    val t = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(rate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .setBufferSizeInBytes(pcm.size)
                        .build()
                    track = t
                    // A periodikus időbemondás a zsebben lévő telefonon is
                    // hallatszódjon, ha vezetékes vagy Bluetooth füles aktív.
                    val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                        ?.let { speaker ->
                            if (!t.setPreferredDevice(speaker)) {
                                Log.w(TAG, "A hangszóró-kimenet kiválasztása sikertelen")
                            }
                        }
                    val written = t.write(pcm, 0, pcm.size)
                    if (written == pcm.size) {
                        t.play()
                        Thread.sleep(100)
                        val routed = t.routedDevice
                        if (routed != null && routed.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                            Log.w(TAG, "A beszélő óra nem a hangszóróra került: ${routed.type}")
                        }
                        val ms = pcm.size / 2 * 1000L / rate
                        Thread.sleep(ms + 100)
                        ok = routed == null || routed.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    } else {
                        Log.w(TAG, "AudioTrack write: $written / ${pcm.size}")
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "lejatszas hiba", e)
            } finally {
                try { track?.stop() } catch (_: Throwable) {}
                try { track?.release() } catch (_: Throwable) {}
            }
            val result = ok
            mainHandler.post { onDone(result) }
        }, "BeszeloOra").start()
    }

    /** Fejlesztői próba: a mondat WAV-ba írva, hogy gépen meghallgatható legyen. */
    fun renderToWav(context: Context, hour: Int, minute: Int, target: File): Boolean {
        val (pcm, rate) = render(context, hour, minute) ?: return false
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcm.size)
        }
        target.writeBytes(header.array() + pcm)
        return true
    }
}
