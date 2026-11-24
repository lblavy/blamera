package dev.blamera.chatgpt.storage

import dev.blamera.storage.AtomicFileReplacement
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.nio.file.Files

internal interface SessionFile {
    fun read(): ByteArray?
    fun write(value: ByteArray)
    fun clear()
}

internal class AtomicSessionFile(
    private val file: File,
    private val replacement: AtomicFileReplacement,
    private val writeBytes: (FileOutputStream, ByteArray) -> Unit = { output, value ->
        output.write(value)
    }
) : SessionFile {
    constructor(
        file: File,
        writeBytes: (FileOutputStream, ByteArray) -> Unit = { output, value -> output.write(value) }
    ) : this(file, AtomicFileReplacement(), writeBytes)

    private val pending = File(file.path + ".new")

    override fun read(): ByteArray? {
        val input = try {
            file.inputStream()
        } catch (missing: FileNotFoundException) {
            if (file.exists()) throw missing else return null
        }
        return input.use {
            if (it.channel.size() > MAX_ENCRYPTED_SESSION_BYTES) throw SessionStorageException()
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(READ_BYTES)
            var count = it.read(buffer)
            while (count != -1) {
                if (bytes.size().toLong() + count > MAX_ENCRYPTED_SESSION_BYTES) {
                    throw SessionStorageException()
                }
                bytes.write(buffer, 0, count)
                count = it.read(buffer)
            }
            bytes.toByteArray()
        }
    }

    override fun write(value: ByteArray) {
        if (value.size > MAX_ENCRYPTED_SESSION_BYTES) throw SessionStorageException()
        Files.createDirectories(file.absoluteFile.parentFile!!.toPath())
        try {
            pending.outputStream().use { output ->
                writeBytes(output, value)
                output.fd.sync()
            }
            // No non-atomic fallback: failure leaves the last complete record untouched.
            replacement.replace(pending.toPath(), file.toPath())
        } finally {
            Files.deleteIfExists(pending.toPath())
        }
    }

    override fun clear() {
        for (entry in listOf(file, File(file.path + ".bak"), pending)) {
            Files.deleteIfExists(entry.toPath())
            if (entry.exists()) throw SessionStorageException()
        }
    }
}

private const val READ_BYTES = 4096
