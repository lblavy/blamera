package dev.blamera.chatgpt.ui

import android.content.ActivityNotFoundException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.blamera.chatgpt.ChatGptTrialException
import dev.blamera.chatgpt.ChatGptTrialGateway
import dev.blamera.chatgpt.ConnectedAccount
import dev.blamera.chatgpt.SystemTrialTimeoutScheduler
import dev.blamera.chatgpt.TrialAppForeground
import dev.blamera.chatgpt.TrialConnectionPhase
import dev.blamera.chatgpt.TrialDiagnostic
import dev.blamera.chatgpt.TrialFailure
import dev.blamera.chatgpt.TrialModel
import dev.blamera.chatgpt.TrialTimeoutScheduler
import dev.blamera.chatgpt.trialReasoningEfforts
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val TRIAL_INSTRUCTION =
    "Décris cette image, identifie l’objet principal et indique tes incertitudes. Réponds en français."
internal const val CHATGPT_USAGE_URL = "https://chatgpt.com/settings/usage"

internal data class TrialUiState(
    val account: ConnectedAccount? = null,
    val models: List<TrialModel> = emptyList(),
    val selectedModel: String? = null,
    val selectedReasoningEffort: String? = null,
    val busy: Boolean = false,
    val status: String = "Aucune image envoyée.",
    val response: String? = null,
    val responseModel: String? = null,
    val responseIncomplete: Boolean = false,
    val error: String? = null,
    val connectionPhase: TrialConnectionPhase? = null,
    val restoreAvailable: Boolean = false
) {
    val canAnalyze: Boolean
        get() = !busy && account?.planUsageEnabled == true &&
            models.any { it.slug == selectedModel }
}

internal class ChatGptTrialViewModel(
    private val gateway: ChatGptTrialGateway,
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val timeoutScheduler: TrialTimeoutScheduler = SystemTrialTimeoutScheduler,
    private val diagnostics: (TrialDiagnostic) -> Unit = { },
    val appForeground: TrialAppForeground = TrialAppForeground(),
    private val image: () -> ByteArray = ::createTrialJpeg
) : ViewModel(),
    AutoCloseable {
    var state by mutableStateOf(TrialUiState())
        private set

    val imageJpeg: ByteArray by lazy(image)
    val objects = ObjectChatGptState(this)
    private val operation = TrialOperationLifetime(gateway)
    val sessionRetention: Boolean get() = gateway.retainsSession
    private var closed = false
    private var restoreStarted = false
    private val preferences = TrialPreferenceWriter(scope) { error ->
        state = state.copy(error = safeTrialError(error))
    }
    private var activePreview: TrialStreamPreview? = null

    init {
        addCloseable(this)
    }

    fun restore(once: Boolean = false) {
        if (!sessionRetention || (once && restoreStarted)) return
        if (closed || state.busy || state.account != null) {
            return
        }
        restoreStarted = true
        appForeground.beginConnection()
        runOperation("Reprise de la connexion…", connecting = true) {
            state = state.copy(
                connectionPhase = TrialConnectionPhase.RESTORING_SESSION,
                status = TrialConnectionPhase.RESTORING_SESSION.status
            )
            diagnostics(TrialDiagnostic(TrialConnectionPhase.RESTORING_SESSION))
            val account = gateway.restore()
            currentCoroutineContext().ensureActive()
            if (account == null) {
                TrialUiState()
            } else {
                state = state.copy(
                    connectionPhase = TrialConnectionPhase.LOADING_MODELS,
                    status = TrialConnectionPhase.LOADING_MODELS.status
                )
                diagnostics(TrialDiagnostic(TrialConnectionPhase.LOADING_MODELS))
                val models = gateway.models()
                state.copy(
                    account = account,
                    models = models,
                    selectedModel = preferredTrialModel(gateway, models),
                    selectedReasoningEffort = preferredTrialEffort(gateway, models),
                    restoreAvailable = false,
                    status = connectionStatus(account, models)
                )
            }
        }
    }

    fun connect(openBrowser: (String) -> Unit) {
        if (closed || state.busy || state.account?.planUsageEnabled == true) return
        appForeground.beginConnection()
        val attempt = operation.generation + 1
        val progress: (TrialConnectionPhase) -> Unit = { phase ->
            if (currentTrialAttempt(closed, attempt, operation.generation, state.busy)) {
                diagnostics(TrialDiagnostic(phase))
                state = state.copy(connectionPhase = phase, status = phase.status)
            }
        }
        runOperation("Connexion en cours…", connecting = true) {
            val account = gateway.connect(onPhase = progress) { url ->
                if (currentTrialAttempt(
                        closed,
                        attempt,
                        operation.generation,
                        state.busy
                    )
                ) {
                    openBrowser(url)
                }
            }
            currentCoroutineContext().ensureActive()
            val models = if (account.planUsageEnabled) {
                progress(TrialConnectionPhase.LOADING_MODELS)
                gateway.models()
            } else {
                emptyList()
            }
            state.copy(
                account = account,
                models = models,
                selectedModel = preferredTrialModel(gateway, models),
                selectedReasoningEffort = preferredTrialEffort(gateway, models),
                status = connectionStatus(account, models)
            )
        }
    }

    fun selectModel(slug: String) {
        if (!closed && !state.busy && state.models.any { it.slug == slug }) {
            val effort = state.selectedReasoningEffort?.takeIf { it in trialReasoningEfforts(slug) }
            state = state.copy(
                selectedModel = slug,
                selectedReasoningEffort = effort,
                response = null,
                responseModel = null,
                responseIncomplete = false
            )
            val attempt = operation.generation
            preferences.save({ attempt == operation.generation && !closed }) {
                gateway.rememberModel(slug)
                gateway.rememberReasoningEffort(effort)
            }
        }
    }

    fun selectReasoningEffort(effort: String?) {
        val selected = state.selectedModel ?: return
        val supported = effort == null || effort in trialReasoningEfforts(selected)
        if (closed || !state.canAnalyze || !supported) return
        state = state.copy(selectedReasoningEffort = effort)
        val attempt = operation.generation
        preferences.save({ attempt == operation.generation && !closed }) {
            gateway.rememberModel(selected)
            gateway.rememberReasoningEffort(effort)
        }
    }

    fun analyze(
        jpeg: ByteArray? = null,
        instruction: String = TRIAL_INSTRUCTION,
        isCurrent: () -> Boolean = { true }
    ) {
        if (closed || !state.canAnalyze || !isCurrent()) return
        val selected = checkNotNull(state.selectedModel)
        val effort = state.selectedReasoningEffort
        val input = jpeg ?: imageJpeg
        val attempt = operation.generation + 1
        val preview = TrialStreamPreview()
        runOperation("Analyse en cours…", preview = preview) {
            val response = gateway.analyzeStreaming(selected, input, instruction, effort) { text ->
                withContext(scope.coroutineContext.minusKey(Job)) {
                    currentCoroutineContext().ensureActive()
                    if (currentTrialAttempt(closed, attempt, operation.generation, state.busy) &&
                        isCurrent() && preview.update(text)
                    ) {
                        state = state.copy(
                            response = text,
                            responseModel = selected,
                            responseIncomplete = true
                        )
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            if (!isCurrent()) throw CancellationException("Obsolete object")
            preview.update(response)
            state.copy(
                response = response,
                responseModel = selected,
                responseIncomplete = false,
                status = "Analyse terminée."
            )
        }
    }

    fun cancel(clearDocument: Boolean = false) {
        if (closed || (!state.busy && !clearDocument)) return
        if (clearDocument && state.busy && state.connectionPhase != null) {
            state = state.copy(response = null, responseModel = null, responseIncomplete = false)
            return
        }
        val partial = activePreview?.latest ?: state.response
        activePreview = null
        operation.invalidate()
        state = if (clearDocument) {
            state.copy(
                busy = false,
                error = null,
                response = null,
                responseModel = null,
                responseIncomplete = false,
                connectionPhase = null,
                status = "Aucune image envoyée."
            )
        } else {
            state.copy(
                busy = false,
                error = null,
                response = partial,
                responseIncomplete = !partial.isNullOrEmpty(),
                status = "Essai annulé."
            )
        }
    }

    fun disconnect() {
        if (closed || state.busy) return
        preferences.cancel()
        state = TrialUiState()
        runOperation("Déconnexion en cours…") {
            gateway.disconnect()
            TrialUiState(status = "Session déconnectée.")
        }
    }

    fun browserUnavailable() {
        state = state.copy(error = "Impossible d’ouvrir le navigateur.")
    }

    private fun runOperation(
        status: String,
        connecting: Boolean = false,
        preview: TrialStreamPreview? = null,
        work: suspend () -> TrialUiState
    ) {
        state = state.copy(
            busy = true,
            status = status,
            error = null,
            response = null,
            responseModel = null,
            responseIncomplete = false,
            connectionPhase = if (connecting) TrialConnectionPhase.PREPARING_CALLBACK else null
        )
        val attempt = ++operation.generation
        activePreview = preview
        operation.job = scope.launch {
            val deadline = armConnectionWatchdog(connecting, scope, timeoutScheduler) {
                if (attempt == operation.generation && state.busy) {
                    operation.invalidate()
                    state = interruptedTrialState(
                        state.copy(restoreAvailable = gateway.canRestore),
                        ChatGptTrialException(TrialFailure.TIMEOUT),
                        diagnostics
                    ).copy(busy = false)
                }
            }
            operation.deadline = deadline
            try {
                val result = trialOperation {
                    deadline?.check()
                    work().also { deadline?.finish() }
                }
                currentCoroutineContext().ensureActive()
                if (attempt == operation.generation) {
                    state = result.fold(
                        onSuccess = { it },
                        onFailure = {
                            interruptedTrialState(
                                state.copy(
                                    restoreAvailable = gateway.canRestore,
                                    response = preview?.latest ?: state.response
                                ),
                                it,
                                diagnostics
                            )
                        }
                    )
                }
            } finally {
                deadline?.close()
                if (operation.deadline === deadline) operation.deadline = null
                if (attempt == operation.generation) {
                    activePreview = null
                    state = state.copy(busy = false)
                }
            }
        }
    }

    override fun close() {
        if (closed) return
        objects.close()
        preferences.cancel()
        operation.invalidate()
        closed = true
        scope.cancel()
        gateway.close()
        activePreview = null
        state = TrialUiState()
    }
}

private fun interruptedTrialState(
    state: TrialUiState,
    error: Throwable,
    diagnostics: (TrialDiagnostic) -> Unit
): TrialUiState {
    diagnostics(
        TrialDiagnostic(state.connectionPhase, (error as? ChatGptTrialException)?.kind, true)
    )
    val reconnect = (error as? ChatGptTrialException)?.kind.let {
        it == TrialFailure.AUTHENTICATION || it == TrialFailure.PLAN_PERMISSION
    }
    val accountState = if (reconnect) {
        state.copy(
            account = null,
            models = emptyList(),
            selectedModel = null,
            selectedReasoningEffort = null,
            restoreAvailable = false
        )
    } else {
        state
    }
    return accountState.copy(
        responseIncomplete = !state.response.isNullOrEmpty(),
        error = safeTrialError(error),
        status = "Essai interrompu."
    )
}

private fun connectionStatus(account: ConnectedAccount, models: List<TrialModel>): String = when {
    !account.planUsageEnabled -> "Compte connecté ; usage de l’abonnement non autorisé."
    models.isEmpty() -> "Aucun modèle disponible pour ce compte."
    else -> "Compte connecté. Aucune image envoyée."
}

private fun safeTrialError(error: Throwable): String =
    (error as? ChatGptTrialException)?.kind?.message ?: "L’essai a échoué. Vous pouvez réessayer."

/** Expected gateway, browser and local image failures are reduced to safe UI messages. */
internal suspend fun <T> trialOperation(work: suspend () -> T): Result<T> = try {
    Result.success(work())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: ChatGptTrialException) {
    Result.failure(error)
} catch (error: IllegalStateException) {
    Result.failure(error)
} catch (error: IllegalArgumentException) {
    Result.failure(error)
} catch (error: ActivityNotFoundException) {
    Result.failure(error)
} catch (error: SecurityException) {
    Result.failure(error)
} catch (error: IOException) {
    Result.failure(error)
}

internal fun ChatGptTrialViewModel.restoreOnce() = restore(once = true)

private fun preferredTrialModel(gateway: ChatGptTrialGateway, models: List<TrialModel>): String? =
    models.firstOrNull { it.slug == gateway.preferredModel }?.slug ?: models.firstOrNull()?.slug

private fun preferredTrialEffort(gateway: ChatGptTrialGateway, models: List<TrialModel>): String? {
    val selected = preferredTrialModel(gateway, models) ?: return null
    return gateway.preferredReasoningEffort?.takeIf { it in trialReasoningEfforts(selected) }
}
