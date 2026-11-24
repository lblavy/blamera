package dev.blamera.chatgpt

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class OpenAiTrialGateway(
    private val registration: TrialRegistrationStore,
    private val http: TrialHttp,
    private val listenerFactory: () -> TrialCallbackListener,
    environment: TrialGatewayEnvironment,
    private val persistence: TrialSessionPersistence? = null
) : ChatGptTrialGateway {
    constructor(
        registration: TrialRegistrationStore,
        http: TrialHttp,
        listenerFactory: () -> TrialCallbackListener,
        nowSeconds: () -> Long = {
            System.currentTimeMillis() / TrialLimits.MILLIS_PER_SECOND
        },
        random: () -> String = ::trialRandomValue,
        awaitAppForeground: suspend () -> Unit = { }
    ) : this(
        registration,
        http,
        listenerFactory,
        TrialGatewayEnvironment(nowSeconds, random, awaitAppForeground, awaitAppForeground)
    )

    private val nowSeconds = environment.nowSeconds
    private val random = environment.random
    private val mutex = Mutex()
    private val accountApi = TrialAccountApi(http, nowSeconds)
    private val foregroundWait = TrialForegroundWait(environment.awaitAppForeground)
    private val refreshForegroundWait = TrialForegroundWait(environment.awaitRefreshForeground)
    private val session = TrialGatewaySession(accountApi, nowSeconds, persistence) { check ->
        refreshForegroundWait.await(check)
    }
    private val lifetime = TrialGatewayEpoch()

    @Volatile private var listener: TrialCallbackListener? = null

    private var allowedModels = emptySet<String>()

    override val preferredModel: String? get() = session.preferredModel
    override val preferredReasoningEffort: String? get() = session.preferredReasoningEffort
    override val retainsSession: Boolean get() = persistence != null
    override val canRestore: Boolean get() = session.canRestore

    override suspend fun restore(): ConnectedAccount? = mutex.withLock {
        val epoch = lifetime.begin()
        foregroundWait.await { lifetime.check(epoch) }
        session.restore { lifetime.check(epoch) }.also { allowedModels = emptySet() }
    }

    override suspend fun rememberModel(slug: String) = mutex.withLock {
        val epoch = lifetime.begin()
        requireTrial(slug in allowedModels, TrialFailure.MODEL_UNAVAILABLE)
        session.rememberSelection({ current ->
            TrialSessionRecord(current.credentials, slug, current.preferredReasoningEffort)
        }) { lifetime.check(epoch) }
    }

    override suspend fun rememberReasoningEffort(effort: String?) = mutex.withLock {
        val epoch = lifetime.begin()
        requireTrial(validTrialEffort(effort), TrialFailure.INPUT)
        session.rememberSelection({ current ->
            TrialSessionRecord(current.credentials, current.preferredModel, effort)
        }) { lifetime.check(epoch) }
    }

    override suspend fun connect(
        onPhase: (TrialConnectionPhase) -> Unit,
        openBrowser: (String) -> Unit
    ): ConnectedAccount = mutex.withLock {
        val epoch = lifetime.begin()
        val startedAt = session.epoch
        val acquired = AtomicReference<TrialCallbackListener?>()
        try {
            onPhase(TrialConnectionPhase.CHECKING_SERVICE)
            accountApi.prepare()
            lifetime.check(epoch)
            onPhase(TrialConnectionPhase.PREPARING_CALLBACK)
            val callback = withContext(Dispatchers.IO) {
                listenerFactory().also {
                    // Own the resource before a cancelled dispatcher return can discard it.
                    acquired.set(it)
                    listener = it
                }
            }
            val previousId = registration.issuedClientId?.takeIf(::validTrialClientId)
            val attempt = TrialAuthAttempt(
                registration.hostId,
                previousId ?: TRIAL_DYNAMIC_CLIENT,
                callback.redirectUri,
                random(),
                random(),
                random()
            )
            lifetime.check(epoch)
            onPhase(TrialConnectionPhase.WAITING_BROWSER)
            openBrowser(trialAuthorizeUrl(attempt))
            lifetime.check(epoch)
            val code = callback.await(attempt.state, previousId)
            lifetime.check(epoch)
            onPhase(TrialConnectionPhase.CALLBACK_RECEIVED)
            requireTrial(validTrialClientId(code.clientId), TrialFailure.AUTHENTICATION)
            registration.issuedClientId = code.clientId
            onPhase(TrialConnectionPhase.WAITING_APP_RETURN)
            foregroundWait.await { lifetime.check(epoch) }
            onPhase(TrialConnectionPhase.EXCHANGING_CODE)
            lifetime.check(epoch)
            val grant = accountApi.exchange(code, attempt)
            onPhase(TrialConnectionPhase.VERIFYING_ACCOUNT)
            val identity = accountApi.verify(grant.idToken, code.clientId, attempt.nonce)
            lifetime.check(epoch)
            session.connected(
                TrialCredentials(code.clientId, grant, identity, nowSeconds()),
                startedAt
            ) {
                lifetime.check(epoch)
            }
            allowedModels = emptySet()
            ConnectedAccount(identity.displayName, grant.planEnabled)
        } finally {
            val callback = acquired.get()
            callback?.close()
            if (listener === callback) listener = null
        }
    }

    override suspend fun models(): List<TrialModel> = mutex.withLock {
        val epoch = lifetime.begin()
        val credentials = session.authorized { lifetime.check(epoch) }
        val body =
            session.request(
                TrialHttpRequest(
                    "$TRIAL_RESOURCE/models",
                    bearer = credentials.grant.accessToken,
                    maxBytes =
                    TrialLimits.MODELS_BYTES
                )
            )
        val models = parseTrialModels(body)
        lifetime.check(epoch)
        allowedModels = models.map { it.slug }.toSet()
        models
    }

    override suspend fun analyze(model: String, jpeg: ByteArray, instruction: String): String =
        analyzeStreaming(model, jpeg, instruction, null) { }

    override suspend fun analyzeStreaming(
        model: String,
        jpeg: ByteArray,
        instruction: String,
        effort: String?,
        onText: suspend (String) -> Unit
    ): String = mutex.withLock {
        val epoch = lifetime.begin()
        requireTrial(model in allowedModels, TrialFailure.MODEL_UNAVAILABLE)
        val body = trialImageRequest(model, jpeg, instruction, effort)
        val credentials = session.authorized { lifetime.check(epoch) }
        lifetime.check(epoch)
        val stream = TrialResponseStream()
        var published: String? = null
        session.request(
            TrialHttpRequest(
                "$TRIAL_RESOURCE/responses",
                "POST",
                body,
                credentials.grant.accessToken
            )
        ) { bytes ->
            lifetime.check(epoch)
            stream.accept(bytes)?.let { text ->
                onText(text)
                lifetime.check(epoch)
                published = text
            }
        }
        lifetime.check(epoch)
        val result = stream.finish()
        if (result != published) onText(result)
        lifetime.check(epoch)
        result
    }

    override fun cancel() {
        lifetime.cancel()
        foregroundWait.cancel()
        refreshForegroundWait.cancel()
        listener?.close()
        http.cancel()
    }

    override suspend fun disconnect() {
        cancel()
        mutex.withLock {
            allowedModels = emptySet()
            session.disconnect()
        }
    }

    override fun close() {
        lifetime.close()
        cancel()
        session.close()
        allowedModels = emptySet()
        http.close()
    }
}
