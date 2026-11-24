package dev.blamera.information

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.blamera.vision.ImageBox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal data class ObjectInformationTools(
    val preparer: SearchImagePreparer,
    val publisher: SearchImagePublisher,
    val sender: ImageShareSender
)

internal class ObjectSearchState(
    private val scope: CoroutineScope,
    private val tools: ObjectInformationTools,
    private val isCurrent: () -> Boolean = { true }
) : AutoCloseable {
    var image by mutableStateOf<SearchImage?>(null)
        private set
    var preparing by mutableStateOf(false)
        private set
    var sending by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null
    private var closed = false
    private var shared: SharedSearchImage? = null
    private var handedToApp = false
    private var generation = 0

    fun adopt(prepared: SearchImage) {
        check(!closed && !preparing && !sending && image == null)
        image = prepared
    }

    fun prepare(source: Bitmap, box: ImageBox) {
        if (closed || preparing || image != null) return
        preparing = true
        message = null
        val token = ++generation
        job = scope.launch { prepareOwned(source, box, token) }
    }

    private suspend fun prepareOwned(source: Bitmap, box: ImageBox, token: Int) {
        try {
            val prepared = tools.preparer.prepare(source, box)
            if (closed || token != generation) prepared.close() else image = prepared
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            fail(token, "Préparation impossible. Vous pouvez réessayer.")
        } catch (_: OutOfMemoryError) {
            fail(token, "Mémoire insuffisante pour préparer cette zone.")
        } finally {
            if (token == generation) preparing = false
        }
    }

    fun search(target: ImageShareTarget = ImageShareTarget.LENS) {
        val prepared = image ?: return
        if (closed || sending || !isCurrent()) return
        shared = null
        handedToApp = false
        sending = true
        message = null
        val token = ++generation
        job = scope.launch { performSearch(prepared, token, target) }
    }

    private suspend fun performSearch(prepared: SearchImage, token: Int, target: ImageShareTarget) {
        var published: SharedSearchImage? = null
        var opened = false
        try {
            val entry = tools.publisher.publish(prepared)
            published = entry
            currentCoroutineContext().ensureActive()
            opened = open(entry, token, target)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            fail(token, "Recherche impossible. Vous pouvez réessayer.")
        } catch (_: OutOfMemoryError) {
            fail(token, "Mémoire insuffisante pour envoyer cette zone.")
        } finally {
            if (!opened) published?.close()
            if (token == generation) sending = false
        }
    }

    private fun fail(token: Int, explanation: String) {
        if (!closed && token == generation) message = explanation
    }

    private fun open(entry: SharedSearchImage, token: Int, target: ImageShareTarget): Boolean {
        if (closed || token != generation || !isCurrent()) return false
        shared = entry
        handedToApp = true
        val result = tools.sender.send(entry.uri, target)
        handedToApp = result == ImageShareResult.OPENED
        message = when (result) {
            ImageShareResult.OPENED -> "Image confiée à Google Lens."
            ImageShareResult.UNAVAILABLE ->
                "Google Lens ou l’application Google ne peut pas recevoir cette image."
            ImageShareResult.FAILED ->
                "Impossible d’ouvrir ${target.appName}. Vous pouvez réessayer."
        }
        return handedToApp
    }

    fun cancel() {
        generation++
        job?.cancel()
        if (!handedToApp) shared?.close()
        sending = false
        preparing = false
        message =
            "Recherche annulée. Une image déjà remise à une application ne peut pas être retirée."
    }

    fun onBackground() {
        if (sending && !handedToApp) cancel()
    }

    override fun close() {
        closed = true
        job?.cancel()
        if (!handedToApp) shared?.close()
        image?.close()
        image = null
    }
}
