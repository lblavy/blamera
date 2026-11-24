package dev.blamera.faces

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FaceGalleryBootstrapTest {
    @Test fun emptyStorageBootstrapsOnceAndRecreationPrefersSavedManualGallery() = runBlocking {
        val store = FaceMemoryStore()
        var reads = 0
        val first = FaceGallerySettings(this, store, faceDecoder()) {
            reads++
            faceFixture().toByteArray()
        }
        settle(first)
        assertEquals(2, first.gallery?.identities?.size)
        assertNotNull(store.value)
        val saved = store.value
        val second = FaceGallerySettings(this, store, faceDecoder()) {
            reads++
            error("An existing valid gallery must not be replaced by the seed")
        }
        settle(second)
        assertEquals(saved, store.value)
        assertEquals(2, second.gallery?.identities?.size)
        assertEquals(1, reads)
    }

    @Test fun absentSeedIsNormalAndCorruptStoredGalleryIsNotOverwritten() = runBlocking {
        val missing = FaceGallerySettings(this, FaceMemoryStore(), faceDecoder()) { null }
        settle(missing)
        assertNull(missing.gallery)
        assertNull(missing.message)
        val store = FaceMemoryStore("invalid stored data")
        var reads = 0
        val corrupt = FaceGallerySettings(this, store, faceDecoder()) {
            reads++
            faceFixture().toByteArray()
        }
        settle(corrupt)
        assertEquals(0, reads)
        assertEquals("invalid stored data", store.value)
        assertNull(corrupt.gallery)
        assertNotNull(corrupt.message)
    }

    @Test fun invalidSeedOrFailedSaveNeverPublishesAReadyGallery() = runBlocking {
        val invalidStore = FaceMemoryStore()
        val invalid = FaceGallerySettings(this, invalidStore, faceDecoder()) {
            "invalid integrated data".toByteArray()
        }
        settle(invalid)
        assertNull(invalid.gallery)
        assertNull(invalidStore.value)
        assertNotNull(invalid.message)
        val failedStore = FaceMemoryStore().apply { fail = true }
        val failed =
            FaceGallerySettings(this, failedStore, faceDecoder()) { faceFixture().toByteArray() }
        settle(failed)
        assertNull(failed.gallery)
        assertNull(failedStore.value)
        assertNotNull(failed.message)
        assertFalse(failed.busy)
    }

    private suspend fun settle(settings: FaceGallerySettings) {
        repeat(2000) {
            yield()
            if (!settings.busy) return
            kotlinx.coroutines.delay(1)
        }
        error("Gallery load did not finish")
    }
}
