package dev.blamera.faces

import android.graphics.Bitmap
import dev.blamera.faces.runtime.FaceBounds
import dev.blamera.faces.runtime.FaceEngine
import dev.blamera.faces.runtime.FaceEngineException
import dev.blamera.faces.runtime.FaceEngineResult
import dev.blamera.faces.runtime.FaceObservation
import dev.blamera.faces.runtime.FaceRgbFrame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
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
class FacePhotoAnalysisTest {
    @Test fun analysisWaitsForGalleryAndAReplacementReanalyzesOnlyTheCurrentPhoto() = runBlocking {
        val seed = CompletableDeferred<ByteArray?>()
        val settings = FaceGallerySettings(this, FaceMemoryStore(), faceDecoder()) { seed.await() }
        val engine = PhotoFaceEngine { recognized() }
        val analysis = FacePhotoAnalysis(this, settings, engine) { frame() }
        val photo = bitmap()
        analysis.show(photo)
        yield()
        assertEquals(0, engine.calls)
        seed.complete(faceFixture().toByteArray())
        await { analysis.state is FacePhotoState.Detected }
        assertEquals(
            "Personne synthétique A",
            (analysis.state as FacePhotoState.Detected).identity.name
        )
        settings.import { faceFixture().toByteArray() }
        await { !settings.busy && engine.calls == 2 && analysis.state is FacePhotoState.Detected }
        analysis.clear()
        settings.import { faceFixture().toByteArray() }
        await { !settings.busy }
        assertEquals(2, engine.calls)
        analysis.close()
        analysis.close()
        assertEquals(1, engine.closes)
        assertFalse(photo.isRecycled)
    }

    @Test fun absentCapacityAndUnavailableFacesNeverForceAnIdentity() = runBlocking {
        val settings = readySettings()
        val results = listOf(FaceEngineResult.NoFace, FaceEngineResult.TooManyFaces)
        val engine = PhotoFaceEngine { results[it - 1] }
        val analysis = FacePhotoAnalysis(this, settings, engine) { frame() }
        analysis.show(bitmap())
        await { analysis.state == FacePhotoState.NoFace }
        analysis.show(bitmap())
        await { analysis.state == FacePhotoState.TooManyFaces }
        analysis.close()
        val failed =
            FacePhotoAnalysis(
                this,
                settings,
                PhotoFaceEngine {
                    throw FaceEngineException()
                }
            ) { frame() }
        failed.show(bitmap())
        await { failed.state == FacePhotoState.Unavailable }
        failed.close()
    }

    @Test fun lateNativeResultsCannotReplaceNewPhotosOrReturnAfterBackground() = runBlocking {
        val settings = readySettings()
        val first = CompletableDeferred<FaceEngineResult>()
        val second = CompletableDeferred<FaceEngineResult>()
        val engine = PhotoFaceEngine { call ->
            withContext(NonCancellable) { (if (call == 1) first else second).await() }
        }
        val analysis = FacePhotoAnalysis(this, settings, engine) { frame() }
        analysis.show(bitmap())
        await { engine.calls == 1 }
        analysis.show(bitmap())
        await { engine.calls == 2 }
        second.complete(recognized())
        await { analysis.state is FacePhotoState.Detected }
        val current = analysis.state
        first.complete(FaceEngineResult.NoFace)
        repeat(10) { yield() }
        assertSame(current, analysis.state)
        analysis.background()
        assertSame(FacePhotoState.Empty, analysis.state)
        analysis.close()
    }

    @Test fun rgbConversionPreservesCallerAndRejectsOversize() = runBlocking {
        val source = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        source.setPixel(0, 0, android.graphics.Color.RED)
        source.setPixel(1, 0, android.graphics.Color.argb(128, 128, 64, 32))
        val original = source.getPixel(1, 0)
        val converted = prepareFaceFrame(source)
        assertEquals(2, converted.width)
        assertEquals(1, converted.height)
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(
                -1,
                0,
                0,
                android.graphics.Color.red(original).toByte(),
                android.graphics.Color.green(original).toByte(),
                android.graphics.Color.blue(original).toByte()
            ),
            converted.pixels
        )
        assertFalse(source.isRecycled)
        assertEquals(original, source.getPixel(1, 0))
        val huge = Bitmap.createBitmap(2049, 1, Bitmap.Config.ARGB_8888)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            runBlocking { prepareFaceFrame(huge) }
        }
        huge.recycle()
        source.recycle()
    }

    @Test fun lateOldGalleryCannotRelabelTheCurrentPhotoAfterReplacement() = runBlocking {
        val settings = readySettings()
        val old = CompletableDeferred<FaceEngineResult>()
        val engine = PhotoFaceEngine { call ->
            if (call == 1) withContext(NonCancellable) { old.await() } else recognized()
        }
        val analysis = FacePhotoAnalysis(this, settings, engine) { frame() }
        analysis.show(bitmap())
        await { engine.calls == 1 }
        val replacement = org.json.JSONObject(faceFixture())
        replacement.getJSONArray("identities").getJSONObject(0).put("name", "Nouvelle galerie")
        settings.import { replacement.toString().toByteArray() }
        await { (analysis.state as? FacePhotoState.Detected)?.identity?.name == "Nouvelle galerie" }
        old.complete(recognized())
        repeat(10) { yield() }
        assertEquals("Nouvelle galerie", (analysis.state as FacePhotoState.Detected).identity.name)
        analysis.close()
    }

    @Test fun lateBootstrapWaitsForForegroundWithoutDuplicatingWork() = runBlocking {
        val seed = CompletableDeferred<ByteArray?>()
        val settings = FaceGallerySettings(this, FaceMemoryStore(), faceDecoder()) { seed.await() }
        val engine = PhotoFaceEngine { recognized() }
        val analysis = FacePhotoAnalysis(this, settings, engine) { frame() }
        analysis.show(bitmap())
        analysis.background()
        seed.complete(faceFixture().toByteArray())
        await { !settings.busy }
        assertEquals(0, engine.calls)
        analysis.foreground()
        await { analysis.state is FacePhotoState.Detected }
        analysis.foreground()
        repeat(10) { yield() }
        assertEquals(1, engine.calls)
        analysis.close()
        analysis.show(bitmap())
        analysis.foreground()
        assertEquals(1, engine.calls)
    }

    @Test fun malformedEmbeddingsAndOwnedConversionFailuresStayLocalToFaces() = runBlocking {
        val settings = readySettings()
        val malformed = PhotoFaceEngine {
            FaceEngineResult.Detected(
                listOf(FaceObservation.Encoded(FaceBounds(0f, 0f, 2f, 1f), listOf(1.0)))
            )
        }
        val analysis = FacePhotoAnalysis(this, settings, malformed) { frame() }
        analysis.show(bitmap())
        await { analysis.state is FacePhotoState.Detected }
        assertEquals(null, (analysis.state as FacePhotoState.Detected).faces.single().identity)
        analysis.close()
        for (failure in listOf(
            IllegalArgumentException(),
            IllegalStateException(),
            OutOfMemoryError()
        )) {
            val engine = PhotoFaceEngine { recognized() }
            val failed = FacePhotoAnalysis(this, settings, engine) { throw failure }
            failed.show(bitmap())
            await { failed.state == FacePhotoState.Unavailable }
            assertEquals(0, engine.calls)
            failed.close()
        }
    }

    @Test fun rgbConversionRejectsRecycledPhotosAndOversizePortraits() = runBlocking {
        val recycled = bitmap()
        recycled.recycle()
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            runBlocking { prepareFaceFrame(recycled) }
        }
        val portrait = Bitmap.createBitmap(1, 2049, Bitmap.Config.ARGB_8888)
        try {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                runBlocking { prepareFaceFrame(portrait) }
            }
            assertFalse(portrait.isRecycled)
            assertEquals(2049, portrait.height)
        } finally {
            portrait.recycle()
        }
    }

    @Test fun closingOldAnalysisKeepsNewOwnersGalleryReplacementUpdates() = runBlocking {
        val settings = readySettings()
        val oldEngine = PhotoFaceEngine { recognized() }
        val previous = FacePhotoAnalysis(this, settings, oldEngine) { frame() }
        val currentEngine = PhotoFaceEngine { recognized() }
        val current = FacePhotoAnalysis(this, settings, currentEngine) { frame() }
        current.show(bitmap())
        await { current.state is FacePhotoState.Detected }
        previous.close()
        val replacement = org.json.JSONObject(faceFixture())
        replacement.getJSONArray("identities").getJSONObject(0).put("name", "Galerie remplacée")
        settings.import { replacement.toString().toByteArray() }
        await { (current.state as? FacePhotoState.Detected)?.identity?.name == "Galerie remplacée" }
        assertEquals(2, currentEngine.calls)
        assertEquals(0, oldEngine.calls)
        assertEquals(1, oldEngine.closes)
        assertSame(FacePhotoState.Empty, previous.state)
        current.close()
        assertEquals(1, currentEngine.closes)
    }

    private suspend fun kotlinx.coroutines.CoroutineScope.readySettings(): FaceGallerySettings {
        val settings = FaceGallerySettings(this, FaceMemoryStore(faceFixture()), faceDecoder())
        await { !settings.busy }
        return settings
    }

    private suspend fun await(condition: () -> Boolean) {
        repeat(2000) {
            yield()
            if (condition()) return
            kotlinx.coroutines.delay(1)
        }
        error("Face analysis did not reach the expected state")
    }

    private fun bitmap() = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
    private fun frame() = FaceRgbFrame(2, 1, byteArrayOf(0, 0, 0, 0, 0, 0))
    private fun recognized() = FaceEngineResult.Detected(
        listOf(FaceObservation.Encoded(FaceBounds(0f, 0f, 2f, 1f), faceVector(1.0)))
    )
    private val FacePhotoState.Detected.identity get() = requireNotNull(faces.single().identity)
    private val FacePhotoState.Detected.bounds get() = faces.single().bounds
}

internal class PhotoFaceEngine(private val answer: suspend (Int) -> FaceEngineResult) : FaceEngine {
    var calls = 0
    var closes = 0
    override suspend fun analyze(frame: FaceRgbFrame): FaceEngineResult = answer(++calls)
    override fun close() {
        closes++
    }
}
