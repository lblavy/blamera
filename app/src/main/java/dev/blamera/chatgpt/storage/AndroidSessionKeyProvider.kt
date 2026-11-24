package dev.blamera.chatgpt.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.Key
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal class AndroidSessionKeyProvider(
    private val existing: () -> Key? = { existingSessionKey() },
    private val generate: (KeyGenParameterSpec) -> SecretKey = ::generateSessionKey
) : SessionKeyProvider {
    override fun key(): SecretKey = when (val current = existing()) {
        null -> generate(sessionKeySpecification())
        is SecretKey -> current
        else -> throw SessionStorageException()
    }
}

internal fun sessionKeySpecification(alias: String = SESSION_KEY_ALIAS): KeyGenParameterSpec =
    KeyGenParameterSpec.Builder(
        alias,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    ).setKeySize(SESSION_KEY_BITS)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true)
        .build()

private fun existingSessionKey(alias: String = SESSION_KEY_ALIAS): Key? =
    KeyStore.getInstance(ANDROID_KEYSTORE).run {
        load(null)
        getKey(alias, null)
    }

internal fun androidSessionKeys(alias: String): SessionKeyProvider = AndroidSessionKeyProvider(
    existing = { existingSessionKey(alias) },
    generate = { generateSessionKey(sessionKeySpecification(alias)) }
)

private fun generateSessionKey(specification: KeyGenParameterSpec): SecretKey =
    KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
        init(specification)
        generateKey()
    }

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val SESSION_KEY_BITS = 256
private const val SESSION_KEY_ALIAS = "dev.blamera.chatgpt.session.v1"
