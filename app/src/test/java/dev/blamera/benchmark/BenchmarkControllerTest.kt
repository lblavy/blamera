package dev.blamera.benchmark

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkControllerTest {
    @Test fun boundedContourFailureIsReadableAndNeverLeavesACompletedReport() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = BenchmarkController(
            scope,
            BenchmarkWork {
                throw dev.blamera.vision.VisionException("Contour limit")
            },
            Dispatchers.Default
        )
        controller.start()
        await { !controller.busy }
        assertEquals(BenchmarkState.Failed, controller.state)
        controller.close()
        scope.cancel()
    }
    private fun await(condition: () -> Boolean) {
        val limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < limit) Thread.sleep(5)
        assertTrue(condition())
    }

    @Test fun startIsExplicitDoubleStartBlockedAndCancelWaitsBeforeRetry() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val entered = CountDownLatch(1)
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        var calls = 0
        val controller = BenchmarkController(
            scope,
            BenchmarkWork { progress ->
                calls++
                entered.countDown()
                withContext(NonCancellable) {
                    release.await()
                    progress("late")
                }
                BenchmarkResult(emptyList())
            },
            Dispatchers.Default
        )
        assertEquals(BenchmarkState.Idle, controller.state)
        assertEquals(0, calls)
        controller.start()
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        controller.start()
        assertEquals(1, calls)
        controller.cancel()
        assertTrue(controller.busy)
        controller.start()
        assertEquals(1, calls)
        release.complete(Unit)
        await { !controller.busy }
        assertEquals(BenchmarkState.Cancelled, controller.state)
        controller.start()
        await { !controller.busy }
        assertEquals(2, calls)
        assertTrue(controller.state is BenchmarkState.Complete)
        controller.close()
        controller.start()
        assertEquals(2, calls)
        scope.cancel()
    }

    @Test fun failureIsReadableAndAllowsRetryWhileCancellationIsNotFailure() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var fail = true
        val controller =
            BenchmarkController(
                scope,
                BenchmarkWork {
                    if (fail) throw java.io.IOException("fixture")
                    BenchmarkResult(emptyList())
                },
                Dispatchers.Default
            )
        controller.start()
        await { !controller.busy }
        assertEquals(BenchmarkState.Failed, controller.state)
        fail = false
        controller.start()
        await { !controller.busy }
        assertTrue(controller.state is BenchmarkState.Complete)
        controller.close()
        scope.cancel()
    }
}
