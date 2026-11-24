package dev.blamera.camera

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class PreviewStatus { CONNECTING, STREAMING, FAILED }

fun interface PreviewProviderLoader {
    suspend fun load(): PreviewProvider
}

fun interface PreviewProvider {
    fun bind(
        owner: LifecycleOwner,
        view: PreviewView,
        report: (PreviewStatus) -> Unit
    ): AutoCloseable
}

suspend fun runPreviewSession(
    loader: PreviewProviderLoader,
    owner: LifecycleOwner,
    view: PreviewView,
    report: (PreviewStatus) -> Unit
): Nothing {
    val provider = loader.load()
    currentCoroutineContext().ensureActive()
    val binding = provider.bind(owner, view, report)
    try {
        awaitCancellation()
    } finally {
        binding.close()
    }
}
