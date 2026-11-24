package dev.blamera.chatgpt

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrialReasoningEffortTest {
    @Test fun profilesAreExplicitAndNeverGuessedForOtherSlugs() {
        val five = listOf("low", "medium", "high", "xhigh", "max")
        for (slug in listOf(
            "gpt-6.1-sol",
            "gpt-6-astra"
        )) {
            assertEquals(slug, five, trialReasoningEfforts(slug))
        }
        for (slug in listOf(
            "gpt-6.1-terra",
            "gpt-6-sol",
            "gpt-6-luna",
            "gpt-5.6-sol",
            "gpt-5.6-terra",
            "gpt-5.6-luna",
            "gpt-5.6"
        )) {
            assertEquals(slug, listOf("none") + five, trialReasoningEfforts(slug))
        }
        for (slug in listOf("gpt-6.1-sol-pro", "gpt-6.1-sol-future", "gpt-6.1-luna", "unknown")) {
            assertTrue(trialReasoningEfforts(slug).isEmpty())
        }
    }

    @Test fun explicitEffortAddsOnlyReasoningEffortAndDefaultOmitsTheField() {
        val explicit = JSONObject(trialImageRequest("gpt-6.1-sol", JPEG, "Synthetic prompt", "low"))
        assertTrue("The request must carry the explicitly chosen effort", explicit.has("reasoning"))
        assertEquals("low", explicit.getJSONObject("reasoning").getString("effort"))
        assertFalse(
            JSONObject(trialImageRequest("unknown", JPEG, "Synthetic prompt")).has("reasoning")
        )
        assertFalse(explicit.has("service_tier"))
        assertFalse(explicit.getBoolean("store"))
        assertTrue(explicit.getBoolean("stream"))
    }

    @Test
    fun preferenceSurvivesNewGatewayModelChangesAndRefreshWithoutExtendingTheGrant() = runBlocking {
        val fixture = SessionFixture()
        val first = fixture.gateway()
        fixture.connect(first)
        first.models()
        first.rememberReasoningEffort("low")
        assertEquals("low", first.preferredReasoningEffort)
        val date = JSONObject(checkNotNull(fixture.store.value)).getLong("savedAt")
        first.rememberModel("preferred")
        assertEquals(date, JSONObject(checkNotNull(fixture.store.value)).getLong("savedAt"))
        first.close()
        fixture.now = 4000
        val next = fixture.gateway()
        next.restore()
        assertEquals("low", next.preferredReasoningEffort)
        next.models()
        next.rememberReasoningEffort(null)
        assertEquals(null, next.preferredReasoningEffort)
        next.close()
        val third = fixture.gateway()
        third.restore()
        assertEquals(null, third.preferredReasoningEffort)
        third.close()
    }

    private companion object {
        val JPEG = byteArrayOf(-1, -40, 1, -1, -39)
    }
}
