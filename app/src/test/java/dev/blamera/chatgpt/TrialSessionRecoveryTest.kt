package dev.blamera.chatgpt

import dev.blamera.chatgpt.storage.SessionStorageException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrialSessionRecoveryTest {
    @Test fun savingAPreferencePreservesGrantDatesAndTheMostRecentlyRotatedTokens() = runBlocking {
        val fixture = SessionFixture()
        val first = fixture.gateway()
        fixture.connect(first)
        first.models()
        val initial = JSONObject(checkNotNull(fixture.store.value))
        fixture.now = 3695
        first.rememberModel("preferred")
        val preferred = JSONObject(checkNotNull(fixture.store.value))
        assertEquals(initial.getLong("savedAt"), preferred.getLong("savedAt"))
        assertEquals(
            initial.getJSONObject("grant").getLong("expires_in"),
            preferred.getJSONObject("grant").getLong("expires_in")
        )
        val other = fixture.gateway()
        other.restore()
        assertEquals(1, fixture.refreshCount)
        first.rememberModel("first")
        val latest = JSONObject(checkNotNull(fixture.store.value))
        assertEquals(3695L, latest.getLong("savedAt"))
        assertEquals("refresh-1", latest.getJSONObject("grant").getString("refresh_token"))
        assertEquals("first", latest.getString("preferredModel"))
        first.close()
        other.close()
    }

    @Test fun anExpiredOfflineGrantIsPreservedBeforePostButNeverReusedAfterAnAmbiguousPost() =
        runBlocking {
            val fixture = SessionFixture()
            val first = fixture.gateway()
            fixture.connect(first)
            first.close()
            fixture.now = 4000
            val saved = fixture.store.value
            fixture.before =
                {
                    if (it.url.endsWith(
                            "openid-configuration"
                        )
                    ) {
                        throw ChatGptTrialException(TrialFailure.NETWORK)
                    }
                }
            val next = fixture.gateway()
            expect(TrialFailure.NETWORK) { next.restore() }
            assertEquals(saved, fixture.store.value)
            assertTrue(next.canRestore)
            assertEquals(0, fixture.refreshCount)
            fixture.before =
                {
                    if (it.body?.contains("grant_type=refresh_token") ==
                        true
                    ) {
                        throw ChatGptTrialException(TrialFailure.NETWORK)
                    }
                }
            expect(TrialFailure.NETWORK) { next.restore() }
            assertNull(fixture.store.value)
            assertFalse(next.canRestore)
            val count = fixture.requests.size
            assertNull(next.restore())
            assertEquals(count, fixture.requests.size)
            assertEquals(
                1,
                fixture.requests.count {
                    it.body?.contains("grant_type=refresh_token") ==
                        true
                }
            )
            next.close()
        }

    @Test fun concurrentClientsSerializeRotationAndObserveTheNewGrant() = runBlocking {
        val fixture = SessionFixture()
        val first = fixture.gateway()
        fixture.connect(first)
        val second = fixture.gateway()
        second.restore()
        fixture.now = 4000
        val posted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        fixture.before = {
            if (it.body?.contains("grant_type=refresh_token") == true) {
                posted.complete(Unit)
                release.await()
            }
        }
        val a = async { first.models() }
        posted.await()
        val b = async { second.models() }
        yield()
        assertEquals(
            1,
            fixture.requests.count {
                it.body?.contains("grant_type=refresh_token") ==
                    true
            }
        )
        assertFalse(b.isCompleted)
        release.complete(Unit)
        assertEquals(a.await(), b.await())
        assertEquals(1, fixture.refreshCount)
        assertTrue(
            fixture.requests.filter { it.url.endsWith("models") }.all {
                it.bearer ==
                    "access-1"
            }
        )
        first.close()
        second.close()
    }

    @Test fun disconnectDuringAnAlreadyStartedSaveCannotResurrectTheSession() = runBlocking {
        val fixture = SessionFixture()
        val subject = fixture.gateway()
        val saving = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        fixture.store.beforeSave =
            {
                withContext(NonCancellable) {
                    saving.complete(Unit)
                    release.await()
                }
            }
        val connection = async { runCatching { fixture.connect(subject) } }
        saving.await()
        val disconnect = async { subject.disconnect() }
        yield()
        release.complete(Unit)
        assertTrue(connection.await().isFailure)
        disconnect.await()
        assertNull(fixture.store.value)
        assertNull(fixture.gateway().restore())
        assertFalse(fixture.requests.any { it.url.endsWith("responses") })
        subject.close()
    }

    @Test fun revokeFailureAlwaysClearsAndAnOldInstanceCannotAdoptALaterAccount() = runBlocking {
        val fixture = SessionFixture()
        val first = fixture.gateway()
        fixture.connect(first)
        val stale = fixture.gateway()
        stale.restore()
        fixture.alter =
            { request, response ->
                if (itUrlIsRevoke(request)) TrialHttpResponse(503, "{}") else response
            }
        expect(TrialFailure.REVOKE_FAILED) { first.disconnect() }
        assertNull(fixture.store.value)
        expect(TrialFailure.AUTHENTICATION) { stale.models() }
        assertNull(fixture.gateway().restore())
        assertEquals("client", fixture.registration.issuedClientId)
        first.close()
        stale.close()
    }

    @Test fun aDifferentRefreshSubjectAndInvalidPermissionCanNeverBePersisted() = runBlocking {
        for (differentSubject in listOf(true, false)) {
            val fixture = SessionFixture()
            val first = fixture.gateway()
            fixture.connect(first)
            first.close()
            fixture.now = 4000
            fixture.alter = { request, response ->
                if (request.body?.contains("grant_type=refresh_token") == true) {
                    val json = JSONObject(response.body)
                    if (differentSubject) {
                        json.put(
                            "id_token",
                            TrialAuthorizationTest.token(
                                TrialAuthorizationTest.claims().put(
                                    "exp",
                                    100_000
                                ).put("sub", "other")
                            )
                        )
                    } else {
                        json.put("scope", "openid offline_access")
                    }
                    TrialHttpResponse(200, json.toString())
                } else {
                    response
                }
            }
            val next = fixture.gateway()
            expect(
                if (differentSubject) TrialFailure.AUTHENTICATION else TrialFailure.PLAN_PERMISSION
            ) {
                next.restore()
            }
            assertNull(fixture.store.value)
            assertFalse(next.canRestore)
            assertEquals(1, fixture.refreshCount)
            next.close()
        }
    }

    private fun itUrlIsRevoke(request: TrialHttpRequest) = request.url.endsWith("revoke")
    private suspend fun expect(kind: TrialFailure, work: suspend () -> Unit) {
        try {
            work()
            fail("Expected $kind")
        } catch (
            failure: ChatGptTrialException
        ) {
            assertEquals(kind, failure.kind)
        }
    }
}
