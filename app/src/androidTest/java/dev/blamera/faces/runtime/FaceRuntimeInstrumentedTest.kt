package dev.blamera.faces.runtime

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaceRuntimeInstrumentedTest {
    @Test fun publicFixturesMatchTheFrozenPcPipeline() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manifest = context.assets.open("faces/parity/manifest.json").bufferedReader().use {
            JSONObject(it.readText())
        }
        val cases = manifest.getJSONArray("cases")
        createFaceEngine(context).use { engine ->
            for (index in 0 until cases.length()) {
                val expected = cases.getJSONObject(index)
                val frame = readFrame(context, "faces/parity/" + expected.getString("image"))
                val result = engine.analyze(frame)
                when (expected.getString("expected_kind")) {
                    "none" -> assertEquals(FaceEngineResult.NoFace, result)
                    "multiple" -> checkMultiple(expected, result as FaceEngineResult.Detected)
                    "single" -> {
                        val face = (result as FaceEngineResult.Detected).faces.single()
                        checkSingle(expected, face as FaceObservation.Encoded)
                    }
                    else -> error("Unknown public fixture kind")
                }
            }
        }
    }

    private fun readFrame(context: Context, asset: String): FaceRgbFrame {
        val bitmap = context.assets.open(asset).use { checkNotNull(BitmapFactory.decodeStream(it)) }
        try {
            val colors = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(colors, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val rgb = ByteArray(colors.size * 3)
            colors.forEachIndexed { index, color ->
                rgb[index * 3] = (color shr 16).toByte()
                rgb[index * 3 + 1] = (color shr 8).toByte()
                rgb[index * 3 + 2] = color.toByte()
            }
            return FaceRgbFrame(bitmap.width, bitmap.height, rgb)
        } finally {
            bitmap.recycle()
        }
    }

    private fun checkMultiple(expected: JSONObject, actual: FaceEngineResult.Detected) {
        val references = expected.getJSONArray("faces")
        assertEquals(references.length(), actual.faces.size)
        val remaining = actual.faces.map { it as FaceObservation.Encoded }.toMutableList()
        for (index in 0 until references.length()) {
            val reference = references.getJSONObject(index)
            val bounds = reference.getJSONArray("bounds")
            val matched = remaining.minBy {
                abs(it.bounds.left - bounds.getDouble(0)) + abs(it.bounds.top - bounds.getDouble(1))
            }
            remaining.remove(matched)
            checkSingle(reference, matched)
        }
    }

    private fun checkSingle(expected: JSONObject, actual: FaceObservation.Encoded) {
        val bounds = expected.getJSONArray("bounds")
        listOf(actual.bounds.left, actual.bounds.top, actual.bounds.right, actual.bounds.bottom)
            .forEachIndexed { index, coordinate ->
                assertTrue(abs(bounds.getDouble(index) - coordinate) <= 1.0)
            }
        val vector = expected.getJSONArray("embedding")
        assertEquals(512, actual.embedding.size)
        val cosine = actual.embedding.indices.sumOf { actual.embedding[it] * vector.getDouble(it) }
        assertTrue("The native output must match the PC embedding", cosine >= .9999)
    }
}
