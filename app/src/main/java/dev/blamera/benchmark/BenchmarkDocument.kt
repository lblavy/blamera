package dev.blamera.benchmark

import dev.blamera.BuildConfig
import dev.blamera.vision.ImageBox
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import org.json.JSONArray
import org.json.JSONObject

internal fun benchmarkDocument(
    runId: String,
    status: String,
    started: Long,
    plan: BenchmarkPlan?,
    result: BenchmarkResult?
): String = JSONObject().apply {
    put("schema", 2)
    put("protocol", "capacity-phone-v1")
    put("runId", runId)
    put("status", status)
    put("appVersion", BuildConfig.VERSION_NAME)
    put("appVersionCode", BuildConfig.VERSION_CODE)
    put("startedAtMillis", started)
    put("updatedAtMillis", System.currentTimeMillis())
    put("settings", settingsJson())
    put(
        "models",
        JSONArray(
            plan?.models.orEmpty().map { model ->
                JSONObject().apply {
                    put("id", model.id)
                    put("sha256", model.hash)
                    put("sourceSha256", model.sourceHash)
                    put("bytes", model.size)
                    put("classes", model.classes)
                    put("agnostic", model.agnostic)
                }
            }
        )
    )
    put(
        "fixtures",
        JSONArray(
            plan?.inputs.orEmpty().map { input ->
                JSONObject().apply {
                    put("id", input.id)
                    put("sha256", input.hash)
                    put("referenceTensorSha256", input.referenceTensorHash)
                }
            }
        )
    )
    put("timings", JSONArray(result?.models.orEmpty().map(::timingJson)))
    put("blocks", JSONArray(result?.blocks.orEmpty().map(::blockJson)))
    put("quality", JSONArray(result?.quality.orEmpty().map(::qualityJson)))
    put(
        "error",
        if (status ==
            "error"
        ) {
            "Calcul ou stockage local indisponible"
        } else {
            JSONObject.NULL
        }
    )
}.toString().also(::checkedReportDocument)

private fun timingJson(timing: BenchmarkModelTiming) = JSONObject().apply {
    put("model", timing.model)
    put("readHashNs", timing.readHashNanos)
    put("sessionNs", timing.sessionNanos)
    put("cacheHit", timing.cacheHit)
    put("pssBeforeModelKiB", timing.pssBefore ?: JSONObject.NULL)
    put("pssAfterSessionCloseKiB", timing.pssAfter ?: JSONObject.NULL)
}
private fun sampleJson(sample: BenchmarkSample) = JSONObject().apply {
    put("runNs", sample.runNanos)
    put("copyNs", sample.copyNanos)
    put("postNs", sample.postNanos)
    put("totalNs", sample.totalNanos)
    put("detections", sample.detections)
    put("emptyMasks", sample.emptyMasks)
    put("segments", sample.segments)
}
private fun statsJson(stats: BenchmarkStats) = JSONObject().apply {
    put("count", stats.count)
    put("medianNs", stats.medianNanos)
    put("minNs", stats.minNanos)
    put("maxNs", stats.maxNanos)
}
private fun blockJson(block: BenchmarkBlock) = JSONObject().apply {
    put("model", block.model.id)
    put("fixture", block.fixture)
    put("fixtureHash", block.fixtureHash)
    put("tensorSha256", block.tensorHash)
    put("sessionFirst", block.sessionFirst)
    put("first", sampleJson(block.first))
    put("samples", JSONArray(block.samples.map(::sampleJson)))
    put("run", statsJson(block.runs))
    put("copy", statsJson(block.copies))
    put("post", statsJson(benchmarkStats(block.samples.map { it.postNanos })))
    put("total", statsJson(benchmarkStats(block.samples.map { it.totalNanos })))
    put("pssBeforeBlockKiB", block.pssBefore ?: JSONObject.NULL)
    put("pssAfterBlockOpenKiB", block.pssAfter ?: JSONObject.NULL)
}
private fun qualityJson(quality: BenchmarkQuality) = JSONObject().apply {
    put("model", quality.model)
    put("fixture", quality.fixture)
    put("tensorSha256", quality.tensorHash)
    put("width", quality.projection.width)
    put("height", quality.projection.height)
    put("sample", sampleJson(quality.sample))
    put("objects", JSONArray(quality.sample.objects.map(::detectionJson)))
}
private fun boxJson(box: ImageBox) = JSONArray(listOf(box.left, box.top, box.right, box.bottom))
private fun detectionJson(detection: BenchmarkDetection) = JSONObject().apply {
    put("classId", detection.classId)
    put("nameEnglish", detection.nameEnglish)
    put("score", detection.score)
    put("box", boxJson(detection.box))
    put("networkBox", boxJson(detection.networkBox))
    put("segments", detection.segments)
    val output = ByteArrayOutputStream()
    DeflaterOutputStream(output).use { it.write(detection.mask) }
    put(
        "mask",
        JSONObject().apply {
            put("codec", "zlib-bitpack-msb")
            put("width", BENCHMARK_MASK_EDGE)
            put("height", BENCHMARK_MASK_EDGE)
            put("data", Base64.getEncoder().encodeToString(output.toByteArray()))
            put("sha256", benchmarkSha256(detection.mask))
            put("activePixels", detection.activePixels)
            put("empty", detection.activePixels == 0)
        }
    )
}

private fun settingsJson() = JSONObject().apply {
    put("cpu", true)
    put("intra", 2)
    put("inter", 1)
    put("edge", BENCHMARK_EDGE)
    put("confidence", BENCHMARK_MIN_SCORE)
    put("iou", BENCHMARK_NMS_IOU)
    put("maxDetections", BENCHMARK_ROWS)
    put("preCap", JSONObject.NULL)
    put("nmsEmbedded", true)
    put("warmups", BENCHMARK_WARMUPS)
    put("measured", BENCHMARK_MEASURED)
    put("performanceInputs", JSONArray(listOf("nasa", "lvis_371699")))
}
