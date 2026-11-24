package dev.blamera.chatgpt.storage

/** Opaque credential record; the gateway owns its schema and account validation. */
internal interface TrialSessionStore {
    suspend fun load(): String?
    suspend fun save(value: String)
    suspend fun clear()
}

internal class SessionStorageException : Exception("Protected session storage unavailable.")

internal const val MAX_SESSION_BYTES = 128 * 1024
