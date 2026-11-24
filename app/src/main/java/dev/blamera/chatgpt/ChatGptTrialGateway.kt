package dev.blamera.chatgpt

import android.content.Context

internal interface ChatGptTrialGateway : AutoCloseable {
    val retainsSession: Boolean get() = false
    val preferredModel: String? get() = null
    val preferredReasoningEffort: String? get() = null
    val canRestore: Boolean get() = false
    suspend fun restore(): ConnectedAccount? = null
    suspend fun rememberModel(slug: String) { }
    suspend fun rememberReasoningEffort(effort: String?) { }
    suspend fun connect(
        onPhase: (TrialConnectionPhase) -> Unit = { },
        openBrowser: (String) -> Unit
    ): ConnectedAccount
    suspend fun models(): List<TrialModel>
    suspend fun analyze(model: String, jpeg: ByteArray, instruction: String): String
    suspend fun analyzeStreaming(
        model: String,
        jpeg: ByteArray,
        instruction: String,
        effort: String?,
        onText: suspend (String) -> Unit
    ): String {
        val text = analyze(model, jpeg, instruction)
        onText(text)
        return text
    }
    fun cancel()
    suspend fun disconnect()
}

internal data class ConnectedAccount(val displayName: String, val planUsageEnabled: Boolean)
internal data class TrialModel(val slug: String, val displayName: String)

internal enum class TrialFailure(val message: String) {
    AUTHENTICATION("Connexion ChatGPT refusée ou expirée. Reconnectez-vous."),
    PLAN_PERMISSION("L’utilisation de l’abonnement n’a pas été autorisée."),
    NETWORK("Connexion au service impossible. Vérifiez le réseau puis réessayez."),
    SERVICE_UNAVAILABLE("Le service ChatGPT est temporairement indisponible. Réessayez plus tard."),
    CANCELLED("Essai annulé."),
    TIMEOUT("Le délai est dépassé. Vous pouvez réessayer manuellement."),
    INVALID_RESPONSE("Réponse du service invalide ou incomplète."),
    USAGE_LIMIT("Limite d’utilisation atteinte. Aucun nouvel envoi automatique."),
    MODEL_UNAVAILABLE("Ce modèle ne peut pas traiter cette demande. Choisissez-en un autre."),
    INPUT("Image ou consigne invalide pour cet essai."),
    STORAGE(
        "La connexion protégée n’a pas pu être mémorisée ou retrouvée. Reconnectez-vous explicitement."
    ),
    REVOKE_FAILED(
        "Session locale effacée. Révocation distante non confirmée : " +
            "vérifiez les accès dans ChatGPT."
    )
}

internal class ChatGptTrialException(val kind: TrialFailure) : Exception(kind.message)

internal fun createChatGptTrialGateway(
    context: Context,
    awaitAppForeground: suspend () -> Unit
): ChatGptTrialGateway = createChatGptTrialGateway(context, awaitAppForeground, awaitAppForeground)

internal fun createChatGptTrialGateway(
    context: Context,
    awaitAppForeground: suspend () -> Unit,
    awaitRefreshForeground: suspend () -> Unit
): ChatGptTrialGateway = OpenAiTrialGateway(
    AndroidTrialRegistration(context),
    HttpsTrialHttp(diagnostics = ::writeTrialHttpDiagnostic),
    ::TrialLoopback,
    TrialGatewayEnvironment(
        awaitAppForeground = awaitAppForeground,
        awaitRefreshForeground = awaitRefreshForeground
    )
)
