package dev.blamera.botany

import dev.blamera.chatgpt.storage.SessionStorageException
import dev.blamera.chatgpt.storage.TrialSessionStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PlantSettingsTest {
    @Test
    fun keyLoadsSavesAndRemovesOnlyOnExplicitActionsWithSafeStorageFailures() = runBlocking {
        val store = MemoryPlantStore()
        val settings = PlantSettings(this, store)
        yield()
        assertNull(settings.key)
        settings.save("FAKE-KEY")
        yield()
        assertEquals("FAKE-KEY", settings.key)
        assertEquals("FAKE-KEY", PlantSettings(this, store).also { yield() }.key)
        settings.remove()
        yield()
        assertNull(settings.key)
        assertNull(store.value)
        store.failed = true
        settings.save("SECRET-DO-NOT-LEAK")
        yield()
        assertNull(settings.key)
        assertEquals(PlantFailure.STORAGE.explanation, settings.message)
        assertFalse(settings.message.orEmpty().contains("SECRET"))
    }

    @Test
    fun dailyJournalSurvivesRestartAndZeroRemainingBlocksUntilUtcReset() {
        var record: String? = null
        var day = 10L
        fun quota() = PlantQuota({ record }, { record = it }, { day })
        repeat(500) { quota().claim() }
        assertQuota { quota().claim() }
        day++
        quota().claim()
        quota().exhausted()
        assertQuota { quota().claim() }
        day++
        quota().claim()
    }

    @Test
    fun httpFailuresAreDistinctAndNoPlantIsNotAnIdentification() = runBlocking {
        for ((status, failure) in listOf(
            401 to PlantFailure.KEY,
            403 to PlantFailure.KEY,
            429 to PlantFailure.QUOTA,
            503 to PlantFailure.SERVICE,
            400 to PlantFailure.INVALID
        )) {
            var calls = 0
            val identifier =
                PlantIdentifier(
                    PlantHttp { _, _ ->
                        calls++
                        PlantHttpResponse(status, "SECRET")
                    }
                )
            val error = runCatching {
                identifier.identify("FAKE", byteArrayOf(1))
            }.exceptionOrNull()
            assertEquals(failure, (error as? PlantException)?.failure)
            assertEquals(1, calls)
        }
        assertNull(
            PlantIdentifier(
                PlantHttp { _, _ ->
                    PlantHttpResponse(404, "")
                }
            ).identify("FAKE", byteArrayOf(1))
        )
    }

    private fun assertQuota(action: () -> Unit) {
        assertEquals(
            PlantFailure.QUOTA,
            (runCatching(action).exceptionOrNull() as? PlantException)?.failure
        )
    }
}

internal class MemoryPlantStore : TrialSessionStore {
    var value: String? = null
    var failed = false
    override suspend fun load(): String? {
        if (failed) throw SessionStorageException()
        return value
    }
    override suspend fun save(value: String) {
        if (failed) throw SessionStorageException()
        this.value =
            value
    }
    override suspend fun clear() {
        if (failed) throw SessionStorageException()
        value = null
    }
}

/** Pure orchestration tests control scheduling; the concrete default IO boundary has its own test. */
internal fun plantTestIdentifier(http: PlantHttp) =
    PlantIdentifier(http, quotaDispatcher = kotlinx.coroutines.Dispatchers.Unconfined)
