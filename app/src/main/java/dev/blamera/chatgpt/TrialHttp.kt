package dev.blamera.chatgpt

import java.net.HttpURLConnection

internal class TrialHttpRequest(
    val url: String,
    val method: String = "GET",
    val body: String? = null,
    val bearer: String? = null,
    val contentType: String = "application/json",
    val maxBytes: Int = TrialLimits.RESPONSE_BYTES
)

internal data class TrialHttpResponse(val status: Int, val body: String)

internal interface TrialHttp : AutoCloseable {
    suspend fun execute(request: TrialHttpRequest): TrialHttpResponse
    suspend fun executeStreaming(
        request: TrialHttpRequest,
        onChunk: suspend (ByteArray) -> Unit
    ): TrialHttpResponse {
        val response = execute(request)
        if (response.status in HttpURLConnection.HTTP_OK until HttpURLConnection.HTTP_MULT_CHOICE) {
            onChunk(response.body.toByteArray(Charsets.UTF_8))
        }
        return response
    }
    fun cancel()
}

internal interface TrialCallbackListener : AutoCloseable {
    val redirectUri: String
    suspend fun await(state: String, issuedClientId: String?): TrialAuthCode
}

internal interface TrialRegistrationStore {
    val hostId: String
    var issuedClientId: String?
    var sessionBlocked: Boolean
        get() = false
        set(_) { }
}
