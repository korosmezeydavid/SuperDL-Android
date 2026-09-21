package com.superdl.launcher.screenrecord

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A KÉPERNYŐFELVÉTEL GÉPEZETE — KÉP, A TELEFON HANGJA ÉS A FELHASZNÁLÓ HANGJA
 * EGYETLEN VIDEÓBAN.
 *
 * MIÉRT ÍGY, ÉS MIÉRT NEM EGYSZERŰBBEN: a rendszer beépített felvevője
 * EGYETLEN hangforrást tud. Vagy a mikrofont, vagy semmit. Alph viszont
 * pontosan azt kérte, hogy MINDKETTŐ legyen rajta: amit a telefon mond, és
 * amit ő mond közben. Egy vak felhasználó hibajelentésénél ez a kettő
 * EGYÜTT ér valamit — a kép önmagában nem árulja el, hogy a program mit
 * mondott, a beszéd önmagában meg nem árulja el, hol járt.
 *
 * Ezért itt kézzel áll össze a lánc:
 *   kép      -> kódoló felület -> virtuális kijelző rajzol rá
 *   hang     -> két hangforrás összekeverve -> kódoló
 *   a kettő  -> egyetlen mp4 fájl
 *
 * AMIT TUDNI KELL A TELEFON SAJÁT HANGJÁRÓL: a rendszer csak a "média"
 * besorolású hangot engedi rögzíteni. Ha a beszéd a KISEGÍTŐ csatornán
 * szól (ez a beszéd beállításainál választható), akkor a rendszer NEM adja
 * oda — ilyenkor a program beszéde csak a mikrofonon át, a hangszóróból
 * kerül a felvételre. A program ezt indításkor be is mondja.
 */
class ScreenRecordPipeline(
    private val context: Context,
    private val outputFile: File,
    private val withMic: Boolean,
    private val withDeviceAudio: Boolean
) {

    companion object {
        private const val TAG = "SDL_KEPFELVETEL"

        private const val VIDEO_MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val AUDIO_MIME = MediaFormat.MIMETYPE_AUDIO_AAC

        private const val FRAME_RATE = 30
        private const val I_FRAME_SEC = 2

        /**
         * A hosszabbik oldal ennél nagyobb nem lesz. Egy diagnosztikai
         * videónál a fájlméret többet számít, mint a képpontok száma.
         */
        private const val MAX_SIDE = 1280

        private const val SAMPLE_RATE = 44_100
        private const val AUDIO_BITRATE = 96_000
    }

    private var videoCodec: MediaCodec? = null
    private var audioCodec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var projection: MediaProjection? = null

    private var videoTrack = -1
    private var audioTrack = -1
    private var muxerStarted = false
    private val muxerLock = Object()

    /** Hány sávra várunk az összefűző indítása előtt. */
    private var expectedTracks = 1

    private val running = AtomicBoolean(false)
    private var videoThread: Thread? = null
    private var audioThread: Thread? = null

    private var micRecord: AudioRecord? = null
    private var playbackRecord: AudioRecord? = null

    var width = 0
        private set
    var height = 0
        private set

    /** Mi lett ténylegesen felvéve — a bemondáshoz kell. */
    var micActive = false
        private set
    var deviceAudioActive = false
        private set

    // ── INDÍTÁS ──────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun start(mediaProjection: MediaProjection): Boolean {
        if (running.get()) return true
        projection = mediaProjection

        try {
            measureScreen()

            // 1. A KÉP KÓDOLÓJA. A virtuális kijelző közvetlenül erre a
            //    felületre rajzol — így a képpontok sosem járják meg a
            //    memóriát, ami egy telefonnál óriási különbség.
            val videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, bitrateFor(width, height))
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_SEC)
            }
            val vc = MediaCodec.createEncoderByType(VIDEO_MIME)
            vc.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = vc.createInputSurface()
            vc.start()
            videoCodec = vc

            // 2. A HANG. Csak akkor építjük fel, ha van mit rögzíteni.
            micActive = withMic && openMic()
            deviceAudioActive = withDeviceAudio && openDeviceAudio(mediaProjection)
            val anyAudio = micActive || deviceAudioActive
            expectedTracks = if (anyAudio) 2 else 1

            if (anyAudio) {
                val audioFormat = MediaFormat.createAudioFormat(AUDIO_MIME, SAMPLE_RATE, 1).apply {
                    setInteger(
                        MediaFormat.KEY_AAC_PROFILE,
                        MediaCodecInfo.CodecProfileLevel.AACObjectLC
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
                }
                val ac = MediaCodec.createEncoderByType(AUDIO_MIME)
                ac.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                ac.start()
                audioCodec = ac
            }

            // 3. A DOBOZ, AMIBE A KETTŐ KERÜL.
            outputFile.parentFile?.mkdirs()
            muxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            // 4. A VIRTUÁLIS KIJELZŐ. Ez tükrözi a valódi képernyőt a
            //    kódoló felületére.
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "SuperDL-kepernyo",
                width,
                height,
                context.resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )

            running.set(true)
            videoThread = Thread({ drainVideo() }, "SDL-kep").also { it.start() }
            if (anyAudio) {
                try { micRecord?.startRecording() } catch (_: Exception) {}
                try { playbackRecord?.startRecording() } catch (_: Exception) {}
                audioThread = Thread({ pumpAudio() }, "SDL-hang").also { it.start() }
            }
            return true
        } catch (t: Throwable) {
            Log.e(TAG, "inditas hiba", t)
            releaseAll()
            return false
        }
    }

    // ── LEÁLLÍTÁS ────────────────────────────────────────────────────────

    /** @return true, ha a fájl használható videó lett. */
    fun stop(): Boolean {
        if (!running.getAndSet(false)) {
            releaseAll()
            return outputFile.exists() && outputFile.length() > 0L
        }
        try { audioThread?.join(3_000) } catch (_: InterruptedException) {}
        try { videoThread?.join(3_000) } catch (_: InterruptedException) {}
        releaseAll()
        val ok = outputFile.exists() && outputFile.length() > 1024L
        if (!ok) {
            // EGY NULLA BÁJTOS MP4 ROSSZABB, MINT A SEMMI: a felhasználó
            // azt hinné, megvan a felvétel.
            try { outputFile.delete() } catch (_: Exception) {}
        }
        return ok
    }

    private fun releaseAll() {
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { videoCodec?.stop() } catch (_: Exception) {}
        try { videoCodec?.release() } catch (_: Exception) {}
        videoCodec = null
        try { audioCodec?.stop() } catch (_: Exception) {}
        try { audioCodec?.release() } catch (_: Exception) {}
        audioCodec = null
        try { micRecord?.stop() } catch (_: Exception) {}
        try { micRecord?.release() } catch (_: Exception) {}
        micRecord = null
        try { playbackRecord?.stop() } catch (_: Exception) {}
        try { playbackRecord?.release() } catch (_: Exception) {}
        playbackRecord = null
        synchronized(muxerLock) {
            try { if (muxerStarted) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            muxer = null
            muxerStarted = false
            videoTrack = -1
            audioTrack = -1
        }
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
    }

    // ── A KÉP KIÜRÍTÉSE ──────────────────────────────────────────────────

    private fun drainVideo() {
        val codec = videoCodec ?: return
        val info = MediaCodec.BufferInfo()
        try {
            while (running.get()) {
                val index = codec.dequeueOutputBuffer(info, 10_000L)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    synchronized(muxerLock) {
                        if (videoTrack < 0) {
                            videoTrack = muxer?.addTrack(codec.outputFormat) ?: -1
                            maybeStartMuxer()
                        }
                    }
                } else if (index >= 0) {
                    writeSample(codec, index, info, video = true)
                }
            }
            // A maradék képkockák is kerüljenek ki.
            try { codec.signalEndOfInputStream() } catch (_: Exception) {}
            var guard = 0
            while (guard++ < 200) {
                val index = codec.dequeueOutputBuffer(info, 10_000L)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    synchronized(muxerLock) {
                        if (videoTrack < 0) {
                            videoTrack = muxer?.addTrack(codec.outputFormat) ?: -1
                            maybeStartMuxer()
                        }
                    }
                    continue
                }
                if (index < 0) continue
                writeSample(codec, index, info, video = true)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        } catch (t: Throwable) {
            Log.w(TAG, "kep kiuritese hiba", t)
        }
    }

    // ── A HANG ───────────────────────────────────────────────────────────

    private fun pumpAudio() {
        val codec = audioCodec ?: return
        val chunk = 2048
        val mic = ShortArray(chunk)
        val play = ShortArray(chunk)
        val mixed = ShortArray(chunk)
        val info = MediaCodec.BufferInfo()
        var samples = 0L
        val startUs = System.nanoTime() / 1000
        val micOn = micActive
        val playOn = deviceAudioActive

        try {
            while (running.get()) {
                var count = 0
                if (micOn) {
                    val r = micRecord?.read(mic, 0, chunk) ?: -1
                    if (r > 0) count = r
                }
                if (playOn) {
                    val r = playbackRecord?.read(play, 0, chunk) ?: -1
                    if (r > 0) count = maxOf(count, r)
                }
                if (count <= 0) {
                    drainAudio(codec, info)
                    continue
                }

                // ÖSSZEKEVERÉS. Sima összeadás, levágással: ha mindkét oldal
                // egyszerre szól hangosan, torzítás helyett a határon megáll.
                // Beszédhangnál ez nem hallatszik.
                for (i in 0 until count) {
                    val a = if (micOn) mic[i].toInt() else 0
                    val b = if (playOn) play[i].toInt() else 0
                    val sum = a + b
                    mixed[i] = when {
                        sum > Short.MAX_VALUE -> Short.MAX_VALUE
                        sum < Short.MIN_VALUE -> Short.MIN_VALUE
                        else -> sum.toShort()
                    }
                }

                val inIndex = codec.dequeueInputBuffer(10_000L)
                if (inIndex >= 0) {
                    val buf = codec.getInputBuffer(inIndex)
                    if (buf != null) {
                        buf.clear()
                        for (i in 0 until count) buf.putShort(mixed[i])
                        val pts = startUs + samples * 1_000_000L / SAMPLE_RATE
                        codec.queueInputBuffer(inIndex, 0, count * 2, pts, 0)
                        samples += count
                    }
                }
                drainAudio(codec, info)
            }

            // Végjel a hangkódolónak.
            val inIndex = codec.dequeueInputBuffer(50_000L)
            if (inIndex >= 0) {
                val pts = startUs + samples * 1_000_000L / SAMPLE_RATE
                codec.queueInputBuffer(
                    inIndex, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }
            var guard = 0
            while (guard++ < 200) {
                if (!drainAudio(codec, info)) break
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        } catch (t: Throwable) {
            Log.w(TAG, "hang hiba", t)
        }
    }

    /** @return true, ha volt még kimenet. */
    private fun drainAudio(codec: MediaCodec, info: MediaCodec.BufferInfo): Boolean {
        val index = codec.dequeueOutputBuffer(info, 0L)
        return when {
            index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                synchronized(muxerLock) {
                    if (audioTrack < 0) {
                        audioTrack = muxer?.addTrack(codec.outputFormat) ?: -1
                        maybeStartMuxer()
                    }
                }
                true
            }
            index >= 0 -> {
                writeSample(codec, index, info, video = false)
                true
            }
            else -> false
        }
    }

    // ── A KÖZÖS ÍRÁS ─────────────────────────────────────────────────────

    private fun writeSample(
        codec: MediaCodec,
        index: Int,
        info: MediaCodec.BufferInfo,
        video: Boolean
    ) {
        try {
            val buffer = codec.getOutputBuffer(index)
            val codecConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
            if (buffer != null && info.size > 0 && !codecConfig) {
                synchronized(muxerLock) {
                    if (muxerStarted) {
                        val track = if (video) videoTrack else audioTrack
                        if (track >= 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer?.writeSampleData(track, buffer, info)
                        }
                    }
                }
            }
            codec.releaseOutputBuffer(index, false)
        } catch (t: Throwable) {
            Log.w(TAG, "irasi hiba", t)
        }
    }

    /** Csak akkor indul az összefűzés, ha MINDEN várt sáv megvan. */
    private fun maybeStartMuxer() {
        if (muxerStarted) return
        val have = (if (videoTrack >= 0) 1 else 0) + (if (audioTrack >= 0) 1 else 0)
        if (have < expectedTracks) return
        try {
            muxer?.start()
            muxerStarted = true
        } catch (t: Throwable) {
            Log.w(TAG, "osszefuzest nem lehetett inditani", t)
        }
    }

    // ── HANGFORRÁSOK ─────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun openMic(): Boolean = try {
        val min = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(min * 4, 16_384)
        )
        if (rec.state == AudioRecord.STATE_INITIALIZED) {
            micRecord = rec
            true
        } else {
            rec.release()
            micRecord = null
            false
        }
    } catch (t: Throwable) {
        Log.w(TAG, "mikrofon nem nyithato", t)
        micRecord = null
        false
    }

    /**
     * A TELEFON SAJÁT HANGJA. Csak Android 10-től van rá mód, és a rendszer
     * csak a "média" besorolású hangot adja oda — ez nem a mi döntésünk,
     * hanem a rendszer adatvédelmi szabálya.
     */
    @SuppressLint("MissingPermission")
    private fun openDeviceAudio(mediaProjection: MediaProjection): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_GAME)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_UNKNOWN)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            val min = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val rec = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(min * 4, 16_384))
                .setAudioPlaybackCaptureConfig(config)
                .build()
            if (rec.state == AudioRecord.STATE_INITIALIZED) {
                playbackRecord = rec
                true
            } else {
                rec.release()
                playbackRecord = null
                false
            }
        } catch (t: Throwable) {
            Log.w(TAG, "telefon hangja nem rogzitheto", t)
            playbackRecord = null
            false
        }
    }

    // ── MÉRETEK ──────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun measureScreen() {
        val metrics = DisplayMetrics()
        val dm = context.getSystemService(DisplayManager::class.java)
        val display = dm?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        if (display != null) {
            display.getRealMetrics(metrics)
        } else {
            metrics.setTo(context.resources.displayMetrics)
        }
        var w = metrics.widthPixels
        var h = metrics.heightPixels
        if (w <= 0 || h <= 0) {
            w = context.resources.displayMetrics.widthPixels
            h = context.resources.displayMetrics.heightPixels
        }
        val longest = maxOf(w, h)
        if (longest > MAX_SIDE) {
            val scale = MAX_SIDE.toDouble() / longest
            w = (w * scale).toInt()
            h = (h * scale).toInt()
        }
        // A KÓDOLÓK PÁROS SZÁMOT SZERETNEK, sok közülük 16 többszörösét.
        // Egy páratlan méret némán fekete vagy csíkos képet ad.
        width = maxOf(16, (w / 16) * 16)
        height = maxOf(16, (h / 16) * 16)
    }

    private fun bitrateFor(w: Int, h: Int): Int {
        val raw = (w.toLong() * h * FRAME_RATE * 0.12).toInt()
        return raw.coerceIn(1_500_000, 8_000_000)
    }
}
