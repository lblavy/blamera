package dev.blamera.chatgpt

import dev.blamera.chatgpt.storage.SessionStorageException
import dev.blamera.chatgpt.storage.TrialSessionStore
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared ordering only: no credentials are retained in this coordinator. */
internal class TrialSessionCoordination {
    val mutex = Mutex()
    private val revision = AtomicLong()
    val epoch: Long get() = revision.get()
    fun invalidate() {
        revision.incrementAndGet()
    }
}

internal class TrialSessionPersistence(
    private val store: TrialSessionStore,
    private val registration: TrialRegistrationStore,
    private val coordination: TrialSessionCoordination = TrialSessionCoordination()
) {
    @Volatile var available = false
        private set
    val epoch: Long get() = coordination.epoch
    fun invalidate() = coordination.invalidate()
    fun block() {
        registration.sessionBlocked = true
        available = false
    }
    fun unblock() {
        registration.sessionBlocked = false
    }
    suspend fun <T> coordinated(work: suspend () -> T): T = coordination.mutex.withLock { work() }

    suspend fun read(now: Long, forRevocation: Boolean = false): TrialSessionRecord? {
        if (registration.sessionBlocked && !forRevocation) {
            available = false
            return null
        }
        val value = try {
            protectedStore { store.load() }
        } catch (failure: ChatGptTrialException) {
            clear()
            throw failure
        }
        available = value != null && !forRevocation
        return value?.let {
            try {
                decodeTrialSession(value, registration, now).also { available = !forRevocation }
            } catch (failure: ChatGptTrialException) {
                clear()
                throw failure
            }
        }
    }

    suspend fun save(record: TrialSessionRecord) {
        val encoded = encodeTrialSession(record, registration.hostId)
        decodeTrialSession(encoded, registration, record.credentials.savedAt)
        protectedStore { store.save(encoded) }
        available = true
    }

    suspend fun clear() {
        available = false
        protectedStore { store.clear() }
    }
}

private suspend inline fun <T> protectedStore(work: () -> T): T = try {
    work()
} catch (_: SessionStorageException) {
    throw ChatGptTrialException(TrialFailure.STORAGE)
}
