package dev.blamera.botany

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class PlantQuotaThreadTest {
    @Test
    fun durableQuotaWritesRunOffCallerThreadBeforeTheHttpRequest() = runBlocking {
        val caller = Thread.currentThread()
        val writes = CopyOnWriteArrayList<Thread>()
        val quota = PlantQuota(write = { writes.add(Thread.currentThread()) })
        val identifier = PlantIdentifier(
            PlantHttp { _, _ ->
                assertEquals("Reservation must finish before upload", 1, writes.size)
                PlantHttpResponse(404, "", remaining = 0)
            },
            quota
        )
        identifier.identify("FAKE", byteArrayOf(1))
        assertEquals(2, writes.size)
        writes.forEach { assertNotSame("No durable write on caller/UI thread", caller, it) }
    }
}
