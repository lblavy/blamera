package dev.blamera.vision

import ai.onnxruntime.OrtException
import android.graphics.Bitmap
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class VisionTiming(val loadMillis: Long, val inferenceMillis: Long, val totalMillis: Long)

class OnnxImageAnalyzer(
    private val infer: (FloatArray) -> InferenceOutput,
    private val report: (VisionTiming) -> Unit = {},
    private val decode: (
        RawSegmentation,
        Letterbox
    ) -> List<VisionDetection> = YoloPostprocessor()::decode
) : VisionAnalyzer {
    override suspend fun analyze(bitmap: Bitmap): AnalysisResult =
        withContext(Dispatchers.Default) {
            // Cancellation cannot interrupt a native CPU call. Share this lock across Activity instances.
            VisionExecution.mutex.withLock {
                currentCoroutineContext().ensureActive()
                val result = analyzeSafely(bitmap)
                currentCoroutineContext().ensureActive()
                result
            }
        }

    private fun analyzeSafely(bitmap: Bitmap): AnalysisResult = try {
        val start = System.nanoTime()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val input = prepareImage(pixels, bitmap.width, bitmap.height)
        val output = infer(input.values)
        val detections = decode(output.raw, input.letterbox)
        report(
            VisionTiming(
                output.loadMillis,
                output.inferenceMillis,
                (System.nanoTime() - start) / NANOS_PER_MILLI
            )
        )
        AnalysisResult.Success(detections)
    } catch (_: IOException) {
        AnalysisResult.Failed
    } catch (_: OrtException) {
        AnalysisResult.Failed
    } catch (_: VisionException) {
        AnalysisResult.Failed
    } catch (_: IllegalArgumentException) {
        AnalysisResult.Failed
    } catch (_: IllegalStateException) {
        AnalysisResult.Failed
    } catch (_: OutOfMemoryError) {
        AnalysisResult.Failed
    } catch (_: UnsatisfiedLinkError) {
        AnalysisResult.Failed
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000
    }
}
