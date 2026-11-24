package dev.blamera.botany

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.blamera.image.DecodedPhoto
import dev.blamera.image.ImageCrop
import dev.blamera.information.AndroidSearchImagePreparer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal data class PlantSnapshot(
    val photo: DecodedPhoto,
    val crop: ImageCrop,
    val result: PlantIdentification? = null,
    val status: String = "Identification Pl@ntNet en cours…",
    val running: Boolean = true,
    val applicable: Boolean = true,
    val settingsRevision: Int = 0
)

internal class PlantAnalysis(
    private val scope: CoroutineScope,
    val settings: PlantSettings,
    private val identifier: PlantIdentifier,
    private val prepare: suspend (Bitmap) -> ByteArray = ::preparePlantJpeg
) : AutoCloseable {
    var snapshot: PlantSnapshot? by mutableStateOf(null)
        private set
    private var job: Job? = null
    private var generation = 0
    private var closed = false
    init {
        settings.onChanged = { cancel(clear = true) }
    }

    fun start(photo: DecodedPhoto) {
        if (closed) return
        cancel(clear = true)
        if (settings.loading || (settings.configured && !settings.busy)) {
            val current = PlantSnapshot(
                photo,
                ImageCrop(0, 0, photo.bitmap.width, photo.bitmap.height),
                settingsRevision = settings.revision
            )
            snapshot = current
            val token = generation
            job = scope.launch { identify(current, token) }
        }
    }

    private suspend fun identify(current: PlantSnapshot, token: Int) {
        try {
            val key = awaitKey(token) ?: return
            val jpeg = prepare(current.photo.bitmap)
            currentCoroutineContext().ensureActive()
            val identified = identifier.identify(key, jpeg)
            currentCoroutineContext().ensureActive()
            if (isCurrent(token)) {
                snapshot = current.copy(
                    result = identified,
                    status = if (identified == null) {
                        "Aucune plante identifiée par Pl@ntNet."
                    } else {
                        "Hypothèses Pl@ntNet — à vérifier"
                    },
                    running = false
                )
                writePlantOutcome(identified != null)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: PlantException) {
            fail(current, token, failure.failure.explanation, failure.failure)
        } catch (_: IllegalArgumentException) {
            fail(current, token, PlantFailure.INVALID.explanation)
        } catch (_: IllegalStateException) {
            fail(current, token, "Préparation botanique impossible.")
        } catch (_: OutOfMemoryError) {
            fail(current, token, "Mémoire insuffisante pour Pl@ntNet.")
        }
    }

    private suspend fun awaitKey(token: Int): String? {
        settings.awaitLoaded()
        currentCoroutineContext().ensureActive()
        val key = settings.key?.takeUnless { settings.busy }
        if (key == null && isCurrent(token)) snapshot = null
        return key
    }

    private fun isCurrent(token: Int) = token == generation && !closed

    private fun fail(
        current: PlantSnapshot,
        token: Int,
        explanation: String,
        failure: PlantFailure = PlantFailure.INVALID
    ) {
        if (isCurrent(token)) {
            snapshot = current.copy(status = explanation, running = false)
            writePlantFailure(failure)
        }
    }
    fun cancel(clear: Boolean = false) {
        generation++
        job?.cancel()
        job = null
        snapshot = if (clear) {
            null
        } else {
            snapshot?.let {
                if (it.running) {
                    it.copy(
                        status = "Identification Pl@ntNet interrompue.",
                        running = false
                    )
                } else {
                    it
                }
            }
        }
    }
    fun restore(snapshot: PlantSnapshot?) {
        if (closed) return
        cancel(clear = true)
        this.snapshot =
            snapshot?.takeUnless { it.running || it.settingsRevision != settings.revision }
    }
    fun hint(photo: DecodedPhoto, crop: ImageCrop): String? = snapshot?.takeIf {
        it.photo.bitmap === photo.bitmap && it.crop == crop && !it.running &&
            it.settingsRevision == settings.revision
    }?.result?.hypothesis
    override fun close() {
        closed = true
        cancel(clear = true)
        identifier.close()
    }
}

internal suspend fun preparePlantJpeg(bitmap: Bitmap): ByteArray =
    AndroidSearchImagePreparer(PLANT_EDGE, PLANT_QUALITY, MAX_PLANT_JPEG)
        .prepareRegion(bitmap, ImageCrop(0, 0, bitmap.width, bitmap.height)).use { it.jpeg }

private const val PLANT_EDGE = 1280
private const val PLANT_QUALITY = 90
