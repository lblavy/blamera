package dev.blamera.chatgpt

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal class TrialLoopback(
    private val timeoutMillis: Long = TrialLimits.AUTH_TIMEOUT_MILLIS,
    private val scheduler: TrialTimeoutScheduler = SystemTrialTimeoutScheduler,
    private val acceptSocket: (ServerSocket) -> Socket = { it.accept() },
    private val reply: (Socket, Int) -> Unit = ::sendCallbackPage,
    private val clockMillis: () -> Long = { System.nanoTime() / TrialLimits.NANOS_PER_MILLISECOND }
) : TrialCallbackListener {
    private val server = ServerSocket().apply {
        reuseAddress = false
        bind(InetSocketAddress("127.0.0.1", 0), 1)
        soTimeout = TrialLimits.ACCEPT_TIMEOUT_MILLIS
    }
    private val stopped = AtomicBoolean(false)

    @Volatile private var client: Socket? = null
    override val redirectUri = "http://127.0.0.1:${server.localPort}/auth/callback"

    override suspend fun await(state: String, issuedClientId: String?): TrialAuthCode = try {
        withContext(Dispatchers.IO) {
            val deadline = TrialDeadline(timeoutMillis, scheduler, ::close)
            try {
                awaitBeforeDeadline(state, issuedClientId, deadline)
            } finally {
                deadline.close()
            }
        }
    } finally {
        close()
    }

    private suspend fun awaitBeforeDeadline(
        state: String,
        issuedClientId: String?,
        deadline: TrialDeadline
    ): TrialAuthCode = suspendCancellableCoroutine { pending ->
        pending.invokeOnCancellation { close() }
        try {
            val code = receive(state, issuedClientId, deadline::check)
            deadline.finish()
            pending.resume(code)
        } catch (failure: ChatGptTrialException) {
            pending.resumeWithException(deadline.failure(failure.kind))
        } catch (_: IOException) {
            val failure = if (stopped.get()) TrialFailure.CANCELLED else TrialFailure.NETWORK
            pending.resumeWithException(deadline.failure(failure))
        }
    }

    private fun receive(
        state: String,
        issuedClientId: String?,
        checkDeadline: () -> Unit
    ): TrialAuthCode {
        val deadline = clockMillis() + timeoutMillis
        while (!stopped.get()) {
            checkDeadline()
            requireTrial(clockMillis() < deadline, TrialFailure.TIMEOUT)
            val socket = acceptClient() ?: continue
            client = socket
            try {
                val code = socket.use {
                    checkDeadline()
                    handleClient(it, state, issuedClientId, checkDeadline)
                }
                if (code != null) return code
            } catch (peerFailure: IOException) {
                // A browser may abandon a speculative connection before its real callback.
                if (stopped.get()) throw peerFailure
                checkDeadline()
            } finally {
                client = null
            }
        }
        throw ChatGptTrialException(TrialFailure.CANCELLED)
    }

    private fun acceptClient(): Socket? = try {
        acceptSocket(server).apply { soTimeout = TrialLimits.CALLBACK_READ_MILLIS }
    } catch (_: SocketTimeoutException) {
        null
    } catch (peerFailure: SocketException) {
        if (stopped.get() || server.isClosed) throw peerFailure
        null
    }

    private fun handleClient(
        socket: Socket,
        state: String,
        issuedClientId: String?,
        checkDeadline: () -> Unit
    ): TrialAuthCode? {
        val target = try {
            readTrialRequest(socket.getInputStream(), server.localPort, checkDeadline)
        } catch (
            _: IOException
        ) {
            null
        }
        if (target == null) {
            acknowledge(socket, java.net.HttpURLConnection.HTTP_BAD_REQUEST)
            return null
        }
        val result = try {
            parseTrialCallback(target, state, issuedClientId)
        } catch (failure: ChatGptTrialException) {
            acknowledge(socket, java.net.HttpURLConnection.HTTP_OK)
            throw failure
        }
        acknowledge(
            socket,
            if (result ==
                null
            ) {
                java.net.HttpURLConnection.HTTP_BAD_REQUEST
            } else {
                java.net.HttpURLConnection.HTTP_OK
            }
        )
        return result
    }

    private fun acknowledge(socket: Socket, status: Int) {
        try {
            reply(socket, status)
        } catch (_: IOException) {
            // A closed browser page cannot invalidate an already validated code or denial.
        }
    }

    override fun close() {
        stopped.set(true)
        try {
            client?.close()
        } catch (
            _: IOException
        ) { /* Closing is best effort after cancellation. */ }
        try {
            server.close()
        } catch (_: IOException) { /* The listener may already be closed. */ }
    }
}

internal fun readTrialRequest(
    input: InputStream,
    port: Int,
    checkDeadline: () -> Unit = {
    }
): String? = try {
    val first = requireNotNull(boundedTrialLine(input, checkDeadline))
    val parts = first.split(' ')
    require(parts.size == TrialLimits.HTTP_PARTS)
    require(parts[0] == "GET" && parts[2] in setOf("HTTP/1.1", "HTTP/1.0"))
    val host = readTrialHost(input, first.length, checkDeadline)
    parts[1].takeIf { host == "127.0.0.1:$port" }
} catch (_: IllegalArgumentException) {
    null
}

private fun readTrialHost(
    input: InputStream,
    initialBytes: Int,
    checkDeadline: () -> Unit
): String? {
    var total = initialBytes
    var host: String? = null
    var line = requireNotNull(boundedTrialLine(input, checkDeadline))
    while (line.isNotEmpty()) {
        total += line.length
        require(total <= TrialLimits.HTTP_HEADERS_BYTES)
        if (line.substringBefore(':').equals("Host", ignoreCase = true)) {
            require(host == null)
            host = line.substringAfter(':', "").trim()
        }
        line = requireNotNull(boundedTrialLine(input, checkDeadline))
    }
    return host
}

private fun boundedTrialLine(input: InputStream, checkDeadline: () -> Unit): String? {
    val output = ByteArrayOutputStream()
    var value = readTrialByte(input, checkDeadline)
    while (value != -1 && value != '\n'.code) {
        require(output.size() < TrialLimits.CALLBACK_BYTES)
        output.write(value)
        value = readTrialByte(input, checkDeadline)
    }
    return if (value == -1) null else output.toString(Charsets.US_ASCII.name()).removeSuffix("\r")
}

private fun readTrialByte(input: InputStream, checkDeadline: () -> Unit): Int {
    checkDeadline()
    val value = input.read()
    checkDeadline()
    return value
}

private fun sendCallbackPage(socket: Socket, status: Int) {
    val page = (
        "<!doctype html><meta charset=utf-8><title>Blamera</title>" +
            "<p>Revenez dans Blamera pour poursuivre la connexion.</p>"
        ).toByteArray(Charsets.UTF_8)
    val reason = if (status == java.net.HttpURLConnection.HTTP_OK) "OK" else "Bad Request"
    val headers =
        "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: text/html; charset=utf-8\r\nContent-Length: ${page.size}\r\n" +
            "Cache-Control: no-store\r\nContent-Security-Policy: default-src 'none'\r\n" +
            "Connection: close\r\n\r\n"
    socket.getOutputStream().apply {
        write(headers.toByteArray(Charsets.US_ASCII))
        write(page)
        flush()
    }
}
