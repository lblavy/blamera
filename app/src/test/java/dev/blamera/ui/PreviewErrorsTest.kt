package dev.blamera.ui

import java.util.concurrent.ExecutionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewErrorsTest {
    @Test fun errorsAreRecoverableAndCancellationPropagates(): Unit = runBlocking {
        assertTrue(previewAttempt {})
        listOf(
            ExecutionException(RuntimeException("camera unavailable")),
            SecurityException("permission revoked"),
            IllegalArgumentException("camera unsupported"),
            IllegalStateException("camera occupied")
        ).forEach { error -> assertFalse(previewAttempt { throw error }) }
        assertThrows(CancellationException::class.java) {
            runBlocking { previewAttempt { throw CancellationException("navigation") } }
        }
    }
}
