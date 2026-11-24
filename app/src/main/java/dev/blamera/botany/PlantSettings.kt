package dev.blamera.botany

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.blamera.chatgpt.storage.SessionStorageException
import dev.blamera.chatgpt.storage.TrialSessionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PlantSettings(
    private val scope: CoroutineScope,
    private val store: TrialSessionStore
) {
    var key: String? by mutableStateOf(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var busy by mutableStateOf(true)
        private set
    var message: String? by mutableStateOf(null)
        private set
    val configured: Boolean get() = key != null
    var revision = 0
        private set
    var onChanged: () -> Unit = {}
    private val mutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch {
            mutex.withLock {
                try {
                    key =
                        store.load()?.also {
                            if (!validPlantKey(it)) throw SessionStorageException()
                        }
                } catch (_: SessionStorageException) {
                    message = PlantFailure.STORAGE.explanation
                } finally {
                    loading = false
                    busy = false
                    loaded.complete(Unit)
                }
            }
        }
    }

    suspend fun awaitLoaded() = loaded.await()

    fun save(value: String) {
        if (!validPlantKey(value)) {
            message = PlantFailure.KEY.explanation
            return
        }
        update(value)
    }

    fun remove() = update(null)

    private fun update(value: String?) {
        if (busy) return
        busy = true
        revision++
        onChanged()
        scope.launch {
            mutex.withLock {
                try {
                    if (value == null) store.clear() else store.save(value)
                    key = value
                    message = if (value == null) "Clé supprimée." else "Clé enregistrée."
                } catch (_: SessionStorageException) {
                    message = PlantFailure.STORAGE.explanation
                } finally {
                    busy = false
                }
            }
        }
    }
}
