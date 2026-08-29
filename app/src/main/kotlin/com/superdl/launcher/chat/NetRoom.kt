package com.superdl.launcher.chat

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ably-alapú „szoba" a gépek közti, valós idejű üzenetváltáshoz (Csevejcenter).
 *
 * A PC-s `netroom.py` PONTOS párja: minden szoba egy Ably-CSATORNA a szobakód
 * alapján, ugyanazzal a „csevej:" előtaggal – így egy PC-s és egy telefonos
 * felhasználó UGYANABBAN a szobában lehet. REST-en PUBLIKÁLUNK, és a csatorna
 * ELŐZMÉNYÉT (history) pollozzuk az újakért. Nincs SDK, csak HttpURLConnection.
 *
 * A kulcs sosem a forrásban: az `assets/ably_key.txt`-ből olvassuk (git-ignorált,
 * csak a kész APK-ban) – így a végfelhasználónak csak internet kell.
 */
class NetRoom(
    private val context: Context,
    kod: String,
    val nev: String,
    prefix: String = "csevej"
) {
    private val kod = kod.trim().uppercase()
    private val channel = "$prefix:${this.kod}"
    private val key = ablyKey(context)

    @Volatile private var lastTs: Long = 0
    private val seen = HashSet<String>()

    @Volatile private var running = false
    private var thread: Thread? = null
    private val main = Handler(Looper.getMainLooper())

    fun available(): Boolean = key.isNotBlank() && kod.isNotBlank()

    private fun authHeader(): String {
        // az Ably-kulcs: 'appId.keyId:secret' -> HTTP Basic (a teljes kulcs base64-e)
        val raw = key.toByteArray(Charsets.UTF_8)
        return "Basic " + Base64.encodeToString(raw, Base64.NO_WRAP)
    }

    /** Egy üzenet a szobába. `type` = üzenet neve, `data` = tetszőleges JSON. */
    fun send(type: String, data: JSONObject? = null): Boolean {
        return try {
            val payload = JSONObject()
                .put("ki", nev)
                .put("adat", data ?: JSONObject())
            val body = JSONObject()
                .put("name", type)
                .put("data", payload.toString())
                .toString()
            val url = URL("https://rest.ably.io/channels/$channel/messages")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Authorization", authHeader())
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val ok = conn.responseCode in 200..299
            conn.disconnect()
            ok
        } catch (e: Exception) {
            Log.w(TAG, "send failed", e)
            false
        }
    }

    /** Az utolsó lekérés óta érkezett üzenetek (időrendben, dedupolva). */
    fun poll(): List<RoomMessage> {
        val out = ArrayList<RoomMessage>()
        try {
            val sb = StringBuilder("https://rest.ably.io/channels/$channel/messages")
            sb.append("?limit=100&direction=forwards")
            if (lastTs > 0) sb.append("&start=").append(lastTs + 1)
            val conn = (URL(sb.toString()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Authorization", authHeader())
            }
            if (conn.responseCode !in 200..299) { conn.disconnect(); return out }
            val text = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            conn.disconnect()
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                val id = m.optString("id", "")
                if (id.isNotEmpty() && !seen.add(id)) continue
                val ts = m.optLong("timestamp", 0)
                if (ts > lastTs) lastTs = ts
                val dataStr = m.optString("data", "{}")
                var ki = ""
                var adat = JSONObject()
                try {
                    val d = JSONObject(dataStr)
                    ki = d.optString("ki", "")
                    adat = d.optJSONObject("adat") ?: JSONObject()
                } catch (_: Exception) { }
                out.add(RoomMessage(m.optString("name", ""), ki, adat, ts))
            }
        } catch (e: Exception) {
            Log.w(TAG, "poll failed", e)
        }
        return out
    }

    /** Háttérben pollozza az új üzeneteket; a callback a FŐSZÁLON hívódik. */
    fun listen(intervalMs: Long = 1000, callback: (RoomMessage) -> Unit) {
        running = true
        thread = Thread {
            while (running) {
                try {
                    for (msg in poll()) {
                        if (!running) break
                        main.post { callback(msg) }
                    }
                } catch (_: Exception) { }
                try { Thread.sleep(intervalMs) } catch (_: InterruptedException) { break }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    companion object {
        private const val TAG = "CsevejNetRoom"
        private val KOD_ABC = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        /** Rövid, bemondható szobakód (pl. GK7QP), a PC-vel azonos ábécével. */
        fun roomCode(len: Int = 5): String {
            val r = java.util.Random()
            val sb = StringBuilder()
            repeat(len) { sb.append(KOD_ABC[r.nextInt(KOD_ABC.length)]) }
            return sb.toString()
        }

        /** Az Ably-kulcs az assets/ably_key.txt-ből (üres, ha nincs). */
        fun ablyKey(context: Context): String {
            return try {
                context.assets.open("ably_key.txt").bufferedReader()
                    .use(BufferedReader::readText).trim()
            } catch (e: Exception) {
                ""
            }
        }
    }
}

/** Egy beérkezett szoba-üzenet. */
data class RoomMessage(
    val type: String,
    val ki: String,
    val adat: JSONObject,
    val ts: Long
)
