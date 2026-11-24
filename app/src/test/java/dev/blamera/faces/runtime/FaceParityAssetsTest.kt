package dev.blamera.faces.runtime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FaceParityAssetsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val profile = "4a6472a476b37ec36626f41542d7fd41f13da63dfbeb09cd1ffbc480993aded1"

    @Test fun bundledPublicReferencesHaveAllKindsAndExactlyTheApprovedPipeline() {
        val cases = loadFaceParityCases(context, profile)
        assertEquals(3, cases.size)
        assertEquals(3, cases.map { it.expected::class }.toSet().size)
        val single = cases.first().expected as FaceParityExpected.Single
        assertEquals(512, single.embedding.size)
        assertEquals(112 * 112 * 3, single.alignedRgb.size)
    }

    @Test fun rejectsSchemaProfileAndToleranceSubstitutions() {
        invalid { put("schema", 3) }
        invalid { getJSONObject("profile").put("key", "wrong") }
        invalidText(original().toString().replace("\"dimension\":512", "\"dimension\":512.0"))
        invalid { getJSONObject("tolerances").put("bounds_abs_px", true) }
        invalid { getJSONObject("tolerances").put("embedding_cosine_min", .9) }
        invalid { put("unexpected", 1) }
        invalid { put("cases", JSONArray()) }
    }

    @Test fun rejectsImageTamperingTraversalAndIncompatibleDimensions() {
        invalid { firstCase().put("image_sha256", "0".repeat(64)) }
        invalid { firstCase().put("image", "../private.png") }
        invalid { firstCase().put("width", 513) }
        invalidText(original().toString().replaceFirst("\"height\":512", "\"height\":512.0"))
        invalid { firstCase().put("expected_kind", "unknown") }
        invalid { firstCase().put("aligned", "images/blank.png") }
    }

    @Test fun rejectsWrongVectorSizesTypesNormalizationAndIncompleteCoverage() {
        invalid { firstCase().put("embedding", JSONArray(listOf(1.0))) }
        invalid { firstCase().getJSONArray("embedding").put(0, "1.0") }
        invalid { firstCase().put("embedding", JSONArray(List(512) { 0.0 })) }
        invalid { firstCase().put("landmarks", JSONArray(listOf(1.0))) }
        invalid { getJSONArray("cases").put(2, getJSONArray("cases").getJSONObject(1)) }
    }

    @Test fun assetReadsAreBoundedAndPropagateIoWithoutReturningPartialSuccess() {
        val bytes = faceAssetBytes(context, "faces/parity/images/blank.png", 100000)
        assertTrue(bytes.isNotEmpty())
        rejects { faceAssetBytes(context, "faces/parity/images/blank.png", 1) }
        try {
            faceAssetBytes(context, "faces/parity/missing.png", 100)
            fail("Missing assets must fail")
        } catch (_: IOException) {
            // A missing file cannot qualify the native pipeline.
        }
    }

    @Test fun corruptAndOversizedDecodedImagesCannotBecomeFrames() {
        invalidImage(byteArrayOf(1, 2, 3))
        val bitmap = android.graphics.Bitmap.createBitmap(
            2049,
            1,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val buffer = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, buffer)
        bitmap.recycle()
        invalidImage(buffer.toByteArray())
    }

    private fun invalidImage(bytes: ByteArray) {
        val manifest = original()
        val hash = java.security.MessageDigest.getInstance(
            "SHA-256"
        ).digest(bytes).joinToString("") {
            "%02x".format(it)
        }
        manifest.firstCase().put("image_sha256", hash)
        rejects {
            loadFaceParityCases(context, profile) { path ->
                when (path) {
                    "faces/parity/manifest.json" -> manifest.toString().byteInputStream()
                    "faces/parity/images/portrait.png" -> bytes.inputStream()
                    else -> context.assets.open(path)
                }
            }
        }
    }

    private fun invalid(change: JSONObject.() -> Unit) {
        val manifest = original().apply(change)
        invalidText(manifest.toString())
    }

    private fun invalidText(text: String) {
        rejects {
            loadFaceParityCases(context, profile) { path ->
                if (path == "faces/parity/manifest.json") {
                    text.byteInputStream()
                } else {
                    context.assets.open(path)
                }
            }
        }
    }

    private fun original() = JSONObject(
        context.assets.open("faces/parity/manifest.json").bufferedReader().use {
            it.readText()
        }
    )
    private fun JSONObject.firstCase() = getJSONArray("cases").getJSONObject(0)
    private fun rejects(block: () -> Unit) {
        try {
            block()
            fail("An incompatible public reference must be refused")
        } catch (_: IllegalArgumentException) {
            // Strict contract violation.
        }
    }
}
