package dev.blamera.chatgpt

import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.security.cert.Certificate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real JDK HTTP transport on PC; the adapter does not claim to test Android TLS. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrialHttpWireTest {
    @Test fun tokenExchangeWritesTheExpectedFormAndLengthThroughARealConnection() = runBlocking {
        WireServer(200).use { server ->
            val http = HttpsTrialHttp { RealHttpAdapter(server.url) }
            val api = TrialAccountApi(http) { 100 }
            val grant = api.exchange(
                TrialAuthCode("fixture code", "client"),
                TrialAuthAttempt(
                    "host",
                    "client",
                    "http://127.0.0.1:1234/auth/callback",
                    "s",
                    "n",
                    "verifier"
                )
            )
            assertEquals("fixture-access", grant.accessToken)
            val actual = server.captured.get(5, TimeUnit.SECONDS)
            assertEquals("POST /token HTTP/1.1", actual.first)
            assertEquals("application/x-www-form-urlencoded", actual.headers["content-type"])
            assertEquals(
                actual.body.toByteArray(Charsets.UTF_8).size.toString(),
                actual.headers["content-length"]
            )
            val form = actual.body.split('&').associate {
                val pair = it.split('=', limit = 2)
                URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
            }
            assertEquals(
                mapOf(
                    "grant_type" to "authorization_code",
                    "client_id" to "client",
                    "code" to "fixture code",
                    "code_verifier" to "verifier",
                    "redirect_uri" to "http://127.0.0.1:1234/auth/callback",
                    "resource" to TRIAL_RESOURCE
                ),
                form
            )
            assertFalse(actual.headers.containsKey("authorization"))
            assertTrue(actual.body.isNotBlank())
            http.close()
        }
    }

    @Test fun realServiceUnavailableIsDistinctFromTransportFailure() = runBlocking {
        WireServer(503).use { server ->
            val events = mutableListOf<TrialHttpDiagnostic>()
            val http = HttpsTrialHttp(diagnostics = events::add) { RealHttpAdapter(server.url) }
            val api = TrialAccountApi(http) { 100 }
            try {
                api.exchange(
                    TrialAuthCode("fixture", "client"),
                    TrialAuthAttempt(
                        "host",
                        "client",
                        "http://127.0.0.1:1234/auth/callback",
                        "s",
                        "n",
                        "v"
                    )
                )
                throw AssertionError("Expected service failure")
            } catch (failure: ChatGptTrialException) {
                assertEquals(TrialFailure.SERVICE_UNAVAILABLE, failure.kind)
            }
            assertEquals("POST /token HTTP/1.1", server.captured.get(5, TimeUnit.SECONDS).first)
            assertEquals(
                listOf(
                    TrialHttpDiagnostic(
                        TrialHttpEndpoint.TOKEN,
                        TrialHttpStage.READ_STATUS,
                        TrialHttpCategory.HTTP_STATUS,
                        503
                    )
                ),
                events
            )
            http.close()
        }
    }

    private data class Captured(
        val first: String,
        val headers: Map<String, String>,
        val body: String
    )

    private class WireServer(private val status: Int) : AutoCloseable {
        private val server = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val url = URL("http://127.0.0.1:${server.localPort}/token")
        val captured = CompletableFuture<Captured>()
        init {
            Thread({
                try {
                    server.accept().use(::respond)
                } catch (failure: Exception) {
                    captured.completeExceptionally(failure)
                }
            }, "fixture-http-server").apply {
                isDaemon = true
                start()
            }
        }

        private fun respond(socket: Socket) {
            socket.soTimeout = 5000
            val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
            val first = checkNotNull(reader.readLine())
            val headers = linkedMapOf<String, String>()
            var line = checkNotNull(reader.readLine())
            while (line.isNotEmpty()) {
                headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                line = checkNotNull(reader.readLine())
            }
            val length = headers.getValue("content-length").toInt()
            require(length in 1..16_384)
            val chars = CharArray(length)
            var offset = 0
            while (offset < length) {
                val count = reader.read(chars, offset, length - offset)
                check(count > 0)
                offset += count
            }
            captured.complete(Captured(first, headers, String(chars)))
            val body = if (status == 200) {
                """{"token_type":"Bearer","access_token":"fixture-access",
                    "refresh_token":"fixture-refresh","id_token":"fixture-id","expires_in":3600,
                    "scope":"chatgpt.tokens.use.direct"}"""
            } else {
                """{"error":{"code":"service_unavailable","message":"fixture server"}}"""
            }
            val bytes = body.toByteArray(Charsets.UTF_8)
            val response = "HTTP/1.1 $status Fixture\r\nContent-Length: ${bytes.size}\r\n" +
                "Content-Type: application/json\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().apply {
                write(response.toByteArray(Charsets.US_ASCII))
                write(bytes)
                flush()
            }
        }
        override fun close() {
            server.close()
        }
    }

    /** Every configured property and I/O call reaches the real stateful JDK connection. */
    private class RealHttpAdapter(url: URL) : HttpsURLConnection(url) {
        private val delegate = url.openConnection() as HttpURLConnection
        override fun setRequestMethod(method: String) {
            delegate.requestMethod = method
        }
        override fun getRequestMethod(): String = delegate.requestMethod
        override fun setInstanceFollowRedirects(follow: Boolean) {
            delegate.instanceFollowRedirects =
                follow
        }
        override fun setConnectTimeout(timeout: Int) {
            delegate.connectTimeout = timeout
        }
        override fun setReadTimeout(timeout: Int) {
            delegate.readTimeout = timeout
        }
        override fun setDoOutput(output: Boolean) {
            delegate.doOutput = output
        }
        override fun setRequestProperty(key: String, value: String) {
            delegate.setRequestProperty(key, value)
        }
        override fun setFixedLengthStreamingMode(length: Int) {
            delegate.setFixedLengthStreamingMode(length)
        }
        override fun getOutputStream(): OutputStream = delegate.outputStream
        override fun getResponseCode(): Int = delegate.responseCode
        override fun getInputStream(): InputStream = delegate.inputStream
        override fun getErrorStream(): InputStream? = delegate.errorStream
        override fun disconnect() {
            delegate.disconnect()
        }
        override fun usingProxy() = delegate.usingProxy()
        override fun connect() {
            delegate.connect()
        }
        override fun getCipherSuite() = "NOT_TLS_PC_FIXTURE"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
}
