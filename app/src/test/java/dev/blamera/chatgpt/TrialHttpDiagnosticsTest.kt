package dev.blamera.chatgpt

import android.system.ErrnoException
import android.system.OsConstants
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.cert.Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException
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
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrialHttpDiagnosticsTest {
    @Test fun transportFailuresReportOnlyTheFixedEndpointStageAndPreciseCategory() = runBlocking {
        val cases = listOf(
            UnknownHostException(SECRET) to TrialHttpCategory.DNS,
            SSLHandshakeException(SECRET) to TrialHttpCategory.TLS,
            ConnectException(SECRET) to TrialHttpCategory.CONNECT_REFUSED,
            NoRouteToHostException(SECRET) to TrialHttpCategory.CONNECT_UNREACHABLE,
            SocketTimeoutException(SECRET) to TrialHttpCategory.TIMEOUT,
            SocketException(SECRET) to TrialHttpCategory.SOCKET,
            IOException(SECRET) to TrialHttpCategory.IO,
            UnknownHostException(SECRET).apply {
                initCause(ErrnoException("fixture", OsConstants.EACCES))
            } to TrialHttpCategory.SOCKET_PERMISSION,
            UnknownHostException(SECRET).apply {
                initCause(ErrnoException("fixture", OsConstants.EPERM))
            } to TrialHttpCategory.SOCKET_PERMISSION,
            errno(OsConstants.EACCES) to TrialHttpCategory.SOCKET_PERMISSION,
            errno(OsConstants.EPERM) to TrialHttpCategory.SOCKET_PERMISSION,
            errno(OsConstants.ECONNREFUSED) to TrialHttpCategory.CONNECT_REFUSED,
            errno(OsConstants.EHOSTUNREACH) to TrialHttpCategory.CONNECT_UNREACHABLE,
            errno(OsConstants.ENETUNREACH) to TrialHttpCategory.CONNECT_UNREACHABLE,
            errno(OsConstants.ETIMEDOUT) to TrialHttpCategory.TIMEOUT,
            errno(OsConstants.EIO) to TrialHttpCategory.SOCKET
        )
        cases.forEach { (error, expected) ->
            val events = mutableListOf<TrialHttpDiagnostic>()
            val connection = DiagnosticConnection(TrialHttpStage.OPEN_OUTPUT, error)
            val http = HttpsTrialHttp(diagnostics = events::add) { connection }
            runCatching { http.execute(tokenRequest()) }
            assertEquals(
                listOf(
                    TrialHttpDiagnostic(
                        TrialHttpEndpoint.TOKEN,
                        TrialHttpStage.OPEN_OUTPUT,
                        expected
                    )
                ),
                events
            )
            assertTrue(connection.disconnected)
            assertFalse(connection.trustChanged)
            http.close()
        }
    }

    @Test fun aReadOrWriteFailureKeepsItsStageAndAnyReceivedStatusWithoutRetry() = runBlocking {
        listOf(
            TrialHttpStage.WRITE_BODY,
            TrialHttpStage.READ_STATUS,
            TrialHttpStage.READ_BODY
        ).forEach { stage ->
            val events = mutableListOf<TrialHttpDiagnostic>()
            var opens = 0
            val connection = DiagnosticConnection(stage, IOException(SECRET))
            val http = HttpsTrialHttp(diagnostics = events::add) {
                opens++
                connection
            }
            runCatching { http.execute(tokenRequest()) }
            val expectedStatus = if (stage == TrialHttpStage.READ_BODY) 200 else null
            assertTrue("A safe transport diagnostic must be recorded", events.isNotEmpty())
            assertEquals(
                TrialHttpDiagnostic(
                    TrialHttpEndpoint.TOKEN,
                    stage,
                    TrialHttpCategory.IO,
                    expectedStatus
                ),
                events.last()
            )
            assertEquals(1, opens)
            assertTrue(connection.disconnected)
            http.close()
        }
    }

    @Test fun statusAndPublicEndpointNamesAreWhitelistedWithoutLoggingAnyRequestOrThrowable() {
        val endpoints = mapOf(
            "$TRIAL_AUTH_ORIGIN/api/accounts/oauth/token" to TrialHttpEndpoint.TOKEN,
            "$TRIAL_AUTH_ORIGIN/.well-known/openid-configuration" to TrialHttpEndpoint.DISCOVERY,
            "$TRIAL_AUTH_ORIGIN/.well-known/jwks.json" to TrialHttpEndpoint.JWKS,
            "$TRIAL_AUTH_ORIGIN/api/accounts/oauth/revoke" to TrialHttpEndpoint.REVOCATION,
            "$TRIAL_RESOURCE/models" to TrialHttpEndpoint.MODELS,
            "$TRIAL_RESOURCE/responses" to TrialHttpEndpoint.RESPONSES,
            "https://auth.openai.com/metadata-fixture" to TrialHttpEndpoint.OTHER
        )
        endpoints.forEach { (url, endpoint) -> assertEquals(endpoint, trialHttpEndpoint(url)) }
        listOf(null, -1, 99, 100, 200, 503, 599, 600).forEach { status ->
            writeTrialHttpDiagnostic(
                TrialHttpDiagnostic(
                    TrialHttpEndpoint.TOKEN,
                    TrialHttpStage.READ_STATUS,
                    TrialHttpCategory.HTTP_STATUS,
                    status
                )
            )
        }
        val logs = ShadowLog.getLogsForTag(CHATGPT_DIAGNOSTIC_TAG)
        assertEquals(8, logs.size)
        assertTrue(
            logs.all {
                it.msg.matches(Regex("HTTP_TOKEN_READ_STATUS_HTTP_STATUS_(NONE|[1-5][0-9]{2})"))
            }
        )
        assertTrue(logs.all { it.throwable == null && !it.msg.contains(SECRET) })
        assertEquals("HTTP_TOKEN_READ_STATUS_HTTP_STATUS_NONE", logs.last().msg)
    }

    @Test fun cancellationIsReportedOnceAndDoesNotBecomeASpuriousNetworkFailure() = runBlocking {
        val started = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val source = object : InputStream() {
            override fun read(): Int {
                started.countDown()
                check(closed.await(5, TimeUnit.SECONDS))
                throw IOException(SECRET)
            }
            override fun close() {
                closed.countDown()
            }
        }
        val events = java.util.concurrent.CopyOnWriteArrayList<TrialHttpDiagnostic>()
        val connection = DiagnosticConnection(source = source)
        val http = HttpsTrialHttp(diagnostics = events::add) { connection }
        val request = async(Dispatchers.IO) { http.execute(tokenRequest()) }
        assertTrue(started.await(3, TimeUnit.SECONDS))
        request.cancelAndJoin()
        assertEquals(
            listOf(TrialHttpCategory.CANCELLED),
            events.filter {
                it.category != TrialHttpCategory.HTTP_STATUS
            }.map { it.category }
        )
        assertTrue(connection.disconnected)
        http.close()
    }

    private fun errno(code: Int): SocketException = SocketException(SECRET).apply {
        initCause(ErrnoException("fixture", code))
    }
    private fun tokenRequest() = TrialHttpRequest(
        "$TRIAL_AUTH_ORIGIN/api/accounts/oauth/token",
        "POST",
        "code=fixture",
        contentType = "application/x-www-form-urlencoded"
    )

    private class DiagnosticConnection(
        private val faultAt: TrialHttpStage? = null,
        private val failure: IOException? = null,
        private val source: InputStream? = null
    ) : HttpsURLConnection(URL(TRIAL_AUTH_ORIGIN)) {
        var disconnected = false
        var trustChanged = false
        override fun getResponseCode(): Int {
            fail(TrialHttpStage.READ_STATUS)
            return 200
        }
        override fun getInputStream(): InputStream = source ?: object : InputStream() {
            override fun read(): Int {
                fail(TrialHttpStage.READ_BODY)
                return -1
            }
        }
        override fun getOutputStream(): OutputStream {
            fail(TrialHttpStage.OPEN_OUTPUT)
            return object : ByteArrayOutputStream() {
                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    fail(TrialHttpStage.WRITE_BODY)
                    super.write(bytes, offset, length)
                }
            }
        }
        private fun fail(stage: TrialHttpStage) {
            if (stage ==
                faultAt
            ) {
                throw checkNotNull(failure)
            }
        }
        override fun disconnect() {
            disconnected = true
            source?.close()
        }
        override fun usingProxy() = false
        override fun connect() = Unit
        override fun getCipherSuite() = "TLS_FIXTURE"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun setHostnameVerifier(verifier: javax.net.ssl.HostnameVerifier) {
            trustChanged = true
            throw AssertionError("TLS trust must remain unchanged")
        }
        override fun setSSLSocketFactory(factory: javax.net.ssl.SSLSocketFactory) {
            trustChanged = true
            throw AssertionError("TLS trust must remain unchanged")
        }
    }

    private companion object {
        const val SECRET = "private@example.invalid?code=SECRET-token"
    }
}
