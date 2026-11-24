package dev.blamera.chatgpt

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal fun readTrialBody(
    input: InputStream,
    maximum: Int,
    checkDeadline: () -> Unit = { },
    onLimit: () -> Unit = { }
): String {
    val reader = TrialBodyReader(input, maximum, checkDeadline, onLimit)
    val output = ByteArrayOutputStream()
    while (true) {
        val chunk = reader.next() ?: break
        output.write(chunk)
    }
    return output.toString(Charsets.UTF_8.name())
}

internal suspend fun readTrialStreamingBody(
    input: InputStream,
    maximum: Int,
    checkDeadline: () -> Unit,
    onLimit: () -> Unit,
    onChunk: suspend (ByteArray) -> Unit
) {
    val reader = TrialBodyReader(input, maximum, checkDeadline, onLimit)
    while (true) {
        val chunk = reader.next() ?: break
        if (chunk.isNotEmpty()) onChunk(chunk)
    }
}

/** At most one bounded block is outstanding, including while the consumer is suspended. */
private class TrialBodyReader(
    private val input: InputStream,
    private val maximum: Int,
    private val checkDeadline: () -> Unit,
    private val onLimit: () -> Unit
) {
    private val buffer = ByteArray(TrialLimits.READ_BUFFER_BYTES)
    private var received = 0L

    fun next(): ByteArray? {
        checkDeadline()
        val count = input.read(buffer)
        checkDeadline()
        if (count == -1) return null
        received += count
        if (received > maximum) {
            onLimit()
            throw ChatGptTrialException(TrialFailure.INVALID_RESPONSE)
        }
        return buffer.copyOf(count)
    }
}
