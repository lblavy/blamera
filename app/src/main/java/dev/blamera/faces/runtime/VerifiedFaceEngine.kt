package dev.blamera.faces.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.opencv.core.CvException

internal class VerifiedFaceEngine(
    private val report: (String, Int, Long) -> Unit = { _, _, _ -> },
    private val open: suspend () -> FaceKernel
) : FaceEngine {
    private val calls = Mutex()
    private val lifecycle = Any()
    private var closed = false
    private var working = false
    private var kernel: FaceKernel? = null

    override suspend fun analyze(frame: FaceRgbFrame): FaceEngineResult =
        withContext(Dispatchers.Default) {
            val started = System.nanoTime()
            calls.withLock {
                begin()
                try {
                    validateFaceFrame(frame)
                    val current = kernel ?: open().also { kernel = it }
                    currentCoroutineContext().ensureActive()
                    analyze(current, frame).also { result ->
                        val count = (result as? FaceEngineResult.Detected)?.faces?.size ?: 0
                        report(
                            result.kind(),
                            count,
                            (System.nanoTime() - started) / NANOS_PER_MILLISECOND
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: RuntimeException) {
                    throw FaceEngineException()
                } catch (_: LinkageError) {
                    throw FaceEngineException()
                } catch (_: OutOfMemoryError) {
                    throw FaceEngineException()
                } finally {
                    finish()
                }
            }
        }

    private suspend fun analyze(current: FaceKernel, frame: FaceRgbFrame): FaceEngineResult {
        val transform = detectorTransform(frame)
        val detections = remapFaceCandidates(current.detect(frame, transform), frame, transform)
        currentCoroutineContext().ensureActive()
        return when {
            detections.isEmpty() -> FaceEngineResult.NoFace
            detections.size > FACE_MAX_CANDIDATES -> FaceEngineResult.TooManyFaces
            else -> FaceEngineResult.Detected(detections.map { encodeFace(current, frame, it) })
        }
    }

    private suspend fun encodeFace(
        current: FaceKernel,
        frame: FaceRgbFrame,
        detection: FaceDetection
    ): FaceObservation {
        currentCoroutineContext().ensureActive()
        if (!validFaceDetection(
                detection,
                frame
            )
        ) {
            return FaceObservation.Unavailable(detection.bounds)
        }
        return try {
            val encoded = current.encode(frame, detection)
            currentCoroutineContext().ensureActive()
            require(
                encoded.alignedRgb.size == FACE_ALIGNED_SIZE * FACE_ALIGNED_SIZE * FACE_RGB_CHANNELS
            )
            FaceObservation.Encoded(detection.bounds, normalizedFaceEmbedding(encoded.embedding))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: CvException) {
            // Native allocation failures also arrive as CvException, not JVM OOM errors.
            // Conservatively abort the batch on any native OpenCV failure.
            throw FaceEngineException()
        } catch (_: RuntimeException) {
            FaceObservation.Unavailable(detection.bounds)
        }
    }

    private fun begin() = synchronized(lifecycle) {
        if (closed) throw FaceEngineException()
        working = true
    }

    private fun finish() {
        val dispose = synchronized(lifecycle) {
            working = false
            if (closed) kernel.also { kernel = null } else null
        }
        dispose?.close()
    }

    override fun close() {
        val dispose = synchronized(lifecycle) {
            closed = true
            if (!working) kernel.also { kernel = null } else null
        }
        dispose?.close()
    }
}

private fun FaceEngineResult.kind(): String = when (this) {
    FaceEngineResult.NoFace -> "none"
    FaceEngineResult.TooManyFaces -> "capacity"
    is FaceEngineResult.Detected -> if (faces.size == 1) "single" else "multiple"
}

private const val NANOS_PER_MILLISECOND = 1000000L
