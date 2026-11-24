package dev.blamera.chatgpt

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal const val TRIAL_AUTH_ORIGIN = "https://auth.openai.com"
internal const val TRIAL_RESOURCE = "https://api.openai.com/v1"
internal const val TRIAL_DYNAMIC_CLIENT = "dynamic_agent_client"
internal const val TRIAL_PLAN_SCOPE = "chatgpt.tokens.use.direct"

internal data class TrialAuthAttempt(
    val hostId: String,
    val clientId: String,
    val redirectUri: String,
    val state: String,
    val nonce: String,
    val verifier: String
)

internal data class TrialAuthCode(val code: String, val clientId: String)
internal data class TrialIdentity(val subject: String, val displayName: String)

internal fun trialAuthorizeUrl(attempt: TrialAuthAttempt): String {
    val challenge = MessageDigest.getInstance(
        "SHA-256"
    ).digest(attempt.verifier.toByteArray(Charsets.US_ASCII))
    val parameters = linkedMapOf(
        "client_id" to attempt.clientId,
        "response_type" to "code",
        "redirect_uri" to attempt.redirectUri,
        "scope" to "openid profile email offline_access resource.invoke $TRIAL_PLAN_SCOPE",
        "resource" to TRIAL_RESOURCE,
        "state" to attempt.state,
        "nonce" to attempt.nonce,
        "code_challenge_method" to "S256",
        "code_challenge" to Base64.getUrlEncoder().withoutPadding().encodeToString(challenge),
        "ext_agent_host_id" to attempt.hostId
    )
    if (attempt.clientId == TRIAL_DYNAMIC_CLIENT) parameters["agent_name_hint"] = "Blamera"
    return "$TRIAL_AUTH_ORIGIN/api/accounts/authorize?" + trialForm(parameters)
}

internal fun trialForm(parameters: Map<String, String>): String =
    parameters.entries.joinToString("&") {
        URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
    }

internal fun trialRandomValue(): String {
    val bytes = ByteArray(TrialLimits.RANDOM_BYTES)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

internal fun parseTrialCallback(target: String, state: String, clientId: String?): TrialAuthCode? =
    try {
        val query = requireNotNull(callbackQuery(target))
        val returnedState = requireNotNull(query["state"])
        require(MessageDigest.isEqual(returnedState.toByteArray(), state.toByteArray()))
        if (query.containsKey("error")) throw ChatGptTrialException(TrialFailure.AUTHENTICATION)
        val issued = requireNotNull(query["client_id"] ?: clientId)
        require(validTrialClientId(issued))
        if (clientId != null) require(issued == clientId)
        val code = requireNotNull(query["code"])
        require(code.isNotBlank() && code.length <= TrialLimits.CODE_CHARS)
        TrialAuthCode(code, issued)
    } catch (_: IllegalArgumentException) {
        null
    }

private fun callbackQuery(target: String): Map<String, String>? = try {
    val uri = URI(target)
    val localPath = uri.scheme == null && uri.rawAuthority == null
    val expectedPath = uri.path == "/auth/callback" && uri.fragment == null
    if (target.length > TrialLimits.CALLBACK_BYTES || !localPath) {
        null
    } else if (!expectedPath || uri.rawQuery == null) {
        null
    } else {
        val pairs = uri.rawQuery.split('&').map { item ->
            val fields = item.split('=', limit = 2)
            require(fields.size == 2)
            URLDecoder.decode(fields[0], "UTF-8") to URLDecoder.decode(fields[1], "UTF-8")
        }
        pairs.toMap().takeIf { it.size == pairs.size }
    }
} catch (_: IllegalArgumentException) {
    null
} catch (_: java.net.URISyntaxException) {
    null
}

internal fun validTrialClientId(value: String): Boolean = value != TRIAL_DYNAMIC_CLIENT &&
    value.length in 1..TrialLimits.ID_CHARS &&
    value.all { it.isLetterOrDigit() || it == '_' || it == '-' }
