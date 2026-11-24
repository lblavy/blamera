package dev.blamera.chatgpt.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class EncryptedSessionStore(
    private val file: SessionFile,
    private val cipher: SessionCipher,
    private val mutex: Mutex = sessionFileMutex
) : TrialSessionStore {
    override suspend fun load(): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            protectedSessionOperation { file.read()?.let(cipher::decrypt) }
        }
    }

    override suspend fun save(value: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            val encrypted = cipher.encrypt(value)
            currentCoroutineContext().ensureActive()
            protectedSessionOperation { file.write(encrypted) }
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            protectedSessionOperation { file.clear() }
        }
    }
}

/** This file-operation lock is deliberately distinct from the gateway's refresh lock. */
private val sessionFileMutex = Mutex()
