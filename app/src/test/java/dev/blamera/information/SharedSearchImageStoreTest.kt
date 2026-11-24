package dev.blamera.information

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import dev.blamera.vision.ImageBox
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [WindowsFileProviderShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharedSearchImageStoreTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val files = ShareImageFiles(context)

    @After fun cleanupOwnedFixtures() {
        synchronized(ShareCacheGuard.lock) { files.cleanup(Long.MAX_VALUE) }
    }

    @Test fun publishedFileContainsExactlyThePreparedJpegWithOpaquePrivateUri() = runBlocking {
        val image = preparedImage()
        val entry = SharedSearchImageStore(context).publish(image)
        try {
            assertEquals("content", entry.uri.scheme)
            assertEquals("dev.blamera.lens-images", entry.uri.authority)
            val token = sharedImageToken(entry.uri, files.authority)
            assertTrue(SHARE_TOKEN.matches(token))
            assertEquals(2, entry.uri.pathSegments.size)
            assertArrayEquals(
                image.jpeg,
                files.liveFile(entry.uri, System.currentTimeMillis()).readBytes()
            )
            assertTrue(files.record(token).isFile)
            assertTrue(files.drafts.listFiles().orEmpty().isEmpty())
        } finally {
            entry.close()
            image.close()
        }
        assertFalse(files.image(sharedImageToken(entry.uri, files.authority)).exists())
    }

    @Test fun expirationSurvivesAStoreRestartAndCannotBeRenewedByOpening() = runBlocking {
        val clock = AtomicLong(System.currentTimeMillis())
        val image = preparedImage()
        val store = SharedSearchImageStore(context, clock::get)
        val entry = store.publish(image)
        val created = clock.get()
        assertTrue(files.liveFile(entry.uri, created + SHARE_TTL_MILLIS - 1).exists())
        assertThrows(FileNotFoundException::class.java) {
            ShareImageFiles(context).liveFile(entry.uri, created + SHARE_TTL_MILLIS)
        }
        clock.addAndGet(SHARE_TTL_MILLIS)
        SharedSearchImageStore(context, clock::get).cleanup()
        assertFalse(files.image(sharedImageToken(entry.uri, files.authority)).exists())
        image.close()
    }

    @Test fun atMostTwoImagesAndFourMebibytesSurviveRepeatedPublications() = runBlocking {
        val clock = AtomicLong(System.currentTimeMillis())
        val image =
            SearchImage(
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                ByteArray(MAX_JPEG_BYTES)
            )
        val store = SharedSearchImageStore(context, clock::get)
        val first = store.publish(image)
        clock.incrementAndGet()
        val second = store.publish(image)
        clock.incrementAndGet()
        val third = store.publish(image)
        assertNotEquals(first.uri, second.uri)
        assertNotEquals(second.uri, third.uri)
        assertThrows(FileNotFoundException::class.java) { files.liveFile(first.uri, clock.get()) }
        val remaining = files.images.listFiles().orEmpty()
        assertEquals(2, remaining.size)
        assertTrue(remaining.sumOf { it.length() } <= 2L * MAX_JPEG_BYTES)
        assertTrue(files.drafts.listFiles().orEmpty().isEmpty())
        image.close()
    }

    @Test fun missingCorruptFutureAndLengthMismatchedMetadataAlwaysFailClosed() = runBlocking {
        val image = preparedImage()
        val store = SharedSearchImageStore(context)
        for (metadata in listOf<String?>(
            null,
            "bad",
            "0\n1\n2",
            "9999999999999\n10000000299999\n1"
        )) {
            val entry = store.publish(image)
            val record = files.record(sharedImageToken(entry.uri, files.authority))
            if (metadata == null) record.delete() else record.writeText(metadata)
            assertThrows(FileNotFoundException::class.java) {
                files.liveFile(entry.uri, System.currentTimeMillis())
            }
            entry.close()
        }
        val entry = store.publish(image)
        files.image(sharedImageToken(entry.uri, files.authority)).writeBytes(byteArrayOf(1))
        assertThrows(FileNotFoundException::class.java) {
            files.liveFile(entry.uri, System.currentTimeMillis())
        }
        entry.close()
        image.close()
    }

    @Test fun oversizedAndEmptyDraftsCannotBePublishedAndAbandonedDraftsAreSwept() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val store = SharedSearchImageStore(context)
        listOf(ByteArray(0), ByteArray(MAX_JPEG_BYTES + 1)).forEach { bytes ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { store.publish(SearchImage(bitmap, bytes)) }
            }
        }
        val draft = files.draft("a".repeat(64)).apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1))
        }
        val orphan = files.record("b".repeat(64)).apply {
            parentFile!!.mkdirs()
            writeText("bad")
        }
        runBlocking { store.cleanup() }
        assertFalse(draft.exists())
        assertFalse(orphan.exists())
        bitmap.recycle()
    }

    @Test fun malformedRecordFieldsOrTamperedLifetimeAndSizesCannotAuthorizeAFile() = runBlocking {
        val image = preparedImage()
        val created = System.currentTimeMillis()
        val expires = created + SHARE_TTL_MILLIS
        val length = image.jpeg.size
        val invalidRecords = listOf(
            "bad\n$expires\n$length",
            "$created\nbad\n$length",
            "$created\n$expires\nbad",
            "$created\n${created - 1}\n$length",
            "$created\n${expires + 1}\n$length",
            "$created\n$expires\n0",
            "$created\n$expires\n-1",
            "$created\n$expires\n${MAX_JPEG_BYTES + 1}",
            "x".repeat(129)
        )
        val store = SharedSearchImageStore(context)
        for (record in invalidRecords) {
            val entry = store.publish(image)
            files.record(sharedImageToken(entry.uri, files.authority)).writeText(record)
            assertThrows(FileNotFoundException::class.java) {
                ShareImageFiles(context).liveFile(entry.uri, created)
            }
            store.cleanup()
            assertFalse(files.image(sharedImageToken(entry.uri, files.authority)).exists())
            entry.close()
        }
        image.close()
    }

    @Test fun cleanupPreservesFilesOutsideTheOwnedNonceNamespace() = runBlocking {
        val foreign = listOf(
            File(files.images, "keep.txt"),
            File(files.drafts, "keep.txt"),
            File(files.record("a".repeat(64)).parentFile, "keep.meta")
        )
        try {
            foreign.forEach {
                it.parentFile!!.mkdirs()
                it.writeText("fixture remains untouched")
            }
            val image = preparedImage()
            val store = SharedSearchImageStore(context)
            val entry = store.publish(image)
            store.cleanup()
            foreign.forEach { assertEquals("fixture remains untouched", it.readText()) }
            assertTrue(files.liveFile(entry.uri, System.currentTimeMillis()).isFile)
            entry.close()
            image.close()
        } finally {
            foreign.forEach { it.delete() }
        }
    }

    private suspend fun preparedImage(): SearchImage {
        val source = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
        return AndroidSearchImagePreparer().prepare(source, ImageBox(0f, 0f, 64f, 32f)).also {
            source.recycle()
        }
    }
}
