package dev.blamera.chatgpt.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

/** The composition owns its decoded bitmap; the retained session only owns JPEG bytes. */
@Composable
internal fun PreparedJpegImage(jpeg: ByteArray, description: String, modifier: Modifier) {
    val bitmap = remember(jpeg) { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) }
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
    if (bitmap == null) {
        Text("Aperçu indisponible.")
    } else {
        Image(bitmap.asImageBitmap(), description, modifier, contentScale = ContentScale.Fit)
    }
}
