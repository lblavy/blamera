package dev.blamera.information

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri

internal enum class ImageShareResult { OPENED, UNAVAILABLE, FAILED }

internal class ImageShareSender(
    private val authority: String,
    private val canHandle: (Intent) -> Boolean,
    private val launch: (Intent) -> Unit
) {
    fun send(uri: Uri, target: ImageShareTarget = ImageShareTarget.LENS): ImageShareResult = try {
        sharedImageToken(uri, authority)
        val intent = target.packages.map {
            imageShareIntent(uri, it)
        }.firstOrNull(canHandle)
        if (intent == null) {
            ImageShareResult.UNAVAILABLE
        } else {
            launch(intent)
            ImageShareResult.OPENED
        }
    } catch (_: ActivityNotFoundException) {
        ImageShareResult.FAILED
    } catch (_: SecurityException) {
        ImageShareResult.FAILED
    } catch (_: IllegalArgumentException) {
        ImageShareResult.FAILED
    }
}

internal fun imageShareIntent(uri: Uri, target: String) = Intent(Intent.ACTION_SEND).apply {
    type = "image/jpeg"
    setPackage(target)
    putExtra(Intent.EXTRA_STREAM, uri)
    clipData =
        ClipData(
            "Image à rechercher",
            arrayOf("image/jpeg"),
            ClipData.Item(uri)
        )
    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
}

internal fun createImageShareSender(context: Context): ImageShareSender {
    val application = context.applicationContext
    return ImageShareSender(
        "${application.packageName}.lens-images",
        { intent -> compatibleShareHandler(application, intent) },
        { intent -> application.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    )
}

private fun compatibleShareHandler(context: Context, intent: Intent): Boolean =
    context.packageManager.queryIntentActivities(
        intent,
        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
    ).any { resolved ->
        val activity = resolved.activityInfo
        val enabled = activity?.enabled == true && activity.applicationInfo?.enabled == true
        enabled && activity.exported && activity.packageName == intent.`package` &&
            entryPermissionAllowed(context, activity)
    }

private fun entryPermissionAllowed(context: Context, activity: ActivityInfo): Boolean =
    activity.permission == null ||
        context.checkSelfPermission(activity.permission) == PackageManager.PERMISSION_GRANTED
