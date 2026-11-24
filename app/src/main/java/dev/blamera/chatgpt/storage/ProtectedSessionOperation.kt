package dev.blamera.chatgpt.storage

import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException

/** Boundary errors never retain a provider's message, path, cause or credential value. */
internal inline fun <T> protectedSessionOperation(action: () -> T): T = try {
    action()
} catch (_: IOException) {
    throw SessionStorageException()
} catch (_: GeneralSecurityException) {
    throw SessionStorageException()
} catch (_: ProviderException) {
    throw SessionStorageException()
} catch (_: SecurityException) {
    throw SessionStorageException()
} catch (_: IllegalArgumentException) {
    throw SessionStorageException()
}
