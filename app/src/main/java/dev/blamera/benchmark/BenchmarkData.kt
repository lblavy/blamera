package dev.blamera.benchmark

import dev.blamera.vision.Letterbox
import dev.blamera.vision.PreparedImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

internal data class BenchmarkModel(
    val id: String,
    val asset: String,
    val hash: String,
    val size: Long,
    val classes: Int,
    val labels: List<String> = emptyList(),
    val sourceHash: String = hash,
    val agnostic: Boolean = false
)
internal class BenchmarkInput(
    val id: String,
    val hash: String,
    private val loader: () -> PreparedImage,
    val referenceTensorHash: String = ""
) {
    constructor(id: String, hash: String, values: FloatArray) :
        this(id, hash, { PreparedImage(values, Letterbox(BENCHMARK_EDGE, BENCHMARK_EDGE)) })
    fun load(): PreparedImage = loader()
    fun tensorHash(values: FloatArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteBuffer.allocate(BENCHMARK_BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        for (value in values) {
            if (buffer.remaining() < BENCHMARK_FLOAT_BYTES) {
                digest.update(buffer.array())
                buffer.clear()
            }
            buffer.putFloat(value)
        }
        digest.update(buffer.array(), 0, buffer.position())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
internal data class BenchmarkSample(
    val runNanos: Long,
    val copyNanos: Long,
    val detections: Int,
    val postNanos: Long = 0,
    val totalNanos: Long = runNanos + copyNanos + postNanos,
    val objects: List<BenchmarkDetection> = emptyList(),
    val emptyMasks: Int = 0,
    val segments: Int = 0
)
internal data class BenchmarkStats(
    val count: Int,
    val medianNanos: Double,
    val minNanos: Long,
    val maxNanos: Long
)
internal interface BenchmarkExecution : AutoCloseable {
    fun execute(): BenchmarkSample
    fun quality(): BenchmarkSample = execute()
}
internal interface BenchmarkSession : AutoCloseable {
    val readHashNanos: Long
    val sessionNanos: Long
    val cacheHit: Boolean get() = false
    fun input(values: FloatArray): BenchmarkExecution
    fun input(image: PreparedImage): BenchmarkExecution = input(image.values)
}
internal fun interface BenchmarkBackend {
    fun open(model: BenchmarkModel): BenchmarkSession
}
internal data class BenchmarkBlock(
    val model: BenchmarkModel,
    val fixture: String,
    val fixtureHash: String,
    val sessionFirst: Boolean,
    val first: BenchmarkSample,
    val runs: BenchmarkStats,
    val copies: BenchmarkStats,
    val pssBefore: Long?,
    val pssAfter: Long?,
    val samples: List<BenchmarkSample> = emptyList(),
    val tensorHash: String = ""
)
internal data class BenchmarkModelTiming(
    val model: String,
    val readHashNanos: Long,
    val sessionNanos: Long,
    val pssBefore: Long?,
    val pssAfter: Long?,
    val cacheHit: Boolean = false
)
internal data class BenchmarkQuality(
    val model: String,
    val fixture: String,
    val tensorHash: String = "",
    val projection: Letterbox = Letterbox(BENCHMARK_EDGE, BENCHMARK_EDGE),
    val sample: BenchmarkSample = BenchmarkSample(0, 0, 0)
)
internal data class BenchmarkResult(
    val blocks: List<BenchmarkBlock>,
    val models: List<BenchmarkModelTiming> = emptyList(),
    val quality: List<BenchmarkQuality> = emptyList()
)
