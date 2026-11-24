package dev.blamera.chatgpt

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrialBrowserConnectionTest {
    @Test fun resetSpeculativeConnectionDoesNotDiscardTheActualCallback() = runBlocking {
        val accepted = CountDownLatch(1)
        val listener = TrialLoopback(acceptSocket = { server ->
            server.accept().also { accepted.countDown() }
        })
        val callback = async(Dispatchers.IO) { runCatching { listener.await("expected", null) } }
        val port = URI(listener.redirectUri).port
        try {
            Socket("127.0.0.1", port).use { speculative ->
                speculative.setSoLinger(true, 0)
                speculative.getOutputStream().write("GET /preconnect HTTP/1.1\r\n".toByteArray())
                speculative.getOutputStream().flush()
                assertTrue(accepted.await(2, TimeUnit.SECONDS))
            }
            runCatching {
                Socket().use { browser ->
                    browser.connect(InetSocketAddress("127.0.0.1", port), 1000)
                    browser.soTimeout = 5000
                    val request =
                        "GET /auth/callback?state=expected&code=fixture&client_id=client " +
                            "HTTP/1.1\r\nHost: 127.0.0.1:$port\r\n\r\n"
                    browser.getOutputStream().write(request.toByteArray())
                    browser.getOutputStream().flush()
                    browser.getInputStream().readBytes()
                }
            }
            val outcome = withTimeout(5000) { callback.await() }
            assertEquals(TrialAuthCode("fixture", "client"), outcome.getOrNull())
        } finally {
            listener.close()
            callback.cancel()
        }
    }

    @Test fun failedAcknowledgementCannotDiscardAValidatedCode() = runBlocking {
        val outcome = withFailedReply("state=expected&code=fixture&client_id=client")
        assertEquals(TrialAuthCode("fixture", "client"), outcome.getOrNull())
    }

    @Test fun failedAcknowledgementCannotHideAValidatedDenial() = runBlocking {
        val outcome = withFailedReply("state=expected&error=access_denied")
        assertEquals(
            TrialFailure.AUTHENTICATION,
            (outcome.exceptionOrNull() as? ChatGptTrialException)?.kind
        )
    }

    private suspend fun withFailedReply(query: String): Result<TrialAuthCode> = coroutineScope {
        val acknowledgement = CountDownLatch(1)
        val listener = TrialLoopback(reply = { _, _ ->
            acknowledgement.countDown()
            throw IOException("fixture peer stopped reading")
        })
        val callback = async(Dispatchers.IO) { runCatching { listener.await("expected", null) } }
        val port = URI(listener.redirectUri).port
        try {
            Socket("127.0.0.1", port).use { browser ->
                val request = "GET /auth/callback?$query HTTP/1.1\r\nHost: 127.0.0.1:$port\r\n\r\n"
                browser.getOutputStream().write(request.toByteArray())
                browser.getOutputStream().flush()
                assertTrue(acknowledgement.await(2, TimeUnit.SECONDS))
                withTimeout(3000) { callback.await() }
            }
        } finally {
            listener.close()
            callback.cancel()
        }
    }
}
