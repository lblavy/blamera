package dev.blamera.ui

import dev.blamera.chatgpt.ui.ObjectDocumentKey
import dev.blamera.image.DecodedPhoto
import dev.blamera.image.ImageCrop

internal enum class PhotoTarget { FRAME, OBJECT }

internal data class PhotoTargetSnapshot(
    val photo: DecodedPhoto,
    val key: ObjectDocumentKey,
    val crop: ImageCrop,
    val target: PhotoTarget,
    val plantHint: String? = null
)
