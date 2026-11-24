package dev.blamera.faces.runtime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FaceMultipleParityTest {
    @Test fun twentyRealVectorsExceedTheGalleryLimitButRemainValidPublicParity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manifest = org.json.JSONObject(
            context.assets.open("faces/parity/manifest.json").bufferedReader().use {
                it.readText()
            }
        )
        val multiple = manifest.getJSONArray("cases").getJSONObject(2)
        val originals = multiple.getJSONArray("faces")
        multiple.put("faces", org.json.JSONArray(List(20) { originals.getJSONObject(it % 2) }))
        val bytes = manifest.toString().toByteArray()
        assertTrue(
            "The fixture must exercise the larger parity-specific budget",
            bytes.size > 122880
        )
        val cases =
            loadFaceParityCases(
                context,
                "4a6472a476b37ec36626f41542d7fd41f13da63dfbeb09cd1ffbc480993aded1"
            ) { path ->
                if (path ==
                    "faces/parity/manifest.json"
                ) {
                    bytes.inputStream()
                } else {
                    context.assets.open(path)
                }
            }
        assertEquals(20, (cases.last().expected as FaceParityExpected.Multiple).faces.size)
        multiple.put("faces", org.json.JSONArray(List(21) { originals.getJSONObject(it % 2) }))
        assertThrows(IllegalArgumentException::class.java) {
            loadFaceParityCases(
                context,
                "4a6472a476b37ec36626f41542d7fd41f13da63dfbeb09cd1ffbc480993aded1"
            ) { path ->
                if (path ==
                    "faces/parity/manifest.json"
                ) {
                    manifest.toString().byteInputStream()
                } else {
                    context.assets.open(path)
                }
            }
        }
    }

    @Test fun schemaTwoLoadsTheIndividualReferencesForEveryPublicFace() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cases =
            loadFaceParityCases(
                context,
                "4a6472a476b37ec36626f41542d7fd41f13da63dfbeb09cd1ffbc480993aded1"
            )
        val multiple = cases.last().expected as FaceParityExpected.Multiple
        assertEquals(2, multiple.faces.size)
        assertEquals(512, multiple.faces[1].embedding.size)
    }

    @Test fun publicMultipleFacesAreAllComparedByGeometryEvenWhenDetectorOrderChanges() {
        val pixels = ByteArray(640 * 640 * 3)
        val single = FaceRgbFrame(640, 640, pixels)
        val multiple = FaceRgbFrame(640, 640, pixels.copyOf().apply { this[0] = 1 })
        var encodings = 0
        val kernel = object : FaceKernel {
            override fun detect(frame: FaceRgbFrame, transform: FaceDetectorTransform) =
                if (frame.pixels[0].toInt() ==
                    1
                ) {
                    listOf(row(220.0), row(20.0))
                } else {
                    listOf(row(20.0))
                }
            override fun encode(frame: FaceRgbFrame, detection: FaceDetection): FaceEncoding {
                encodings++
                return FaceEncoding(ByteArray(112 * 112 * 3), vector(detection.bounds.left > 100))
            }
            override fun close() = Unit
        }
        val report = verifyFaceParity(
            kernel,
            listOf(
                FaceParityCase(single, expected(20.0)),
                FaceParityCase(
                    multiple,
                    FaceParityExpected.Multiple(listOf(expected(20.0), expected(220.0)))
                )
            )
        )
        assertEquals("Every public face needs its own alignment and embedding check", 3, encodings)
        assertEquals(1.0, report.embeddingCosineMin, 0.0)
    }

    private fun vector(second: Boolean) = List(512) { if (it == if (second) 1 else 0) 1.0 else 0.0 }
    private fun row(left: Double) = listOf(
        left, 20.0, 40.0, 40.0, left + 5, 30.0, left + 25, 30.0,
        left + 15, 40.0, left + 5, 50.0, left + 25, 50.0, .95
    )
    private fun expected(left: Double) = FaceParityExpected.Single(
        listOf(left, 20.0, left + 40, 60.0),
        row(left).subList(4, 14),
        ByteArray(112 * 112 * 3),
        vector(left > 100)
    )
}
