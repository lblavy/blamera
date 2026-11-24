package dev.blamera.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import dev.blamera.image.DecodedPhoto
import dev.blamera.image.PhotoLoadResult
import dev.blamera.image.PreviewDimensions
import dev.blamera.vision.ContourSegment
import dev.blamera.vision.ImageBox
import dev.blamera.vision.ImagePoint
import dev.blamera.vision.MaskGrid
import dev.blamera.vision.VisionDetection

internal class CapturePicker(private val context: Context) :
    ActivityResultRegistry(),
    ActivityResultRegistryOwner {
    override val activityResultRegistry get() = this
    var next: Uri? = null
    var intent: Intent? = null
    var permissions = 0
    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?
    ) {
        if (contract is ActivityResultContracts.RequestPermission) {
            permissions++
            dispatchResult(requestCode, false)
        } else {
            intent = contract.createIntent(context, input)
            dispatchResult(
                requestCode,
                if (next ==
                    null
                ) {
                    Activity.RESULT_CANCELED
                } else {
                    Activity.RESULT_OK
                },
                Intent().setData(next)
            )
        }
    }
}

internal fun capturePhoto(width: Int = 640, height: Int = 480) = DecodedPhoto(
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        eraseColor(android.graphics.Color.rgb(36, 48, 64))
    },
    PreviewDimensions(width, height)
)

internal fun captureLoaded(width: Int = 640, height: Int = 480) =
    PhotoLoadResult.Loaded(capturePhoto(width, height))

internal fun captureDetection(category: Int = 15): VisionDetection {
    val corners =
        listOf(
            ImagePoint(160f, 120f),
            ImagePoint(480f, 120f),
            ImagePoint(480f, 360f),
            ImagePoint(160f, 360f)
        )
    val mask = MaskGrid(
        160,
        160,
        BooleanArray(160 * 160) {
            it % 160 in 40 until 120 && it / 160 in 50 until 110
        }
    )
    return VisionDetection(
        category,
        0.9f,
        ImageBox(160f, 120f, 480f, 360f),
        mask,
        corners.indices.map { ContourSegment(corners[it], corners[(it + 1) % corners.size]) }
    )
}
