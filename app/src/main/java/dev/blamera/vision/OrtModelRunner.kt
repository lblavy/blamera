package dev.blamera.vision

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.FloatBuffer
import java.security.MessageDigest

data class InferenceOutput(
    val raw: RawSegmentation,
    val loadMillis: Long,
    val inferenceMillis: Long
)

class OrtModelRunner(private val readModel: () -> ByteArray, private val expectedHash: String) {
    fun run(input: FloatArray): InferenceOutput {
        val started = System.nanoTime()
        require(input.size == RGB_CHANNELS * Letterbox.EDGE * Letterbox.EDGE)
        val bytes = readModel()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it)
        }
        if (hash != expectedHash) throw VisionException("Model SHA-256 mismatch")
        return inferOrt(input, started) { environment, options ->
            environment.createSession(bytes, options)
        }
    }
}

internal fun inferOrt(
    input: FloatArray,
    started: Long,
    open: (OrtEnvironment, OrtSession.SessionOptions) -> OrtSession
): InferenceOutput {
    val environment = OrtEnvironment.getEnvironment()
    environment.setTelemetry(false)
    // Options outlive their session; all native resources close before the shared analysis lock releases.
    OrtSession.SessionOptions().use { options ->
        options.addCPU(true)
        options.setIntraOpNumThreads(2)
        options.setInterOpNumThreads(1)
        open(environment, options).use { session ->
            val loaded = System.nanoTime()
            return InferenceOutput(
                readOrtOutput(environment, session, input),
                (loaded - started) / NANOS_PER_MILLI,
                (System.nanoTime() - loaded) / NANOS_PER_MILLI
            )
        }
    }
}

private fun readOrtOutput(
    environment: OrtEnvironment,
    session: OrtSession,
    input: FloatArray
): RawSegmentation = OnnxTensor.createTensor(
    environment,
    FloatBuffer.wrap(input),
    longArrayOf(1, RGB_CHANNELS.toLong(), Letterbox.EDGE.toLong(), Letterbox.EDGE.toLong())
).use { tensor ->
    session.run(mapOf("images" to tensor)).use { results ->
        require(results.size() == 2)
        val predictions = results[0] as OnnxTensor
        val prototypes = results[1] as OnnxTensor
        RawSegmentation(
            predictions.copyFloats(),
            prototypes.copyFloats(),
            (predictions.info as TensorInfo).shape,
            (prototypes.info as TensorInfo).shape
        )
    }
}

private const val NANOS_PER_MILLI = 1_000_000
private const val RGB_CHANNELS = 3

private fun OnnxTensor.copyFloats(): FloatArray = floatBuffer.let { buffer ->
    FloatArray(buffer.remaining()).also(buffer::get)
}
