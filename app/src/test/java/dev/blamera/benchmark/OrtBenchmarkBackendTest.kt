package dev.blamera.benchmark

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [36])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class OrtBenchmarkBackendTest {
    private fun model(id: String, file: String, classes: Int): BenchmarkModel {
        val bytes = File("../$file").readBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it)
        }
        return BenchmarkModel(id, file, hash, bytes.size.toLong(), classes)
    }

    @Test fun realNativeCompactModelsValidateNegativeAndNasaWithCopiedOutputs() {
        val models = listOf(
            model("baseline", "models/m1-yoloe-phone-assets/baseline/baseline.onnx", 80),
            model("small", "models/m1-yoloe-phone-assets/small/small.onnx", 4585),
            model("large", "models/m1-yoloe-phone-assets/large/large.onnx", 4585),
            model("xlarge", "models/m1-yoloe-phone-assets/xlarge/xlarge.onnx", 4585)
        )
        val image =
            checkNotNull(
                android.graphics.BitmapFactory.decodeFile(
                    "../models/m1-yoloe-phone-assets/inputs/nasa.png"
                )
            )
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        val nasa = dev.blamera.vision.prepareImage(pixels, image.width, image.height).values
        var tick = 0L
        val backend =
            OrtBenchmarkBackend(
                BenchmarkModelCache(
                    java.nio.file.Files.createTempDirectory("native-model").toFile()
                ) {
                    File("../$it").inputStream()
                },
                { ++tick }
            )
        for (model in models) {
            backend.open(model).use { session ->
                assertEquals(1, session.readHashNanos)
                assertEquals(1, session.sessionNanos)
                session.input(nasa).use { input ->
                    repeat(1) {
                        val sample = input.execute()
                        assertEquals(1, sample.runNanos)
                        assertEquals(1, sample.copyNanos)
                        assertTrue(sample.detections > 0)
                        assertEquals(1, sample.postNanos)
                        assertEquals(3, sample.totalNanos)
                        assertTrue(sample.segments > 0)
                        assertTrue(sample.objects.isEmpty())
                    }
                }
                session.input(nasa).use { execution ->
                    val quality = execution.quality()
                    val reference = org.json.JSONObject(
                        File(
                            "../models/m1-yoloe-phone-assets/kotlin-references/${model.id}/nasa.json"
                        ).readText()
                    )
                    val expected = reference.getJSONArray("detections")
                    assertEquals(expected.length(), quality.objects.size)
                    quality.objects.forEachIndexed { index, detection ->
                        val row = expected.getJSONObject(index)
                        assertEquals(row.getInt("classId"), detection.classId)
                        assertEquals(row.getDouble("score"), detection.score.toDouble(), .001)
                        val raw = java.util.Base64.getDecoder().decode(row.getString("maskBase64"))
                        var intersection = 0
                        var union = 0
                        raw.indices.forEach { pixel ->
                            val both = raw[pixel].toInt() and detection.mask[pixel].toInt() and 255
                            intersection += both.countOneBits()
                            val either =
                                (raw[pixel].toInt() or detection.mask[pixel].toInt()) and 255
                            union += either.countOneBits()
                        }
                        assertTrue("mask IoU", union == 0 || intersection.toDouble() / union >= .99)
                    }
                }
                val owned = session.input(nasa)
                owned.close()
                owned.close()
                assertThrows(IllegalStateException::class.java) { owned.execute() }
                val invalid = nasa.copyOf().apply { this[0] = Float.NaN }
                assertThrows(IllegalArgumentException::class.java) { session.input(invalid) }
                assertThrows(IllegalArgumentException::class.java) { session.input(FloatArray(1)) }
            }
        }
    }

    @Test fun hashAndLengthFailBeforeNativeResourcesAreOpened() {
        val spec = BenchmarkModel("bad", "unused", "no", 2, 80)
        val backend =
            OrtBenchmarkBackend(
                BenchmarkModelCache(java.nio.file.Files.createTempDirectory("bad-model").toFile()) {
                    byteArrayOf(1, 2).inputStream()
                }
            )
        assertThrows(IllegalArgumentException::class.java) { backend.open(spec) }
        assertThrows(IllegalArgumentException::class.java) { backend.open(spec.copy(size = 3)) }
    }

    @Test fun invalidOnnxFailsDuringSessionCreationWithMatchingChecksum() {
        val bytes = byteArrayOf(1, 2)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it)
        }
        assertThrows(ai.onnxruntime.OrtException::class.java) {
            OrtBenchmarkBackend(
                BenchmarkModelCache(java.nio.file.Files.createTempDirectory("bad-onnx").toFile()) {
                    bytes.inputStream()
                }
            ).open(BenchmarkModel("bad", "unused", hash, 2, 80))
        }
    }
}
