package dev.blamera.botany

import java.net.HttpURLConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class PlantIdentifier(
    private val http: PlantHttp,
    private val quota: PlantQuota = PlantQuota(),
    private val quotaDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AutoCloseable {
    suspend fun identify(key: String, jpeg: ByteArray): PlantIdentification? {
        withContext(quotaDispatcher) { quota.claim() }
        val response = http.identify(key, jpeg)
        if (response.remaining == 0 || response.status == HTTP_QUOTA) {
            withContext(quotaDispatcher) { quota.exhausted() }
        }
        if (response.status == HttpURLConnection.HTTP_NOT_FOUND) return null
        val failure = plantStatusFailure(response.status)
        if (failure != null) throw PlantException(failure)
        plantRequire(response.status == HttpURLConnection.HTTP_OK)
        val parsed = parsePlantIdentification(response.body)
        if (parsed.remaining == 0) withContext(quotaDispatcher) { quota.exhausted() }
        return parsed
    }

    override fun close() {
        (http as? AutoCloseable)?.close()
    }
}

private fun plantStatusFailure(status: Int): PlantFailure? = when (status) {
    HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> PlantFailure.KEY
    HTTP_QUOTA -> PlantFailure.QUOTA
    in HttpURLConnection.HTTP_INTERNAL_ERROR..LAST_SERVER_ERROR -> PlantFailure.SERVICE
    else -> null
}

private const val LAST_SERVER_ERROR = 599

private const val HTTP_QUOTA = 429
