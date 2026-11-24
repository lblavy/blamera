package dev.blamera.ui

import android.view.View
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.blamera.camera.PhotoCaptureProvider
import dev.blamera.camera.PreviewProviderLoader
import dev.blamera.camera.PreviewStatus
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class CapturePreviewBinding {
    var status by mutableStateOf(PreviewStatus.CONNECTING)
    var source by mutableStateOf<PhotoCaptureProvider?>(null)
    var laidOut by mutableStateOf(false)
    var attempt by mutableIntStateOf(0)

    fun canCapture(resumed: Boolean, loading: Boolean) =
        resumed && !loading && status == PreviewStatus.STREAMING && source != null

    fun message(resumed: Boolean, loading: Boolean) = when {
        loading -> "Capture en cours…"
        !resumed -> "Caméra en pause."
        else -> previewMessage(status)
    }
}

@Composable
internal fun rememberCaptureBinding(
    loader: PreviewProviderLoader,
    view: PreviewView,
    resumed: Boolean
): CapturePreviewBinding {
    val owner = LocalLifecycleOwner.current
    val state = remember(view) { CapturePreviewBinding() }
    DisposableEffect(view) {
        val listener = View.OnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            state.laidOut = right > left && bottom > top
        }
        view.addOnLayoutChangeListener(listener)
        onDispose { view.removeOnLayoutChangeListener(listener) }
    }
    LaunchedEffect(loader, resumed, state.laidOut, state.attempt) {
        state.status = PreviewStatus.CONNECTING
        state.source = null
        if (resumed && state.laidOut) guardedCaptureBinding(loader, owner, view, state)
    }
    return state
}

private suspend fun guardedCaptureBinding(
    loader: PreviewProviderLoader,
    owner: LifecycleOwner,
    view: PreviewView,
    state: CapturePreviewBinding
) {
    if (!previewAttempt {
            bindCapture(loader, owner, view, state)
        }
    ) {
        state.status = PreviewStatus.FAILED
    }
}

private suspend fun bindCapture(
    loader: PreviewProviderLoader,
    owner: LifecycleOwner,
    view: PreviewView,
    state: CapturePreviewBinding
): Nothing {
    val provider = loader.load()
    currentCoroutineContext().ensureActive()
    val binding = provider.bind(owner, view) { state.status = it }
    state.source = provider as? PhotoCaptureProvider
    try {
        awaitCancellation()
    } finally {
        state.source = null
        binding.close()
    }
}
