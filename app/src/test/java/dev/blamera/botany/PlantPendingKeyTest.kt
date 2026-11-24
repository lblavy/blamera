package dev.blamera.botany

import android.graphics.Bitmap
import dev.blamera.chatgpt.storage.TrialSessionStore
import dev.blamera.image.DecodedPhoto
import dev.blamera.image.PreviewDimensions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlantPendingKeyTest {
    @Test
    fun anExplicitPhotoWaitsForStoredKeyAndOnlyTheNewestPhotoCanReachHttp() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val loaded = CompletableDeferred<String?>()
        val settings = PlantSettings(
            scope,
            object : TrialSessionStore {
                override suspend fun load() = loaded.await()
                override suspend fun save(value: String) = Unit
                override suspend fun clear() = Unit
            }
        )
        var calls = 0
        val prepared = mutableListOf<Bitmap>()
        val analysis = PlantAnalysis(
            scope,
            settings,
            plantTestIdentifier(
                PlantHttp { _, _ ->
                    calls++
                    PlantHttpResponse(404, "")
                }
            )
        ) { bitmap ->
            prepared.add(bitmap)
            byteArrayOf(1)
        }
        fun photo() = DecodedPhoto(
            Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888),
            PreviewDimensions(8, 4)
        )
        try {
            analysis.start(photo())
            val current = photo()
            analysis.start(current)
            assertEquals(0, calls)
            loaded.complete("FAKE")
            assertEquals(1, calls)
            assertSame(current.bitmap, prepared.single())
            assertSame(current, analysis.snapshot!!.photo)
        } finally {
            analysis.close()
            loaded.complete(null)
            scope.cancel()
        }
    }
}
