package com.superdl.launcher.chat

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.SecureRandom

/**
 * Minimál STUN-kliens (RFC 5389) – a gép PUBLIKUS (internet felőli) cím:portja.
 * A PC `stun.py` párja: ez teszi lehetővé az internetes hangot fizetős szerver
 * NÉLKÜL. FONTOS: UGYANAZON a DatagramSocketen kérdezzük, amit a hanghoz is
 * használunk (a router-leképezés így ugyanaz marad). Csak beépített `java.net`.
 */
object Stun {

    private const val TAG = "CsevejStun"
    private const val MAGIC = 0x2112A442.toInt()

    private val SZERVEREK = listOf(
        "stun.l.google.com" to 19302,
        "stun1.l.google.com" to 19302,
        "stun.cloudflare.com" to 3478
    )

    /** A megadott socket publikus (ip, port)-ja, vagy null, ha egyik STUN sem
     * válaszol. A socket eredeti időtúllépését visszaállítja. */
    fun publikusCim(socket: DatagramSocket, timeoutMs: Int = 2000): InetSocketAddress? {
        val regi = socket.soTimeout
        try {
            for ((host, port) in SZERVEREK) {
                try {
                    val cel = InetSocketAddress(InetAddress.getByName(host), port)
                    val txid = ByteArray(12).also { SecureRandom().nextBytes(it) }
                    val req = keres(txid)
                    socket.soTimeout = timeoutMs
                    socket.send(DatagramPacket(req, req.size, cel))
                    // több választ is fogadhatunk (más forgalom is jöhet)
                    repeat(4) {
                        val buf = ByteArray(512)
                        val pkt = DatagramPacket(buf, buf.size)
                        socket.receive(pkt)
                        val cim = valaszCim(buf, pkt.length, txid)
                        if (cim != null) return cim
                    }
                } catch (_: Exception) {
                    // időtúllépés vagy hiba → jöhet a következő szerver
                }
            }
            return null
        } finally {
            try { socket.soTimeout = regi } catch (_: Exception) { }
        }
    }

    private fun keres(txid: ByteArray): ByteArray {
        val b = ByteArray(20)
        // type 0x0001 (Binding Request), length 0
        b[0] = 0x00; b[1] = 0x01; b[2] = 0x00; b[3] = 0x00
        putInt(b, 4, MAGIC)
        System.arraycopy(txid, 0, b, 8, 12)
        return b
    }

    private fun valaszCim(data: ByteArray, len: Int, txid: ByteArray): InetSocketAddress? {
        if (len < 20) return null
        val magic = getInt(data, 4)
        if (magic != MAGIC) return null
        // a tranzakció-azonosító egyezzen
        for (i in 0 until 12) if (data[8 + i] != txid[i]) return null
        val mlen = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        var i = 20
        val veg = minOf(20 + mlen, len)
        while (i + 4 <= veg) {
            val atype = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            val alen = ((data[i + 2].toInt() and 0xFF) shl 8) or (data[i + 3].toInt() and 0xFF)
            i += 4
            if (i + alen > len) break
            if ((atype == 0x0020 || atype == 0x0001) && alen >= 8 &&
                (data[i + 1].toInt() and 0xFF) == 0x01) {
                val xored = atype == 0x0020
                val port0 = (((data[i + 2].toInt() and 0xFF) shl 8) or
                             (data[i + 3].toInt() and 0xFF))
                val ip0 = getInt(data, i + 4)
                val port = if (xored) port0 xor (MAGIC ushr 16 and 0xFFFF) else port0
                val ipnum = if (xored) ip0 xor MAGIC else ip0
                val ipStr = "${(ipnum ushr 24) and 0xFF}.${(ipnum ushr 16) and 0xFF}." +
                        "${(ipnum ushr 8) and 0xFF}.${ipnum and 0xFF}"
                return try {
                    InetSocketAddress(InetAddress.getByName(ipStr), port and 0xFFFF)
                } catch (e: Exception) { Log.w(TAG, "cím-hiba", e); null }
            }
            i += alen + ((4 - alen % 4) % 4)
        }
        return null
    }

    private fun putInt(b: ByteArray, off: Int, v: Int) {
        b[off] = (v ushr 24).toByte()
        b[off + 1] = (v ushr 16).toByte()
        b[off + 2] = (v ushr 8).toByte()
        b[off + 3] = v.toByte()
    }

    private fun getInt(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or
        ((b[off + 1].toInt() and 0xFF) shl 16) or
        ((b[off + 2].toInt() and 0xFF) shl 8) or
        (b[off + 3].toInt() and 0xFF)
}
