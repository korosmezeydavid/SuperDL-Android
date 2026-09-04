package com.superdl.launcher.share

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * A FELTÖLTÉS MAGA.
 *
 * Szándékosan csupasz: `HttpURLConnection`, nincs hozzá könyvtár. Egy
 * fájlfeltöltéshez nem kell keretrendszer, és minden új függőség egy újabb
 * dolog, ami egy Android-frissítés után elromolhat.
 *
 * KÉT SZABÁLY, AMIT A WINDOWSOS P2P MODULBÓL HOZUNK ÁT:
 *
 *  1. A HALADÁS ÉLŐBEN KELL. Vakon egy néma feltöltés megkülönböztethetetlen
 *     egy lefagyott programtól.
 *  2. NÉMA `catch` NINCS. Ha a felhasználó annyit hall, hogy „nem sikerült",
 *     az hiányos válasz. Minden hibaágnak van kimondható oka.
 */
object CloudUploader {

    /** Ekkora darabokban írunk — elég nagy a sebességhez, elég kicsi a haladáshoz. */
    private const val CHUNK = 64 * 1024

    private const val USER_AGENT =
        "SuperDL-Android (akadalymentes launcher; github.com/korosmezeydavid/SuperDL-Android)"

    data class Result(
        val ok: Boolean,
        /** Felolvasható mondat — sikernél is, hibánál is. */
        val message: String,
        val url: String = "",
        /** Amivel a fájl később törölhető a tárhelyről (ahol van ilyen). */
        val deleteToken: String = "",
        val expiresAtMillis: Long = 0L
    )

    @Volatile
    private var cancelled = false

    /** Megszakítás: a következő darab írásánál áll meg. */
    fun cancel() {
        cancelled = true
    }

    fun upload(file: File, target: CloudTarget, onProgress: (Int) -> Unit): Result {
        cancelled = false
        if (!file.isFile) return Result(false, "Ez a fájl már nem érhető el.")
        val size = file.length()
        if (size <= 0L) return Result(false, "Ez a fájl üres, nincs mit feltölteni.")
        if (size > target.maxBytes) {
            return Result(
                false,
                "Ez a fájl túl nagy ehhez a tárhelyhez: ${CloudTargets.sizeText(size)}, " +
                    "a határ ${CloudTargets.sizeText(target.maxBytes)}."
            )
        }
        return try {
            when (target.style) {
                UploadStyle.MULTIPART -> sendMultipart(file, target, onProgress)
                UploadStyle.RAW_PUT -> sendRaw(file, target, onProgress, "PUT")
                UploadStyle.RAW_POST -> sendRaw(file, target, onProgress, "POST")
            }
        } catch (e: java.net.UnknownHostException) {
            Result(false, "Nincs internet, vagy a tárhely nem található. Ok: ${e.message}")
        } catch (e: java.net.SocketTimeoutException) {
            Result(false, "A tárhely nem válaszolt időben. Próbáld újra, vagy válassz másikat.")
        } catch (e: javax.net.ssl.SSLException) {
            Result(false, "A biztonságos kapcsolat nem jött létre a tárhellyel. Ok: ${e.message}")
        } catch (t: Throwable) {
            // SZÁNDÉKOSAN Throwable: egy OutOfMemoryError nem Exception, és
            // enélkül némán megölné a feltöltést.
            Result(false, "A feltöltés megszakadt. Ok: ${t.javaClass.simpleName}, ${t.message}")
        }
    }

    // ==================== Feltöltési módok ====================

    private fun sendMultipart(
        file: File,
        target: CloudTarget,
        onProgress: (Int) -> Unit
    ): Result {
        val boundary = "SuperDLHatar" + System.currentTimeMillis()
        val head = (
            "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"${target.fieldName}\"; " +
                "filename=\"${safeAsciiName(file.name)}\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n"
            ).toByteArray(Charsets.UTF_8)
        val tail = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val total = head.size + file.length() + tail.size

        val ep = endpointFor(target, file.name)
        val conn = open(ep.url, "POST")
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        conn.setFixedLengthStreamingMode(total)
        conn.outputStream.use { out ->
            out.write(head)
            val moved = pump(file, out, head.size.toLong(), total, onProgress)
            if (moved < 0) return Result(false, "A feltöltést megszakítottad.")
            out.write(tail)
            out.flush()
        }
        return finish(conn, target, file, ep.bin)
    }

    private fun sendRaw(
        file: File,
        target: CloudTarget,
        onProgress: (Int) -> Unit,
        method: String
    ): Result {
        val ep = endpointFor(target, file.name)
        val conn = open(ep.url, method)
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.setFixedLengthStreamingMode(file.length())
        conn.outputStream.use { out ->
            val moved = pump(file, out, 0L, file.length(), onProgress)
            if (moved < 0) return Result(false, "A feltöltést megszakítottad.")
            out.flush()
        }
        return finish(conn, target, file, ep.bin)
    }

    // ==================== Segédek ====================

    private data class Endpoint(val url: String, val bin: String)

    private fun open(url: String, method: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.doOutput = true
        conn.useCaches = false
        conn.instanceFollowRedirects = true
        // A csatlakozás legyen türelmetlen, az OLVASÁS türelmes: egy nagy fájl
        // feldolgozása a szerveren perceket is vehet, és a felhasználó ilyenkor
        // már végigvárta a feltöltést — kár lenne itt elvágni.
        conn.connectTimeout = 20_000
        conn.readTimeout = 180_000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "*/*")
        return conn
    }

    /**
     * A címek. A `bin` csak a filebinnél él: ott a fájl egy „tárolóba" kerül,
     * és a MEGOSZTHATÓ hivatkozás a tároló címe, nem a fájlé.
     */
    private fun endpointFor(target: CloudTarget, fileName: String): Endpoint {
        val enc = java.net.URLEncoder.encode(safeAsciiName(fileName), "UTF-8")
            .replace("+", "%20")
        return when (target.id) {
            "zerox" -> Endpoint("https://0x0.st", "")
            "tempsh" -> Endpoint("https://temp.sh/upload", "")
            "tmpfiles" -> Endpoint("https://tmpfiles.org/api/v1/upload", "")
            "fileio" -> Endpoint("https://file.io", "")
            "bashupload" -> Endpoint("https://bashupload.com/$enc", "")
            "filebin" -> {
                val bin = randomBin()
                Endpoint("https://filebin.net/$bin/$enc", bin)
            }
            else -> Endpoint("https://0x0.st", "")
        }
    }

    /**
     * A filebin „tárolójának" neve. Kitalálhatatlannak kell lennie: aki
     * eltalálja, az látja a fájlt. Húsz véletlen karakter bőven elég.
     */
    private fun randomBin(): String {
        val betuk = "abcdefghijklmnopqrstuvwxyz0123456789"
        val r = java.security.SecureRandom()
        return (1..20).map { betuk[r.nextInt(betuk.length)] }.joinToString("")
    }

    /**
     * ÉKEZET NÉLKÜLI, BIZTONSÁGOS FÁJLNÉV a HTTP-fejlécbe és az útvonalba.
     *
     * MIÉRT: a magyar fájlnevek ékezetesek, és a szolgáltatók egy része az
     * ékezetes nevet vagy elrontja, vagy elutasítja. A fájl TARTALMA nem
     * változik, csak a neve lesz egyszerűbb az úton.
     */
    private fun safeAsciiName(name: String): String {
        val nfd = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
        val ascii = nfd.replace(Regex("\\p{Mn}+"), "")
        val tiszta = ascii.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_')
        return if (tiszta.isBlank()) "fajl" else tiszta.take(120)
    }

    /**
     * A fájl átpumpálása a kimenetre, haladás-jelzéssel.
     * Visszatérés: az átvitt bájtok, vagy -1, ha megszakították.
     */
    private fun pump(
        file: File,
        out: java.io.OutputStream,
        alreadyWritten: Long,
        totalBytes: Long,
        onProgress: (Int) -> Unit
    ): Long {
        var sent = 0L
        var lastPct = -1
        val buf = ByteArray(CHUNK)
        file.inputStream().use { input ->
            while (true) {
                if (cancelled) return -1L
                val n = input.read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
                sent += n
                val pct = (((alreadyWritten + sent) * 100L) / totalBytes).toInt().coerceIn(0, 100)
                if (pct != lastPct) {
                    lastPct = pct
                    onProgress(pct)
                }
            }
        }
        return sent
    }

    /** A válasz első nyolc kilobájtja — ennél többre soha nincs szükség. */
    private fun readBody(conn: HttpURLConnection, error: Boolean): String = try {
        val stream = if (error) conn.errorStream else conn.inputStream
        stream?.use { s ->
            val buf = ByteArray(8 * 1024)
            val n = s.read(buf)
            if (n > 0) String(buf, 0, n, Charsets.UTF_8) else ""
        } ?: ""
    } catch (_: Exception) {
        ""
    }

    /**
     * A VÁLASZ FELDOLGOZÁSA — és itt dől el, sikerült-e.
     *
     * A nullás… illetve a 200-as kód ÖNMAGÁBAN nem elég: ha nem találunk a
     * válaszban valódi linket, akkor a feltöltés nem ér semmit, hiába
     * mondta a szerver, hogy rendben. Ez ugyanaz a tanulság, mint a
     * windowsos fogadásnál: az eredményt ELLENŐRIZNI kell, nem elhinni.
     */
    private fun finish(
        conn: HttpURLConnection,
        target: CloudTarget,
        file: File,
        bin: String
    ): Result {
        val code = try {
            conn.responseCode
        } catch (e: Exception) {
            return Result(false, "A tárhely nem válaszolt. Ok: ${e.message}")
        }
        val body = readBody(conn, error = code !in 200..299).trim()
        val token = conn.getHeaderField("X-Token") ?: ""
        conn.disconnect()

        if (code !in 200..299) {
            val reszlet = body.take(160).replace(Regex("\\s+"), " ")
            val ok = when (code) {
                413 -> "a fájl túl nagy ennek a tárhelynek"
                403, 401 -> "a tárhely elutasította a feltöltést"
                429 -> "túl sok feltöltés rövid idő alatt; várj pár percet"
                in 500..599 -> "a tárhely épp nem működik; próbálj másikat"
                else -> "a tárhely $code kóddal válaszolt"
            }
            return Result(
                false,
                "A feltöltés nem sikerült: $ok." + if (reszlet.isNotBlank()) " A tárhely üzenete: $reszlet" else ""
            )
        }

        val link = extractLink(target, body, bin)
        if (link.isBlank()) {
            return Result(
                false,
                "A tárhely elfogadta a fájlt, de nem adott vissza használható linket. " +
                    "Ezt ne tekintsd megosztottnak; próbáld másik tárhellyel."
            )
        }
        val expires = System.currentTimeMillis() + target.lifetimeHours * 3600_000L
        val figyelmeztetes = if (target.oneTimeDownload) {
            " Figyelem: ezt a fájlt csak EGYSZER lehet letölteni."
        } else {
            ""
        }
        return Result(
            ok = true,
            message = "Kész. ${file.name} feltöltve ide: ${target.spokenName}. " +
                "A link a vágólapon van. ${CloudTargets.lifetimeText(target.lifetimeHours)} él.$figyelmeztetes",
            url = link,
            deleteToken = token,
            expiresAtMillis = expires
        )
    }

    /**
     * A LINK KISZEDÉSE a válaszból — tárhelyenként.
     *
     * Van, ahol a válasz MAGA a link (0x0.st, temp.sh), van, ahol JSON
     * (tmpfiles, file.io), és van, ahol a link nem is a fájlé, hanem a
     * tárolóé (filebin).
     */
    private fun extractLink(target: CloudTarget, body: String, bin: String): String {
        if (target.id == "filebin") {
            return if (bin.isNotBlank()) "https://filebin.net/$bin" else ""
        }
        // JSON-os tárhelyek: a legelső http(s) mező kell, de a JSON idézőjelei
        // és a \/ escape-elés nélkül.
        val tisztitott = body.replace("\\/", "/")
        val elso = Regex("https?://[^\\s\"'<>\\\\)]+").find(tisztitott)?.value ?: return ""
        var link = elso.trimEnd('.', ',', ';', ')')
        // A tmpfiles a NÉZŐ oldalt adja vissza; a letöltő cím a /dl/ útvonal.
        // A nézőt osztjuk meg: böngészőben az működik mindenkinek.
        if (target.id == "bashupload") {
            // A válasz szövege: „wget https://bashupload.com/AZONOSITO/nev"
            link = Regex("https://bashupload\\.com/\\S+").find(tisztitott)?.value ?: link
            link = link.trimEnd('.', ',', ';', ')')
        }
        return link
    }

    // ==================== Törlés a tárhelyről ====================

    /**
     * VISSZAVONÁS. Ahol a tárhely megengedi, a feltöltött fájl utólag
     * törölhető — és ez nem apróság: ez a különbség aközött, hogy „elküldtem
     * valamit, amit nem kellett volna" és „elküldtem, aztán visszavontam".
     *
     * Ahol nincs rá mód, ott ezt KIMONDJUK, nem hallgatjuk el.
     */
    fun deleteRemote(entry: ShareEntry): Result = try {
        when (entry.providerId) {
            "zerox" -> deleteZerox(entry)
            "filebin" -> deleteFilebin(entry)
            else -> Result(
                false,
                "Erről a tárhelyről nem tudom törölni a fájlt. Magától lejár, " +
                    "addig viszont elérhető marad annak, akinek megvan a link."
            )
        }
    } catch (t: Throwable) {
        Result(false, "A törlés nem sikerült. Ok: ${t.javaClass.simpleName}, ${t.message}")
    }

    private fun deleteZerox(entry: ShareEntry): Result {
        if (entry.deleteToken.isBlank()) {
            return Result(false, "Ehhez a feltöltéshez nincs törlési jelszavam, ezért nem tudom levenni.")
        }
        val conn = URL(entry.url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        val torzs = "token=" + java.net.URLEncoder.encode(entry.deleteToken, "UTF-8") + "&delete="
        conn.outputStream.use { it.write(torzs.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val body = readBody(conn, error = code !in 200..299).take(160)
        conn.disconnect()
        return if (code in 200..299) {
            Result(true, "Törölve a tárhelyről. A link mostantól nem működik.")
        } else {
            Result(false, "A törlés nem sikerült, a tárhely $code kóddal válaszolt. $body")
        }
    }

    private fun deleteFilebin(entry: ShareEntry): Result {
        val conn = URL(entry.url).openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        val code = conn.responseCode
        conn.disconnect()
        return if (code in 200..299 || code == 404) {
            Result(true, "Törölve a tárhelyről. A link mostantól nem működik.")
        } else {
            Result(false, "A törlés nem sikerült, a tárhely $code kóddal válaszolt.")
        }
    }
}
