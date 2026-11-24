package dev.blamera.benchmark

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import dev.blamera.vision.Letterbox
import dev.blamera.vision.PreparedImage
import java.nio.FloatBuffer
import java.security.MessageDigest

internal fun checkedBenchmarkBytes(bytes: ByteArray, size: Long, hash: String): ByteArray {
    require(bytes.size.toLong() == size) { "Benchmark asset length mismatch" }
    val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
        "%02x".format(it)
    }
    require(actual == hash) { "Benchmark asset SHA-256 mismatch" }
    return bytes
}

internal class OrtBenchmarkBackend(
    private val cache: BenchmarkModelCache,
    private val clock: () -> Long = System::nanoTime
) : BenchmarkBackend {
    override fun open(model: BenchmarkModel): BenchmarkSession {
        val start = clock()
        val cached = cache.prepare(model)
        val read = clock() - start
        val environment = OrtEnvironment.getEnvironment()
        environment.setTelemetry(false)
        val options = OrtSession.SessionOptions()
        var owned = false
        try {
            options.addCPU(true)
            options.setIntraOpNumThreads(2)
            options.setInterOpNumThreads(1)
            options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            val created = clock()
            val session = environment.createSession(cached.file.absolutePath, options)
            val elapsed = clock() - created
            owned = true
            return OwnedBenchmarkSession(
                environment,
                session,
                options,
                model,
                BenchmarkLoadTiming(read, elapsed, cached.hit),
                clock
            )
        } finally {
            if (!owned) options.close()
        }
    }
}

private class OwnedBenchmarkSession(
    private val environment: OrtEnvironment,
    private val session: OrtSession,
    private val options: OrtSession.SessionOptions,
    private val model: BenchmarkModel,
    private val timing: BenchmarkLoadTiming,
    private val clock: () -> Long
) : BenchmarkSession {
    override val readHashNanos: Long get() = timing.readHashNanos
    override val sessionNanos: Long get() = timing.sessionNanos
    override val cacheHit: Boolean get() = timing.cacheHit
    private var closed = false
    override fun input(values: FloatArray): BenchmarkExecution =
        input(PreparedImage(values, Letterbox(BENCHMARK_EDGE, BENCHMARK_EDGE)))
    override fun input(image: PreparedImage): BenchmarkExecution {
        check(!closed)
        val values = image.values
        require(values.size == INPUT_VALUES && values.all { it.isFinite() })
        val tensor = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(values),
            longArrayOf(1, RGB_CHANNELS, INPUT_EDGE, INPUT_EDGE)
        )
        return OwnedBenchmarkExecution(session, tensor, model, image.letterbox, clock)
    }
    override fun close() {
        if (!closed) {
            closed = true
            try {
                session.close()
            } finally {
                options.close()
            }
        }
    }
}

private class OwnedBenchmarkExecution(
    private val session: OrtSession,
    private val input: OnnxTensor,
    private val model: BenchmarkModel,
    private val projection: Letterbox,
    private val clock: () -> Long
) : BenchmarkExecution {
    private var closed = false
    override fun execute(): BenchmarkSample = evaluate(false)
    override fun quality(): BenchmarkSample = evaluate(true)
    private fun evaluate(retainMasks: Boolean): BenchmarkSample {
        check(!closed)
        val start = clock()
        session.run(mapOf("images" to input)).use { result ->
            val ran = clock()
            require(result.size() == 2)
            val rows = result[0] as OnnxTensor
            val prototypes = result[1] as OnnxTensor
            val predictions = rows.copyBenchmarkFloats()
            val masks = prototypes.copyBenchmarkFloats()
            val copied = clock()
            val count =
                validateCompact(
                    predictions,
                    masks,
                    rows.info.shape,
                    prototypes.info.shape,
                    model.classes
                )
            val labels = model.labels.ifEmpty { List(model.classes) { "class-$it" } }
            val objects = compactDetections(predictions, masks, projection, labels, retainMasks)
            val finished = clock()
            return BenchmarkSample(
                ran - start,
                copied - ran,
                count,
                finished - copied,
                finished - start,
                if (retainMasks) objects else emptyList(),
                objects.count { it.activePixels == 0 },
                objects.sumOf { it.segments }
            )
        }
    }
    override fun close() {
        if (!closed) {
            closed = true
            input.close()
        }
    }
}

private fun OnnxTensor.copyBenchmarkFloats(): FloatArray = floatBuffer.let { buffer ->
    FloatArray(buffer.remaining()).also(buffer::get)
}
private const val INPUT_VALUES = 3 * 640 * 640

private data class BenchmarkLoadTiming(
    val readHashNanos: Long,
    val sessionNanos: Long,
    val cacheHit: Boolean
)
private const val RGB_CHANNELS = 3L
private const val INPUT_EDGE = 640L
