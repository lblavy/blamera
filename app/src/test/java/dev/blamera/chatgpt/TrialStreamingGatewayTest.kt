package dev.blamera.chatgpt

import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrialStreamingGatewayTest {
    @Test fun actualGatewayAndHttpPublishBeforeEofAndTransmitOnlyTheExplicitCropPromptAndEffort() =
        runBlocking {
            val fixture = fixture()
            val source = PausedStreamingInput(delta("Une "), delta("tasse.") + completed())
            val connection = StreamingHttps(source)
            val http = HttpsTrialHttp { connection }
            fixture.streamingTransport = http
            val gateway = fixture.gateway()
            fixture.connect(gateway)
            gateway.models()
            assertFalse(fixture.requests.any { it.url.endsWith("responses") })
            val received = CompletableDeferred<Unit>()
            val texts = mutableListOf<String>()
            val result = async {
                gateway.analyzeStreaming("gpt-6.1-sol", JPEG, "Observe la tasse.", "low") {
                    texts.add(it)
                    received.complete(Unit)
                }
            }
            try {
                withTimeout(5_000L) { received.await() }
                assertTrue(
                    withContext(Dispatchers.IO) {
                        source.waiting.await(5, TimeUnit.SECONDS)
                    }
                )
                assertFalse("A prefix must not mark the request completed", result.isCompleted)
                assertEquals(listOf("Une "), texts)
                val request = JSONObject(connection.output.toString(Charsets.UTF_8.name()))
                assertEquals("low", request.getJSONObject("reasoning").getString("effort"))
                assertEquals(false, request.getBoolean("store"))
                assertEquals(true, request.getBoolean("stream"))
                val content = request.getJSONArray("input").getJSONObject(0).getJSONArray("content")
                assertEquals("Observe la tasse.", content.getJSONObject(0).getString("text"))
                assertEquals(
                    "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(JPEG),
                    content.getJSONObject(1).getString("image_url")
                )
                source.release.countDown()
                assertEquals("Une tasse.", result.await())
                assertEquals(listOf("Une ", "Une tasse."), texts)
                assertEquals(1, fixture.requests.count { it.url.endsWith("responses") })
            } finally {
                source.release.countDown()
                result.cancel()
                gateway.close()
                http.close()
            }
        }

    @Test
    fun unsupportedEffortIsRejectedBeforeAnyPostWithoutInvalidatingTheAccount() = runBlocking {
        val fixture = fixture()
        val gateway = fixture.gateway()
        fixture.connect(gateway)
        gateway.models()
        for (effort in listOf("none", "ultra", "x".repeat(1000))) {
            val before = fixture.requests.size
            try {
                gateway.analyzeStreaming("gpt-6.1-sol", JPEG, "Observe.", effort) { }
                throw AssertionError("Unsupported effort must fail")
            } catch (
                failure: ChatGptTrialException
            ) {
                assertEquals(TrialFailure.INPUT, failure.kind)
            }
            assertEquals(before, fixture.requests.size)
        }
        assertNotNull(fixture.store.value)
        assertFalse(fixture.registration.sessionBlocked)
        assertTrue(gateway.models().isNotEmpty())
        gateway.close()
    }

    @Test
    fun oldRecordsLackingEffortRestoreWithoutBrowserAndKnownPreferencesRemainIndependentOfModel() =
        runBlocking {
            val fixture = SessionFixture()
            val first = fixture.gateway()
            fixture.connect(first)
            fixture.store.value = JSONObject(checkNotNull(fixture.store.value)).apply {
                remove("preferredReasoningEffort")
            }.toString()
            first.close()
            val next = fixture.gateway()
            assertNotNull(next.restore())
            assertEquals(null, next.preferredReasoningEffort)
            next.models()
            next.rememberReasoningEffort("max")
            next.rememberModel("preferred")
            next.close()
            val restored = fixture.gateway()
            assertNotNull(restored.restore())
            assertEquals("max", restored.preferredReasoningEffort)
            assertTrue(trialReasoningEfforts("preferred").isEmpty())
            assertEquals(1, fixture.browserCount)
            assertFalse(fixture.requests.any { it.url.endsWith("responses") })
            restored.close()
        }

    private fun fixture() = SessionFixture().apply {
        alter = { request, response ->
            if (request.url.endsWith("models")) {
                TrialHttpResponse(
                    200,
                    """{"models":[{"slug":"gpt-6.1-sol",
                    "display_name":"Sol","visibility":"list"}]}"""
                )
            } else {
                response
            }
        }
    }
    private fun delta(text: String) = (
        "data: " + JSONObject().put("type", "response.output_text.delta")
            .put("delta", text).toString() + "\n\n"
        ).toByteArray(Charsets.UTF_8)
    private fun completed() = "data: {\"type\":\"response.completed\"}\n\n".toByteArray()
    private companion object {
        val JPEG = byteArrayOf(-1, -40, 1, -1, -39)
    }
}
