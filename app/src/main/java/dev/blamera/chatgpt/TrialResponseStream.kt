package dev.blamera.chatgpt

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import org.json.JSONObject

/** SSE lines retain raw UTF-8 bytes until their delimiter, including split code points. */
internal class TrialResponseStream {
    private val line = ByteArrayOutputStream()
    private val event = StringBuilder()
    private val text = StringBuilder()
    private var eventBytes = 0
    private var received = 0L
    private var previousCr = false
    private var firstLine = true
    private var completed = false
    private var ended = false

    fun accept(bytes: ByteArray): String? {
        requireTrial(!ended)
        received += bytes.size
        requireTrial(received <= TrialLimits.RESPONSE_BYTES)
        val before = text.length
        bytes.forEach(::acceptByte)
        return if (text.length != before) text.toString() else null
    }

    fun finish(): String {
        requireTrial(!ended)
        ended = true
        if (line.size() != 0) acceptLine()
        dispatchEvent()
        requireTrial(completed && text.isNotBlank())
        return text.toString()
    }

    private fun acceptByte(value: Byte) {
        when (value) {
            '\r'.code.toByte() -> {
                acceptLine()
                previousCr = true
            }
            '\n'.code.toByte() -> {
                if (!previousCr) acceptLine()
                previousCr = false
            }
            else -> {
                previousCr = false
                requireTrial(line.size() < TrialLimits.STREAM_EVENT_BYTES)
                line.write(value.toInt())
            }
        }
    }

    private fun acceptLine() {
        var decoded = decodeTrialStreamLine(line.toByteArray())
        line.reset()
        if (firstLine) {
            decoded = decoded.removePrefix("\uFEFF")
            firstLine = false
        }
        if (decoded.isEmpty()) {
            dispatchEvent()
        } else if (decoded.startsWith("data:")) {
            val data = decoded.substring("data:".length).removePrefix(" ")
            eventBytes += data.toByteArray(Charsets.UTF_8).size + 1
            requireTrial(eventBytes <= TrialLimits.STREAM_EVENT_BYTES)
            event.append(data).append('\n')
        }
    }

    private fun dispatchEvent() {
        if (event.isEmpty()) return
        requireTrial(!completed)
        val payload = event.toString().trimEnd('\n')
        event.clear()
        eventBytes = 0
        trialJson {
            val json = JSONObject(payload)
            when (json.getString("type")) {
                "response.output_text.delta" -> {
                    val delta = json.getString("delta")
                    requireTrial(text.length.toLong() + delta.length <= MAX_TRIAL_REPLY_CHARS)
                    text.append(delta)
                }
                "response.completed" -> completed = true
                "response.failed", "error", "response.incomplete", "response.cancelled" ->
                    throw ChatGptTrialException(trialStreamEventFailure(json))
            }
            Unit
        }
    }
}

private fun trialStreamEventFailure(event: JSONObject): TrialFailure =
    when (event.getString("type")) {
        "response.failed" -> trialFailureCode(
            event.optJSONObject("response")?.optJSONObject("error")?.optString("code")
        )
        "error" -> trialFailureCode(event.optString("code"))
        else -> TrialFailure.INVALID_RESPONSE
    }

private fun decodeTrialStreamLine(bytes: ByteArray): String = try {
    Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
} catch (_: CharacterCodingException) {
    throw ChatGptTrialException(TrialFailure.INVALID_RESPONSE)
}
