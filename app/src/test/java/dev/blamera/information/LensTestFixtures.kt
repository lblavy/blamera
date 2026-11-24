package dev.blamera.information

import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.runBlocking

internal fun searchImage() = SearchImage(
    Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888),
    byteArrayOf(-1, -40, 1, 2, 3, -1, -39)
)

internal class FakeSharedImage : SharedSearchImage {
    override val uri = Uri.parse(
        "content://dev.blamera.lens-images/images/" + "a".repeat(64) + ".jpg"
    )
    var closed = false
    override fun close() {
        closed = true
    }
}

internal fun fakeImageShareSender(launch: (android.content.Intent) -> Unit) =
    ImageShareSender("dev.blamera.lens-images", { true }, launch)
