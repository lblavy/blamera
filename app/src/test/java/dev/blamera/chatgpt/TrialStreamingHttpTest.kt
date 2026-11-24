package dev.blamera.chatgpt

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import java.security.cert.Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrialStreamingHttpTest {
    @Test fun cancellingABlockedReadClosesTheConnectionAndPreventsLaterChunks() = runBlocking {
        val source = PausedStreamingInput("first".toByteArray(), "late".toByteArray())
        val connection = StreamingHttps(source)
        val http = HttpsTrialHttp { connection }
        val chunks = mutableListOf<String>()
        val first = CompletableDeferred<Unit>()
        val request = async {
            http.executeStreaming(TrialHttpRequest("$TRIAL_RESOURCE/responses", "POST", "{}")) {
                chunks.add(it.toString(Charsets.UTF_8))
                first.complete(Unit)
            }
        }
        try {
            withTimeout(5_000L) { first.await() }
            assertTrue(withContext(Dispatchers.IO) { source.waiting.await(5, TimeUnit.SECONDS) })
            withTimeout(3_000L) { request.cancelAndJoin() }
            assertTrue(source.closed)
            assertTrue(connection.disconnected)
            assertEquals(listOf("first"), chunks)
        } finally {
            http.close()
            request.cancel()
        }
    }

    @Test fun absoluteDeadlineClosesABlockedStreamWithOneTimeoutDiagnostic() = runBlocking {
        val source = PausedStreamingInput("first".toByteArray())
        val connection = StreamingHttps(source)
        var expire: (() -> Unit)? = null
        val events = java.util.concurrent.CopyOnWriteArrayList<TrialHttpDiagnostic>()
        val http =
            HttpsTrialHttp(
                timeoutMillis = 100L,
                scheduler = TrialTimeoutScheduler {
                        millis,
                        action
                    ->
                    assertEquals(100L, millis)
                    expire = action
                    AutoCloseable { }
                },
                diagnostics = events::add
            ) { connection }
        val result = async {
            runCatching {
                http.executeStreaming(TrialHttpRequest("$TRIAL_RESOURCE/responses", "POST", "{}")) {
                }
            }
        }
        try {
            assertTrue(withContext(Dispatchers.IO) { source.waiting.await(5, TimeUnit.SECONDS) })
            checkNotNull(expire).invoke()
            val failure =
                withTimeout(3_000L) { result.await().exceptionOrNull() as ChatGptTrialException }
            assertEquals(TrialFailure.TIMEOUT, failure.kind)
            assertTrue(source.closed)
            assertTrue(connection.disconnected)
            assertEquals(
                listOf(TrialHttpCategory.TIMEOUT),
                events.filter {
                    it.category != TrialHttpCategory.HTTP_STATUS
                }.map { it.category }
            )
        } finally {
            http.close()
            result.cancel()
        }
    }

    @Test fun errorBodiesAreNotDeliveredAsStreamTextAndRemainClassified() = runBlocking {
        val source = java.io.ByteArrayInputStream("{\"error\":\"SECRET\"}".toByteArray())
        val http = HttpsTrialHttp { StreamingHttps(source, 429) }
        var callback = false
        try {
            TrialAccountApi(http) { 100L }.request(
                TrialHttpRequest("$TRIAL_RESOURCE/responses", "POST", "{}")
            ) { callback = true }
            throw AssertionError("Expected quota failure")
        } catch (failure: ChatGptTrialException) {
            assertEquals(TrialFailure.USAGE_LIMIT, failure.kind)
            assertEquals(false, callback)
        } finally {
            http.close()
        }
    }

    @Test fun actualHttpAdapterPublishesTheFirstChunkBeforeEof() = runBlocking {
        val source = PausedStreamingInput("data: first\n\n".toByteArray())
        val connection = StreamingHttps(source)
        val http = HttpsTrialHttp { connection }
        val first = CompletableDeferred<String>()
        val request = async(Dispatchers.Default) {
            http.executeStreaming(
                TrialHttpRequest("$TRIAL_RESOURCE/responses", "POST", "{}", "synthetic")
            ) {
                first.complete(it.toString(Charsets.UTF_8))
            }
        }
        try {
            assertTrue(withContext(Dispatchers.IO) { source.waiting.await(5, TimeUnit.SECONDS) })
            val received = withTimeoutOrNull(2_000L) { first.await() }
            assertNotNull(
                "A chunk must be observable while the underlying stream still awaits EOF",
                received
            )
            assertEquals("data: first\n\n", received)
            source.release.countDown()
            assertEquals(200, request.await().status)
            assertTrue(connection.disconnected)
        } finally {
            source.release.countDown()
            request.cancel()
            http.close()
        }
    }
}

internal class PausedStreamingInput(
    private val first: ByteArray,
    private val tail: ByteArray = byteArrayOf()
) : InputStream() {
    val waiting = CountDownLatch(1)
    val release = CountDownLatch(1)
    private var delivered = false
    private var tailDelivered = false
    var closed = false
    override fun read(): Int = error("Use buffered reads")
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!delivered) {
            check(first.size <= length)
            first.copyInto(buffer, offset)
            delivered = true
            return first.size
        }
        waiting.countDown()
        check(release.await(10, TimeUnit.SECONDS))
        if (!closed && !tailDelivered && tail.isNotEmpty()) {
            check(tail.size <= length)
            tail.copyInto(buffer, offset)
            tailDelivered = true
            return tail.size
        }
        return -1
    }
    override fun close() {
        closed = true
        release.countDown()
    }
}

internal class StreamingHttps(private val input: InputStream, private val status: Int = 200) :
    HttpsURLConnection(URL(TRIAL_RESOURCE)) {
    var disconnected = false
    val output = ByteArrayOutputStream()
    override fun getInputStream() = input
    override fun getResponseCode() = status
    override fun getErrorStream(): InputStream? = if (status >= 400) input else null
    override fun getOutputStream() = output
    override fun disconnect() {
        disconnected = true
        input.close()
    }
    override fun usingProxy() = false
    override fun connect() = Unit
    override fun getCipherSuite() = "TLS_FIXTURE"
    override fun getLocalCertificates(): Array<Certificate>? = null
    override fun getServerCertificates(): Array<Certificate> = emptyArray()
}
