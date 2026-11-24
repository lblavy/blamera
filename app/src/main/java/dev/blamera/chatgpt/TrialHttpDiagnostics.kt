package dev.blamera.chatgpt

import android.system.ErrnoException
import android.system.OsConstants
import android.util.Log
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLException

internal enum class TrialHttpEndpoint {
    TOKEN,
    DISCOVERY,
    JWKS,
    REVOCATION,
    MODELS,
    RESPONSES,
    OTHER
}
internal enum class TrialHttpStage {
    OPEN_CONNECTION,
    CONFIGURE,
    OPEN_OUTPUT,
    WRITE_BODY,
    READ_STATUS,
    READ_BODY
}
internal enum class TrialHttpCategory {
    HTTP_STATUS,
    DNS,
    TLS,
    CONNECT_REFUSED,
    CONNECT_UNREACHABLE,
    SOCKET_PERMISSION,
    TIMEOUT,
    SOCKET,
    IO,
    CANCELLED,
    PROTOCOL,
    BODY_LIMIT,
    INPUT
}

internal data class TrialHttpDiagnostic(
    val endpoint: TrialHttpEndpoint,
    val stage: TrialHttpStage,
    val category: TrialHttpCategory,
    val status: Int? = null
)

internal enum class TrialActivityState { RESUMED, PAUSED }

internal fun writeTrialHttpDiagnostic(event: TrialHttpDiagnostic) {
    val status =
        event.status?.takeIf { it in HTTP_STATUS_MIN..HTTP_STATUS_MAX }?.toString() ?: "NONE"
    Log.i(
        CHATGPT_DIAGNOSTIC_TAG,
        "HTTP_${event.endpoint.name}_${event.stage.name}_${event.category.name}_$status"
    )
}

internal fun writeTrialActivityState(state: TrialActivityState) {
    Log.i(CHATGPT_DIAGNOSTIC_TAG, "LIFECYCLE_${state.name}")
}

internal fun trialHttpEndpoint(url: String): TrialHttpEndpoint = when (url) {
    "$TRIAL_AUTH_ORIGIN/api/accounts/oauth/token" -> TrialHttpEndpoint.TOKEN
    "$TRIAL_AUTH_ORIGIN/.well-known/openid-configuration" -> TrialHttpEndpoint.DISCOVERY
    "$TRIAL_AUTH_ORIGIN/.well-known/jwks.json" -> TrialHttpEndpoint.JWKS
    "$TRIAL_AUTH_ORIGIN/api/accounts/oauth/revoke" -> TrialHttpEndpoint.REVOCATION
    "$TRIAL_RESOURCE/models" -> TrialHttpEndpoint.MODELS
    "$TRIAL_RESOURCE/responses" -> TrialHttpEndpoint.RESPONSES
    else -> TrialHttpEndpoint.OTHER
}

internal fun trialIoCategory(error: IOException): TrialHttpCategory {
    val errno = (error.cause as? ErrnoException)?.errno
    return trialErrnoCategory(errno) ?: when (error) {
        is UnknownHostException -> TrialHttpCategory.DNS
        is SSLException -> TrialHttpCategory.TLS
        is ConnectException -> TrialHttpCategory.CONNECT_REFUSED
        is NoRouteToHostException -> TrialHttpCategory.CONNECT_UNREACHABLE
        is SocketTimeoutException -> TrialHttpCategory.TIMEOUT
        is SocketException -> TrialHttpCategory.SOCKET
        else -> TrialHttpCategory.IO
    }
}

private fun trialErrnoCategory(errno: Int?): TrialHttpCategory? = when (errno) {
    OsConstants.EACCES, OsConstants.EPERM -> TrialHttpCategory.SOCKET_PERMISSION
    OsConstants.ECONNREFUSED -> TrialHttpCategory.CONNECT_REFUSED
    OsConstants.EHOSTUNREACH, OsConstants.ENETUNREACH -> TrialHttpCategory.CONNECT_UNREACHABLE
    OsConstants.ETIMEDOUT -> TrialHttpCategory.TIMEOUT
    else -> null
}

internal class TrialHttpTrace(
    private val endpoint: TrialHttpEndpoint,
    private val emit: (TrialHttpDiagnostic) -> Unit
) {
    @Volatile var stage: TrialHttpStage = TrialHttpStage.OPEN_CONNECTION

    @Volatile private var status: Int? = null
    private val failureRecorded = AtomicBoolean(false)

    fun receivedStatus(value: Int) {
        status = value.takeIf { it in HTTP_STATUS_MIN..HTTP_STATUS_MAX }
        emit(TrialHttpDiagnostic(endpoint, stage, TrialHttpCategory.HTTP_STATUS, status))
    }

    fun fail(category: TrialHttpCategory, expired: Boolean = false) {
        if (failureRecorded.compareAndSet(false, true)) {
            val actual = if (expired) TrialHttpCategory.TIMEOUT else category
            emit(TrialHttpDiagnostic(endpoint, stage, actual, status))
        }
    }
}

internal fun trialFailureHttpCategory(kind: TrialFailure): TrialHttpCategory = when (kind) {
    TrialFailure.INPUT -> TrialHttpCategory.INPUT
    TrialFailure.TIMEOUT -> TrialHttpCategory.TIMEOUT
    TrialFailure.CANCELLED -> TrialHttpCategory.CANCELLED
    else -> TrialHttpCategory.PROTOCOL
}

private const val HTTP_STATUS_MIN = 100
private const val HTTP_STATUS_MAX = 599
