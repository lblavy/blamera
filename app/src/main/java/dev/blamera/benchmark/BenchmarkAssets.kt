package dev.blamera.benchmark

import android.graphics.BitmapFactory
import dev.blamera.vision.PreparedImage
import dev.blamera.vision.prepareImage
import java.util.Properties
import org.json.JSONArray

internal data class BenchmarkPlan(
    val models: List<BenchmarkModel>,
    val inputs: List<BenchmarkInput>
)
internal class BenchmarkAssets(private val reader: (String) -> ByteArray) {
    fun prepare(): BenchmarkPlan {
        val spec = Properties().apply { reader("catalog.properties").inputStream().use(::load) }
        require(spec.getProperty("models") == "baseline,small,large,xlarge")
        require(spec.getProperty("performance.inputs") == "nasa,lvis_371699")
        val models = spec.getProperty("models").split(",").map { id -> model(spec, id) }
        val inputs = spec.getProperty("inputs").split(",").map { id ->
            BenchmarkInput(id, spec.getProperty("$id.sha256"), {
                image(spec, id)
            }, spec.getProperty("$id.tensor.sha256"))
        }
        require(
            inputs.size == BENCHMARK_FIXTURES &&
                inputs.map { it.id }.toSet().size == BENCHMARK_FIXTURES
        )
        return BenchmarkPlan(models, inputs)
    }
    private fun model(spec: Properties, id: String): BenchmarkModel {
        val classes = spec.getProperty("$id.classes").toInt()
        require(classes == if (id == "baseline") BENCHMARK_BASE_CLASSES else BENCHMARK_PF_CLASSES)
        val labels = JSONArray(String(bytes(spec, spec.getProperty("$id.labels")), Charsets.UTF_8))
        require(labels.length() == classes)
        val names = (0 until classes).map {
            labels.getString(it).also { name -> require(name.isNotBlank()) }
        }
        return BenchmarkModel(
            id,
            spec.getProperty("$id.asset"),
            spec.getProperty("$id.sha256"),
            spec.getProperty("$id.size").toLong(),
            classes,
            names,
            spec.getProperty("$id.source.sha256"),
            spec.getProperty("$id.agnostic").toBooleanStrict()
        )
    }
    private fun bytes(spec: Properties, id: String): ByteArray = checkedBenchmarkBytes(
        reader(spec.getProperty("$id.asset")),
        spec.getProperty("$id.size").toLong(),
        spec.getProperty("$id.sha256")
    )
    private fun image(spec: Properties, id: String): PreparedImage {
        val bytes = bytes(spec, id)
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        return try {
            require(
                bitmap.width == spec.getProperty("$id.width").toInt() &&
                    bitmap.height == spec.getProperty("$id.height").toInt()
            )
            require(bitmap.width.toLong() * bitmap.height <= BENCHMARK_MAX_PIXELS)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            prepareImage(pixels, bitmap.width, bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }
}
