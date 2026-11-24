package dev.blamera.faces

import dev.blamera.chatgpt.storage.SessionStorageException
import dev.blamera.chatgpt.storage.TrialSessionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FaceGallerySettingsTest {
    @Test fun failedReplacementKeepsLastGalleryAndImportSurvivesReload() = runBlocking {
        val store = FaceMemoryStore()
        val settings = FaceGallerySettings(this, store, faceDecoder())
        settle(settings)
        assertNull(settings.gallery)
        settings.import { faceFixture().toByteArray() }
        settle(settings)
        assertEquals(2, settings.gallery?.identities?.size)
        val previous = settings.gallery
        val saved = store.value
        settings.import { byteArrayOf(1) }
        settle(settings)
        assertSame(previous, settings.gallery)
        assertEquals(saved, store.value)
        store.fail = true
        settings.import { faceFixture().toByteArray() }
        settle(settings)
        assertSame(previous, settings.gallery)
        assertEquals(saved, store.value)
        assertFalse(settings.message.orEmpty().contains("Personne synthétique"))
        store.fail = false
        val reloaded = FaceGallerySettings(this, store, faceDecoder())
        settle(reloaded)
        assertEquals(2, reloaded.gallery?.identities?.size)
    }

    @Test fun cancelBeforeCommitAndConcurrentImportDoNotReplaceStoredGallery() = runBlocking {
        val store = FaceMemoryStore(faceFixture())
        val settings = FaceGallerySettings(this, store, faceDecoder())
        settle(settings)
        val previous = settings.gallery
        val gate = CompletableDeferred<ByteArray>()
        settings.import { gate.await() }
        yield()
        assertTrue(settings.busy)
        var second = false
        settings.import {
            second = true
            faceFixture().toByteArray()
        }
        settings.cancel()
        settle(settings)
        gate.complete(faceFixture().toByteArray())
        yield()
        assertFalse(second)
        assertSame(previous, settings.gallery)
        assertEquals(faceFixture(), store.value)
    }

    @Test fun corruptStorageIsReportedWithoutRemovingItOrLeakingItsContent() = runBlocking {
        val store = FaceMemoryStore("PRIVATE-CORRUPT-DATA")
        val settings = FaceGallerySettings(this, store, faceDecoder())
        settle(settings)
        assertNull(settings.gallery)
        assertEquals("PRIVATE-CORRUPT-DATA", store.value)
        org.junit.Assert.assertNotNull(settings.message)
        assertFalse(settings.message.orEmpty().contains("PRIVATE"))
    }

    private suspend fun settle(settings: FaceGallerySettings) {
        repeat(1000) {
            yield()
            if (!settings.busy) return
            kotlinx.coroutines.delay(1)
        }
        error("Import did not finish")
    }

    @Test fun unavailableStorageAndUnreadableFilesPreserveExistingGallery() = runBlocking {
        val store = FaceMemoryStore(faceFixture())
        val settings = FaceGallerySettings(this, store, faceDecoder())
        settle(settings)
        val previous = settings.gallery
        for (error in listOf(java.io.IOException("PRIVATE"), SecurityException("PRIVATE"))) {
            settings.import { throw error }
            settle(settings)
            assertSame(previous, settings.gallery)
            assertEquals(faceFixture(), store.value)
            assertFalse(settings.message.orEmpty().contains("PRIVATE"))
        }
        val failed = object : TrialSessionStore {
            override suspend fun load(): String? = throw SessionStorageException()
            override suspend fun save(value: String) = Unit
            override suspend fun clear() = Unit
        }
        val unavailable = FaceGallerySettings(this, failed, faceDecoder())
        settle(unavailable)
        org.junit.Assert.assertNotNull(unavailable.message)
        assertNull(unavailable.gallery)
    }
}

internal class FaceMemoryStore(var value: String? = null) : TrialSessionStore {
    var fail = false
    override suspend fun load(): String? = value
    override suspend fun save(value: String) {
        if (fail) throw SessionStorageException()
        this.value = value
    }
    override suspend fun clear() {
        value = null
    }
}
