package dev.blamera.chatgpt

import android.util.Log

internal enum class TrialConnectionPhase(val label: String, val status: String) {
    RESTORING_SESSION("reprise de la connexion", "Reprise de la connexion protégée…"),
    CHECKING_SERVICE("vérification du service", "Vérification de l’accès au service ChatGPT…"),
    PREPARING_CALLBACK("préparation de la connexion", "Préparation de la connexion…"),
    WAITING_BROWSER("retour du navigateur", "En attente du retour de connexion du navigateur…"),
    CALLBACK_RECEIVED("retour reçu", "Retour de connexion reçu."),
    WAITING_APP_RETURN("retour dans Blamera", "Revenez dans Blamera pour terminer la connexion…"),
    EXCHANGING_CODE("finalisation de la connexion", "Finalisation de la connexion…"),
    VERIFYING_ACCOUNT("vérification du compte", "Vérification du compte ChatGPT…"),
    LOADING_MODELS("chargement des modèles", "Chargement des modèles disponibles…")
}

internal data class TrialDiagnostic(
    val phase: TrialConnectionPhase?,
    val failure: TrialFailure? = null,
    val failed: Boolean = failure != null
)

internal const val CHATGPT_DIAGNOSTIC_TAG = "BlameraChatGpt"

internal fun writeTrialDiagnostic(event: TrialDiagnostic) {
    val phase = event.phase?.name ?: "NO_PHASE"
    val message = if (event.failed) {
        "FAILURE_${phase}_${event.failure?.name ?: "BOUNDARY"}"
    } else {
        "PHASE_$phase"
    }
    Log.i(CHATGPT_DIAGNOSTIC_TAG, message)
}
