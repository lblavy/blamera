package dev.blamera.chatgpt

import dev.blamera.chatgpt.storage.AtomicSessionFile
import dev.blamera.chatgpt.storage.EncryptedSessionStore
import dev.blamera.chatgpt.storage.SessionCipher
import dev.blamera.chatgpt.storage.SessionStorageException
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrialSessionBoundaryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun aCancelledRealFileCommitIsCompensatedBeforeLogoutAndRestart() = runBlocking {
        val fixture = SessionFixture()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val path = File(temporary.root, "cancelled.bin")
        val writing = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        fixture.activeStore = EncryptedSessionStore(
            AtomicSessionFile(path) { output, bytes ->
                writing.complete(Unit)
                check(release.await(10, TimeUnit.SECONDS))
                output.write(bytes)
            },
            SessionCipher { key }
        )
        val subject = fixture.gateway()
        val connection = async { runCatching { fixture.connect(subject) } }
        writing.await()
        connection.cancel()
        val logout = async { subject.disconnect() }
        release.countDown()
        connection.join()
        logout.await()
        assertFalse(path.exists())
        assertTrue(fixture.registration.sessionBlocked)
        assertNull(fixture.gateway().restore())
        assertFalse(fixture.requests.any { it.url.endsWith("responses") })
        subject.close()
    }

    @Test fun logoutWithAnUndeletableFileMustStillPreventRestorationAfterRestart() = runBlocking {
        val fixture = SessionFixture()
        val first = fixture.gateway()
        fixture.connect(first)
        val stale = fixture.gateway()
        stale.restore()
        fixture.store.beforeClear = { throw SessionStorageException() }
        expect(TrialFailure.STORAGE) { first.disconnect() }
        assertNotNull("The test really keeps the old encrypted payload", fixture.store.value)
        val restart = fixture.gateway()
        assertNull("Logout must durably prohibit this old payload", restart.restore())
        expect(TrialFailure.AUTHENTICATION) { stale.models() }
        fixture.store.beforeClear = { }
        fixture.connect(restart)
        restart.close()
        assertNotNull(
            "Only a new verified login may unblock restoration",
            fixture.gateway().restore()
        )
        first.close()
        stale.close()
    }

    @Test fun actualEncryptedStoreSurvivesANewGatewayWithoutPersistingAResponse() = runBlocking {
        val fixture = SessionFixture()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val path = File(temporary.root, "session.bin")
        fun store() = EncryptedSessionStore(AtomicSessionFile(path), SessionCipher { key })
        fixture.activeStore = store()
        val first = fixture.gateway()
        fixture.connect(first)
        first.models()
        first.rememberModel("preferred")
        first.close()
        assertFalse(path.readBytes().toString(Charsets.ISO_8859_1).contains("refresh-0"))
        fixture.activeStore = store()
        val second = fixture.gateway()
        assertNotNull(second.restore())
        assertEquals("preferred", second.preferredModel)
        assertEquals(1, fixture.browserCount)
        assertFalse(fixture.requests.any { it.url.endsWith("responses") })
        second.close()
    }

    @Test fun invalidSchemaValuesAreNeverUsedAndAreRemovedBeforeAnyNetworkCall() = runBlocking {
        val fixture = SessionFixture()
        val first = fixture.gateway()
        fixture.connect(first)
        val original = checkNotNull(fixture.store.value)
        first.close()
        val changes: List<(JSONObject) -> Unit> = listOf(
            { it.put("issuer", "other") }, { it.put("resource", "other") },
            { it.put("subject", "") }, { it.put("displayName", "x".repeat(201)) },
            { it.put("savedAt", 101) }, { it.put("savedAt", -1) },
            { it.put("savedAt", "100") }, { it.put("version", "1") },
            { it.put("preferredModel", "") }, { it.put("preferredModel", "x".repeat(201)) },
            { it.getJSONObject("grant").put("access_token", "") },
            { it.getJSONObject("grant").put("refresh_token", "x".repeat(32 * 1024 + 1)) },
            { it.getJSONObject("grant").put("expires_in", 0) },
            { it.getJSONObject("grant").put("scope", "offline_access resource.invoke") },
            { it.getJSONObject("grant").put("scope", "$TRIAL_PLAN_SCOPE resource.invoke") },
            { it.getJSONObject("grant").put("scope", "$TRIAL_PLAN_SCOPE offline_access") },
            { it.getJSONObject("grant").put("token_type", "Basic") }
        )
        for (change in changes) {
            fixture.store.value = JSONObject(original).apply(change).toString()
            val before = fixture.requests.size
            val subject = fixture.gateway()
            expect(TrialFailure.AUTHENTICATION) { subject.restore() }
            assertNull(fixture.store.value)
            assertFalse(subject.canRestore)
            assertEquals(before, fixture.requests.size)
            subject.close()
        }
        fixture.store.value = "x".repeat(128 * 1024 + 1)
        expect(TrialFailure.AUTHENTICATION) { fixture.gateway().restore() }
        assertNull(fixture.store.value)
    }

    @Test fun storageFailureIsExplicitAndNeverClaimsAMemorizedAccount() = runBlocking {
        val fixture = SessionFixture()
        fixture.store.beforeSave = { throw SessionStorageException() }
        val first = fixture.gateway()
        expect(TrialFailure.STORAGE) { fixture.connect(first) }
        assertNull(fixture.store.value)
        expect(TrialFailure.AUTHENTICATION) { first.models() }
        first.close()
        val inaccessible = SessionFixture()
        inaccessible.store.beforeLoad = { throw SessionStorageException() }
        val second = inaccessible.gateway()
        expect(TrialFailure.STORAGE) { second.restore() }
        assertFalse(second.canRestore)
        second.close()
    }

    @Test fun restorationWaitsForForegroundAndCancelOrClosePreventsLateRequests() = runBlocking {
        for (close in listOf(false, true)) {
            val fixture = SessionFixture()
            val first = fixture.gateway()
            fixture.connect(first)
            first.close()
            fixture.now = 4000
            val gate = TrialAppForeground { 0L }
            val subject = fixture.gateway(gate::await)
            val before = fixture.requests.size
            val restore = async { runCatching { subject.restore() } }
            yield()
            assertFalse(restore.isCompleted)
            assertEquals(before, fixture.requests.size)
            if (close) subject.close() else subject.cancel()
            gate.update(true)
            assertTrue(restore.await().isFailure)
            assertEquals(before, fixture.requests.size)
            assertNotNull(fixture.store.value)
            subject.close()
        }
    }

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
