package dev.blamera.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

internal data class CameraPermission(
    val access: CameraAccess,
    val resumed: Boolean,
    val activate: () -> Unit,
    val refresh: () -> Unit
)

@Composable
internal fun rememberCameraPermission(activateIfGranted: Boolean = false): CameraPermission {
    val context = LocalContext.current
    val activity = LocalActivity.current
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    val access = rememberSaveable(
        saver = Saver<CameraAccess, Boolean>(save = {
            it.activated
        }, restore = { CameraAccess(granted(), it) })
    ) { CameraAccess(granted(), activateIfGranted) }
    val request =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            access.acceptPermission(
                allowed,
                activity != null &&
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.CAMERA
                    )
            )
        }
    val resumed = rememberCameraLifecycle(LocalLifecycleOwner.current, access, ::granted)
    DisposableEffect(access) { onDispose { access.dispose() } }
    return CameraPermission(
        access,
        resumed,
        activate = {
            access.refresh(granted())
            if (access.activateOrRequest()) request.launch(Manifest.permission.CAMERA)
        },
        refresh = { access.refresh(granted()) }
    )
}
