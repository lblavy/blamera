package dev.blamera.vision

import ai.onnxruntime.OrtSession
import java.io.File

internal class OrtPhotoRunner(private val modelFile: () -> File) {
    fun run(input: FloatArray): InferenceOutput {
        val started = System.nanoTime()
        require(
            input.size == RGB_PLANES * Letterbox.EDGE * Letterbox.EDGE &&
                input.all(Float::isFinite)
        )
        val file = modelFile()
        return inferOrt(input, started) { environment, options ->
            options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            environment.createSession(file.absolutePath, options)
        }
    }

    private companion object {
        const val RGB_PLANES = 3
    }
}
