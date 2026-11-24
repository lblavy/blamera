package dev.blamera.vision

import android.graphics.BitmapFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
class LiveAnalyzerNativeTest {
    @Test fun liveFactoryKeepsTheMinimalModelAndItsFrenchCategoryNames() = runBlocking {
        val image = checkNotNull(BitmapFactory.decodeFile(System.getProperty("blamera.fixture")))
        val result = createLiveAnalyzer(RuntimeEnvironment.getApplication()).analyze(image)
            as AnalysisResult.Success
        val person = result.detections.single { it.classId == 0 }
        assertTrue(result.detections.all { it.classId in 0 until 80 })
        assertEquals("Personne", categoryLabel(person.classId))
        assertEquals(.8841f, person.score, .03f)
        assertTrue(person.mask.active.count { it } > 9000)
        assertTrue(person.contours.isNotEmpty())
        image.recycle()
    }
}
