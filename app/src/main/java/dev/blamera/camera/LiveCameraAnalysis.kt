package dev.blamera.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.OutputTransform
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.VisionAnalyzer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(markerClass = [androidx.camera.view.TransformExperimental::class])
internal interface LiveFrame : AutoCloseable {
    fun bitmap(): Bitmap
    fun transform(): OutputTransform
}

/** millis covers analyzer.analyze only, including its lock wait, excluding conversion and publication. */
internal data class LiveResult(val result: AnalysisResult, val points: Matrix, val millis: Long)

@androidx.annotation.OptIn(markerClass = [androidx.camera.view.TransformExperimental::class])
internal class LiveCameraAnalysis(
    private val analyzer: VisionAnalyzer,
    private val scope: CoroutineScope,
    private val target: () -> OutputTransform?,
    private val report: (LiveResult?) -> Unit,
    private val gate: LiveFrameGate = sharedGate,
    private val callbacks: CoroutineDispatcher = Dispatchers.Main.immediate
) : AutoCloseable {
    @Volatile private var closed = false

    @Volatile private var enabled = true
    private var job: Job? = null

    fun offer(frame: LiveFrame) {
        val token = if (closed || !enabled) null else gate.acquire()
        if (token == null) {
            frame.close()
            return
        }
        // Enter finally even when the owner has already been cancelled, before dispatching work.
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                process(frame, token)
            } finally {
                try {
                    frame.close()
                } finally {
                    gate.release()
                }
            }
        }
    }

    private suspend fun process(frame: LiveFrame, token: Int) {
        val output = withContext(callbacks) { target() } ?: return
        if (!gate.current(token) || closed || !enabled) return
        val result = withContext(Dispatchers.Default) { analyzeFrame(frame, output) }
        publish(token, result)
    }

    private suspend fun analyzeFrame(frame: LiveFrame, output: OutputTransform): LiveResult {
        var bitmap: Bitmap? = null
        return try {
            val matrix = Matrix()
            CoordinateTransform(frame.transform(), output).transform(matrix)
            bitmap = frame.bitmap()
            val start = SystemClock.elapsedRealtime()
            val result = analyzer.analyze(bitmap)
            LiveResult(result, matrix, SystemClock.elapsedRealtime() - start)
        } catch (error: CancellationException) {
            throw error
        } catch (_: IllegalArgumentException) {
            LiveResult(AnalysisResult.Failed, Matrix(), 0)
        } catch (_: IllegalStateException) {
            LiveResult(AnalysisResult.Failed, Matrix(), 0)
        } catch (_: IndexOutOfBoundsException) {
            LiveResult(AnalysisResult.Failed, Matrix(), 0)
        } catch (_: OutOfMemoryError) {
            LiveResult(AnalysisResult.Failed, Matrix(), 0)
        } finally {
            bitmap?.recycle()
        }
    }

    private suspend fun publish(token: Int, result: LiveResult) = withContext(callbacks) {
        if (!closed && enabled && gate.current(token)) report(result)
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        invalidate()
    }

    fun invalidate() {
        gate.invalidate()
        job?.cancel()
        report(null)
    }

    override fun close() {
        closed = true
        invalidate()
    }

    private companion object {
        val sharedGate = LiveFrameGate(SystemClock::elapsedRealtime)
    }
}
