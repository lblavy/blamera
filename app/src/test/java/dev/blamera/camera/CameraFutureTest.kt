package dev.blamera.camera

import androidx.concurrent.futures.CallbackToFutureAdapter
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class CameraFutureTest {
    @Test
    fun completedProviderAndInitializationFailureAreDelivered() = runBlocking {
        val provider = PreviewProvider { _, _, _ -> AutoCloseable {} }
        val success = CallbackToFutureAdapter.getFuture<PreviewProvider> {
            it.set(provider)
            "success"
        }
        assertSame(provider, awaitCameraFuture(success, Runnable::run))
        val failure = CallbackToFutureAdapter.getFuture<PreviewProvider> {
            it.setException(IllegalStateException("unavailable"))
            "failure"
        }
        assertThrows(ExecutionException::class.java) {
            runBlocking { awaitCameraFuture(failure, Runnable::run) }
        }
        Unit
    }

    @Test
    fun cancellingTheConsumerLeavesTheGlobalProviderFutureUntouched() = runBlocking {
        var completion: CallbackToFutureAdapter.Completer<PreviewProvider>? = null
        val future = CallbackToFutureAdapter.getFuture<PreviewProvider> {
            completion = it
            "pending"
        }
        var delivered = 0
        val job =
            launch(start = CoroutineStart.UNDISPATCHED) {
                awaitCameraFuture(future, Runnable::run)
                delivered++
            }
        job.cancelAndJoin()
        assertEquals(false, future.isCancelled)
        checkNotNull(completion).set(PreviewProvider { _, _, _ -> AutoCloseable {} })
        assertEquals(0, delivered)
    }
}
