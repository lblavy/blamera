package dev.blamera.chatgpt

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatGptProtocolTest {
    @Test fun accountCatalogPreservesOrderAndHidesUnlistedModels() {
        val body = """{"models":[{"slug":"vision-b","display_name":"Vision B","visibility":"list"},
            {"slug":"hidden","display_name":"Hidden","visibility":"hidden"},
            {"slug":"vision-a","display_name":"Vision A","visibility":"list"}]}"""
        assertEquals(
            listOf(TrialModel("vision-b", "Vision B"), TrialModel("vision-a", "Vision A")),
            parseTrialModels(body)
        )
    }

    @Test fun imageAndInstructionShareOneRequestWithoutPaidApiFallbackFields() {
        val request =
            JSONObject(
                trialImageRequest("vision", byteArrayOf(-1, -40, 1, -1, -39), "Décris cet objet.")
            )
        assertEquals("vision", request.getString("model"))
        assertFalse(request.getBoolean("store"))
        assertTrue(request.getBoolean("stream"))
        val message = request.getJSONArray("input").getJSONObject(0)
        assertEquals("user", message.getString("role"))
        val content = message.getJSONArray("content")
        assertEquals("Décris cet objet.", content.getJSONObject(0).getString("text"))
        assertEquals(
            "data:image/jpeg;base64,/9gB/9k=",
            content.getJSONObject(1).getString("image_url")
        )
        assertFalse(request.has("max_output_tokens"))
    }

    @Test fun streamRequiresCompletedAndDoesNotReturnPartialTextAsSuccess() {
        val partial = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Une tasse\"}\n\n"
        assertThrows(ChatGptTrialException::class.java) { parseTrialStream(partial) }
        assertEquals(
            "Une tasse",
            parseTrialStream(partial + "data: {\"type\":\"response.completed\"}\n\n")
        )
    }
}
