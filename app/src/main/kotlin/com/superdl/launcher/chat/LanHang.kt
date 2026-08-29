package com.superdl.launcher.chat

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.SocketTimeoutException

/**
 * Csevejcenter – valós idejű HANG-ÁTVITEL, HELYI hálón ÉS interneten át
 * (a PC `lanhang.py` párja). Host-modell, fizetős szerver NÉLKÜL: egy résztvevő
 * a HOST (UDP-port), a többiek hozzá küldik a mikrofon-kockákat, a host
 * TOVÁBBÍTJA minden másiknak → mindenki a saját gépén kever térben.
 *
 * A felek a szoba Ably-csatornáján kicserélik a VÉGPONT-JELÖLTJEIKET (LAN +
 * publikus/STUN), majd UDP hole-punchinggal átlyukasztják a routereket.
 * Csomag: [1B névhossz][név utf8][PCM16 mono]. Üres PCM = hello (punch/keepalive).
 */
class HangHalozat(val nev: String) {

    val th = TerbeliHang()

    @Volatile private var running = false
    @Volatile private var host = false
    private var sock: DatagramSocket? = null
    private val punch = HashSet<InetSocketAddress>()
    private val clients = HashMap<SocketAddress, Pair<String, Long>>()   // host: megerősített kliensek
    @Volatile private var hostAddr: SocketAddress? = null                 // kliens: megerősített host
    private val lock = Any()
    var cimek: List<Pair<String, Int>> = emptyList()
        private set

    // ---- indítás ------------------------------------------------------
    fun hostIndit(port: Int = 47690): List<Pair<String, Int>> {
        bindStun(port)
        host = true
        running = true
        th.indit { pcm -> hostKimeno(pcm) }
        indulSzalak()
        return cimek
    }

    fun kliensIndit(hostCimek: List<Pair<String, Int>>): List<Pair<String, Int>> {
        bindStun(0)
        host = false
        running = true
        punchHozzaad(hostCimek)
        th.indit { pcm -> kliensKimeno(pcm) }
        indulSzalak()
        return cimek
    }

    fun punchHozzaad(celok: List<Pair<String, Int>>) {
        synchronized(lock) {
            for ((ip, port) in celok) {
                try { punch.add(InetSocketAddress(InetAddress.getByName(ip), port)) }
                catch (_: Exception) { }
            }
        }
    }

    fun setResztvevok(nevek: List<String>) {
        th.setUlesek(ulesek(nevek.filter { it != nev }))
    }

    fun nemit(b: Boolean) = th.nemit(b)

    // ---- bind + STUN --------------------------------------------------
    private fun bindStun(port: Int) {
        var s: DatagramSocket? = null
        if (port != 0) {
            for (p in port until port + 8) {
                try { s = DatagramSocket(p); break } catch (_: Exception) { }
            }
        }
        if (s == null) s = DatagramSocket()          // szabad port
        val socket = s
        val helyiPort = socket.localPort
        val jeloltek = ArrayList<Pair<String, Int>>()
        jeloltek.add(lanIp() to helyiPort)
        val pub = try { Stun.publikusCim(socket) } catch (_: Exception) { null }
        if (pub != null) {
            val cim = pub.address?.hostAddress to pub.port
            if (cim.first != null && jeloltek.none { it.first == cim.first && it.second == cim.second }) {
                jeloltek.add(cim.first!! to cim.second)
            }
        }
        socket.soTimeout = 300                        // hogy a fogadó ciklus figyelhesse a leállást
        sock = socket
        cimek = jeloltek
    }

    // ---- kimenő mikrofon ---------------------------------------------
    private fun hostKimeno(pcm: ByteArray) = szor(csomag(nev, pcm))

    private fun kliensKimeno(pcm: ByteArray) {
        val cs = csomag(nev, pcm)
        val ha = hostAddr
        if (ha != null) kuld(cs, ha)
        else {
            val celok = synchronized(lock) { punch.toList() }
            for (a in celok) kuld(cs, a)
        }
    }

    // ---- szálak -------------------------------------------------------
    private fun indulSzalak() {
        Thread { fogado() }.also { it.isDaemon = true; it.start() }
        Thread { punchLoop() }.also { it.isDaemon = true; it.start() }
    }

    private fun fogado() {
        val buf = ByteArray(2048)
        while (running) {
            val pkt = DatagramPacket(buf, buf.size)
            try { sock?.receive(pkt) } catch (e: SocketTimeoutException) { continue }
            catch (e: Exception) { break }
            val (n, pcm) = bont(pkt.data, pkt.length)
            if (n.isEmpty()) continue
            if (host) {
                synchronized(lock) { clients[pkt.socketAddress] = n to System.currentTimeMillis() }
                if (pcm.isNotEmpty()) {
                    th.fogad(n, pcm)
                    szor(pkt.data.copyOf(pkt.length), kiveve = pkt.socketAddress)
                }
            } else {
                if (hostAddr == null) hostAddr = pkt.socketAddress
                if (pcm.isNotEmpty()) th.fogad(n, pcm)
            }
        }
    }

    private fun punchLoop() {
        var i = 0
        val hello = csomag(nev, ByteArray(0))
        while (running) {
            val celok = synchronized(lock) { punch.toList() }
            for (a in celok) kuld(hello, a)
            if (host) takarit()
            try { Thread.sleep(if (i < 30) 400 else 2000) } catch (_: InterruptedException) { break }
            i++
        }
    }

    // ---- küldés-segédek ----------------------------------------------
    private fun kuld(data: ByteArray, addr: SocketAddress) {
        try { sock?.send(DatagramPacket(data, data.size, addr)) } catch (_: Exception) { }
    }

    private fun szor(data: ByteArray, kiveve: SocketAddress? = null) {
        val celok = synchronized(lock) { clients.keys.filter { it != kiveve } }
        for (a in celok) kuld(data, a)
    }

    private fun takarit() {
        val most = System.currentTimeMillis()
        val kiesok = ArrayList<Pair<SocketAddress, String>>()
        synchronized(lock) {
            val it = clients.entries.iterator()
            while (it.hasNext()) {
                val e = it.next()
                if (most - e.value.second > 15000) { kiesok.add(e.key to e.value.first); it.remove() }
            }
        }
        for ((_, n) in kiesok) th.elenged(n)
    }

    fun leallit() {
        running = false
        try { th.leallit() } catch (_: Exception) { }
        try { sock?.close() } catch (_: Exception) { }
        sock = null
        try { Log.i("CsevejLanHang", "hang-hálózat leállítva") } catch (_: Exception) { }
    }

    companion object {
        /** A gép LAN-IP-je (nem 127.0.0.1). */
        fun lanIp(): String {
            return try {
                DatagramSocket().use { s ->
                    s.connect(InetAddress.getByName("8.8.8.8"), 80)
                    s.localAddress?.hostAddress ?: "127.0.0.1"
                }
            } catch (e: Exception) { "127.0.0.1" }
        }

        fun csomag(nev: String, pcm: ByteArray): ByteArray {
            val nb = nev.toByteArray(Charsets.UTF_8).let { if (it.size > 255) it.copyOf(255) else it }
            val out = ByteArray(1 + nb.size + pcm.size)
            out[0] = nb.size.toByte()
            System.arraycopy(nb, 0, out, 1, nb.size)
            System.arraycopy(pcm, 0, out, 1 + nb.size, pcm.size)
            return out
        }

        fun bont(data: ByteArray, len: Int): Pair<String, ByteArray> {
            if (len < 1) return "" to ByteArray(0)
            val n = data[0].toInt() and 0xFF
            if (1 + n > len) return "" to ByteArray(0)
            val nev = String(data, 1, n, Charsets.UTF_8)
            val pcm = data.copyOfRange(1 + n, len)
            return nev to pcm
        }
    }
}
