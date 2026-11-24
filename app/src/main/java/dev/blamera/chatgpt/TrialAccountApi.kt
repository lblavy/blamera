package dev.blamera.chatgpt

import java.net.HttpURLConnection
import org.json.JSONObject

internal class TrialAccountApi(private val http: TrialHttp, private val now: () -> Long) {
    private var discoveryCache: JSONObject? = null

    suspend fun prepare() {
        discoveryCache = null
        endpoint(awaitDiscovery(), "jwks_uri")
    }

    suspend fun exchange(code: TrialAuthCode, attempt: TrialAuthAttempt): TrialGrant = tokenGrant(
        linkedMapOf(
            "grant_type" to "authorization_code",
            "client_id" to code.clientId,
            "code" to code.code,
            "code_verifier" to attempt.verifier,
            "redirect_uri" to attempt.redirectUri,
            "resource" to TRIAL_RESOURCE
        )
    )

    suspend fun refresh(clientId: String, previous: TrialGrant): TrialGrant = tokenGrant(
        linkedMapOf(
            "grant_type" to "refresh_token",
            "client_id" to clientId,
            "refresh_token" to previous.refreshToken,
            "resource" to TRIAL_RESOURCE
        ),
        previous.scope
    )

    suspend fun verify(
        token: String?,
        clientId: String,
        nonce: String?,
        subject: String? = null
    ): TrialIdentity {
        requireTrial(token != null, TrialFailure.AUTHENTICATION)
        val jwksUrl = endpoint(awaitDiscovery(), "jwks_uri")
        val jwks = request(TrialHttpRequest(jwksUrl, maxBytes = TrialLimits.JWKS_BYTES))
        return verifyTrialIdentity(
            checkNotNull(token),
            jwks,
            TrialIdentityExpectation(clientId, nonce, now(), subject)
        )
    }

    suspend fun revoke(clientId: String, refreshToken: String) {
        try {
            val url = endpoint(awaitDiscovery(), "revocation_endpoint")
            request(
                TrialHttpRequest(
                    url,
                    "POST",
                    trialForm(
                        mapOf(
                            "token" to refreshToken,
                            "token_type_hint" to "refresh_token",
                            "client_id" to clientId
                        )
                    ),
                    contentType = "application/x-www-form-urlencoded",
                    maxBytes = TrialLimits.JSON_BYTES
                )
            )
        } catch (_: ChatGptTrialException) {
            throw ChatGptTrialException(TrialFailure.REVOKE_FAILED)
        }
    }

    suspend fun request(
        request: TrialHttpRequest,
        onChunk: (suspend (ByteArray) -> Unit)? = null
    ): String {
        val response = if (onChunk ==
            null
        ) {
            http.execute(request)
        } else {
            http.executeStreaming(request, onChunk)
        }
        if (response.status !in
            HttpURLConnection.HTTP_OK until HttpURLConnection.HTTP_MULT_CHOICE
        ) {
            throw ChatGptTrialException(trialHttpFailure(response))
        }
        return response.body
    }

    private suspend fun tokenGrant(
        form: Map<String, String>,
        retainedScope: String = ""
    ): TrialGrant {
        val body = request(
            TrialHttpRequest(
                "$TRIAL_AUTH_ORIGIN/api/accounts/oauth/token",
                "POST",
                trialForm(form),
                contentType = "application/x-www-form-urlencoded",
                maxBytes = TrialLimits.JSON_BYTES
            )
        )
        return parseTrialGrant(body, retainedScope)
    }

    private suspend fun awaitDiscovery(): JSONObject {
        discoveryCache?.let { return it }
        val body = request(
            TrialHttpRequest(
                "$TRIAL_AUTH_ORIGIN/.well-known/openid-configuration",
                maxBytes = TrialLimits.JSON_BYTES
            )
        )
        val discovery = trialJson {
            JSONObject(body).also { requireTrial(it.getString("issuer") == TRIAL_AUTH_ORIGIN) }
        }
        discoveryCache = discovery
        return discovery
    }

    private fun endpoint(discovery: JSONObject, key: String): String = trialJson {
        trustedTrialAuthUrl(discovery.getString(key))
    }
}

internal class TrialGrant(
    val accessToken: String,
    val refreshToken: String,
    val idToken: String?,
    val expiresIn: Long,
    val scope: String
) {
    val planEnabled: Boolean get() = scope.split(' ').contains(TRIAL_PLAN_SCOPE)
}

internal fun parseTrialGrant(body: String, retainedScope: String = ""): TrialGrant = trialJson {
    val json = JSONObject(body)
    requireTrial(
        json.optString("token_type").equals("Bearer", ignoreCase = true),
        TrialFailure.AUTHENTICATION
    )
    val access = json.getString("access_token")
    val refresh = json.getString("refresh_token")
    requireTrial(
        access.isNotBlank() && access.length <= TrialLimits.TOKEN_BYTES,
        TrialFailure.AUTHENTICATION
    )
    requireTrial(
        refresh.isNotBlank() && refresh.length <= TrialLimits.TOKEN_BYTES,
        TrialFailure.AUTHENTICATION
    )
    val expires = json.getLong("expires_in")
    requireTrial(
        expires in (TrialLimits.REFRESH_MARGIN_SECONDS + 1)..TrialLimits.MAX_GRANT_SECONDS,
        TrialFailure.AUTHENTICATION
    )
    return@trialJson TrialGrant(
        access,
        refresh,
        json.optString("id_token").takeIf { it.isNotBlank() },
        expires,
        json.optString("scope", retainedScope)
    )
}

private fun trialHttpFailure(response: TrialHttpResponse): TrialFailure {
    val code = try {
        val json = JSONObject(response.body)
        json.optJSONObject("error")?.optString("code") ?: json.optString("error")
    } catch (_: org.json.JSONException) {
        ""
    }
    return when {
        response.status == HttpURLConnection.HTTP_UNAUTHORIZED ||
            response.status == HttpURLConnection.HTTP_FORBIDDEN -> TrialFailure.AUTHENTICATION
        response.status == TrialLimits.TOO_MANY_REQUESTS -> TrialFailure.USAGE_LIMIT
        response.status >= HttpURLConnection.HTTP_INTERNAL_ERROR -> TrialFailure.SERVICE_UNAVAILABLE
        else -> trialFailureCode(code)
    }
}
