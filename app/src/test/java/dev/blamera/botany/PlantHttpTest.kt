package dev.blamera.botany

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlantHttpTest {
    @Test
    fun multipartReachesLocalWireExactlyOnceWithNoRedirectOrMetadata() = runBlocking {
        val wire = CompletableFuture<Pair<String, ByteArray>>()
        val server = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        server.soTimeout = 10000
        val worker = thread(isDaemon = true) {
            server.accept().use { socket ->
                socket.soTimeout = 10000
                val input = socket.getInputStream()
                val headers = StringBuilder()
                while (!headers.endsWith("\r\n\r\n")) headers.append(input.read().toChar())
                val length = Regex("Content-Length: (\\d+)", RegexOption.IGNORE_CASE)
                    .find(headers)!!.groupValues[1].toInt()
                val bytes = ByteArray(length)
                var read = 0
                while (read < length) read += input.read(bytes, read, length - read)
                val type = headers.lines().first {
                    it.startsWith("Content-Type:", true)
                }.substringAfter(':').trim()
                wire.complete(type to bytes)
                val body = "{\"results\":[]}"
                socket.getOutputStream().write(
                    (
                        "HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\n" +
                            "RateLimit: \"dailyQuota\";r=0;t=100\r\nConnection: close\r\n\r\n$body"
                        ).toByteArray()
                )
            }
        }
        try {
            var requested = ""
            val client = PlantNetHttp(open = { official ->
                requested = official.toExternalForm()
                URL(
                    "http://127.0.0.1:${server.localPort}/"
                ).openConnection() as java.net.HttpURLConnection
            })
            val jpeg = byteArrayOf(-1, -40, 0, 1, 2, -1, -39)
            val response = client.identify("FAKE-key", jpeg)
            assertEquals(200, response.status)
            assertEquals(0, response.remaining)
            assertEquals(
                "https://my-api.plantnet.org/v2/identify/all?api-key=FAKE-key&lang=fr&nb-results=3&no-reject=false&include-related-images=false",
                requested
            )
            val (type, bytes) = wire.getNow(null)
            assertTrue(type.startsWith("multipart/form-data; boundary="))
            val body = bytes.toString(Charsets.ISO_8859_1)
            assertTrue(body.contains("name=\"organs\"\r\n\r\nauto\r\n"))
            assertTrue(body.contains("name=\"images\"; filename=\"image.jpg\""))
            assertTrue(body.contains("Content-Type: image/jpeg"))
            assertTrue(body.contains(jpeg.toString(Charsets.ISO_8859_1)))
            assertFalse(body.contains("FAKE-key"))
        } finally {
            server.close()
            worker.join(1000)
        }
    }
}
