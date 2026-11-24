package dev.blamera.chatgpt.storage

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal fun interface SessionKeyProvider {
    fun key(): SecretKey
}

internal class SessionCipher(private val domain: String, private val keys: SessionKeyProvider) {
    constructor(keys: SessionKeyProvider) : this("dev.blamera.chatgpt.session.v1", keys)
    fun encrypt(value: String): ByteArray = protectedSessionOperation {
        val plain = sessionUtf8(value)
        try {
            val cipher = Cipher.getInstance(SESSION_CIPHER)
            cipher.init(Cipher.ENCRYPT_MODE, keys.key())
            cipher.updateAAD(domain.toByteArray(Charsets.UTF_8))
            if (cipher.iv.size != SESSION_IV_BYTES) throw SessionStorageException()
            byteArrayOf(SESSION_VERSION) + cipher.iv + cipher.doFinal(plain)
        } finally {
            plain.fill(0)
        }
    }

    fun decrypt(value: ByteArray): String = protectedSessionOperation {
        if (value.size !in MIN_ENCRYPTED_SESSION_BYTES..MAX_ENCRYPTED_SESSION_BYTES ||
            value[0] != SESSION_VERSION
        ) {
            throw SessionStorageException()
        }
        val cipher = Cipher.getInstance(SESSION_CIPHER)
        val iv = value.copyOfRange(1, 1 + SESSION_IV_BYTES)
        cipher.init(Cipher.DECRYPT_MODE, keys.key(), GCMParameterSpec(SESSION_TAG_BITS, iv))
        cipher.updateAAD(domain.toByteArray(Charsets.UTF_8))
        val plain = cipher.doFinal(value, 1 + SESSION_IV_BYTES, value.size - 1 - SESSION_IV_BYTES)
        try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(plain))
                .toString()
        } finally {
            plain.fill(0)
        }
    }
}

private fun sessionUtf8(value: String): ByteArray {
    if (value.length > MAX_SESSION_BYTES) throw SessionStorageException()
    val encoded = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value))
    if (encoded.remaining() > MAX_SESSION_BYTES) throw SessionStorageException()
    return ByteArray(encoded.remaining()).also(encoded::get)
}

private const val SESSION_VERSION: Byte = 1
private const val SESSION_IV_BYTES = 12
private const val SESSION_TAG_BITS = 128
private const val SESSION_TAG_BYTES = SESSION_TAG_BITS / 8
private const val SESSION_CIPHER = "AES/GCM/NoPadding"
private const val MIN_ENCRYPTED_SESSION_BYTES = 1 + SESSION_IV_BYTES + SESSION_TAG_BYTES
internal const val MAX_ENCRYPTED_SESSION_BYTES = MIN_ENCRYPTED_SESSION_BYTES + MAX_SESSION_BYTES
