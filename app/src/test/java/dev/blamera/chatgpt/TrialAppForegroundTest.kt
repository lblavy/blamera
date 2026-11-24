package dev.blamera.chatgpt

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TrialAppForegroundTest {
    @Test fun budgetStartsOnConnectAndExpiresAtTheExactElapsedBoundary() = runBlocking {
        var elapsed = 0L
        val foreground = TrialAppForeground { elapsed }
        foreground.update(true)
        elapsed = 600_000L
        foreground.beginConnection()
        elapsed += 299_999L
        foreground.await()
        elapsed++
        val failure = assertThrows(ChatGptTrialException::class.java) {
            runBlocking { foreground.await() }
        }
        assertEquals(TrialFailure.TIMEOUT, failure.kind)
        foreground.beginConnection()
        foreground.await()
    }
}
