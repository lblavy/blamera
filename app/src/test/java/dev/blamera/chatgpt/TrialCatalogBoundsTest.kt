package dev.blamera.chatgpt

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.security.cert.Certificate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrialCatalogBoundsTest {
    @Test fun deadlineWinsWhenTheCatalogCrossesItsByteCap() = runBlocking {
        lateinit var expire: () -> Unit
        var disarmed = false
        val alarm = TrialTimeoutScheduler { _, action ->
            expire = action
            AutoCloseable { disarmed = true }
        }
        val input = GeneratedCatalogInput(CATALOG_CAP + 1) { consumed ->
            if (consumed > CATALOG_CAP) expire()
        }
        val connection = CatalogHttps(input)
        val events = mutableListOf<TrialHttpDiagnostic>()
        val http = HttpsTrialHttp(scheduler = alarm, diagnostics = events::add) { connection }
        try {
            try {
                http.execute(catalogRequest())
                throw AssertionError("Deadline must interrupt the catalogue")
            } catch (error: ChatGptTrialException) {
                assertEquals(TrialFailure.TIMEOUT, error.kind)
            }
            assertEquals(CATALOG_CAP + 1, input.consumed)
            assertTrue(input.closed)
            assertTrue(connection.disconnected)
            assertTrue(disarmed)
            assertEquals(
                listOf(TrialHttpCategory.TIMEOUT),
                events.filter {
                    it.category != TrialHttpCategory.HTTP_STATUS
                }.map { it.category }
            )
        } finally {
            http.close()
        }
    }

    @Test fun cancellationAndCloseReleaseALargeCatalogReadWithoutRetry() = runBlocking {
        for (cancelJob in listOf(true, false)) {
            val reading = CountDownLatch(1)
            val closed = CountDownLatch(1)
            val input = object : InputStream() {
                override fun read(): Int {
                    reading.countDown()
                    check(closed.await(5, TimeUnit.SECONDS))
                    throw IOException("synthetic fixture closed")
                }
                override fun close() {
                    closed.countDown()
                }
            }
            val connection = CatalogHttps(input)
            val events = CopyOnWriteArrayList<TrialHttpDiagnostic>()
            var opens = 0
            val http = HttpsTrialHttp(diagnostics = events::add) {
                opens++
                connection
            }
            val request = async(Dispatchers.IO) {
                try {
                    http.execute(catalogRequest())
                    throw AssertionError("Read must be interrupted")
                } catch (error: ChatGptTrialException) {
                    error.kind
                }
            }
            try {
                assertTrue(reading.await(3, TimeUnit.SECONDS))
                if (cancelJob) {
                    request.cancelAndJoin()
                    assertTrue(request.isCancelled)
                    assertEquals(
                        listOf(TrialHttpCategory.CANCELLED),
                        events.filter {
                            it.category != TrialHttpCategory.HTTP_STATUS
                        }.map { it.category }
                    )
                } else {
                    http.close()
                    assertEquals(TrialFailure.NETWORK, request.await())
                }
                assertEquals(0L, closed.count)
                assertTrue(connection.disconnected)
                assertEquals(1, opens)
            } finally {
                http.close()
                request.cancelAndJoin()
            }
        }
    }

    @Test fun effectivelyUnendingCatalogStopsWithinOneReadBufferOfTheCap() = runBlocking {
        val input = GeneratedCatalogInput(Int.MAX_VALUE)
        val connection = CatalogHttps(input)
        val http = HttpsTrialHttp { connection }
        try {
            failure { http.execute(catalogRequest()) }
            assertTrue(input.consumed > CATALOG_CAP)
            assertTrue(input.consumed <= CATALOG_CAP + 4096)
            assertTrue(input.closed)
            assertTrue(connection.disconnected)
        } finally {
            http.close()
        }
    }

    @Test fun exactEightMiBCatalogIsAcceptedAndItsStreamIsClosed() = runBlocking {
        val input = GeneratedCatalogInput(CATALOG_CAP)
        val connection = CatalogHttps(input)
        val http = HttpsTrialHttp { connection }
        val result = http.execute(catalogRequest())
        assertEquals(CATALOG_CAP, result.body.length)
        assertEquals(CATALOG_CAP, input.consumed)
        assertTrue(input.closed)
        assertTrue(connection.disconnected)
        assertFalse(connection.instanceFollowRedirects)
        http.close()
    }

    @Test fun oneByteBeyondCatalogCapStopsReadingAndReportsOnlyBodyLimit() = runBlocking {
        val input = GeneratedCatalogInput(CATALOG_CAP + 1)
        val connection = CatalogHttps(input)
        val events = mutableListOf<TrialHttpDiagnostic>()
        var opens = 0
        val http = HttpsTrialHttp(diagnostics = events::add) {
            opens++
            connection
        }
        try {
            failure { http.execute(catalogRequest()) }
            assertEquals(CATALOG_CAP + 1, input.consumed)
            assertEquals(
                listOf("BODY_LIMIT"),
                events.filter {
                    it.category != TrialHttpCategory.HTTP_STATUS
                }.map { it.category.name }
            )
            assertEquals(TrialHttpStage.READ_BODY, events.last().stage)
            assertEquals(200, events.last().status)
            assertEquals(TrialHttpEndpoint.MODELS, events.last().endpoint)
            assertEquals(1, opens)
            assertTrue(input.closed)
            assertTrue(connection.disconnected)
        } finally {
            http.close()
        }
    }

    @Test fun largerBudgetIsRejectedForOtherRoutesAndMethodsBeforeOpening() = runBlocking {
        var opens = 0
        val http = HttpsTrialHttp {
            opens++
            CatalogHttps(GeneratedCatalogInput(1))
        }
        val requests = listOf(
            TrialHttpRequest("$TRIAL_RESOURCE/responses", "POST", maxBytes = CATALOG_CAP),
            TrialHttpRequest("$TRIAL_RESOURCE/responses", maxBytes = CATALOG_CAP),
            TrialHttpRequest("$TRIAL_RESOURCE/models", "POST", maxBytes = CATALOG_CAP),
            TrialHttpRequest("$TRIAL_AUTH_ORIGIN/jwks", maxBytes = CATALOG_CAP),
            TrialHttpRequest("$TRIAL_RESOURCE/models?extra=1", maxBytes = CATALOG_CAP),
            TrialHttpRequest("$TRIAL_RESOURCE/models/", maxBytes = CATALOG_CAP),
            TrialHttpRequest("$TRIAL_RESOURCE/models", maxBytes = CATALOG_CAP + 1)
        )
        requests.forEach { request -> failure { http.execute(request) } }
        assertEquals(0, opens)
        http.close()
    }

    @Test fun inferenceAndErrorBodiesKeepTheirTwoMiBAnd64KiBBounds() = runBlocking {
        val cases = listOf(
            Triple(TrialHttpRequest("$TRIAL_RESOURCE/responses"), 200, 2 * 1024 * 1024),
            Triple(catalogRequest(), 503, 64 * 1024)
        )
        cases.forEach { (request, status, cap) ->
            val exact = GeneratedCatalogInput(cap)
            val success = HttpsTrialHttp { CatalogHttps(exact, status) }
            assertEquals(cap, success.execute(request).body.length)
            success.close()
            val oversized = GeneratedCatalogInput(cap + 1)
            val rejected = HttpsTrialHttp { CatalogHttps(oversized, status) }
            failure { rejected.execute(request) }
            assertEquals(cap + 1, oversized.consumed)
            assertTrue(oversized.closed)
            rejected.close()
        }
    }

    private suspend fun failure(action: suspend () -> Unit) {
        try {
            action()
            throw AssertionError("Oversized response must be rejected")
        } catch (error: ChatGptTrialException) {
            assertEquals(TrialFailure.INVALID_RESPONSE, error.kind)
        }
    }

    private fun catalogRequest() =
        TrialHttpRequest("$TRIAL_RESOURCE/models", maxBytes = CATALOG_CAP)

    private companion object {
        const val CATALOG_CAP = 8 * 1024 * 1024
    }
}

internal class GeneratedCatalogInput(
    private val length: Int,
    private val afterRead: (Int) -> Unit = { }
) : InputStream() {
    var consumed = 0
        private set
    var closed = false
        private set
    override fun read(): Int {
        if (closed) throw IOException("fixture closed")
        return if (consumed < length) {
            consumed++
            'x'.code
        } else {
            -1
        }
    }
    override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        if (closed) throw IOException("fixture closed")
        val amount = minOf(count, length - consumed)
        if (amount == 0) return -1
        buffer.fill('x'.code.toByte(), offset, offset + amount)
        consumed += amount
        afterRead(consumed)
        return amount
    }
    override fun close() {
        closed = true
    }
}

internal class CatalogHttps(private val input: InputStream, private val status: Int = 200) :
    HttpsURLConnection(URL("$TRIAL_RESOURCE/models")) {
    var disconnected = false
        private set
    override fun getResponseCode() = status
    override fun getInputStream() = input
    override fun getErrorStream() = input
    override fun getOutputStream() = ByteArrayOutputStream()
    override fun disconnect() {
        disconnected = true
        input.close()
    }
    override fun usingProxy() = false
    override fun connect() = Unit
    override fun getCipherSuite() = "TLS_TEST"
    override fun getLocalCertificates(): Array<Certificate>? = null
    override fun getServerCertificates(): Array<Certificate> = emptyArray()
}
