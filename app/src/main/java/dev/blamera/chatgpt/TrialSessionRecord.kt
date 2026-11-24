package dev.blamera.chatgpt

import dev.blamera.chatgpt.storage.MAX_SESSION_BYTES
import org.json.JSONException
import org.json.JSONObject

/** Only identity already verified during OAuth is admitted to the authenticated store. */
internal class TrialCredentials(
    val clientId: String,
    val grant: TrialGrant,
    val identity: TrialIdentity,
    val savedAt: Long
)

internal class TrialSessionRecord(
    val credentials: TrialCredentials,
    val preferredModel: String? = null,
    val preferredReasoningEffort: String? = null
)

internal fun encodeTrialSession(record: TrialSessionRecord, hostId: String): String {
    val session = record.credentials
    return JSONObject().put("version", 1).put("hostId", hostId)
        .put("issuer", TRIAL_AUTH_ORIGIN).put("resource", TRIAL_RESOURCE)
        .put("clientId", session.clientId).put("subject", session.identity.subject)
        .put("displayName", session.identity.displayName).put("savedAt", session.savedAt)
        .put("preferredModel", record.preferredModel ?: JSONObject.NULL)
        .put("preferredReasoningEffort", record.preferredReasoningEffort ?: JSONObject.NULL)
        .put(
            "grant",
            JSONObject().put("access_token", session.grant.accessToken)
                .put("refresh_token", session.grant.refreshToken).put("token_type", "Bearer")
                .put("expires_in", session.grant.expiresIn).put("scope", session.grant.scope)
        )
        .toString().also {
            requireTrial(
                it.toByteArray(Charsets.UTF_8).size <= MAX_SESSION_BYTES,
                TrialFailure.STORAGE
            )
        }
}

internal fun decodeTrialSession(
    value: String,
    registration: TrialRegistrationStore,
    now: Long
): TrialSessionRecord = validatedSession {
    require(value.length <= MAX_SESSION_BYTES)
    require(value.toByteArray(Charsets.UTF_8).size <= MAX_SESSION_BYTES)
    val json = JSONObject(value)
    validateRegistration(json, registration)
    val identity = readStoredIdentity(json)
    val savedAt = json.getLong("savedAt")
    require(json.get("savedAt") is Number && savedAt in 0..now)
    val grant = parseTrialGrant(json.getJSONObject("grant").toString())
    require(
        grant.planEnabled &&
            grant.scope.split(' ').containsAll(listOf("offline_access", "resource.invoke"))
    )
    require(grant.scope.length <= TrialLimits.JSON_BYTES)
    val preferred = json.optString("preferredModel").takeIf { !json.isNull("preferredModel") }
    if (preferred !=
        null
    ) {
        require(preferred.isNotBlank() && preferred.length <= TrialLimits.NAME_CHARS)
    }
    val effort = json.optString("preferredReasoningEffort")
        .takeIf { !json.isNull("preferredReasoningEffort") }
    require(validTrialEffort(effort))
    TrialSessionRecord(
        TrialCredentials(json.getString("clientId"), grant, identity, savedAt),
        preferred,
        effort
    )
}

private fun validateRegistration(json: JSONObject, registration: TrialRegistrationStore) {
    require(json.get("version") is Number && json.getInt("version") == 1)
    require(json.getString("issuer") == TRIAL_AUTH_ORIGIN)
    require(json.getString("resource") == TRIAL_RESOURCE)
    require(json.getString("hostId") == registration.hostId)
    val client = json.getString("clientId")
    require(validTrialClientId(client) && client == registration.issuedClientId)
}

private fun readStoredIdentity(json: JSONObject): TrialIdentity {
    val subject = json.getString("subject")
    val display = json.getString("displayName")
    require(subject.isNotBlank() && subject.length <= TrialLimits.ID_CHARS)
    require(display.isNotBlank() && display.length <= TrialLimits.NAME_CHARS)
    return TrialIdentity(subject, display)
}

private inline fun <T> validatedSession(work: () -> T): T = try {
    work()
} catch (_: JSONException) {
    throw ChatGptTrialException(TrialFailure.AUTHENTICATION)
} catch (_: IllegalArgumentException) {
    throw ChatGptTrialException(TrialFailure.AUTHENTICATION)
} catch (_: ChatGptTrialException) {
    throw ChatGptTrialException(TrialFailure.AUTHENTICATION)
}
