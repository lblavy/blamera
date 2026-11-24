package dev.blamera.chatgpt

import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun trustedTrialAuthUrl(value: String): String {
    val uri = try {
        URI(value)
    } catch (_: java.net.URISyntaxException) {
        throw ChatGptTrialException(TrialFailure.INVALID_RESPONSE)
    }
    val authority = uri.scheme == "https" && uri.host == "auth.openai.com" && uri.port == -1
    val resource =
        uri.userInfo == null && uri.fragment == null && uri.rawQuery == null &&
            uri.path.startsWith('/')
    requireTrial(authority && resource)
    return value
}

internal class HttpsTrialHttp(
    private val timeoutMillis: Long = TrialLimits.RESPONSE_TIMEOUT_MILLIS,
    private val scheduler: TrialTimeoutScheduler = SystemTrialTimeoutScheduler,
    private val diagnostics: (TrialHttpDiagnostic) -> Unit = { },
    private val open: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection }
) : TrialHttp {
    private val connections = ConcurrentHashMap.newKeySet<HttpsURLConnection>()

    override suspend fun execute(request: TrialHttpRequest): TrialHttpResponse =
        executeRequest(request, null)

    override suspend fun executeStreaming(
        request: TrialHttpRequest,
        onChunk: suspend (ByteArray) -> Unit
    ): TrialHttpResponse {
        requireTrial(request.url == "$TRIAL_RESOURCE/responses" && request.method == "POST")
        val caller = currentCoroutineContext().minusKey(Job)
        return executeRequest(request) { bytes -> withContext(caller) { onChunk(bytes) } }
    }

    private suspend fun executeRequest(
        request: TrialHttpRequest,
        onChunk: (suspend (ByteArray) -> Unit)?
    ): TrialHttpResponse = withContext(Dispatchers.IO) {
        validateTrialRequest(request)
        val trace = TrialHttpTrace(trialHttpEndpoint(request.url), diagnostics)
        val connection = openConnection(request.url, trace)
        connections.add(connection)
        val deadline = TrialDeadline(timeoutMillis, scheduler, connection::disconnect)
        try {
            coroutineScope {
                val cancellation = watchTrialCancellation(connection, deadline, trace)
                try {
                    currentCoroutineContext().ensureActive()
                    deadline.check()
                    val response = exchange(connection, request, deadline::check, trace, onChunk)
                    deadline.finish()
                    response
                } catch (failure: ChatGptTrialException) {
                    currentCoroutineContext().ensureActive()
                    trace.fail(trialFailureHttpCategory(failure.kind), deadline.expired)
                    throw deadline.failure(failure.kind)
                } catch (_: SocketTimeoutException) {
                    currentCoroutineContext().ensureActive()
                    trace.fail(TrialHttpCategory.TIMEOUT)
                    throw deadline.failure(TrialFailure.TIMEOUT)
                } catch (failure: IOException) {
                    currentCoroutineContext().ensureActive()
                    trace.fail(trialIoCategory(failure), deadline.expired)
                    throw deadline.failure(TrialFailure.NETWORK)
                } catch (failure: SecurityException) {
                    trace.fail(TrialHttpCategory.SOCKET_PERMISSION)
                    throw failure
                } finally {
                    cancellation.cancel()
                }
            }
        } finally {
            deadline.close()
            connections.remove(connection)
            connection.disconnect()
        }
    }

    override fun cancel() {
        connections.forEach { it.disconnect() }
    }
    override fun close() = cancel()

    private fun openConnection(url: String, trace: TrialHttpTrace): HttpsURLConnection = try {
        open(URL(url))
    } catch (failure: IOException) {
        trace.fail(trialIoCategory(failure))
        throw failure
    } catch (failure: SecurityException) {
        trace.fail(TrialHttpCategory.SOCKET_PERMISSION)
        throw failure
    }

    private suspend fun exchange(
        connection: HttpsURLConnection,
        request: TrialHttpRequest,
        checkDeadline: () -> Unit,
        trace: TrialHttpTrace,
        onChunk: (suspend (ByteArray) -> Unit)?
    ): TrialHttpResponse {
        configureTrialConnection(connection, request, trace)
        trace.stage = TrialHttpStage.READ_STATUS
        val status = connection.responseCode
        trace.receivedStatus(status)
        requireTrial(
            status !in
                java.net.HttpURLConnection.HTTP_MULT_CHOICE until
                java.net.HttpURLConnection.HTTP_BAD_REQUEST
        )
        trace.stage = TrialHttpStage.READ_BODY
        val input = if (status in
            java.net.HttpURLConnection.HTTP_OK until java.net.HttpURLConnection.HTTP_MULT_CHOICE
        ) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        val maximum = if (status in
            java.net.HttpURLConnection.HTTP_OK until java.net.HttpURLConnection.HTTP_MULT_CHOICE
        ) {
            request.maxBytes
        } else {
            TrialLimits.JSON_BYTES
        }
        return TrialHttpResponse(
            status,
            input?.use {
                val onLimit = { trace.fail(TrialHttpCategory.BODY_LIMIT) }
                if (onChunk != null && status in java.net.HttpURLConnection.HTTP_OK until
                    java.net.HttpURLConnection.HTTP_MULT_CHOICE
                ) {
                    readTrialStreamingBody(it, maximum, checkDeadline, onLimit, onChunk)
                    ""
                } else {
                    readTrialBody(it, maximum, checkDeadline, onLimit)
                }
            }.orEmpty()
        )
    }
}

private fun validateTrialRequest(request: TrialHttpRequest) {
    if (request.url.startsWith("$TRIAL_AUTH_ORIGIN/")) {
        trustedTrialAuthUrl(request.url)
        requireTrial(request.bearer == null)
    } else {
        requireTrial(
            request.url == "$TRIAL_RESOURCE/models" || request.url == "$TRIAL_RESOURCE/responses"
        )
    }
    requireTrial(request.method == "GET" || request.method == "POST")
    val maximum = if (request.method == "GET" && request.url == "$TRIAL_RESOURCE/models") {
        TrialLimits.MODELS_BYTES
    } else {
        TrialLimits.RESPONSE_BYTES
    }
    requireTrial(request.maxBytes in 1..maximum)
}

private fun sendTrialBody(
    connection: HttpsURLConnection,
    contentType: String,
    body: String,
    trace: TrialHttpTrace
) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    requireTrial(bytes.size <= TrialLimits.REQUEST_BYTES, TrialFailure.INPUT)
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", contentType)
    connection.setFixedLengthStreamingMode(bytes.size)
    trace.stage = TrialHttpStage.OPEN_OUTPUT
    val output = connection.outputStream
    trace.stage = TrialHttpStage.WRITE_BODY
    output.use { it.write(bytes) }
}

private fun configureTrialConnection(
    connection: HttpsURLConnection,
    request: TrialHttpRequest,
    trace: TrialHttpTrace
) {
    trace.stage = TrialHttpStage.CONFIGURE
    connection.instanceFollowRedirects = false
    connection.connectTimeout = TrialLimits.CONNECT_TIMEOUT_MILLIS
    connection.readTimeout = TrialLimits.RESPONSE_READ_MILLIS
    connection.requestMethod = request.method
    connection.setRequestProperty(
        "Accept",
        if (request.url.endsWith("/responses")) "text/event-stream" else "application/json"
    )
    request.bearer?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
    request.body?.let { sendTrialBody(connection, request.contentType, it, trace) }
}

private fun CoroutineScope.watchTrialCancellation(
    connection: HttpsURLConnection,
    deadline: TrialDeadline,
    trace: TrialHttpTrace
): Job {
    val parent = coroutineContext.job
    return launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            if (parent.isCancelled) trace.fail(TrialHttpCategory.CANCELLED, deadline.expired)
            connection.disconnect()
        }
    }
}
