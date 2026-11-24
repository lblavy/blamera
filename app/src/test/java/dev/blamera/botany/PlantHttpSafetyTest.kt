package dev.blamera.botany

import dev.blamera.chatgpt.TrialTimeoutScheduler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlantHttpSafetyTest {
    @Test
    fun boundariesAreExactAndRedirectsNeverForwardThePrivateKey() = runBlocking {
        for ((status, limit) in listOf(200 to PLANT_RESPONSE_BYTES, 401 to PLANT_ERROR_BYTES)) {
            val exact = FakePlantConnection(status, ByteArray(limit) { 120 })
            assertEquals(
                limit,
                PlantNetHttp(open = {
                    exact
                }).identify("FAKE", byteArrayOf(1)).body.length
            )
            assertTrue(exact.disconnected)
            assertFalse(exact.instanceFollowRedirects)
            val over = FakePlantConnection(status, ByteArray(limit + 1))
            assertEquals(
                PlantFailure.INVALID,
                failure {
                    PlantNetHttp(open = { over }).identify("FAKE", byteArrayOf(1))
                }
            )
            assertTrue(over.disconnected)
        }
        val redirect = FakePlantConnection(302, byteArrayOf())
        var opened = 0
        assertEquals(
            PlantFailure.INVALID,
            failure {
                PlantNetHttp(open = {
                    opened++
                    redirect
                }).identify("FAKE", byteArrayOf(1))
            }
        )
        assertEquals(1, opened)
        assertTrue(redirect.disconnected)
    }

    @Test
    fun invalidInputsAndNetworkFailuresUseOnlyFixedMessages() = runBlocking {
        for ((key, jpeg) in listOf(
            "" to byteArrayOf(1),
            "bad\nkey" to byteArrayOf(1),
            "x".repeat(513) to byteArrayOf(1),
            "FAKE" to byteArrayOf(),
            "FAKE" to ByteArray(MAX_PLANT_JPEG + 1)
        )) {
            var opened = false
            assertEquals(
                PlantFailure.INVALID,
                failure {
                    PlantNetHttp(open = {
                        opened = true
                        error("must not open")
                    }).identify(key, jpeg)
                }
            )
            assertFalse(opened)
        }
        for ((exception, expected) in listOf(
            IOException("URL-SECRET") to PlantFailure.NETWORK,
            SocketTimeoutException("URL-SECRET") to PlantFailure.TIMEOUT,
            SecurityException("URL-SECRET") to PlantFailure.NETWORK
        )) {
            val error = runCatching {
                PlantNetHttp(open = { throw exception }).identify("FAKE", byteArrayOf(1))
            }.exceptionOrNull()
            assertEquals(expected, (error as PlantException).failure)
            assertNull(error.cause)
            assertFalse(error.toString().contains("SECRET"))
        }
    }

    @Test
    fun absoluteDeadlineDisconnectsABlockedReadWithoutRetry() = runBlocking {
        val input = BlockingPlantInput()
        val connection = FakePlantConnection(200, byteArrayOf(), input)
        lateinit var expire: () -> Unit
        var alarmClosed = false
        val client =
            PlantNetHttp(
                open = { connection },
                scheduler = TrialTimeoutScheduler { _, action ->
                    expire = action
                    AutoCloseable { alarmClosed = true }
                }
            )
        val task =
            async(Dispatchers.Default) { failure { client.identify("FAKE", byteArrayOf(1)) } }
        assertTrue(withContext(Dispatchers.IO) { input.entered.await(5, TimeUnit.SECONDS) })
        expire()
        assertEquals(PlantFailure.TIMEOUT, task.await())
        assertTrue(connection.disconnected)
        assertTrue(alarmClosed)
        client.close()
    }

    @Test
    fun humanCancellationClosesReadAndDoesNotProduceAServiceFailure() = runBlocking {
        val input = BlockingPlantInput()
        val connection = FakePlantConnection(200, byteArrayOf(), input)
        val task =
            async(Dispatchers.Default) {
                PlantNetHttp(open = { connection }).identify("FAKE", byteArrayOf(1))
            }
        assertTrue(withContext(Dispatchers.IO) { input.entered.await(5, TimeUnit.SECONDS) })
        task.cancelAndJoin()
        assertTrue(task.isCancelled)
        assertTrue(connection.disconnected)
        assertTrue(input.closed)
    }

    private suspend fun failure(action: suspend () -> Any?): PlantFailure? =
        (runCatching { action() }.exceptionOrNull() as? PlantException)?.failure
}

private class FakePlantConnection(
    private val code: Int,
    private val body: ByteArray,
    private val stream: InputStream = ByteArrayInputStream(body)
) : HttpURLConnection(URL("https://my-api.plantnet.org/")) {
    var disconnected = false
    override fun connect() = Unit
    override fun usingProxy() = false
    override fun disconnect() {
        disconnected = true
        stream.close()
    }
    override fun getOutputStream() = ByteArrayOutputStream()
    override fun getResponseCode() = code
    override fun getInputStream() = stream
    override fun getErrorStream() = stream
    override fun getHeaderField(name: String): String? = null
}

private class BlockingPlantInput : InputStream() {
    val entered = CountDownLatch(1)
    private val release = CountDownLatch(1)

    @Volatile var closed = false
    override fun read(): Int {
        entered.countDown()
        release.await()
        throw IOException("closed synthetic stream")
    }
    override fun read(bytes: ByteArray, offset: Int, length: Int) = read()
    override fun close() {
        closed = true
        release.countDown()
    }
}
