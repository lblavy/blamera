package dev.blamera.chatgpt

import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class TrialIdentityExpectation(
    val clientId: String,
    val nonce: String?,
    val nowSeconds: Long,
    val subject: String? = null
)

internal fun verifyTrialIdentity(
    token: String,
    jwks: String,
    expected: TrialIdentityExpectation
): TrialIdentity = identityValidation {
    val parts = token.split('.')
    require(token.length <= TrialLimits.TOKEN_BYTES && parts.size == TrialLimits.JWT_PARTS)
    val header = JSONObject(String(decodeTrialBase64(parts[0]), Charsets.UTF_8))
    require(header.getString("alg") == "RS256")
    val key = trialSigningKey(jwks, header.getString("kid"))
    val signature = Signature.getInstance("SHA256withRSA").apply {
        initVerify(key)
        update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
    }
    require(signature.verify(decodeTrialBase64(parts[2])))
    val claims = JSONObject(String(decodeTrialBase64(parts[1]), Charsets.UTF_8))
    validateTrialClaims(
        claims,
        expected.clientId,
        expected.nonce,
        expected.nowSeconds,
        expected.subject
    )
}

private fun trialSigningKey(jwks: String, kid: String) = identityValidation {
    val keys = JSONObject(jwks).getJSONArray("keys")
    require(keys.length() <= TrialLimits.KEY_COUNT)
    val matching = (0 until keys.length()).map { keys.getJSONObject(it) }
        .filter { it.optString("kid") == kid }
    require(matching.size == 1)
    val key = matching.single()
    require(key.getString("kty") == "RSA")
    require(key.optString("alg", "RS256") == "RS256" && key.optString("use", "sig") == "sig")
    val modulus = BigInteger(1, decodeTrialBase64(key.getString("n")))
    require(modulus.bitLength() in TrialLimits.RSA_MIN_BITS..TrialLimits.RSA_MAX_BITS)
    val exponent = BigInteger(1, decodeTrialBase64(key.getString("e")))
    require(
        exponent.bitLength() <= TrialLimits.EXPONENT_BITS &&
            exponent >= BigInteger.valueOf(TrialLimits.EXPONENT_MIN)
    )
    KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
}

private fun validateTrialClaims(
    claims: JSONObject,
    clientId: String,
    nonce: String?,
    nowSeconds: Long,
    expectedSubject: String?
): TrialIdentity {
    require(claims.getString("iss") == TRIAL_AUTH_ORIGIN)
    require(trialAudienceMatches(claims.get("aud"), clientId))
    require(claims.get("exp") is Number && claims.getLong("exp") > nowSeconds)
    if (nonce != null) require(claims.getString("nonce") == nonce)
    val subject = claims.getString("sub")
    require(subject.isNotBlank() && subject.length <= TrialLimits.ID_CHARS)
    if (expectedSubject != null) require(subject == expectedSubject)
    val display = claims.optString("name").ifBlank { claims.optString("email") }
        .ifBlank { "Compte ChatGPT" }.take(TrialLimits.NAME_CHARS)
    return TrialIdentity(subject, display)
}

private fun trialAudienceMatches(audience: Any, clientId: String): Boolean = when (audience) {
    is String -> audience == clientId
    is JSONArray -> audience.length() == 1 && audience.optString(0) == clientId
    else -> false
}

private fun decodeTrialBase64(value: String): ByteArray = Base64.getUrlDecoder().decode(value)

private fun <T> identityValidation(action: () -> T): T = try {
    action()
} catch (_: IllegalArgumentException) {
    throw ChatGptTrialException(TrialFailure.AUTHENTICATION)
} catch (_: JSONException) {
    throw ChatGptTrialException(TrialFailure.AUTHENTICATION)
} catch (_: GeneralSecurityException) {
    throw ChatGptTrialException(TrialFailure.AUTHENTICATION)
}
