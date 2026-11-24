package dev.blamera.chatgpt

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrialNetworkTest {
    @Test fun httpsSendsOneBoundedRequestWithRedirectsDisabledAndAlwaysCloses() = runBlocking {
        val connection = FakeHttps()
        val http = HttpsTrialHttp { connection }
        assertEquals(
            TrialHttpResponse(200, "reply"),
            http.execute(TrialHttpRequest("$TRIAL_RESOURCE/responses", "POST", "body", "token"))
        )
        assertFalse(connection.instanceFollowRedirects)
        assertEquals("POST", connection.requestMethod)
        assertEquals("Bearer token", connection.getRequestProperty("Authorization"))
        assertEquals("text/event-stream", connection.getRequestProperty("Accept"))
        assertEquals("body", connection.sent.toString("UTF-8"))
        assertTrue(connection.disconnected)
        http.cancel()
        http.close()
    }

    @Test fun unknownHostsCredentialsAndOversizedResponsesCannotEscapeTheBoundary() = runBlocking {
        var opened = false
        val http = HttpsTrialHttp {
            opened = true
            FakeHttps()
        }
        listOf(
            "http://api.openai.com/v1/responses",
            "https://evil.test/",
            "https://api.openai.com/v1/responses?redirect=evil"
        ).forEach {
            expect(TrialFailure.INVALID_RESPONSE) { http.execute(TrialHttpRequest(it)) }
        }
        expect(TrialFailure.INVALID_RESPONSE) {
            http.execute(TrialHttpRequest("$TRIAL_AUTH_ORIGIN/jwks", bearer = "token"))
        }
        assertFalse(opened)
        val huge = HttpsTrialHttp { FakeHttps().apply { reply = "123456" } }
        expect(TrialFailure.INVALID_RESPONSE) {
            huge.execute(TrialHttpRequest("$TRIAL_RESOURCE/models", maxBytes = 5))
        }
        assertEquals("exact", readTrialBody(ByteArrayInputStream("exact".toByteArray()), 5))
        assertThrows(ChatGptTrialException::class.java) {
            readTrialBody(ByteArrayInputStream(ByteArray(8193)), 8192)
        }
        Unit
    }

    @Test fun redirectsTimeoutsAndTransportErrorsAreSanitized() = runBlocking {
        expect(TrialFailure.INVALID_RESPONSE) {
            HttpsTrialHttp {
                FakeHttps().apply { status = 302 }
            }.execute(TrialHttpRequest("$TRIAL_RESOURCE/models"))
        }
        expect(TrialFailure.TIMEOUT) {
            HttpsTrialHttp {
                FakeHttps().apply {
                    failure =
                        SocketTimeoutException("SECRET")
                }
            }.execute(TrialHttpRequest("$TRIAL_RESOURCE/models"))
        }
        expect(TrialFailure.NETWORK) {
            HttpsTrialHttp {
                FakeHttps().apply {
                    failure =
                        IOException("SECRET")
                }
            }.execute(TrialHttpRequest("$TRIAL_RESOURCE/models"))
        }
        val rejected = HttpsTrialHttp {
            FakeHttps().apply {
                status = 403
                reply = "denied"
            }
        }
        assertEquals(
            TrialHttpResponse(403, "denied"),
            rejected.execute(TrialHttpRequest("$TRIAL_RESOURCE/models"))
        )
        assertEquals("$TRIAL_AUTH_ORIGIN/jwks", trustedTrialAuthUrl("$TRIAL_AUTH_ORIGIN/jwks"))
        listOf(
            "https://auth.openai.com.evil/jwks",
            "https://user@auth.openai.com/jwks",
            "https://auth.openai.com:443/jwks",
            "https://auth.openai.com/jwks#fragment",
            "not a url"
        ).forEach {
            assertThrows(ChatGptTrialException::class.java) { trustedTrialAuthUrl(it) }
        }
    }

    @Test fun loopbackValidatesHostMethodAndBoundedHeadersBeforeReadingTheCode() {
        fun request(text: String) = readTrialRequest(ByteArrayInputStream(text.toByteArray()), 1234)
        assertEquals(
            "/auth/callback?state=s",
            request("GET /auth/callback?state=s HTTP/1.1\r\nHost: 127.0.0.1:1234\r\n\r\n")
        )
        assertEquals(null, request("POST /auth/callback HTTP/1.1\r\nHost: 127.0.0.1:1234\r\n\r\n"))
        assertEquals(null, request("GET /auth/callback HTTP/1.1\r\nHost: evil.test\r\n\r\n"))
        assertEquals(
            null,
            request(
                "GET /auth/callback HTTP/1.1\r\nHost: 127.0.0.1:1234\r\n" +
                    "Host: 127.0.0.1:1234\r\n\r\n"
            )
        )
        assertEquals(null, request("GET " + "x".repeat(9000)))
        assertEquals(null, request(""))
    }

    @Test fun loopbackRejectsWrongStateAndDoesNotEchoTheCode() = runBlocking {
        val listener = TrialLoopback()
        val result = async(Dispatchers.IO) { listener.await("correct", null) }
        val port = URI(listener.redirectUri).port
        fun send(state: String): String = Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 5000
            socket.getOutputStream().write(
                (
                    "GET /auth/callback?state=$state&code=secret-code&client_id=client " +
                        "HTTP/1.1\r\n" +
                        "Host: 127.0.0.1:$port\r\n\r\n"
                    ).toByteArray()
            )
            socket.getOutputStream().flush()
            socket.getInputStream().readBytes().toString(Charsets.UTF_8)
        }
        val ignored = send("wrong")
        assertTrue(ignored.startsWith("HTTP/1.1 400"))
        val accepted = send("correct")
        assertTrue(accepted.startsWith("HTTP/1.1 200"))
        assertTrue(accepted.contains("Cache-Control: no-store"))
        assertFalse(accepted.contains("secret-code"))
        assertEquals(TrialAuthCode("secret-code", "client"), result.await())
        listener.close()
    }

    @Test fun loopbackReleasesPortAndRegistrationStoresNoTokens() = runBlocking {
        var clock = 0L
        val timed = TrialLoopback(timeoutMillis = 5) { clock.also { clock += 10 } }
        expect(TrialFailure.TIMEOUT) { timed.await("s", null) }
        val cancelled = TrialLoopback()
        cancelled.close()
        expect(TrialFailure.CANCELLED) { cancelled.await("s", null) }
        val context = RuntimeEnvironment.getApplication()
        val first = AndroidTrialRegistration(context)
        first.issuedClientId = "client"
        val second = AndroidTrialRegistration(context)
        assertEquals(first.hostId, second.hostId)
        assertTrue(second.hostId.startsWith("urn:uuid:"))
        assertEquals("client", second.issuedClientId)
        assertEquals(
            setOf("host", "client"),
            context.getSharedPreferences("chatgpt-trial-registration", 0).all.keys
        )
        createChatGptTrialGateway(context) { }.close()
    }

    private suspend fun expect(failure: TrialFailure, action: suspend () -> Unit) {
        try {
            action()
            throw AssertionError("Expected $failure")
        } catch (
            error: ChatGptTrialException
        ) {
            assertEquals(failure, error.kind)
            assertFalse(error.message!!.contains("SECRET"))
        }
    }

    private class FakeHttps : HttpsURLConnection(URL("https://api.openai.com")) {
        var status = 200
        var reply = "reply"
        var failure: IOException? = null
        var disconnected = false
        val sent = ByteArrayOutputStream()
        override fun getResponseCode(): Int {
            failure?.let { throw it }
            return status
        }
        override fun getInputStream(): InputStream = ByteArrayInputStream(reply.toByteArray())
        override fun getErrorStream(): InputStream = inputStream
        override fun getOutputStream() = sent
        override fun disconnect() {
            disconnected = true
        }
        override fun usingProxy() = false
        override fun connect() = Unit
        override fun getCipherSuite() = "TLS_TEST"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
}
