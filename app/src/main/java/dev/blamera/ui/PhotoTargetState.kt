package dev.blamera.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.blamera.chatgpt.ui.ChatGptTrialViewModel
import dev.blamera.chatgpt.ui.ObjectDocumentKey
import dev.blamera.image.DecodedPhoto
import dev.blamera.image.ImageCrop
import dev.blamera.information.ObjectInformationTools
import dev.blamera.information.searchCrop
import dev.blamera.vision.AnalysisResult
import kotlinx.coroutines.CoroutineScope

internal data class PhotoTargetInput(
    val photo: DecodedPhoto?,
    val interaction: PhotoInteraction?,
    val result: AnalysisResult.Success?,
    val ready: Boolean
) {
    val index get() = interaction?.selected
    val detection get() = result?.detections?.getOrNull(index ?: -1)
    val target get() = if (detection == null) PhotoTarget.FRAME else PhotoTarget.OBJECT
}

/** Selection defines the target; action identity changes for gestures, never layout alone. */
internal class PhotoTargetState(
    private val scope: CoroutineScope,
    private val input: () -> PhotoTargetInput,
    private val onChanged: (ObjectDocumentKey?) -> Unit
) : AutoCloseable {
    val target get() = input().target
    var message by mutableStateOf<String?>(null)
        private set
    private val source = Any()
    private var revision = 0
    private var targetRevision = 0
    private var actionCrop: ImageCrop? = null
    private var actions: PhotoTargetActions? = null
    val key: ObjectDocumentKey? get() {
        val current = input()
        return if (current.ready && current.photo != null) {
            ObjectDocumentKey(
                source,
                revision,
                if (current.target ==
                    PhotoTarget.FRAME
                ) {
                    -1
                } else {
                    checkNotNull(current.index)
                },
                actionCrop,
                targetRevision
            )
        } else {
            null
        }
    }

    fun snapshot(): PhotoTargetSnapshot? {
        val current = input()
        val crop = if (current.ready) crop(current) else null
        val photo = current.photo
        if (photo == null || crop == null) {
            message = "Zone indisponible. Ajustez le cadrage ou choisissez une autre source."
            return null
        }
        message = null
        if (actionCrop != crop) {
            actionCrop = crop
            notifyTarget()
        }
        return PhotoTargetSnapshot(photo, checkNotNull(key), crop, current.target)
    }

    private fun crop(current: PhotoTargetInput): ImageCrop? = when (current.target) {
        PhotoTarget.FRAME -> current.interaction?.visibleRegion
        PhotoTarget.OBJECT -> objectTargetCrop(current)
    }

    fun information(
        tools: ObjectInformationTools,
        chatGpt: ChatGptTrialViewModel?
    ): PhotoTargetActions =
        actions ?: PhotoTargetActions(scope, tools, chatGpt) { key }.also { actions = it }

    private fun changed() {
        targetRevision++
        actionCrop = null
        message = null
        notifyTarget()
    }

    private fun notifyTarget() {
        actions?.invalidate(key)
        onChanged(key)
    }

    fun interaction(photo: DecodedPhoto) = PhotoInteraction(
        photo.bitmap,
        ::changed,
        { if (target == PhotoTarget.FRAME) changed() }
    )

    fun invalidate() {
        revision++
        actionCrop = null
        message = null
        actions?.invalidate(null)
        onChanged(null)
    }

    override fun close() {
        actions?.close()
    }
}

private fun objectTargetCrop(current: PhotoTargetInput): ImageCrop? {
    val photo = current.photo
    val detection = current.detection
    if (photo == null || detection == null) return null
    return try {
        val rect = searchCrop(photo.bitmap, detection.box)
        ImageCrop(rect.left, rect.top, rect.right, rect.bottom)
    } catch (_: IllegalArgumentException) {
        null
    }
}
