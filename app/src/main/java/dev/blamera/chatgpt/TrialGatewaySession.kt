package dev.blamera.chatgpt

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Owns one RAM session; protected storage is authoritative across gateway instances. */
internal class TrialGatewaySession(
    private val api: TrialAccountApi,
    private val now: () -> Long,
    private val persistence: TrialSessionPersistence?,
    private val awaitRefreshForeground: suspend (suspend () -> Unit) -> Unit
) {
    private var record: TrialSessionRecord? = null
    private var sessionEpoch: Long? = null

    @Volatile private var closed = false
    val preferredModel: String? get() = record?.preferredModel
    val preferredReasoningEffort: String? get() = record?.preferredReasoningEffort
    val canRestore: Boolean get() = persistence?.available == true
    val epoch: Long get() = persistence?.epoch ?: 0L

    suspend fun connected(
        credentials: TrialCredentials,
        startedAt: Long,
        check: suspend () -> Unit
    ) = coordinated {
        check()
        requireTrial(startedAt == epoch, TrialFailure.CANCELLED)
        persistence?.block()
        persistence?.clear()
        val next = TrialSessionRecord(credentials)
        if (credentials.grant.planEnabled) {
            commitConnection(next, startedAt, check)
        } else {
            record =
                next
        }
        sessionEpoch = startedAt
    }

    suspend fun restore(check: suspend () -> Unit): ConnectedAccount? = coordinated {
        val storage = persistence ?: return@coordinated null
        val startedAt = epoch
        val saved = storage.read(now()) ?: return@coordinated null
        check()
        val next = fresh(saved, startedAt, check)
        record = next
        sessionEpoch = startedAt
        ConnectedAccount(next.credentials.identity.displayName, next.credentials.grant.planEnabled)
    }

    suspend fun authorized(check: suspend () -> Unit): TrialCredentials = coordinated {
        check()
        requireTrial(sessionEpoch == epoch, TrialFailure.AUTHENTICATION)
        val previous = record ?: throw ChatGptTrialException(TrialFailure.AUTHENTICATION)
        val latest = if (persistence == null) previous else persistence.read(now())
        requireTrial(latest != null, TrialFailure.AUTHENTICATION)
        requireTrial(
            latest!!.credentials.identity.subject == previous.credentials.identity.subject,
            TrialFailure.AUTHENTICATION
        )
        val next = fresh(latest, epoch, check)
        record = next
        next.credentials
    }

    suspend fun rememberSelection(
        select: (TrialSessionRecord) -> TrialSessionRecord,
        check: suspend () -> Unit
    ) = coordinated {
        check()
        requireTrial(sessionEpoch == epoch, TrialFailure.AUTHENTICATION)
        val previous = record ?: throw ChatGptTrialException(TrialFailure.AUTHENTICATION)
        val current = if (persistence == null) record else persistence.read(now())
        requireTrial(current != null, TrialFailure.AUTHENTICATION)
        requireTrial(
            current!!.credentials.identity.subject == previous.credentials.identity.subject,
            TrialFailure.AUTHENTICATION
        )
        val updated = select(current)
        persistence?.save(updated)
        check()
        record = updated
    }

    suspend fun disconnect() {
        persistence?.invalidate()
        val inMemory = record
        record = null
        sessionEpoch = null
        coordinated {
            try {
                persistence?.block()
                val previous = if (persistence ==
                    null
                ) {
                    inMemory
                } else {
                    persistence.read(now(), forRevocation = true)
                }
                if (previous !=
                    null
                ) {
                    api.revoke(
                        previous.credentials.clientId,
                        previous.credentials.grant.refreshToken
                    )
                }
            } finally {
                withContext(NonCancellable) { persistence?.clear() }
            }
        }
    }

    fun close() {
        closed = true
        record = null
        sessionEpoch = null
    }

    suspend fun request(
        request: TrialHttpRequest,
        onChunk: (suspend (ByteArray) -> Unit)? = null
    ): String = try {
        api.request(request, onChunk)
    } catch (failure: ChatGptTrialException) {
        if (failure.kind == TrialFailure.AUTHENTICATION) {
            coordinated {
                val latest = if (persistence == null) record else persistence.read(now())
                if (latest != null && latest.credentials.grant.accessToken == request.bearer) {
                    record = null
                    persistence?.block()
                    persistence?.clear()
                }
            }
        }
        throw failure
    }

    private suspend fun fresh(
        saved: TrialSessionRecord,
        startedAt: Long,
        check: suspend () -> Unit
    ): TrialSessionRecord {
        val previous = saved.credentials
        requireTrial(previous.grant.planEnabled, TrialFailure.PLAN_PERMISSION)
        if (now() <
            previous.savedAt + previous.grant.expiresIn - TrialLimits.REFRESH_MARGIN_SECONDS
        ) {
            return saved
        }
        // An offline public preflight must not consume a still-renewable refresh token.
        api.prepare()
        awaitRefreshForeground(check)
        check()
        requireTrial(startedAt == epoch, TrialFailure.CANCELLED)
        record = null
        persistence?.clear()
        val grant = api.refresh(previous.clientId, previous.grant)
        check()
        val identity = if (grant.idToken == null) {
            previous.identity
        } else {
            api.verify(grant.idToken, previous.clientId, null, previous.identity.subject)
        }
        requireTrial(grant.planEnabled, TrialFailure.PLAN_PERMISSION)
        val updated =
            TrialSessionRecord(
                TrialCredentials(previous.clientId, grant, identity, now()),
                saved.preferredModel,
                saved.preferredReasoningEffort
            )
        retainValidatedRotation(persistence, updated, { startedAt == epoch }) {
            if (!closed) record = updated
        }
        check()
        return updated
    }

    private suspend fun commitConnection(
        next: TrialSessionRecord,
        startedAt: Long,
        check: suspend () -> Unit
    ) {
        try {
            persistence?.save(next)
            check()
            requireTrial(startedAt == epoch, TrialFailure.CANCELLED)
            persistence?.unblock()
            record = next
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                persistence?.block()
                persistence?.clear()
            }
            throw cancelled
        } catch (failure: ChatGptTrialException) {
            withContext(NonCancellable) {
                persistence?.block()
                persistence?.clear()
            }
            throw failure
        }
    }

    private suspend fun <T> coordinated(work: suspend () -> T): T =
        if (persistence == null) work() else persistence.coordinated(work)
}

/** Once cryptographically validated, a rotation belongs to the account, not the image request. */
private suspend fun retainValidatedRotation(
    persistence: TrialSessionPersistence?,
    next: TrialSessionRecord,
    isCurrent: () -> Boolean,
    retain: () -> Unit
) = withContext(NonCancellable) {
    try {
        persistence?.save(next)
    } catch (failure: ChatGptTrialException) {
        persistence?.block()
        persistence?.clear()
        throw failure
    }
    if (!isCurrent()) {
        // Logout must see this new refresh token for revocation, but it cannot authorize reuse.
        persistence?.block()
        throw ChatGptTrialException(TrialFailure.CANCELLED)
    }
    retain()
}
