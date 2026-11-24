package dev.blamera.botany

import dev.blamera.chatgpt.storage.SessionStorageException
import dev.blamera.chatgpt.storage.TrialSessionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
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
class PlantAdditionalContractTest {
    @Test
    fun exhaustedQuotaBlocksNextCallEvenWithTheDefaultMemoryJournal() = runBlocking {
        var calls = 0
        val identifier =
            PlantIdentifier(
                PlantHttp { _, _ ->
                    calls++
                    PlantHttpResponse(200, plantJson())
                }
            )
        assertNotNull(identifier.identify("FAKE", byteArrayOf(1)))
        val failure = runCatching { identifier.identify("FAKE", byteArrayOf(1)) }.exceptionOrNull()
        assertEquals(PlantFailure.QUOTA, (failure as? PlantException)?.failure)
        assertEquals(1, calls)
    }

    @Test
    fun pendingProtectedLoadCannotBeOverwrittenByAnUnreadySettingsAction() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val loaded = CompletableDeferred<String?>()
        var writes = 0
        val store = object : TrialSessionStore {
            override suspend fun load() = loaded.await()
            override suspend fun save(value: String) {
                writes++
            }
            override suspend fun clear() {
                writes++
            }
        }
        try {
            val settings = PlantSettings(scope, store)
            assertTrue(settings.busy)
            settings.save("NEW-FAKE")
            settings.remove()
            assertEquals(0, writes)
            loaded.complete("OLD-FAKE")
            assertEquals("OLD-FAKE", settings.key)
            assertFalse(settings.busy)
            settings.save("NEW-FAKE")
            assertEquals(1, writes)
            assertEquals("NEW-FAKE", settings.key)
        } finally {
            loaded.complete(null)
            scope.cancel()
        }
    }

    @Test
    fun unreadableKeyOrFailedDeletionNeverAdvertisesAFalseSuccess() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val unreadable = MemoryPlantStore().apply { failed = true }
            val absent = PlantSettings(scope, unreadable)
            assertFalse(absent.configured)
            assertEquals(PlantFailure.STORAGE.explanation, absent.message)
            val store = MemoryPlantStore()
            val settings = PlantSettings(scope, store)
            settings.save("OLD-FAKE")
            store.failed = true
            settings.remove()
            assertTrue(settings.configured)
            assertEquals("OLD-FAKE", settings.key)
            assertEquals(PlantFailure.STORAGE.explanation, settings.message)
            settings.save("invalid key\n")
            assertEquals(PlantFailure.KEY.explanation, settings.message)
            assertEquals("OLD-FAKE", settings.key)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun corruptQuotaAndBackwardClockNeverSilentlyResetTheDailyBudget() {
        for (record in listOf("broken", "10:-1", "10:invalid", "11:2")) {
            val quota = PlantQuota({ record }, {}, { 10L })
            val failure = runCatching { quota.claim() }.exceptionOrNull()
            assertTrue("Fail closed for invalid/future journal", failure is PlantException)
        }
    }
}
