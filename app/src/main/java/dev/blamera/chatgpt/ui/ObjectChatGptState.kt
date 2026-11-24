package dev.blamera.chatgpt.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.blamera.image.ImageCrop

internal const val OBJECT_CHATGPT_INSTRUCTION =
    "Identifie cet objet aussi précisément que possible à partir de l’image. " +
        "Décris ses caractéristiques utiles et indique tes incertitudes. " +
        "Ne présente pas une marque ou un modèle comme certain sans indice visible. " +
        "Réponds en français."

internal const val FRAME_CHATGPT_INSTRUCTION =
    "Identifie aussi précisément que possible le sujet principal et les éléments visibles " +
        "dans cette image. " +
        "Décris leurs caractéristiques utiles et indique tes incertitudes. " +
        "Ne présente pas une marque, une espèce ou un modèle comme certain sans indice visible. " +
        "Réponds en français."

internal data class ObjectDocumentKey(
    val source: Any,
    val revision: Int,
    val index: Int,
    val crop: ImageCrop? = null,
    val targetRevision: Int = 0
)
internal class ObjectChatGptDocument(
    val key: ObjectDocumentKey,
    val jpeg: ByteArray,
    val instruction: String = OBJECT_CHATGPT_INSTRUCTION
)

internal class ObjectChatGptState(private val model: ChatGptTrialViewModel) {
    var key by mutableStateOf<ObjectDocumentKey?>(null)
        private set
    var document by mutableStateOf<ObjectChatGptDocument?>(null)
        private set
    private var closed = false

    fun select(key: ObjectDocumentKey?) {
        if (closed || this.key == key) return
        this.key = key
        document = null
        model.cancel(clearDocument = true)
    }

    fun prepare(
        key: ObjectDocumentKey,
        jpeg: ByteArray,
        instruction: String = OBJECT_CHATGPT_INSTRUCTION,
        replace: Boolean = false
    ) {
        val keepExisting = document != null && !replace
        if (closed || this.key != key || keepExisting) return
        if (replace) model.cancel(clearDocument = true)
        document = ObjectChatGptDocument(key, jpeg.copyOf(), instruction)
    }

    fun send() {
        if (closed) return
        val input = document ?: return
        model.analyze(input.jpeg, input.instruction) { document === input }
    }

    fun close() {
        closed = true
        document = null
        key = null
    }
}
