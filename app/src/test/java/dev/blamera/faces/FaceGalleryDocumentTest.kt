package dev.blamera.faces

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

internal fun faceFixture(): String = File("../config/fixtures/face-gallery-v1.json").readText()
internal fun faceDecoder() = FaceGalleryDecoder(
    File("src/main/assets/faces/profile-v1.json").readText()
)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FaceGalleryDocumentTest {
    @Test fun publicPcFixtureDecodesWithoutChangingItsPrototypesOrPolicy() {
        val document = faceDecoder().decode(faceFixture().toByteArray())
        assertEquals(
            listOf("Personne synthétique A", "Personne synthétique B"),
            document.identities.map {
                it.name
            }
        )
        assertEquals(512, document.identities.first().prototype.size)
        assertEquals(1.0, document.identities.first().prototype.first(), 0.0)
        assertEquals(.5, JSONObject(document.json).getJSONObject("policy").getDouble("accept"), 0.0)
    }

    @Test fun invalidEncodingJsonDepthDuplicateKeysAndTrailingDataAreRejected() {
        for (bytes in listOf(
            byteArrayOf(),
            byteArrayOf(0xC3.toByte(), 0x28),
            ("\uFEFF" + faceFixture()).toByteArray(),
            (faceFixture() + "{}").toByteArray(),
            faceFixture().replaceFirst("\"schema\": 1", "\"schema\":1,\"schema\":1").toByteArray(),
            faceFixture().replaceFirst("\"schema\": 1", "\"schema\":1.0").toByteArray(),
            faceFixture().replaceFirst("\"schema\": 1", "\"schema\":1e0").toByteArray(),
            ("[".repeat(13) + "0" + "]".repeat(13)).toByteArray(),
            ByteArray(MAX_FACE_GALLERY_BYTES + 1)
        )) {
            assertThrows(FaceGalleryException::class.java) { faceDecoder().decode(bytes) }
        }
    }

    @Test fun changedSchemaProfilePipelineKeysAndNonIntegerTypesAreRejected() {
        for (change in listOf<(JSONObject) -> Unit>(
            { it.put("schema", true) },
            { it.put("format", "other-gallery") },
            { it.put("extra", "not supported") },
            { it.getJSONObject("profile").put("dimension", 511) },
            { it.put("profile", JSONObject.NULL) },
            { it.getJSONObject("pipeline").put("preprocessing", "other") },
            {
                it.getJSONObject(
                    "pipeline"
                ).getJSONArray("models").getJSONObject(0).put("sha256", "0".repeat(64))
            },
            { it.getJSONArray("identities").getJSONObject(0).put("reference_count", true) }
        )) {
            rejects(change)
        }
    }

    @Test fun invalidIdentitiesVectorsPoliciesAndQualificationAreRejected() {
        for (change in listOf<(JSONObject) -> Unit>(
            { it.put("identities", JSONArray()) },
            { it.getJSONArray("identities").getJSONObject(1).put("id", "1".repeat(32)) },
            { it.getJSONArray("identities").getJSONObject(0).put("name", " bad ") },
            { it.getJSONArray("identities").getJSONObject(0).put("name", "bad\u200Bname") },
            { it.getJSONArray("identities").getJSONObject(0).put("name", "x".repeat(81)) },
            { it.getJSONArray("identities").getJSONObject(0).put("name", "\uE000") },
            { it.getJSONArray("identities").getJSONObject(0).put("name", "") },
            { it.getJSONArray("identities").getJSONObject(0).put("name", true) },
            { it.getJSONArray("identities").getJSONObject(0).put("id", "UPPERCASE") },
            { it.getJSONArray("identities").getJSONObject(0).put("id", JSONObject.NULL) },
            {
                it.getJSONArray("identities").getJSONObject(1).put("name", "Personne synthétique A")
            },
            { it.getJSONArray("identities").getJSONObject(0).put("reference_count", 0) },
            {
                it.getJSONArray(
                    "identities"
                ).getJSONObject(0).put("prototype", JSONArray(listOf(1.0)))
            },
            {
                it.getJSONArray("identities").getJSONObject(0).getJSONArray("prototype").put(0, 0.0)
            },
            {
                it.getJSONArray(
                    "identities"
                ).getJSONObject(0).getJSONArray("prototype").put(0, true)
            },
            { it.getJSONObject("policy").put("reject", .9) },
            { it.getJSONObject("policy").put("reject", -2) },
            { it.getJSONObject("policy").put("accept", 2) },
            { it.getJSONObject("policy").put("margin", -1) },
            { it.getJSONObject("policy").put("margin", 3) },
            {
                it.getJSONArray(
                    "identities"
                ).getJSONObject(0).getJSONArray("prototype").put(0, JSONObject.NULL)
            },
            { it.getJSONObject("qualification").getJSONObject("counts").put("total", 500) },
            { it.getJSONObject("qualification").put("status", "verified") },
            { it.getJSONObject("qualification").put("scope", "other") },
            { it.getJSONObject("qualification").put("frozen_context", "invalid") },
            { it.getJSONObject("qualification").put("test_report", "invalid") },
            { it.getJSONObject("qualification").getJSONObject("counts").put("false_names", 1) },
            { it.getJSONObject("qualification").getJSONObject("counts").put("uncertain", 1) },
            { it.getJSONObject("qualification").getJSONObject("counts").put("usable", true) },
            { it.put("exported_utc", "2026-02-30T00:00:00Z") }
        )) {
            rejects(change)
        }
    }

    private fun rejects(change: (JSONObject) -> Unit) {
        val value = JSONObject(faceFixture()).also(change)
        assertThrows(FaceGalleryException::class.java) {
            faceDecoder().decode(value.toString().toByteArray())
        }
    }

    @Test fun yearZeroIsNotAValidUtcExportDate() {
        rejects { it.put("exported_utc", "0000-01-01T00:00:00Z") }
    }

    @Test fun escapedDuplicateKeysNonJsonNumbersAndQuotedNumbersAreRejected() {
        for (value in listOf(
            faceFixture().replaceFirst("Personne synthétique A", "\\ud800"),
            faceFixture().replaceFirst("\"schema\": 1", "\"schema\":1,\"\\u0073chema\":1"),
            faceFixture().replaceFirst("\"reject\": 0.3", "\"reject\":NaN"),
            faceFixture().replaceFirst("\"reject\": 0.3", "\"reject\":1e1000"),
            faceFixture().replaceFirst("\"reject\": 0.3", "\"reject\":9223372036854775808"),
            faceFixture().replaceFirst("\"reject\": 0.3", "\"reject\":" + "1".repeat(65)),
            faceFixture().replaceFirst("\"reject\": 0.3", "\"reject\":\"0.3\""),
            faceFixture().replaceFirst("\"size\": 640", "\"size\":640.0")
        )) {
            assertThrows(FaceGalleryException::class.java) {
                faceDecoder().decode(value.toByteArray())
            }
        }
    }
}
