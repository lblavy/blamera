package dev.blamera.camera

import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PreviewSessionTest {
    private val owner = object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
            currentState =
                Lifecycle.State.RESUMED
        }
    }
    private val view = PreviewView(RuntimeEnvironment.getApplication())

    @Test
    fun sessionBindsOnceAndClosesItsOwnedBindingOnCancellation() = runBlocking {
        var binds = 0
        var closes = 0
        val provider =
            PreviewProvider { _, _, report ->
                binds++
                report(PreviewStatus.STREAMING)
                AutoCloseable { closes++ }
            }
        val states = mutableListOf<PreviewStatus>()
        val job =
            launch(start = CoroutineStart.UNDISPATCHED) {
                runPreviewSession(PreviewProviderLoader { provider }, owner, view, states::add)
            }
        assertEquals(1, binds)
        assertEquals(listOf(PreviewStatus.STREAMING), states)
        job.cancelAndJoin()
        assertEquals(1, closes)
    }

    @Test
    fun lateProviderAfterCancellationNeverBinds() = runBlocking {
        val pending = CompletableDeferred<PreviewProvider>()
        var binds = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            runPreviewSession(
                PreviewProviderLoader {
                    withContext(NonCancellable) { pending.await() }
                },
                owner,
                view
            ) {}
        }
        job.cancel()
        pending.complete(
            PreviewProvider { _, _, _ ->
                binds++
                AutoCloseable {}
            }
        )
        job.join()
        assertEquals(0, binds)
    }
}
