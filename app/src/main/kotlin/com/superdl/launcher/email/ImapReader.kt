package com.superdl.launcher.email

import android.util.Base64
import android.util.Log
import com.superdl.launcher.search.ArticleTextExtractor
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object ImapReader {

    private const val TAG = "ImapReader"
    private const val MAX_MESSAGES = 20

    fun fetchInbox(config: SmtpConfig): List<ImapMail> {
        if (!config.isValid()) return emptyList()
        return try {
            fetchWithSsl(config)
        } catch (e: Exception) {
            Log.w(TAG, "IMAP failed: ${e.message}")
            emptyList()
        }
    }

    fun countUnread(config: SmtpConfig): Int {
        if (!config.isValid()) return 0
        return try {
            withSession(config) { readUnreadCount(it) }
        } catch (_: Exception) {
            0
        }
    }

    private fun fetchWithSsl(config: SmtpConfig): List<ImapMail> =
        withSession(config) { lines ->
            val uids = searchAll(lines)
            uids.take(MAX_MESSAGES).mapNotNull { uid -> fetchMessage(lines, uid) }
        }

    private fun <T> withSession(config: SmtpConfig, block: (ImapSession) -> T): T {
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val socket = factory.createSocket("imap.gmail.com", 993) as SSLSocket
        socket.soTimeout = 15_000
        socket.startHandshake()
        val reader = BufferedReader(InputStreamReader(socket.inputStream, StandardCharsets.UTF_8))
        val writer = OutputStreamWriter(socket.outputStream, StandardCharsets.UTF_8)
        val session = ImapSession(reader, writer)
        try {
            expectTag(session.readLine(), "* OK")
            session.command("LOGIN", imapQuote(config.username) + " " + imapQuote(config.password))
            session.command("SELECT", "INBOX")
            return block(session)
        } finally {
            try {
                session.command("LOGOUT")
            } catch (_: Exception) {
            }
            socket.close()
        }
    }

    private fun readUnreadCount(session: ImapSession): Int {
        session.command("SEARCH", "UNSEEN")
        val line = session.lastDataLine
        val parts = line.substringAfter("SEARCH", line).trim().split(Regex("\\s+"))
        return parts.count { part -> part.all(Char::isDigit) && part.isNotBlank() }
    }

    private fun searchAll(session: ImapSession): List<Long> {
        session.command("UID SEARCH", "ALL")
        val line = session.lastDataLine
        val tail = line.substringAfter("SEARCH", line).trim()
        return tail.split(Regex("\\s+"))
            .mapNotNull { part -> part.toLongOrNull() }
            .sortedDescending()
    }

    private fun fetchMessage(session: ImapSession, uid: Long): ImapMail? {
        session.command(
            "UID FETCH",
            "$uid (BODY.PEEK[HEADER.FIELDS (FROM SUBJECT DATE)] BODY.PEEK[TEXT])"
        )
        val chunks = session.collectFetchLines()
        if (chunks.isEmpty()) return null
        val header = chunks.firstOrNull { it.contains("From:") || it.contains("FROM:") }.orEmpty()
        val body = chunks.lastOrNull().orEmpty()
        return ImapMail(
            uid = uid,
            from = parseHeaderField(header, "From"),
            subject = parseHeaderField(header, "Subject").ifBlank { "Nincs tárgy" },
            date = parseHeaderField(header, "Date").ifBlank { "Ismeretlen dátum" },
            body = cleanBody(body)
        )
    }

    private fun parseHeaderField(header: String, field: String): String {
        val regex = Regex("(?im)^$field:\\s*(.+)$")
        return regex.find(header)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    }

    private fun cleanBody(raw: String): String {
        var text = raw
        if (text.contains("Content-Type: text/html", ignoreCase = true)) {
            text = ArticleTextExtractor.extractPlainText(text)
        }
        return text.replace(Regex("(?m)^--.*"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun imapQuote(value: String): String {
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }

    private fun expectTag(line: String, prefix: String) {
        if (!line.startsWith(prefix)) throw IllegalStateException("IMAP greeting: $line")
    }

    private class ImapSession(
        private val reader: BufferedReader,
        private val writer: OutputStreamWriter
    ) {
        private var tagCounter = 0
        var lastDataLine: String = ""

        fun readLine(): String = reader.readLine() ?: ""

        fun command(cmd: String, args: String = "") {
            val tag = "A${++tagCounter}"
            if (args.isBlank()) write("$tag $cmd\r\n")
            else write("$tag $cmd $args\r\n")
            var data = ""
            while (true) {
                val line = readLine()
                if (line.startsWith(tag)) {
                    if (!line.contains("OK")) throw IllegalStateException("IMAP $cmd failed: $line")
                    break
                }
                if (line.startsWith("*")) data = line
            }
            lastDataLine = data
        }

        fun collectFetchLines(): List<String> {
            val lines = mutableListOf<String>()
            val tag = "A$tagCounter"
            while (true) {
                val line = readLine()
                if (line.startsWith(tag)) break
                if (line.startsWith("*") && line.contains("FETCH")) {
                    val literal = readLiteralBlock(line)
                    if (literal.isNotBlank()) lines.add(literal)
                }
            }
            return lines
        }

        private fun readLiteralBlock(headerLine: String): String {
            val match = Regex("\\{(\\d+)\\}").find(headerLine) ?: return ""
            val size = match.groupValues[1].toIntOrNull() ?: return ""
            val buf = CharArray(size)
            var read = 0
            while (read < size) {
                val r = reader.read(buf, read, size - read)
                if (r < 0) break
                read += r
            }
            readLine()
            return String(buf, 0, read.coerceAtLeast(0))
        }

        private fun write(text: String) {
            writer.write(text)
            writer.flush()
        }
    }
}