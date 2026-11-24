package dev.blamera.chatgpt.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.ByteArrayOutputStream

private const val IMAGE_WIDTH = 640
private const val IMAGE_HEIGHT = 480
private const val BACKGROUND_RED = 246
private const val BACKGROUND_GREEN = 244
private const val BACKGROUND_BLUE = 238
private const val CUP_RED = 25
private const val CUP_GREEN = 105
private const val CUP_BLUE = 185
private const val HANDLE_STROKE = 26f
private const val HANDLE_LEFT = 366f
private const val HANDLE_TOP = 165f
private const val HANDLE_RIGHT = 468f
private const val HANDLE_BOTTOM = 310f
private const val CUP_LEFT = 178f
private const val CUP_TOP = 140f
private const val CUP_RIGHT = 394f
private const val CUP_BOTTOM = 350f
private const val CUP_RADIUS = 24f
private const val INSIDE_RED = 16
private const val INSIDE_GREEN = 56
private const val INSIDE_BLUE = 95
private const val OPENING_TOP = 120f
private const val OPENING_BOTTOM = 164f
private const val CAPTION_SIZE = 36f
private const val CAPTION_BASELINE = 423f
private const val JPEG_QUALITY = 85

/** A synthetic illustration keeps the first remote request independent of personal photos. */
internal fun createTrialJpeg(): ByteArray {
    val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(BACKGROUND_RED, BACKGROUND_GREEN, BACKGROUND_BLUE))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(CUP_RED, CUP_GREEN, CUP_BLUE)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = HANDLE_STROKE
        canvas.drawOval(HANDLE_LEFT, HANDLE_TOP, HANDLE_RIGHT, HANDLE_BOTTOM, paint)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(
            CUP_LEFT,
            CUP_TOP,
            CUP_RIGHT,
            CUP_BOTTOM,
            CUP_RADIUS,
            CUP_RADIUS,
            paint
        )
        paint.color = Color.rgb(INSIDE_RED, INSIDE_GREEN, INSIDE_BLUE)
        canvas.drawOval(CUP_LEFT, OPENING_TOP, CUP_RIGHT, OPENING_BOTTOM, paint)
        paint.color = Color.DKGRAY
        paint.textSize = CAPTION_SIZE
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("BLAMERA", IMAGE_WIDTH / 2f, CAPTION_BASELINE, paint)
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output))
            output.toByteArray()
        }
    } finally {
        bitmap.recycle()
    }
}
