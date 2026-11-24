package dev.blamera.image

import android.graphics.Bitmap
import android.net.Uri

data class DecodedPhoto(val bitmap: Bitmap, val original: PreviewDimensions)

sealed interface PhotoLoadResult {
    data class Loaded(val photo: DecodedPhoto) : PhotoLoadResult
    data class Failed(val reason: ImageImportError) : PhotoLoadResult
}

fun interface PhotoLoader {
    suspend fun load(uri: Uri): PhotoLoadResult
}
