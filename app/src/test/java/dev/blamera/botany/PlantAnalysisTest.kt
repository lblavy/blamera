package dev.blamera.botany

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.blamera.image.DecodedPhoto
import dev.blamera.image.ImageCrop
import dev.blamera.image.PreviewDimensions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlantAnalysisTest {
    @Test
    fun onlyExplicitStartWithKeyRunsAndHypothesisRequiresExactPixels() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val settings = PlantSettings(scope, MemoryPlantStore())
        val response = CompletableDeferred<PlantHttpResponse>()
        var calls = 0
        val state = PlantAnalysis(
            scope,
            settings,
            plantTestIdentifier(
                PlantHttp { key, bytes ->
                    assertEquals("FAKE", key)
                    assertArrayEquals(byteArrayOf(1, 2), bytes)
                    calls++
                    response.await()
                }
            )
        ) { byteArrayOf(1, 2) }
        val photo = photo()
        try {
            state.start(photo)
            assertNull(state.snapshot)
            settings.save("FAKE")
            assertEquals(0, calls)
            state.start(photo)
            assertEquals(1, calls)
            assertTrue(state.snapshot!!.running)
            assertNull(state.hint(photo, ImageCrop(0, 0, 8, 4)))
            response.complete(PlantHttpResponse(200, plantJson(remaining = 99)))
            assertFalse(state.snapshot!!.running)
            assertEquals("Rosa canina", state.hint(photo, ImageCrop(0, 0, 8, 4)))
            assertNull(state.hint(photo(), ImageCrop(0, 0, 8, 4)))
            assertNull(state.hint(photo, ImageCrop(1, 0, 8, 4)))
            val saved = state.snapshot
            state.cancel(clear = true)
            state.restore(saved)
            assertSame(saved, state.snapshot)
            assertEquals(1, calls)
        } finally {
            state.close()
            scope.cancel()
        }
    }

    @Test
    fun replacementBackgroundKeyChangeAndCloseRejectLateResults() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val settings = PlantSettings(scope, MemoryPlantStore()).also { it.save("FAKE") }
        val responses = mutableListOf<CompletableDeferred<PlantHttpResponse>>()
        val state = PlantAnalysis(
            scope,
            settings,
            plantTestIdentifier(
                PlantHttp { _, _ ->
                    CompletableDeferred<PlantHttpResponse>().also { responses.add(it) }.let {
                        withContext(NonCancellable) { it.await() }
                    }
                }
            )
        ) { byteArrayOf(1) }
        try {
            state.start(photo())
            state.start(photo())
            responses[0].complete(PlantHttpResponse(200, plantJson(remaining = 99)))
            assertTrue(state.snapshot!!.running)
            state.cancel()
            responses[1].complete(PlantHttpResponse(200, plantJson(remaining = 99)))
            assertNull(state.snapshot!!.result)
            assertFalse(state.snapshot!!.running)
            state.start(photo())
            settings.save("NEW-FAKE")
            responses[2].complete(PlantHttpResponse(200, plantJson(remaining = 99)))
            assertNull(state.snapshot)
            state.start(photo())
            state.close()
            responses[3].complete(PlantHttpResponse(200, plantJson(remaining = 99)))
            state.start(photo())
            assertEquals(4, responses.size)
            assertNull(state.snapshot)
        } finally {
            responses.forEach { it.complete(PlantHttpResponse(404, "")) }
            scope.cancel()
        }
    }

    @Test
    fun noPlantAndServiceErrorsFinishIndependentlyAndRemainSafe() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val settings = PlantSettings(scope, MemoryPlantStore()).also { it.save("FAKE") }
        try {
            for (status in listOf(404, 401, 429, 503, 400)) {
                val state =
                    PlantAnalysis(
                        scope,
                        settings,
                        plantTestIdentifier(
                            PlantHttp { _, _ ->
                                PlantHttpResponse(status, "SECRET")
                            }
                        )
                    ) { byteArrayOf(1) }
                state.start(photo())
                assertFalse(state.snapshot!!.running)
                assertNull(state.snapshot!!.result)
                assertFalse(state.snapshot!!.status.contains("SECRET"))
                assertTrue(state.snapshot!!.status.isNotBlank())
                state.close()
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun exportIsJpegAtMost1280PixelsWithoutSourceMetadata() = runBlocking {
        val source = Bitmap.createBitmap(2560, 1280, Bitmap.Config.ARGB_8888)
        try {
            val jpeg = preparePlantJpeg(source)
            assertTrue(jpeg.size in 1..MAX_PLANT_JPEG)
            val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            assertEquals(1280, decoded.width)
            assertEquals(640, decoded.height)
            assertFalse(source.isRecycled)
            assertFalse(jpeg.toString(Charsets.ISO_8859_1).contains("Exif"))
            decoded.recycle()
        } finally {
            source.recycle()
        }
    }

    @Test
    fun preparationFailuresAndCancellationNeverReachHttp() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val settings = PlantSettings(scope, MemoryPlantStore()).also { it.save("FAKE") }
        var calls = 0
        val identifier = plantTestIdentifier(
            PlantHttp { _, _ ->
                calls++
                PlantHttpResponse(404, "")
            }
        )
        try {
            for (failure in listOf(
                IllegalArgumentException("SECRET"),
                IllegalStateException("SECRET"),
                OutOfMemoryError("SECRET")
            )) {
                val state = PlantAnalysis(scope, settings, identifier) { throw failure }
                state.start(photo())
                assertFalse(state.snapshot!!.running)
                assertFalse(state.snapshot!!.status.contains("SECRET"))
                state.close()
            }
            val pending = CompletableDeferred<ByteArray>()
            val state = PlantAnalysis(scope, settings, identifier) {
                withContext(NonCancellable) { pending.await() }
            }
            state.start(photo())
            state.cancel()
            pending.complete(byteArrayOf(1))
            assertEquals(0, calls)
            assertFalse(state.snapshot!!.running)
            state.close()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun replacingTheKeyAlsoInvalidatesAnArchivedOriginalResult() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val settings = PlantSettings(scope, MemoryPlantStore()).also { it.save("FAKE") }
        val state =
            PlantAnalysis(
                scope,
                settings,
                plantTestIdentifier(
                    PlantHttp { _, _ ->
                        PlantHttpResponse(200, plantJson(remaining = 99))
                    }
                )
            ) { byteArrayOf(1) }
        try {
            val image = photo()
            state.start(image)
            val saved = state.snapshot
            assertEquals(
                "Rosa canina",
                state.hint(DecodedPhoto(image.bitmap, image.original), ImageCrop(0, 0, 8, 4))
            )
            settings.save("NEW-FAKE")
            state.restore(saved)
            assertNull(state.snapshot)
            state.close()
            state.restore(saved)
            assertNull(state.snapshot)
        } finally {
            state.close()
            scope.cancel()
        }
    }

    private fun photo() =
        DecodedPhoto(Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888), PreviewDimensions(8, 4))
}
