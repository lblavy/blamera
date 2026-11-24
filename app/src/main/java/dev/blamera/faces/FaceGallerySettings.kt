package dev.blamera.faces

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.blamera.chatgpt.storage.SessionStorageException
import dev.blamera.chatgpt.storage.TrialSessionStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class FaceGallerySettings(
    private val scope: CoroutineScope,
    private val store: TrialSessionStore,
    private val decoder: FaceGalleryDecoder,
    private val bootstrap: suspend () -> ByteArray? = { null }
) {
    var gallery: FaceGalleryDocument? by mutableStateOf(null)
        private set
    var busy by mutableStateOf(true)
        private set
    var message: String? by mutableStateOf(null)
        private set
    var onChanged: () -> Unit = { }
    private var job: Job? = null

    init {
        job = scope.launch {
            try {
                loadGallery()
            } catch (_: FaceGalleryException) {
                message = STORAGE_MESSAGE
            } catch (_: SessionStorageException) {
                message = STORAGE_MESSAGE
            } catch (_: IOException) {
                message = "Galerie intégrée indisponible."
            } finally {
                busy = false
            }
        }
    }

    private suspend fun loadGallery() {
        val saved = store.load()
        val bytes = if (saved != null) {
            saved.toByteArray(Charsets.UTF_8)
        } else {
            withContext(Dispatchers.IO) { bootstrap() }
        } ?: return
        val document = withContext(Dispatchers.IO) { decoder.decode(bytes) }
        currentCoroutineContext().ensureActive()
        if (saved == null) {
            withContext(NonCancellable) {
                store.save(document.json)
                publish(document)
            }
        } else {
            publish(document)
        }
    }

    fun import(load: suspend () -> ByteArray) {
        if (busy) return
        busy = true
        message = null
        job = scope.launch {
            try {
                val document = withContext(Dispatchers.IO) { decoder.decode(load()) }
                currentCoroutineContext().ensureActive()
                // Once the atomic replacement begins, publish the same committed record.
                withContext(NonCancellable) {
                    store.save(document.json)
                    publish(document)
                    message = "Galerie importée."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: FaceGalleryException) {
                message = "Galerie invalide ou incompatible."
            } catch (_: IOException) {
                message = "Impossible de lire le fichier sélectionné."
            } catch (_: SecurityException) {
                message = "Impossible de lire le fichier sélectionné."
            } catch (_: SessionStorageException) {
                message = STORAGE_MESSAGE
            } finally {
                busy = false
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private fun publish(document: FaceGalleryDocument) {
        gallery = document
        onChanged()
    }
}

private const val STORAGE_MESSAGE = "Galerie enregistrée indisponible. Réessayez l’import."
