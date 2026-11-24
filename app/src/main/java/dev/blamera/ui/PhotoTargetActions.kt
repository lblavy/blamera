package dev.blamera.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.blamera.botany.writePlantPrompt
import dev.blamera.chatgpt.ui.ChatGptTrialViewModel
import dev.blamera.chatgpt.ui.FRAME_CHATGPT_INSTRUCTION
import dev.blamera.chatgpt.ui.OBJECT_CHATGPT_INSTRUCTION
import dev.blamera.chatgpt.ui.ObjectDocumentKey
import dev.blamera.chatgpt.ui.plantInstruction
import dev.blamera.information.ObjectInformationTools
import dev.blamera.information.ObjectSearchState
import dev.blamera.information.SearchImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Retained by CaptureSession: rotation never replaces an in-flight export or its frozen pixels. */
internal class PhotoTargetActions(
    private val scope: CoroutineScope,
    private val tools: ObjectInformationTools,
    private val chatGpt: ChatGptTrialViewModel?,
    private val currentKey: () -> ObjectDocumentKey?
) : AutoCloseable {
    var search by mutableStateOf<ObjectSearchState?>(null)
        private set
    var preparing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var snapshot by mutableStateOf<PhotoTargetSnapshot?>(null)
        private set
    private var job: Job? = null
    private var generation = 0
    private var closed = false
    val image: SearchImage? get() = search?.image

    fun invalidate(key: ObjectDocumentKey?) {
        if (snapshot?.key == key) return
        clear()
        chatGpt?.objects?.select(key)
    }

    fun send(snapshot: PhotoTargetSnapshot, lens: Boolean) {
        if (closed || preparing || currentKey() != snapshot.key) return
        clear()
        this.snapshot = snapshot
        chatGpt?.objects?.select(snapshot.key)
        preparing = true
        val token = generation
        job = scope.launch { prepareAndSend(snapshot, lens, token) }
    }

    private suspend fun prepareAndSend(snapshot: PhotoTargetSnapshot, lens: Boolean, token: Int) {
        var prepared: SearchImage? = null
        try {
            prepared = tools.preparer.prepareRegion(snapshot.photo.bitmap, snapshot.crop)
            currentCoroutineContext().ensureActive()
            if (closed || token != generation || currentKey() != snapshot.key) return
            val state = ObjectSearchState(scope, tools) { currentKey() == snapshot.key }
            state.adopt(prepared)
            search = state
            prepared = null
            if (lens) state.search() else sendChatGpt(snapshot, state)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failPreparation(token, "Préparation impossible. Vous pouvez réessayer.")
        } catch (_: OutOfMemoryError) {
            failPreparation(token, "Mémoire insuffisante pour préparer cette zone.")
        } finally {
            prepared?.close()
            if (token == generation) preparing = false
        }
    }

    private fun failPreparation(token: Int, explanation: String) {
        if (token == generation) message = explanation
    }

    private fun sendChatGpt(snapshot: PhotoTargetSnapshot, state: ObjectSearchState) {
        val instruction = if (snapshot.target == PhotoTarget.FRAME) {
            FRAME_CHATGPT_INSTRUCTION
        } else {
            OBJECT_CHATGPT_INSTRUCTION
        }
        chatGpt?.objects?.let {
            val previous = it.document
            it.prepare(
                snapshot.key,
                checkNotNull(state.image).jpeg,
                plantInstruction(instruction, snapshot.plantHint),
                replace = true
            )
            if (it.document !== previous) writePlantPrompt(snapshot.plantHint != null)
            it.send()
        }
    }

    fun cancel() {
        clear()
        message = "Préparation annulée."
    }

    fun onBackground() {
        if (preparing) cancel()
        search?.onBackground()
    }

    private fun clear() {
        generation++
        job?.cancel()
        search?.close()
        search = null
        snapshot = null
        preparing = false
        message = null
    }

    override fun close() {
        closed = true
        clear()
    }
}
