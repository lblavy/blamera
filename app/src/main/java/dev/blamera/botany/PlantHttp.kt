package dev.blamera.botany

import dev.blamera.chatgpt.SystemTrialTimeoutScheduler
import dev.blamera.chatgpt.TrialTimeoutScheduler
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class PlantHttpResponse(val status: Int, val body: String, val remaining: Int? = null)
internal fun interface PlantHttp {
    suspend fun identify(key: String, jpeg: ByteArray): PlantHttpResponse
}

internal class PlantNetHttp(
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val timeoutMillis: Long = PLANT_TIMEOUT,
    private val scheduler: TrialTimeoutScheduler = SystemTrialTimeoutScheduler
) : PlantHttp,
    AutoCloseable {
    private val connections = ConcurrentHashMap.newKeySet<HttpURLConnection>()
    override suspend fun identify(key: String, jpeg: ByteArray): PlantHttpResponse =
        withContext(Dispatchers.IO) {
            plantRequire(validPlantKey(key) && jpeg.isNotEmpty() && jpeg.size <= MAX_PLANT_JPEG)
            var connection: HttpURLConnection? = null
            try {
                val url =
                    URL(
                        PLANT_ENDPOINT + "?api-key=" + URLEncoder.encode(key, "UTF-8") + PLANT_QUERY
                    )
                val opened = open(url)
                connection = opened
                connections.add(opened)
                request(opened, jpeg)
            } catch (_: SocketTimeoutException) {
                currentCoroutineContext().ensureActive()
                throw PlantException(PlantFailure.TIMEOUT)
            } catch (_: IOException) {
                currentCoroutineContext().ensureActive()
                throw PlantException(PlantFailure.NETWORK)
            } catch (_: SecurityException) {
                throw PlantException(PlantFailure.NETWORK)
            } finally {
                connection?.let {
                    connections.remove(it)
                    it.disconnect()
                }
            }
        }

    private suspend fun request(connection: HttpURLConnection, jpeg: ByteArray): PlantHttpResponse =
        coroutineScope {
            val expired = AtomicBoolean(false)
            val alarm = scheduler.schedule(timeoutMillis) {
                expired.set(true)
                connection.disconnect()
            }
            val parent = currentCoroutineContext().job
            val watch = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    if (!parent.isActive) connection.disconnect()
                }
            }
            try {
                val check = { if (expired.get()) throw PlantException(PlantFailure.TIMEOUT) }
                try {
                    currentCoroutineContext().ensureActive()
                    check()
                    val response = exchange(connection, jpeg, check)
                    check()
                    response
                } catch (failure: IOException) {
                    check()
                    throw failure
                }
            } finally {
                watch.cancel()
                alarm.close()
            }
        }

    private fun exchange(
        connection: HttpURLConnection,
        jpeg: ByteArray,
        check: () -> Unit
    ): PlantHttpResponse {
        val boundary = "blamera-" + java.util.UUID.randomUUID()
        val prefix = (
            "--$boundary\r\nContent-Disposition: form-data; name=\"organs\"\r\n\r\nauto\r\n" +
                "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"images\"; filename=\"image.jpg\"\r\n" +
                "Content-Type: image/jpeg\r\n\r\n"
            ).toByteArray(Charsets.UTF_8)
        val suffix = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        connection.instanceFollowRedirects = false
        connection.connectTimeout = timeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        connection.readTimeout = connection.connectTimeout
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.setRequestProperty("Accept", "application/json")
        connection.setFixedLengthStreamingMode(prefix.size + jpeg.size + suffix.size)
        connection.outputStream.use {
            it.write(prefix)
            it.write(jpeg)
            it.write(suffix)
        }
        check()
        val status = connection.responseCode
        plantRequire(
            status !in HttpURLConnection.HTTP_MULT_CHOICE until HttpURLConnection.HTTP_BAD_REQUEST
        )
        val successful = successfulPlantStatus(status)
        val input = if (successful) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        val maximum = if (successfulPlantStatus(status)) PLANT_RESPONSE_BYTES else PLANT_ERROR_BYTES
        val body = input?.use { stream ->
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(READ_BYTES)
            var count = stream.read(buffer)
            while (count != -1) {
                check()
                plantRequire(bytes.size().toLong() + count <= maximum)
                bytes.write(buffer, 0, count)
                count = stream.read(buffer)
            }
            bytes.toString("UTF-8")
        }.orEmpty()
        val remaining = Regex("(?:^|;)\\s*r=(\\d+)(?:;|$)")
            .find(
                connection.getHeaderField("RateLimit").orEmpty()
            )?.groupValues?.get(1)?.toIntOrNull()
        return PlantHttpResponse(status, body, remaining)
    }
    override fun close() {
        connections.forEach(HttpURLConnection::disconnect)
    }
}

internal fun validPlantKey(value: String) = value.length in 1..MAX_KEY_LENGTH &&
    value.all { it.isLetterOrDigit() || it in "_-" }
internal const val MAX_PLANT_JPEG = 4 * 1024 * 1024
internal const val PLANT_RESPONSE_BYTES = 1024 * 1024
internal const val PLANT_ERROR_BYTES = 64 * 1024
private const val PLANT_TIMEOUT = 60_000L
private const val PLANT_ENDPOINT = "https://my-api.plantnet.org/v2/identify/all"
private const val PLANT_QUERY = "&lang=fr&nb-results=3&no-reject=false&include-related-images=false"

private fun successfulPlantStatus(status: Int) =
    status in HttpURLConnection.HTTP_OK until HttpURLConnection.HTTP_MULT_CHOICE
private const val READ_BYTES = 4096
private const val MAX_KEY_LENGTH = 512
