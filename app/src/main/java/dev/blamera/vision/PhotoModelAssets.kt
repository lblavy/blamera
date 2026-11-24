package dev.blamera.vision

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal data class ModelAsset(val name: String, val size: Long, val sha256: String) {
    init {
        require(size > 0 && sha256.matches(Regex("[a-f0-9]{64}")))
    }
}

internal data class CachedModel(val file: File, val hit: Boolean)

internal class ModelFileCache(
    private val directory: File,
    private val openAsset: (String) -> InputStream
) {
    fun prepare(asset: ModelAsset): CachedModel {
        check(directory.isDirectory || directory.mkdirs())
        val file = File(directory, "${asset.sha256}.onnx")
        if (validCached(file, asset)) return CachedModel(file, true)
        val pending = File(directory, "${asset.sha256}.pending")
        try {
            openAsset(asset.name).use { input ->
                pending.outputStream().use { output -> copyVerifiedAsset(input, output, asset) }
            }
            Files.move(
                pending.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
            return CachedModel(file, false)
        } finally {
            pending.delete()
        }
    }

    private fun validCached(file: File, asset: ModelAsset): Boolean {
        if (!file.isFile || file.length() != asset.size) return false
        return file.inputStream().use {
            try {
                copyVerifiedAsset(it, OutputStream.nullOutputStream(), asset)
                true
            } catch (_: IllegalArgumentException) {
                false
            }
        }
    }
}

internal fun copyVerifiedAsset(input: InputStream, output: OutputStream, asset: ModelAsset) {
    val digest = MessageDigest.getInstance("SHA-256")
    val chunk = ByteArray(MODEL_COPY_BUFFER_BYTES)
    var total = 0L
    var count = input.read(chunk)
    while (count != -1) {
        total += count
        require(total <= asset.size) { "Oversized asset: ${asset.name}" }
        digest.update(chunk, 0, count)
        output.write(chunk, 0, count)
        count = input.read(chunk)
    }
    require(total == asset.size) { "Asset length mismatch: ${asset.name}" }
    val hash = digest.digest().joinToString("") { "%02x".format(it) }
    require(hash == asset.sha256) { "Asset SHA-256 mismatch: ${asset.name}" }
}

private const val MODEL_COPY_BUFFER_BYTES = 65536
