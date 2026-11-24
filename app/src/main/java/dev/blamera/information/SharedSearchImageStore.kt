package dev.blamera.information

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal interface SharedSearchImage : AutoCloseable {
    val uri: Uri
}

internal fun interface SearchImagePublisher {
    suspend fun publish(image: SearchImage): SharedSearchImage
    suspend fun cleanup() = Unit
}

internal class SharedSearchImageStore(
    context: Context,
    private val now: () -> Long = System::currentTimeMillis
) : SearchImagePublisher {
    private val context = context.applicationContext
    private val files = ShareImageFiles(context)
    private val expiryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var expiryJob: Job? = null

    override suspend fun publish(image: SearchImage): SharedSearchImage {
        var published: SharedSearchImage? = null
        var returned = false
        return try {
            val result = withContext(Dispatchers.IO) {
                val coroutine = currentCoroutineContext()
                coroutine.ensureActive()
                val entry = synchronized(ShareCacheGuard.lock) {
                    create(image) { coroutine.ensureActive() }
                }
                published = entry
                entry
            }
            scheduleExpiry()
            returned = true
            result
        } finally {
            if (!returned) published?.close()
        }
    }

    private fun create(image: SearchImage, ensureActive: () -> Unit): SharedSearchImage {
        require(image.jpeg.isNotEmpty() && image.jpeg.size <= MAX_JPEG_BYTES)
        files.cleanup(now())
        evictOldImages()
        check(
            files.images.listFiles().orEmpty().count { SHARE_JPEG_NAME.matches(it.name) } <
                MAX_SHARED_IMAGES
        )
        val token = newShareToken()
        val draft = files.draft(token)
        draft.parentFile?.mkdirs()
        var committed = false
        return try {
            draft.writeBytes(image.jpeg)
            ensureActive()
            val created = now()
            val metadata =
                ShareFileMetadata(created, Math.addExact(created, SHARE_TTL_MILLIS), draft.length())
            files.record(token).apply {
                parentFile?.mkdirs()
                writeText(metadata.encode())
            }
            files.image(token).parentFile?.mkdirs()
            ensureActive()
            check(draft.renameTo(files.image(token))) { "Image publication failed" }
            val uri = files.uri(token)
            committed = true
            Entry(uri) { remove(uri) }
        } finally {
            if (!committed) files.remove(token)
        }
    }

    private fun evictOldImages() {
        val existing = files.images.listFiles().orEmpty()
            .filter { SHARE_JPEG_NAME.matches(it.name) }.sortedBy { it.lastModified() }
        val count = (existing.size - MAX_SHARED_IMAGES + 1).coerceAtLeast(0)
        existing.take(count).forEach { file -> files.remove(file.name.removeSuffix(".jpg")) }
    }

    override suspend fun cleanup() = withContext(Dispatchers.IO) {
        synchronized(ShareCacheGuard.lock) { files.cleanup(now()) }
    }

    private fun remove(uri: Uri) {
        synchronized(ShareCacheGuard.lock) { files.remove(sharedImageToken(uri, files.authority)) }
        context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun scheduleExpiry() {
        expiryJob?.cancel()
        expiryJob = expiryScope.launch {
            delay(SHARE_TTL_MILLIS)
            cleanup()
        }
    }

    private class Entry(override val uri: Uri, private val remove: () -> Unit) : SharedSearchImage {
        override fun close() = remove()
    }
}

private fun newShareToken(): String {
    val bytes = ByteArray(SHARE_TOKEN_BYTES)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

private const val SHARE_TOKEN_BYTES = 32
