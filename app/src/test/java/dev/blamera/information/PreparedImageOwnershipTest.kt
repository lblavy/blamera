package dev.blamera.information

import android.content.Intent
import android.graphics.Bitmap
import dev.blamera.vision.ImageBox
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PreparedImageOwnershipTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val source = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
    private val launched = mutableListOf<Intent>()
    private val box = ImageBox(0f, 0f, 64f, 32f)

    @After fun close() {
        scope.cancel()
        source.recycle()
    }

    @Test fun closedStateRejectsAdoptionWithoutTakingOwnershipOfTheCallersImage() {
        val candidate = searchImage()
        val state = state()
        state.close()
        assertThrows(IllegalStateException::class.java) { state.adopt(candidate) }
        assertNull(state.image)
        assertFalse(candidate.preview.isRecycled)
        assertTrue(launched.isEmpty())
        candidate.close()
    }

    @Test fun anAlreadyOwnedImageCannotBeReplacedOrPrematurelyRecycledByAnotherAdoption() {
        val owned = searchImage()
        val candidate = searchImage()
        val state = state()
        state.adopt(owned)
        assertThrows(IllegalStateException::class.java) { state.adopt(candidate) }
        assertSame(owned, state.image)
        assertFalse(owned.preview.isRecycled)
        assertFalse(candidate.preview.isRecycled)
        state.close()
        assertTrue(owned.preview.isRecycled)
        assertFalse(candidate.preview.isRecycled)
        assertFalse(source.isRecycled)
        candidate.close()
    }

    @Test fun adoptionWhilePreparingCannotReplaceThePendingResultOrStealTheRejectedImage() {
        val pending = CompletableDeferred<SearchImage>()
        val candidate = searchImage()
        val prepared = searchImage()
        val state = state(preparer = SearchImagePreparer { _, _ -> pending.await() })
        state.prepare(source, box)
        assertTrue(state.preparing)
        assertThrows(IllegalStateException::class.java) { state.adopt(candidate) }
        assertTrue(state.preparing)
        assertNull(state.image)
        assertFalse(candidate.preview.isRecycled)
        pending.complete(prepared)
        assertSame(prepared, state.image)
        assertTrue(launched.isEmpty())
        state.close()
        assertTrue(prepared.preview.isRecycled)
        assertFalse(candidate.preview.isRecycled)
        candidate.close()
    }

    @Test fun adoptionDuringPublicationPreservesThePublishedPixelsAndTheCallersOwnership() {
        val owned = searchImage()
        val candidate = searchImage()
        val publication = CompletableDeferred<SharedSearchImage>()
        val entry = FakeSharedImage()
        var published: SearchImage? = null
        val state = state(
            publisher = SearchImagePublisher {
                published = it
                publication.await()
            }
        )
        state.adopt(owned)
        state.search()
        assertTrue(state.sending)
        assertThrows(IllegalStateException::class.java) { state.adopt(candidate) }
        assertSame(owned, state.image)
        assertSame(owned, published)
        assertFalse(owned.preview.isRecycled)
        assertFalse(candidate.preview.isRecycled)
        publication.complete(entry)
        assertEquals(1, launched.size)
        state.close()
        assertTrue(owned.preview.isRecycled)
        assertFalse(entry.closed)
        assertFalse(candidate.preview.isRecycled)
        candidate.close()
    }

    private fun state(
        preparer: SearchImagePreparer = SearchImagePreparer { _, _ -> error("Unexpected export") },
        publisher: SearchImagePublisher = SearchImagePublisher { error("Unexpected publication") }
    ) = ObjectSearchState(
        scope,
        ObjectInformationTools(preparer, publisher, fakeImageShareSender(launched::add))
    )
}
