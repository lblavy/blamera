package dev.blamera.information

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileNotFoundException

internal object ShareCacheGuard {
    val lock = Any()
}

internal class ShareImageFiles(context: Context) {
    private val context = context.applicationContext
    val authority = "${context.packageName}.lens-images"
    private val cache = this.context.cacheDir.canonicalFile
    private val home = File(cache, "lens-share")
    val images get() = directory("images")
    private val metadata get() = directory("metadata")
    val drafts get() = directory("drafts")

    fun image(token: String) = ownedFile(images, "$token.jpg")
    fun record(token: String) = ownedFile(metadata, "$token.meta")
    fun draft(token: String) = ownedFile(drafts, "$token.jpg")
    fun uri(token: String): Uri = FileProvider.getUriForFile(context, authority, image(token))

    fun liveFile(uri: Uri, now: Long): File {
        val token = sharedImageToken(uri, authority)
        val image = image(token)
        val meta = readRecord(record(token))
        if (!image.isFile || meta?.isLive(now, image.length()) != true) {
            throw FileNotFoundException("Search image unavailable or expired")
        }
        return image
    }

    fun remove(token: String) {
        image(token).delete()
        record(token).delete()
        draft(token).delete()
    }

    fun cleanup(now: Long) {
        images.listFiles().orEmpty().forEach { file ->
            val token = SHARE_JPEG_NAME.matchEntire(file.name)?.groupValues?.get(1)
            if (token != null && !isLive(token, now)) remove(token)
        }
        drafts.listFiles().orEmpty().filter {
            SHARE_JPEG_NAME.matches(it.name)
        }.forEach { it.delete() }
        metadata.listFiles().orEmpty().forEach { file ->
            val token = file.name.removeSuffix(".meta")
            if (SHARE_TOKEN.matches(token) && !image(token).isFile) file.delete()
        }
    }

    private fun isLive(token: String, now: Long): Boolean =
        runCatching { liveFile(uri(token), now) }.isSuccess

    private fun directory(name: String): File {
        val directory = File(home, name)
        check(home.canonicalFile.parentFile == cache)
        check(directory.canonicalFile.parentFile == home.canonicalFile)
        return directory
    }
}

internal fun sharedImageToken(uri: Uri, authority: String): String {
    require(uri.scheme == "content" && uri.authority == authority)
    require(uri.query == null && uri.fragment == null)
    val segments = uri.pathSegments
    require(segments.size == URI_SEGMENTS && segments[0] == "images")
    return requireNotNull(SHARE_JPEG_NAME.matchEntire(segments[1]))
        .groupValues[1]
}

private fun ownedFile(directory: File, name: String): File {
    val file = File(directory, name)
    check(file.canonicalFile.parentFile == directory.canonicalFile)
    return file
}

private fun readRecord(file: File): ShareFileMetadata? = runCatching {
    if (!file.isFile ||
        file.length() > MAX_METADATA_BYTES
    ) {
        null
    } else {
        parseShareMetadata(file.readText())
    }
}.getOrNull()

private const val URI_SEGMENTS = 2
private const val MAX_METADATA_BYTES = 128
