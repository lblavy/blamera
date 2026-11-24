package dev.blamera.chatgpt

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrialPersistentSessionTest {
    @Test fun aNewGatewayRestoresAccountAndPreferredModelWithoutBrowserOrImage() = runBlocking {
        val fixture = SessionFixture()
        val first = fixture.gateway()
        fixture.connect(first)
        first.models()
        first.rememberModel("preferred")
        assertNotNull("A validated grant must be stored before closing", fixture.store.value)
        val persisted = fixture.store.value!!
        assertFalse(persisted.contains("synthetic-code"))
        assertFalse(persisted.contains("creditsConfirmed"))
        first.close()
        val second = fixture.gateway()
        assertEquals(ConnectedAccount("Test account", true), second.restore())
        assertEquals(listOf("first", "preferred"), second.models().map { it.slug })
        assertEquals("preferred", second.preferredModel)
        assertEquals(1, fixture.browserCount)
        assertEquals(1, fixture.requests.count { it.url.endsWith("oauth/token") })
        assertFalse(fixture.requests.any { it.url.endsWith("responses") })
        second.close()
    }

    @Test fun staleInstancesReadRotatedGrantsWithoutReusingRefreshTokens() = runBlocking {
        val fixture = SessionFixture()
        val first = fixture.gateway()
        fixture.connect(first)
        first.models()
        fixture.now = 4000
        val second = fixture.gateway()
        assertNotNull(second.restore())
        first.models()
        assertEquals(1, fixture.refreshCount)
        val refresh = fixture.requests.single {
            it.body?.contains("grant_type=refresh_token") ==
                true
        }
        assertTrue(refresh.body!!.contains("client_id=client"))
        assertTrue(refresh.body.contains("refresh_token=refresh-0"))
        assertEquals("access-1", fixture.requests.last().bearer)
        assertTrue(fixture.store.value!!.contains("refresh-1"))
        first.close()
        second.close()
    }

    @Test fun malformedOrMismatchedRegistrationIsClearedWithoutAnyRequest() = runBlocking {
        val fixture = SessionFixture()
        val first = fixture.gateway()
        fixture.connect(first)
        assertNotNull("Only a verified stored identity can be restored", fixture.store.value)
        val original = checkNotNull(fixture.store.value)
        first.close()
        for (bad in listOf(
            "not-json",
            JSONObject(original).put("version", 9).toString(),
            JSONObject(original).put("clientId", "other").toString(),
            JSONObject(original).put("hostId", "other").toString()
        )) {
            fixture.store.value = bad
            val next = fixture.gateway()
            val before = fixture.requests.size
            try {
                next.restore()
                fail("Invalid record must fail closed")
            } catch (failure: ChatGptTrialException) {
                assertEquals(TrialFailure.AUTHENTICATION, failure.kind)
            }
            assertNull(fixture.store.value)
            assertFalse(next.canRestore)
            assertEquals(before, fixture.requests.size)
            next.close()
        }
    }
}
