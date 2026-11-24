package dev.blamera.chatgpt

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrialResponseStreamTest {
    @Test fun splitUtf8CrlfBareCrCommentsAndMultilineDataProduceCumulativeText() {
        val stream = TrialResponseStream()
        val frames = "\uFEFF: keepalive\r\nevent: response.output_text.delta\r\n" +
            "data: {\"type\":\"response.output_text.delta\",\r\n" +
            "data: \"delta\":\"Café 🐈\"}\r\n\r\n" +
            ": keepalive\n" + delta(" vert").replace("\n", "\r") +
            "data: {\"type\":\"response.completed\"}"
        val updates = frames.toByteArray(Charsets.UTF_8).asIterable().mapNotNull {
            stream.accept(byteArrayOf(it))
        }
        assertEquals(listOf("Café 🐈", "Café 🐈 vert"), updates)
        assertEquals("Café 🐈 vert", stream.finish())
        assertThrows(ChatGptTrialException::class.java) { stream.accept(byteArrayOf()) }
        assertThrows(ChatGptTrialException::class.java) { stream.finish() }
    }

    @Test fun aCompleteDeltaIsVisibleWhileEofWithoutCompletedIsStillFailure() {
        val stream = TrialResponseStream()
        assertEquals("Début", stream.accept(delta("Début").toByteArray()))
        expect(TrialFailure.INVALID_RESPONSE) { stream.finish() }
    }

    @Test fun allTerminalFailuresRemainFailuresAfterPartialTextAndMessagesStaySafe() {
        val cases = listOf(
            (
                "{\"type\":\"response.failed\",\"response\":{\"error\":{" +
                    "\"code\":\"subscription_sharing_usage_limit_exceeded\",\"message\":\"SECRET\"}}}"
                ) to
                TrialFailure.USAGE_LIMIT,
            """{"type":"error","code":"unsupported_image","message":"SECRET"}""" to
                TrialFailure.MODEL_UNAVAILABLE,
            """{"type":"response.incomplete"}""" to TrialFailure.INVALID_RESPONSE,
            """{"type":"response.cancelled"}""" to TrialFailure.INVALID_RESPONSE,
            """{"type":"error"}""" to TrialFailure.INVALID_RESPONSE
        )
        cases.forEach { (event, failure) ->
            val stream = TrialResponseStream()
            assertEquals("Préfixe", stream.accept(delta("Préfixe").toByteArray()))
            expect(failure) { stream.accept("data: $event\n\n".toByteArray()) }
        }
        val doneOnly = TrialResponseStream()
        expect(TrialFailure.INVALID_RESPONSE) { doneOnly.accept("data: [DONE]\n\n".toByteArray()) }
    }

    @Test fun doneTextAndUnknownMetadataCannotDuplicateTheAnswerOrReplaceItsTerminalEvent() {
        val stream = TrialResponseStream()
        val body = "data: {\"type\":\"response.created\"}\n\n" + delta("Texte") +
            "data: {\"type\":\"response.output_text.done\",\"text\":\"Texte\"}\n\n" + completed()
        assertEquals("Texte", stream.accept(body.toByteArray()))
        assertEquals("Texte", stream.finish())
        expect(TrialFailure.INVALID_RESPONSE) { parseTrialStream(completed()) }
        expect(TrialFailure.INVALID_RESPONSE) { parseTrialStream(delta(" ") + completed()) }
        expect(TrialFailure.INVALID_RESPONSE) {
            parseTrialStream(delta("a") + completed() + delta("b"))
        }
    }

    @Test fun malformedAndTruncatedUtf8AreRejectedWithoutReplacementCharacters() {
        val prefix = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"".toByteArray()
        val invalid = prefix + byteArrayOf(0xC3.toByte(), 0x28) + "\"}\n\n".toByteArray()
        expect(TrialFailure.INVALID_RESPONSE) { TrialResponseStream().accept(invalid) }
        val truncated = TrialResponseStream()
        assertNull(truncated.accept(prefix + byteArrayOf(0xF0.toByte(), 0x9F.toByte())))
        expect(TrialFailure.INVALID_RESPONSE) { truncated.finish() }
    }

    @Test fun lineAndMultilineEventLimitsAcceptTheBoundaryAndRejectTheNextByte() {
        val exactLine = TrialResponseStream()
        assertNull(
            exactLine.accept(
                (":" + "x".repeat(TrialLimits.STREAM_EVENT_BYTES - 1) + "\n").toByteArray()
            )
        )
        exactLine.accept((delta("a") + completed()).toByteArray())
        assertEquals("a", exactLine.finish())
        expect(TrialFailure.INVALID_RESPONSE) {
            TrialResponseStream().accept(
                (":" + "x".repeat(TrialLimits.STREAM_EVENT_BYTES) + "\n").toByteArray()
            )
        }
        val first = "{\"type\":\"response.created\","
        val second =
            "\"padding\":\"" +
                "x".repeat(
                    TrialLimits.STREAM_EVENT_BYTES - first.length - "\"padding\":\"\"}".length - 2
                ) +
                "\"}"
        val exact = TrialResponseStream()
        assertNull(exact.accept("data: $first\ndata: $second\n\n".toByteArray()))
        exact.accept((delta("a") + completed()).toByteArray())
        assertEquals("a", exact.finish())
        expect(TrialFailure.INVALID_RESPONSE) {
            TrialResponseStream().accept(
                "data: $first\ndata: ${second.dropLast(2)}x\"}\n\n".toByteArray()
            )
        }
    }

    @Test fun totalAndAnswerBoundsRejectExcessInsteadOfTruncating() {
        expect(TrialFailure.INVALID_RESPONSE) {
            TrialResponseStream().accept(ByteArray(TrialLimits.RESPONSE_BYTES + 1))
        }
        val full = "x".repeat(MAX_TRIAL_REPLY_CHARS)
        val exact = TrialResponseStream()
        assertEquals(full, exact.accept(delta(full).toByteArray()))
        exact.accept(completed().toByteArray())
        assertEquals(full, exact.finish())
        val excessive = TrialResponseStream()
        excessive.accept(delta(full).toByteArray())
        expect(TrialFailure.INVALID_RESPONSE) { excessive.accept(delta("x").toByteArray()) }
    }

    private fun delta(text: String) =
        "data: " + JSONObject().put("type", "response.output_text.delta")
            .put("delta", text).toString() + "\n\n"
    private fun completed() = "data: {\"type\":\"response.completed\"}\n\n"
    private fun expect(kind: TrialFailure, work: () -> Unit) {
        try {
            work()
            throw AssertionError("Expected $kind")
        } catch (
            failure: ChatGptTrialException
        ) {
            assertEquals(kind, failure.kind)
            assertFalse(failure.message.orEmpty().contains("SECRET"))
        }
    }
}
