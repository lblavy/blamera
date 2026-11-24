package dev.blamera.information

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import dev.blamera.vision.ImageBox
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ObjectSearchStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val source = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
    private val box = ImageBox(0f, 0f, 64f, 32f)
    private val launched = mutableListOf<Intent>()

    @After fun closeScope() = scope.cancel()

    @Test fun preparationDoesNotPublishAndDoubleTapWaitsForTheSinglePublication() {
        val pending = CompletableDeferred<SharedSearchImage>()
        var calls = 0
        val image = searchImage()
        val entry = FakeSharedImage()
        val state = state(
            image,
            SearchImagePublisher {
                calls++
                pending.await()
            }
        )
        state.search()
        state.prepare(source, box)
        state.prepare(source, box)
        assertSame(image, state.image)
        assertEquals(0, calls)
        state.search()
        state.search()
        assertEquals(1, calls)
        assertTrue(state.sending)
        pending.complete(entry)
        assertEquals(1, launched.size)
        assertEquals(Intent.ACTION_SEND, launched.single().action)
        assertFalse(state.sending)
        state.onBackground()
        state.close()
        assertFalse(entry.closed)
        assertTrue(image.preview.isRecycled)
        assertFalse(source.isRecycled)
    }

    @Test fun cancellationOrDisposalBeforeLaunchClosesALateUnpublishedEntry() = runBlocking {
        for (dispose in listOf(false, true)) {
            val pending = CompletableDeferred<SharedSearchImage>()
            val entry = FakeSharedImage()
            val state = state(
                searchImage(),
                SearchImagePublisher {
                    withContext(NonCancellable) { pending.await() }
                }
            )
            state.prepare(source, box)
            state.search()
            if (dispose) state.close() else state.onBackground()
            pending.complete(entry)
            assertTrue(launched.isEmpty())
            assertTrue(entry.closed)
            state.close()
        }
    }

    @Test fun backgroundCancelsANewPublicationAfterAnEarlierSuccessfulLensShare() = runBlocking {
        val first = FakeSharedImage()
        val second = FakeSharedImage()
        val pending = CompletableDeferred<SharedSearchImage>()
        var publications = 0
        val state = state(
            searchImage(),
            SearchImagePublisher {
                if (++publications == 1) first else withContext(NonCancellable) { pending.await() }
            }
        )
        state.prepare(source, box)
        state.search()
        assertEquals(1, launched.size)
        state.onBackground()
        state.search()
        assertTrue(state.sending)
        state.onBackground()
        pending.complete(second)
        assertEquals(1, launched.size)
        assertTrue(second.closed)
        assertFalse(first.closed)
        assertFalse(state.sending)
        state.close()
        assertFalse(first.closed)
        assertFalse(source.isRecycled)
    }

    @Test fun missingOrRefusedGoogleHandlersDiscardTheDraftAndRetryCreatesANewEntry() {
        val image = searchImage()
        val entries = mutableListOf<FakeSharedImage>()
        var calls = 0
        val tools = ObjectInformationTools(
            SearchImagePreparer { _, _ -> image },
            SearchImagePublisher { FakeSharedImage().also(entries::add) },
            ImageShareSender("dev.blamera.lens-images", { true }, {
                if (++calls == 1) throw ActivityNotFoundException() else launched.add(it)
            })
        )
        val state = ObjectSearchState(scope, tools)
        state.prepare(source, box)
        state.search()
        assertTrue(entries.single().closed)
        assertTrue(checkNotNull(state.message).contains("Impossible d’ouvrir Google Lens"))
        state.search()
        assertEquals(2, entries.size)
        assertEquals(1, launched.size)
        state.close()
        assertFalse(entries.last().closed)
        val absent = ObjectSearchState(
            scope,
            tools.copy(
                sender = ImageShareSender("dev.blamera.lens-images", {
                    false
                }, { error("No handler") })
            )
        )
        absent.prepare(source, box)
        absent.search()
        assertTrue(entries.last().closed)
        assertTrue(checkNotNull(absent.message).contains("ne peut pas recevoir"))
        absent.close()
    }

    @Test fun aSourceChangedBeforeRecompositionCannotLaunchAnOldUri() {
        var current = true
        var calls = 0
        val pending = CompletableDeferred<SharedSearchImage>()
        val entry = FakeSharedImage()
        val state = ObjectSearchState(
            scope,
            ObjectInformationTools(
                SearchImagePreparer { _, _ -> searchImage() },
                SearchImagePublisher {
                    calls++
                    pending.await()
                },
                fakeImageShareSender(launched::add)
            )
        ) { current }
        state.prepare(source, box)
        current = false
        state.search()
        assertEquals(0, calls)
        current = true
        state.search()
        current = false
        pending.complete(entry)
        assertTrue(launched.isEmpty())
        assertTrue(entry.closed)
        state.close()
    }

    @Test fun preparationAndPublicationFailuresStayRecoverableWithoutDiscardingTheSource() {
        for (failure in listOf(IOException(), OutOfMemoryError())) {
            var calls = 0
            val image = searchImage()
            val state = ObjectSearchState(
                scope,
                ObjectInformationTools(
                    SearchImagePreparer { _, _ -> if (++calls == 1) throw failure else image },
                    SearchImagePublisher { throw failure },
                    fakeImageShareSender(launched::add)
                )
            )
            state.prepare(source, box)
            assertFalse(state.preparing)
            assertTrue(state.message != null)
            state.prepare(source, box)
            assertSame(image, state.image)
            state.search()
            assertFalse(state.sending)
            assertTrue(state.message != null)
            assertTrue(launched.isEmpty())
            state.close()
            assertFalse(source.isRecycled)
        }
    }

    @Test fun cancelledPreparationRecyclesLateOwnedPreviewAndNeverPublishes() = runBlocking {
        val pending = CompletableDeferred<SearchImage>()
        val image = searchImage()
        val state = ObjectSearchState(
            scope,
            ObjectInformationTools(
                SearchImagePreparer { _, _ -> withContext(NonCancellable) { pending.await() } },
                SearchImagePublisher { error("No publication") },
                fakeImageShareSender(launched::add)
            )
        )
        state.prepare(source, box)
        state.close()
        pending.complete(image)
        assertTrue(image.preview.isRecycled)
        state.prepare(source, box)
        state.search()
        assertTrue(launched.isEmpty())
    }

    private fun state(image: SearchImage, publisher: SearchImagePublisher) = ObjectSearchState(
        scope,
        ObjectInformationTools(
            SearchImagePreparer {
                    _,
                    _
                ->
                image
            },
            publisher,
            fakeImageShareSender(launched::add)
        )
    )
}
