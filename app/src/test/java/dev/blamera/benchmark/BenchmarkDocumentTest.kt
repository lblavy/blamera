package dev.blamera.benchmark

import dev.blamera.BuildConfig
import dev.blamera.vision.ImageBox
import dev.blamera.vision.Letterbox
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.InflaterInputStream
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [36])
class BenchmarkDocumentTest {
    @Test fun durableDocumentContainsPairedSamplesTotalsLabelsAndVerifiableCompressedMask() {
        val model = BenchmarkModel("baseline", "b", "a".repeat(64), 2, 80)
        val input = BenchmarkInput("nasa", "b".repeat(64), floatArrayOf(1f))
        val mask = ByteArray(3200).apply { this[0] = 128.toByte() }
        val box = ImageBox(0f, 0f, 1f, 1f)
        val detection = BenchmarkDetection(15, "cat", .9f, box, box, mask, 1, 4)
        val sample = BenchmarkSample(10, 2, 1, 7, 19, listOf(detection), 0, 4)
        val block =
            BenchmarkBlock(
                model, "nasa", input.hash, true, sample,
                benchmarkStats(
                    listOf(10)
                ),
                benchmarkStats(listOf(2)), null, 100, listOf(sample), "c".repeat(64)
            )
        val result =
            BenchmarkResult(
                listOf(block),
                listOf(BenchmarkModelTiming("baseline", 3, 4, 80, 90)),
                listOf(
                    BenchmarkQuality("baseline", "nasa", "c".repeat(64), Letterbox(100, 50), sample)
                )
            )
        val document =
            JSONObject(
                benchmarkDocument(
                    "run-test",
                    "complete",
                    12,
                    BenchmarkPlan(listOf(model), listOf(input)),
                    result
                )
            )
        assertEquals("complete", document.getString("status"))
        assertEquals(BuildConfig.VERSION_CODE, document.getInt("appVersionCode"))
        assertEquals(BuildConfig.VERSION_NAME, document.getString("appVersion"))
        assertEquals(
            19,
            document.getJSONArray(
                "blocks"
            ).getJSONObject(0).getJSONArray("samples").getJSONObject(0).getLong("totalNs")
        )
        val objectRow = document.getJSONArray(
            "quality"
        ).getJSONObject(0).getJSONArray("objects").getJSONObject(0)
        assertEquals("cat", objectRow.getString("nameEnglish"))
        val encoded = objectRow.getJSONObject("mask")
        val raw = InflaterInputStream(
            ByteArrayInputStream(Base64.getDecoder().decode(encoded.getString("data")))
        ).use {
            it.readBytes()
        }
        assertArrayEquals(mask, raw)
        assertEquals(benchmarkSha256(mask), encoded.getString("sha256"))
        assertEquals(1, encoded.getInt("activePixels"))
        val cancelled = JSONObject(benchmarkDocument("run-test", "cancelled", 12, null, null))
        assertEquals(0, cancelled.getJSONArray("quality").length())
        assertTrue(cancelled.isNull("error"))
    }
}
