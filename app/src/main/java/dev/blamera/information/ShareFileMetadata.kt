package dev.blamera.information

internal const val SHARE_TTL_MILLIS = 300000L
internal const val MAX_SHARED_IMAGES = 2
internal val SHARE_TOKEN = Regex("[a-f0-9]{64}")
internal val SHARE_JPEG_NAME = Regex("([a-f0-9]{64})\\.jpg")

internal data class ShareFileMetadata(val created: Long, val expires: Long, val bytes: Long) {
    fun isLive(now: Long, actualBytes: Long): Boolean = created > 0 && expires > created &&
        expires - created == SHARE_TTL_MILLIS && now >= created && now < expires &&
        bytes in 1..MAX_JPEG_BYTES.toLong() && bytes == actualBytes

    fun encode(): String = "$created\n$expires\n$bytes"
}

internal fun parseShareMetadata(value: String): ShareFileMetadata? {
    val values = value.split('\n')
    if (values.size != METADATA_VALUES) return null
    val created = values[0].toLongOrNull()
    val expires = values[1].toLongOrNull()
    val bytes = values[2].toLongOrNull()
    return if (created != null && expires != null && bytes != null) {
        ShareFileMetadata(created, expires, bytes)
    } else {
        null
    }
}

private const val METADATA_VALUES = 3
