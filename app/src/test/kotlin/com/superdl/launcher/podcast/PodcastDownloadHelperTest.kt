package com.superdl.launcher.podcast

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import kotlin.concurrent.thread

class PodcastDownloadHelperTest {
    @Test
    fun followsPodcastAudioRedirectInsteadOfRejecting301() {
        ServerSocket(0, 2, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5000
            val responder = thread {
                repeat(2) { index ->
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        while (reader.readLine()?.isNotEmpty() == true) { }
                        val header = if (index == 0) {
                            "HTTP/1.1 301 Moved Permanently\r\nLocation: /audio.mp3\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        } else {
                            "HTTP/1.1 200 OK\r\nContent-Type: audio/mpeg\r\nContent-Length: 3\r\nConnection: close\r\n\r\n"
                        }
                        socket.getOutputStream().write(header.toByteArray(Charsets.US_ASCII))
                        if (index == 1) socket.getOutputStream().write(byteArrayOf(1, 2, 3))
                    }
                }
            }
            val connection = PodcastDownloadHelper.openAudioConnection(
                "http://127.0.0.1:${server.localPort}/episode"
            )
            try {
                assertEquals(200, connection.responseCode)
                assertEquals(listOf<Byte>(1, 2, 3), connection.inputStream.use { it.readBytes().toList() })
            } finally {
                connection.disconnect()
            }
            responder.join(5000)
        }
    }

    @Test
    fun permitsHttpToHttpsButRejectsNonWebRedirect() {
        val start = URL("http://example.org/episode.mp3")
        assertEquals("https://example.org/episode.mp3",
            PodcastDownloadHelper.resolveAudioRedirect(start, "https://example.org/episode.mp3").toString())
        try {
            PodcastDownloadHelper.resolveAudioRedirect(start, "file:///private/audio.mp3")
            throw AssertionError("file redirect accepted")
        } catch (_: IllegalArgumentException) {
        }
    }
}
