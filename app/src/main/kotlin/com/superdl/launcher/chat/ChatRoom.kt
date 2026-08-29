package com.superdl.launcher.chat

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

/**
 * A Csevejcenter szoba LOGIKÁJA a telefonon: jelenlét (szívveréssel) + üzenetek.
 * A PC-s `csevejcenter.Csevejszoba` pontos párja, ugyanazokkal az üzenet-
 * típusokkal (uzenet, belep, sziv, kilep) – így PC és telefon egy szobában van.
 *
 * A callbackek a FŐSZÁLON hívódnak (a UI közvetlenül frissíthet).
 */
class ChatRoom(context: Context, kod: String, val nev: String) {

    val net = NetRoom(context, kod, nev)
    private val members = HashMap<String, Long>()   // nev -> utoljára hallottuk (uptimeMillis)
    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var running = false
    private var heartbeat: Thread? = null

    // UI-callbackek (mind opcionális). `history=true`: a belépéskori ELŐZMÉNY
    // (némán jelenítsük meg – ne szóljon hang, ne olvassa fel).
    var onMessage: ((nev: String, szoveg: String, sajat: Boolean, history: Boolean) -> Unit)? = null
    var onJoined: ((nev: String) -> Unit)? = null
    var onLeft: ((nev: String) -> Unit)? = null
    var onMembers: ((nevek: List<String>) -> Unit)? = null
    var onHangHost: ((cimek: List<Pair<String, Int>>, ki: String) -> Unit)? = null
    var onHangTag: ((ki: String, cimek: List<Pair<String, Int>>) -> Unit)? = null

    // a valós idejű hanghoz: az utolsó ismert host + a kliensek jelöltjei
    @Volatile private var hangHostCimek: List<Pair<String, Int>> = emptyList()
    @Volatile private var hangHostKi: String = ""
    private val hangTagok = HashMap<String, List<Pair<String, Int>>>()

    fun available(): Boolean = net.available()

    fun members(): List<String> = synchronized(lock) {
        members.keys.sortedBy { it.lowercase() }
    }

    fun enter() {
        running = true
        synchronized(lock) { members[nev] = SystemClock.uptimeMillis() }
        // ELŐZMÉNY: a korábbi CSEVEGÉST némán behozzuk (jelenlétet nem), és
        // megjegyezzük az utolsó hang-hostot / a kliensek jelöltjeit
        try {
            for (m in net.poll()) {
                when (m.type) {
                    "uzenet" -> deliverMessage(m, history = true)
                    "hang_host" -> hangHostBe(m, notify = false)
                    "hang_tag" -> if (m.ki != nev) hangTagBe(m, notify = false)
                }
            }
        } catch (_: Exception) { }
        notifyMembers()
        net.listen(1000) { receive(it) }
        net.send("belep")
        heartbeat = Thread {
            try { net.send("sziv") } catch (_: Exception) { }
            while (running) {
                try { Thread.sleep(5000) } catch (_: InterruptedException) { break }
                if (!running) break
                try { net.send("sziv") } catch (_: Exception) { }
                synchronized(lock) { members[nev] = SystemClock.uptimeMillis() }
                pruneStale()
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun sendMessage(szoveg: String): Boolean {
        val t = szoveg.trim()
        if (t.isEmpty()) return false
        net.send("uzenet", JSONObject().put("szoveg", t))
        main.post { onMessage?.invoke(nev, t, true, false) }   // optimista, saját
        return true
    }

    fun leave() {
        running = false
        heartbeat?.interrupt(); heartbeat = null
        try { net.send("kilep") } catch (_: Exception) { }
        net.stop()
    }

    // ---- belső ----
    private fun receive(m: RoomMessage) {
        val ki = if (m.ki.isNotEmpty()) m.ki else "Valaki"
        val sajat = ki == nev
        when (m.type) {
            "uzenet" -> if (!sajat) deliverMessage(m, history = false)
            "belep" -> if (!sajat) memberSeen(ki, announce = true)
            "sziv" -> if (!sajat) memberSeen(ki, announce = false)
            "kilep" -> if (!sajat) memberLeft(ki)
            "hang_host" -> hangHostBe(m, notify = true)
            "hang_tag" -> if (!sajat) hangTagBe(m, notify = true)
        }
    }

    private fun deliverMessage(m: RoomMessage, history: Boolean) {
        val szoveg = m.adat.optString("szoveg", "").trim()
        if (szoveg.isEmpty()) return
        val ki = if (m.ki.isNotEmpty()) m.ki else "Valaki"
        if (ki != nev) memberSeen(ki, announce = false)   // az üzenő is jelen van
        main.post { onMessage?.invoke(ki, szoveg, ki == nev, history) }
    }

    private fun memberSeen(nev: String, announce: Boolean) {
        val fresh: Boolean
        synchronized(lock) {
            fresh = !members.containsKey(nev)
            members[nev] = SystemClock.uptimeMillis()
        }
        if (fresh && announce) main.post { onJoined?.invoke(nev) }
        if (fresh) notifyMembers()
    }

    private fun memberLeft(nev: String) {
        val had: Boolean
        synchronized(lock) { had = members.remove(nev) != null }
        if (had) {
            main.post { onLeft?.invoke(nev) }
            notifyMembers()
        }
    }

    private fun pruneStale() {
        val now = SystemClock.uptimeMillis()
        val gone = ArrayList<String>()
        synchronized(lock) {
            val it = members.entries.iterator()
            while (it.hasNext()) {
                val e = it.next()
                if (e.key != nev && now - e.value > 16000) { it.remove(); gone.add(e.key) }
            }
        }
        for (g in gone) main.post { onLeft?.invoke(g) }
        if (gone.isNotEmpty()) notifyMembers()
    }

    private fun notifyMembers() {
        val list = members()
        main.post { onMembers?.invoke(list) }
    }

    // ---- hang-host / hang-tag (a valós idejű hanghoz) -----------------
    private fun cimekParse(adat: JSONObject): List<Pair<String, Int>> {
        val ki = ArrayList<Pair<String, Int>>()
        val arr = adat.optJSONArray("cimek") ?: return ki
        for (i in 0 until arr.length()) {
            val c = arr.optJSONArray(i) ?: continue
            val ip = c.optString(0, "").trim()
            val port = c.optInt(1, 0)
            if (ip.isNotEmpty() && port != 0) ki.add(ip to port)
        }
        return ki
    }

    private fun hangHostBe(m: RoomMessage, notify: Boolean) {
        val cimek = cimekParse(m.adat)
        if (cimek.isEmpty()) return
        hangHostCimek = cimek
        hangHostKi = m.ki
        if (notify) main.post { onHangHost?.invoke(cimek, m.ki) }
    }

    private fun hangTagBe(m: RoomMessage, notify: Boolean) {
        val cimek = cimekParse(m.adat)
        if (m.ki.isEmpty() || cimek.isEmpty()) return
        synchronized(lock) { hangTagok[m.ki] = cimek }
        if (notify) main.post { onHangTag?.invoke(m.ki, cimek) }
    }

    /** Az utolsó ismert hang-host (cimek, ki) vagy null. */
    fun hangHost(): Pair<List<Pair<String, Int>>, String>? =
        if (hangHostCimek.isNotEmpty()) hangHostCimek to hangHostKi else null

    fun hangTagokAll(): Map<String, List<Pair<String, Int>>> =
        synchronized(lock) { HashMap(hangTagok) }

    private fun cimekJson(cimek: List<Pair<String, Int>>): JSONObject {
        val arr = JSONArray()
        for ((ip, port) in cimek) arr.put(JSONArray().put(ip).put(port))
        return JSONObject().put("cimek", arr)
    }

    /** Hostként bejelentjük a hang-VÉGPONT-jelöltjeinket (LAN + publikus). */
    fun hirdetHost(cimek: List<Pair<String, Int>>) {
        Thread { net.send("hang_host", cimekJson(cimek)) }.apply { isDaemon = true }.start()
    }

    /** Kliensként bejelentjük a jelöltjeinket (hogy a host is punch-oljon felénk). */
    fun hirdetTag(cimek: List<Pair<String, Int>>) {
        Thread { net.send("hang_tag", cimekJson(cimek)) }.apply { isDaemon = true }.start()
    }
}
