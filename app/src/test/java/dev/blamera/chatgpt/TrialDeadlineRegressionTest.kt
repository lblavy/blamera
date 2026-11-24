package dev.blamera.chatgpt

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.net.URI
import java.net.URL
import java.security.cert.Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrialDeadlineRegressionTest {
    @Test fun cancelledIoReturnCannotAbandonTheNewlyCreatedListener() = runBlocking {
        val created = AtomicReference<RecordingListener>()
        val acquired = CountDownLatch(1)
        val release = CountDownLatch(1)
        val gateway = OpenAiTrialGateway(Registration(), NoHttp(), {
            RecordingListener().also {
                created.set(it)
                acquired.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        })
        var opened = false
        val attempt = launch(Dispatchers.Default) { gateway.connect { opened = true } }
        try {
            assertTrue(acquired.await(5, TimeUnit.SECONDS))
            attempt.cancel()
            gateway.close()
        } finally {
            release.countDown()
            attempt.join()
            gateway.close()
        }
        assertTrue(
            "Acquired callback must be closed even when withContext discards its result",
            created.get().closed.get()
        )
        assertFalse(opened)
    }

    @Test fun continuouslyArrivingHttpBytesStillExpireAndCloseWithoutRetry() = runBlocking {
        val alarm = ManualTimeout()
        val input = SlowInput("data: a stream that keeps arriving".toByteArray(), alarm, 3, true)
        val connection = RecordingHttps(input)
        var requests = 0
        val events = mutableListOf<TrialHttpDiagnostic>()
        val http =
            HttpsTrialHttp(timeoutMillis = 10, scheduler = alarm, diagnostics = events::add) {
                requests++
                connection
            }
        try {
            val failure = try {
                http.execute(TrialHttpRequest("$TRIAL_RESOURCE/responses"))
                throw AssertionError("An active stream must stop at its absolute deadline")
            } catch (error: ChatGptTrialException) {
                error
            }
            assertEquals(TrialFailure.TIMEOUT, failure.kind)
            assertTrue(connection.disconnected)
            assertTrue(input.closed)
            assertEquals(1, requests)
            assertTrue(alarm.cancelled)
            assertEquals(TrialHttpCategory.TIMEOUT, events.last().category)
            assertEquals(TrialHttpStage.READ_BODY, events.last().stage)
        } finally {
            http.close()
        }
    }

    @Test fun partialCallbackFedByteByByteCannotResetItsAbsoluteDeadline() {
        val alarm = ManualTimeout()
        val input = SlowInput(
            (
                "GET /auth/callback HTTP/1.1\r\nHost: 127.0.0.1:1234\r\n" +
                    "X-Partial: still arriving"
                ).toByteArray(),
            alarm,
            35
        )
        TrialDeadline(10, alarm, input::close).use { deadline ->
            val failure = assertThrows(ChatGptTrialException::class.java) {
                readTrialRequest(input, 1234, deadline::check)
            }
            assertEquals(TrialFailure.TIMEOUT, failure.kind)
            assertTrue(input.closed)
        }
        assertTrue(alarm.cancelled)
    }

    @Test fun watchdogClosesTheRealPcLoopbackWhileAPartialRequestIsOpen() = runBlocking {
        val alarm = ManualTimeout()
        val listener = TrialLoopback(timeoutMillis = 10_000, scheduler = alarm)
        val result = async(Dispatchers.IO) {
            try {
                listener.await("state", null)
                null
            } catch (
                error: ChatGptTrialException
            ) {
                error.kind
            }
        }
        try {
            assertTrue(
                "An absolute callback deadline must be armed",
                alarm.armed.await(2, TimeUnit.SECONDS)
            )
            val port = URI(listener.redirectUri).port
            Socket("127.0.0.1", port).use { socket ->
                socket.getOutputStream().write(
                    "GET /auth/callback HTTP/1.1\r\nHost: ".toByteArray()
                )
                socket.getOutputStream().flush()
                alarm.fire()
                assertEquals(TrialFailure.TIMEOUT, result.await())
                assertThrows(IOException::class.java) { Socket("127.0.0.1", port).close() }
            }
            assertTrue(alarm.cancelled)
        } finally {
            listener.close()
            result.cancel()
        }
    }

    private class ManualTimeout : TrialTimeoutScheduler {
        val armed = CountDownLatch(1)
        var cancelled = false

        @Volatile private var callback: (() -> Unit)? = null
        override fun schedule(timeoutMillis: Long, expired: () -> Unit): AutoCloseable {
            callback = expired
            armed.countDown()
            return AutoCloseable {
                cancelled = true
                callback = null
            }
        }
        fun fire() {
            callback?.invoke()
        }
    }

    private class SlowInput(
        private val bytes: ByteArray,
        private val alarm: ManualTimeout,
        private val expiresAt: Int,
        private val throwOnExpiry: Boolean = false
    ) : InputStream() {
        var closed = false
        private var position = 0
        override fun read(): Int {
            if (closed) throw IOException("closed")
            if (position >= bytes.size) return -1
            position++
            if (position == expiresAt) alarm.fire()
            if (throwOnExpiry && closed) throw IOException("closed during read")
            return bytes[position - 1].toInt() and 0xff
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val value = read()
            if (value == -1) return -1
            buffer[offset] = value.toByte()
            return 1
        }
        override fun close() {
            closed = true
        }
    }

    private class RecordingListener : TrialCallbackListener {
        val closed = AtomicBoolean(false)
        override val redirectUri = "http://127.0.0.1:1/auth/callback"
        override suspend fun await(state: String, issuedClientId: String?) =
            error("No callback expected")
        override fun close() {
            closed.set(true)
        }
    }

    private class Registration : TrialRegistrationStore {
        override val hostId = "urn:uuid:fixture"
        override var issuedClientId: String? = null
    }

    private class NoHttp : TrialHttp {
        override suspend fun execute(request: TrialHttpRequest): TrialHttpResponse {
            check(request.url == "$TRIAL_AUTH_ORIGIN/.well-known/openid-configuration")
            check(request.method == "GET" && request.body == null && request.bearer == null)
            return TrialHttpResponse(
                200,
                """{"issuer":"https://auth.openai.com",
                "jwks_uri":"https://auth.openai.com/.well-known/jwks.json"}"""
            )
        }
        override fun cancel() = Unit
        override fun close() = Unit
    }

    private class RecordingHttps(private val source: InputStream) :
        HttpsURLConnection(URL(TRIAL_RESOURCE)) {
        var disconnected = false
        override fun getResponseCode() = 200
        override fun getInputStream() = source
        override fun getErrorStream() = source
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun disconnect() {
            disconnected = true
            source.close()
        }
        override fun usingProxy() = false
        override fun connect() = Unit
        override fun getCipherSuite() = "TLS_TEST"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
}
