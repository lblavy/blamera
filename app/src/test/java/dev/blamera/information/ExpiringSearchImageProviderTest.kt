package dev.blamera.information

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import dev.blamera.vision.ImageBox
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class ExpiringSearchImageProviderTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val files = ShareImageFiles(context)

    @After fun cleanupOwnedFixtures() {
        synchronized(ShareCacheGuard.lock) { files.cleanup(Long.MAX_VALUE) }
    }

    @Test fun contentResolverReadsTheExactPreviewJpegAndGenericMetadata() = runBlocking {
        val source = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
        }
        val image = AndroidSearchImagePreparer().prepare(source, ImageBox(0f, 0f, 64f, 32f))
        val entry = SharedSearchImageStore(context).publish(image)
        val bytes = context.contentResolver.openInputStream(entry.uri)!!.use { it.readBytes() }
        assertArrayEquals(image.jpeg, bytes)
        assertEquals("image/jpeg", context.contentResolver.getType(entry.uri))
        context.contentResolver.query(entry.uri, null, null, null, null)!!.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                "image.jpg",
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            )
            assertEquals(
                bytes.size.toLong(),
                cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
            )
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(image.preview.width, decoded.width)
        assertEquals(image.preview.height, decoded.height)
        exportReadFixture(bytes)
        decoded.recycle()
        entry.close()
        image.close()
        source.recycle()
    }

    @Test fun onlyReadModeIsAcceptedAndTheUriDoesNotGrantOtherFiles() = runBlocking {
        val image = searchImage()
        val entry = SharedSearchImageStore(context).publish(image)
        listOf("w", "rw", "wa", "rwt", "wt").forEach { mode ->
            assertThrows(SecurityException::class.java) {
                context.contentResolver.openFileDescriptor(entry.uri, mode)
            }
        }
        assertThrows(UnsupportedOperationException::class.java) {
            context.contentResolver.delete(entry.uri, null, null)
        }
        val wrong = listOf(
            "content://${files.authority}/metadata/" + "a".repeat(64) + ".meta",
            "content://${files.authority}/images/../metadata/a.meta",
            entry.uri.toString() + "?other=1",
            entry.uri.toString() + "#fragment",
            "content://${files.authority}/images/wrong.jpg"
        )
        wrong.forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                context.contentResolver.openInputStream(Uri.parse(value))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            FileProvider.getUriForFile(
                context,
                files.authority,
                File(context.cacheDir, "unrelated.jpg")
            )
        }
        entry.close()
        image.close()
    }

    @Test fun projectedMetadataReturnsOnlyTheRequestedGenericFields() = runBlocking {
        val image = searchImage()
        val entry = SharedSearchImageStore(context).publish(image)
        try {
            val projection = arrayOf(OpenableColumns.SIZE, "unknown", OpenableColumns.DISPLAY_NAME)
            context.contentResolver.query(entry.uri, projection, null, null, null)!!.use { cursor ->
                assertArrayEquals(projection, cursor.columnNames)
                assertTrue(cursor.moveToFirst())
                assertEquals(image.jpeg.size.toLong(), cursor.getLong(0))
                assertTrue(cursor.isNull(1))
                assertEquals("image.jpg", cursor.getString(2))
                assertFalse(cursor.moveToNext())
            }
        } finally {
            entry.close()
            image.close()
        }
    }

    @Test fun anExpiredOrMissingRecordIsRejectedWithoutAnInMemoryRegistry() = runBlocking {
        val image = searchImage()
        val entry = SharedSearchImageStore(context) {
            System.currentTimeMillis() - SHARE_TTL_MILLIS -
                1
        }.publish(image)
        assertThrows(FileNotFoundException::class.java) {
            context.contentResolver.openInputStream(entry.uri)
        }
        assertThrows(FileNotFoundException::class.java) {
            context.contentResolver.getType(entry.uri)
        }
        entry.close()
        val fresh = SharedSearchImageStore(context).publish(image)
        files.record(sharedImageToken(fresh.uri, files.authority)).delete()
        assertThrows(FileNotFoundException::class.java) {
            context.contentResolver.openInputStream(fresh.uri)
        }
        assertFalse(image.preview.isRecycled)
        fresh.close()
        image.close()
    }

    private fun exportReadFixture(bytes: ByteArray) {
        val current = File(checkNotNull(System.getProperty("user.dir")))
        val root = if (File(current, "AGENTS.md").isFile) current else current.parentFile!!
        val destination =
            File(root, "build/reports/validation/m2-lens-native/coder/content-resolver-fixture.jpg")
        destination.parentFile!!.mkdirs()
        destination.writeBytes(bytes)
    }
}
