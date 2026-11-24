package dev.blamera.vision

import android.graphics.BitmapFactory
import dev.blamera.BuildConfig
import java.io.File
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
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
class PhotoAnalyzerNativeTest {
    @Test fun productionLargeMatchesFrozenMasksAndNamesOnTwoFixtures() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val cachedModel =
            File(context.cacheDir, "photo-model/${BuildConfig.PHOTO_MODEL_SHA256}.onnx")
        assertFalse("The production pipeline must also work on its first use", cachedModel.exists())
        println("Fresh production model cache path length: ${cachedModel.absolutePath.length}")
        val analyzer = createPhotoAnalyzer(context)
        val name = createPhotoCategoryLabels(context)
        val rawNames = context.assets.open(BuildConfig.PHOTO_LABELS_ASSET)
            .bufferedReader(Charsets.UTF_8).use { JSONArray(it.readText()) }
        val frenchNames = context.assets.open("photo/labels-fr.json")
            .bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()).getJSONArray("labels") }
        for (fixture in listOf("nasa", "lvis_371699")) {
            val image =
                checkNotNull(
                    BitmapFactory.decodeFile("../models/m1-yoloe-phone-assets/inputs/$fixture.png")
                )
            val result = analyzer.analyze(image) as AnalysisResult.Success
            assertTrue(cachedModel.isFile)
            val reference = JSONObject(
                File(
                    "../models/m1-yoloe-phone-assets/kotlin-references/large/$fixture.json"
                ).readText()
            )
                .getJSONArray("detections")
            assertEquals(reference.length(), result.detections.size)
            assertTrue(result.detections.any { it.classId > 79 })
            result.detections.forEachIndexed { index, actual ->
                val expected = reference.getJSONObject(index)
                assertEquals(expected.getInt("classId"), actual.classId)
                assertEquals(expected.getString("nameEnglish"), rawNames.getString(actual.classId))
                assertEquals(frenchNames.getString(actual.classId), name(actual.classId))
                assertEquals(expected.getDouble("score"), actual.score.toDouble(), .001)
                val box = expected.getJSONArray("box")
                listOf(actual.box.left, actual.box.top, actual.box.right, actual.box.bottom)
                    .forEachIndexed { coordinate, value ->
                        assertEquals(box.getDouble(coordinate), value.toDouble(), .01)
                    }
                val mask = Base64.getDecoder().decode(expected.getString("maskBase64"))
                actual.mask.active.forEachIndexed { pixel, active ->
                    val bit = mask[pixel / 8].toInt() and (128 shr (pixel % 8)) != 0
                    assertEquals("$fixture/$index mask pixel $pixel", bit, active)
                }
                assertTrue(actual.contours.isNotEmpty())
                assertTrue(
                    actual.contours.all { segment ->
                        segment.start.x in 0f..image.width.toFloat() &&
                            segment.end.y in 0f..image.height.toFloat()
                    }
                )
            }
            assertTrue(result.detections.any { name(it.classId) != rawNames.getString(it.classId) })
            image.recycle()
            println(
                "Production YOLOE Large: $fixture ${result.detections.size} detections, exact frozen160 masks, labels matched"
            )
        }
        val knownNames = mapOf(
            780 to ("cat" to "Chat"),
            1363 to ("dog" to "Chien"),
            525 to ("bottle" to "Bouteille"),
            2163 to ("person" to "Personne"),
            3307 to ("rapper" to "rappeur"),
            323 to ("bat" to "chauve-souris"),
            297 to ("baseball bat" to "Batte de baseball"),
            2729 to ("mouse" to "Souris")
        )
        knownNames.forEach { (classId, expected) ->
            assertEquals(expected.first, rawNames.getString(classId))
            assertEquals(expected.second, name(classId))
        }
    }
}
