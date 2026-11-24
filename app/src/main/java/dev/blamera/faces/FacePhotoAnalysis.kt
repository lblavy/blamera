package dev.blamera.faces

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.blamera.faces.runtime.FaceBounds
import dev.blamera.faces.runtime.FaceEngine
import dev.blamera.faces.runtime.FaceEngineException
import dev.blamera.faces.runtime.FaceEngineResult
import dev.blamera.faces.runtime.FaceObservation
import dev.blamera.faces.runtime.FaceRgbFrame
import dev.blamera.image.ImageLimits
import dev.blamera.image.ImageMemory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal sealed interface FacePhotoState {
    data object Empty : FacePhotoState
    data object Running : FacePhotoState
    data object NoFace : FacePhotoState
    data object TooManyFaces : FacePhotoState
    data object Unavailable : FacePhotoState
    class Detected(faces: List<FacePhotoFace>) : FacePhotoState {
        val faces: List<FacePhotoFace> = java.util.List.copyOf(faces)
    }
}

internal class FacePhotoFace(val bounds: FaceBounds, val identity: FaceIdentityDecision?)

internal suspend fun prepareFaceFrame(source: Bitmap): FaceRgbFrame =
    withContext(Dispatchers.Default) {
        ImageMemory.mutex.withLock {
            require(!source.isRecycled)
            require(
                source.width in 1..ImageLimits.PREVIEW_EDGE &&
                    source.height in 1..ImageLimits.PREVIEW_EDGE
            )
            val width = source.width
            val height = source.height
            val output = ByteArray(width * height * RGB_COMPONENTS)
            val row = IntArray(width)
            var offset = 0
            for (y in 0 until height) {
                currentCoroutineContext().ensureActive()
                source.getPixels(row, 0, width, 0, y, width, 1)
                for (pixel in row) {
                    // Match PIL.convert("RGB"): discard alpha without a new composite or resize.
                    output[offset++] = Color.red(pixel).toByte()
                    output[offset++] = Color.green(pixel).toByte()
                    output[offset++] = Color.blue(pixel).toByte()
                }
            }
            FaceRgbFrame(width, height, output)
        }
    }

internal class FacePhotoAnalysis(
    private val scope: CoroutineScope,
    private val gallery: FaceGallerySettings,
    private val engine: FaceEngine,
    private val prepare: suspend (Bitmap) -> FaceRgbFrame = ::prepareFaceFrame
) : AutoCloseable {
    var state: FacePhotoState by mutableStateOf(FacePhotoState.Empty)
        private set
    private var photo: Bitmap? = null
    private var job: Job? = null
    private var generation = 0
    private var active = true
    private var closed = false
    private val galleryChanged: () -> Unit = { restart() }

    init {
        gallery.onChanged = galleryChanged
    }

    fun show(photo: Bitmap) {
        if (closed) return
        this.photo = photo
        restart()
    }

    fun clear() {
        photo = null
        invalidate()
    }

    fun background() {
        active = false
        invalidate()
    }

    fun foreground() {
        if (closed || active) return
        active = true
        restart()
    }

    private fun invalidate() {
        generation++
        job?.cancel()
        job = null
        state = FacePhotoState.Empty
    }

    private fun restart() {
        invalidate()
        val image = photo
        val document = gallery.gallery
        val unavailable = closed || !active
        if (image == null || document == null || unavailable) return
        val token = generation
        state = FacePhotoState.Running
        job = scope.launch {
            val next = analyze(image, document)
            currentCoroutineContext().ensureActive()
            if (current(token, image, document)) {
                state = next
            }
        }
    }

    private fun current(token: Int, image: Bitmap, document: FaceGalleryDocument): Boolean =
        token == generation && !closed && active && photo === image && gallery.gallery === document

    private suspend fun analyze(image: Bitmap, document: FaceGalleryDocument): FacePhotoState =
        try {
            val result = engine.analyze(prepare(image))
            currentCoroutineContext().ensureActive()
            when (result) {
                FaceEngineResult.NoFace -> FacePhotoState.NoFace
                FaceEngineResult.TooManyFaces -> FacePhotoState.TooManyFaces
                is FaceEngineResult.Detected -> FacePhotoState.Detected(
                    result.faces.map { identifyFace(it, document) }
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: FaceEngineException) {
            FacePhotoState.Unavailable
        } catch (_: IllegalArgumentException) {
            FacePhotoState.Unavailable
        } catch (_: IllegalStateException) {
            FacePhotoState.Unavailable
        } catch (_: OutOfMemoryError) {
            FacePhotoState.Unavailable
        }

    private fun identifyFace(face: FaceObservation, document: FaceGalleryDocument): FacePhotoFace {
        val identity = when (face) {
            is FaceObservation.Unavailable -> null
            is FaceObservation.Encoded -> try {
                recognizeFace(face.embedding, document)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        return FacePhotoFace(face.bounds, identity)
    }

    override fun close() {
        if (closed) return
        closed = true
        photo = null
        invalidate()
        if (gallery.onChanged === galleryChanged) gallery.onChanged = { }
        engine.close()
    }
}

private const val RGB_COMPONENTS = 3
