package dev.blamera.botany

import android.graphics.Bitmap
import dev.blamera.image.DecodedPhoto
import dev.blamera.image.PreviewDimensions
import java.io.File
import javax.crypto.KeyGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidPlantAnalysisTest {
    @Test
    fun protectedKeyAndQuotaSurviveRestartWithoutAutomaticUploads() = runBlocking {
        val application = RuntimeEnvironment.getApplication()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val path = File(application.noBackupFilesDir, "plantnet-key-v1.bin")
        application.getSharedPreferences("plantnet-quota", 0).edit().clear().commit()
        var calls = 0
        val http = PlantHttp { _, _ ->
            calls++
            PlantHttpResponse(404, "", remaining = 0)
        }
        fun create() = createPlantAnalysis(application, scope, { key }, http)
        suspend fun ready(state: PlantAnalysis) {
            withTimeout(5000) { while (state.settings.busy) delay(1) }
        }
        var first: PlantAnalysis? = null
        var second: PlantAnalysis? = null
        try {
            val active = create().also { first = it }
            ready(active)
            active.settings.save("FAKE-PRIVATE-KEY")
            ready(active)
            assertTrue(active.settings.configured)
            assertTrue(path.isFile)
            assertFalse(path.readBytes().toString(Charsets.ISO_8859_1).contains("FAKE-PRIVATE-KEY"))
            assertEquals(0, calls)
            val photo =
                DecodedPhoto(
                    Bitmap.createBitmap(16, 8, Bitmap.Config.ARGB_8888),
                    PreviewDimensions(16, 8)
                )
            active.start(photo)
            withTimeout(5000) { while (active.snapshot?.running == true) delay(1) }
            assertEquals(1, calls)
            active.close()
            val resumed = create().also { second = it }
            ready(resumed)
            assertEquals("FAKE-PRIVATE-KEY", resumed.settings.key)
            assertEquals(1, calls)
            resumed.start(photo)
            withTimeout(5000) { while (resumed.snapshot?.running == true) delay(1) }
            assertEquals(1, calls)
            assertEquals(PlantFailure.QUOTA.explanation, resumed.snapshot!!.status)
            resumed.settings.remove()
            ready(resumed)
            assertFalse(resumed.settings.configured)
            assertFalse(path.exists())
            photo.bitmap.recycle()
        } finally {
            first?.close()
            second?.close()
            scope.cancel()
            path.delete()
        }
    }
}
