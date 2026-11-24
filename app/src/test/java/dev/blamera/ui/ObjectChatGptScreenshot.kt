package dev.blamera.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import java.io.File

internal fun saveObjectChatGptScreenshot(view: View, name: String) {
    val current = File(checkNotNull(System.getProperty("user.dir")))
    val root = if (File(current, "AGENTS.md").isFile) current else checkNotNull(current.parentFile)
    val output =
        File(root, "build/reports/validation/m2-10-key-visibility/coder/screenshots/$name.png")
    checkNotNull(output.parentFile).mkdirs()
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    try {
        view.draw(Canvas(bitmap))
        output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    } finally {
        bitmap.recycle()
    }
}
