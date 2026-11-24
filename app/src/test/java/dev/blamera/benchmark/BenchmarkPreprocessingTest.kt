package dev.blamera.benchmark

import android.graphics.BitmapFactory
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Properties
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RuntimeEnvironment

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [36])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class BenchmarkPreprocessingTest {
    @Test fun reportExactDecodedPixelsAndKotlinTensorsAgainstFixedPcInputs() {
        fun asset(name: String) = RuntimeEnvironment.getApplication()
            .assets.open("benchmark/$name").use { it.readBytes() }
        val spec = Properties().apply {
            asset("catalog.properties").inputStream().use(::load)
        }
        val plan = BenchmarkAssets(::asset).prepare()
        val checks = File("../build/reports/validation/m1-yoloe-capacity-phone/jvm-checks")
            .apply { mkdirs() }
        val output = java.nio.file.Files.createTempDirectory(checks.toPath(), "run-").toFile()
        val rows = JSONArray()
        for (input in plan.inputs) {
            val prepared = input.load()
            val values = prepared.values
            val tensor = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            tensor.asFloatBuffer().put(values)
            File(output, "${input.id}.f32le").writeBytes(tensor.array())
            assertEquals(
                "Reference tensor ${input.id}",
                input.referenceTensorHash,
                benchmarkSha256(tensor.array())
            )
            val pcBytes = File(
                "../models/m1-yoloe-phone-assets/tensors/${input.id}.f32le"
            ).readBytes()
            val pc = ByteBuffer.wrap(pcBytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            var maxDifference = 0f
            var different = 0
            var worst = 0
            values.forEachIndexed { index, value ->
                val difference = kotlin.math.abs(value - pc.get(index))
                if (difference > 0) different++
                if (difference > maxDifference) {
                    maxDifference = difference
                    worst = index
                }
            }
            val imageBytes = asset(spec.getProperty("${input.id}.asset"))
            val bitmap =
                requireNotNull(BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val rgb = ByteArray(pixels.size * 3)
            pixels.forEachIndexed { index, pixel ->
                rgb[index * 3] = (pixel shr 16).toByte()
                rgb[index * 3 + 1] =
                    (pixel shr 8).toByte()
                rgb[index * 3 + 2] = pixel.toByte()
            }
            bitmap.recycle()
            assertEquals(
                "Reference decoded pixels ${input.id}",
                spec.getProperty("${input.id}.pixels.sha256"),
                benchmarkSha256(rgb)
            )
            rows.put(
                JSONObject().apply {
                    put("id", input.id)
                    put("width", prepared.letterbox.width)
                    put("height", prepared.letterbox.height)
                    put("tensorSha256", benchmarkSha256(tensor.array()))
                    put("pcTensorSha256", benchmarkSha256(pcBytes))
                    put("expectedKotlinTensorSha256", input.referenceTensorHash)
                    put("pixelSha256", benchmarkSha256(rgb))
                    put("pcPixelSha256", spec.getProperty("${input.id}.pixels.sha256"))
                    put("differentValues", different)
                    put("maxAbs", maxDifference)
                    put("worstIndex", worst)
                    put("kotlinValue", values[worst])
                    put("pcValue", pc.get(worst))
                }
            )
            assertTrue(values.all { it.isFinite() && it in 0f..1f })
        }
        File(output, "manifest.json").writeText(JSONObject().put("inputs", rows).toString(2))
        assertEquals(14, rows.length())
    }
}
