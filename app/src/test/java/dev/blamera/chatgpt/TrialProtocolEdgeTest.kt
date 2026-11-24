package dev.blamera.chatgpt

import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrialProtocolEdgeTest {
    @Test fun invalidOrUnboundedImagesAndPromptsAreRejectedBeforeTransmission() {
        val images = listOf(
            byteArrayOf(),
            byteArrayOf(0, -40, 1, -1, -39),
            byteArrayOf(-1, 0, 1, -1, -39),
            byteArrayOf(-1, -40, 1, 0, -39),
            byteArrayOf(-1, -40, 1, -1, 0),
            ByteArray(MAX_TRIAL_JPEG_BYTES + 1)
        )
        images.forEach { expect(TrialFailure.INPUT) { trialImageRequest("model", it, "describe") } }
        listOf("", "x".repeat(TrialLimits.NAME_CHARS + 1)).forEach {
            expect(TrialFailure.INPUT) { trialImageRequest(it, JPEG, "describe") }
        }
        listOf("", "x".repeat(TrialLimits.INSTRUCTION_CHARS + 1)).forEach {
            expect(TrialFailure.INPUT) { trialImageRequest("model", JPEG, it) }
        }
    }

    @Test fun malformedAmbiguousAndUnboundedModelCatalogsCannotBeSelected() {
        val model = JSONObject().put("slug", "m").put("display_name", "Model")
            .put("visibility", "list")
        val catalogs = listOf(
            "invalid",
            "{}",
            JSONObject().put("models", JSONArray().put(model).put(model)).toString(),
            JSONObject().put(
                "models",
                JSONArray().put(JSONObject(model.toString()).put("slug", ""))
            )
                .toString(),
            JSONObject().put(
                "models",
                JSONArray().put(
                    JSONObject(model.toString())
                        .put("slug", "x".repeat(201))
                )
            ).toString(),
            JSONObject().put(
                "models",
                JSONArray().put(
                    JSONObject(model.toString())
                        .put("display_name", "x".repeat(201))
                )
            ).toString(),
            JSONObject().put("models", JSONArray().apply { repeat(501) { put(model) } }).toString()
        )
        catalogs.forEach { expect(TrialFailure.INVALID_RESPONSE) { parseTrialModels(it) } }
        assertTrue(parseTrialModels("""{"models":[]}""").isEmpty())
    }

    @Test fun streamHandlesMultilineEventsAndRejectsFailureIncompleteAndOverflow() {
        val text = "data: {\"type\":\"response.output_text.delta\",\n" +
            "data: \"delta\":\"Tasse\"}\n\n"
        val complete = "data: {\"type\":\"response.completed\"}"
        assertEquals("Tasse", parseTrialStream(": comment\n" + text + complete))
        val errors = listOf(
            "data: invalid\n\n",
            "data: {}\n\n",
            "data: {\"type\":\"response.incomplete\"}\n\n",
            "data: {\"type\":\"error\"}\n\n",
            complete,
            complete + "\n\n" + complete,
            "data: " + "x".repeat(TrialLimits.JWKS_BYTES + 1)
        )
        errors.forEach { expect(TrialFailure.INVALID_RESPONSE) { parseTrialStream(it) } }
        val overflow = JSONObject().put("type", "response.output_text.delta")
            .put("delta", "x".repeat(MAX_TRIAL_REPLY_CHARS + 1))
        expect(TrialFailure.INVALID_RESPONSE) { parseTrialStream("data: $overflow\n\n" + complete) }
        val created = "data: {\"type\":\"response.created\"}\n\n"
        assertEquals("Tasse", parseTrialStream(created + text + complete + "\n\n"))
        listOf(
            "subscription_sharing_usage_limit_exceeded",
            "subscription_sharing_usage_unavailable",
            "rate_limit_exceeded",
            "insufficient_quota"
        ).forEach { assertEquals(TrialFailure.USAGE_LIMIT, trialFailureCode(it)) }
        listOf(
            "invalid_token",
            "invalid_grant",
            "invalid_refresh_token",
            "refresh_token_reused",
            "refresh_token_expired",
            "refresh_token_invalidated"
        ).forEach {
            assertEquals(TrialFailure.AUTHENTICATION, trialFailureCode(it))
        }
        listOf(
            "model_not_found",
            "unsupported_model",
            "unsupported_image",
            "invalid_image"
        ).forEach {
            assertEquals(TrialFailure.MODEL_UNAVAILABLE, trialFailureCode(it))
        }
        assertEquals(TrialFailure.INVALID_RESPONSE, trialFailureCode(null))
    }

    @Test fun malformedCallbacksNeverSupplyCredentialsOrAClientIdentity() {
        val targets = listOf(
            "/auth/callback", "/auth/callback?code=c&client_id=id",
            "/auth/callback?state=s&code=&client_id=id",
            "/auth/callback?state=s&code=c&client_id=bad%20id",
            "/auth/callback?state=s&code=c&client_id=id#fragment",
            "/auth/callback?state=s&code=c&broken",
            "/auth/callback?state=%GG&code=c&client_id=id",
            "/auth/callback?state=s&code=c&client_id=" + "a".repeat(300),
            "/auth/callback?state=s&code=" + "x".repeat(9000)
        )
        targets.forEach { assertNull(it, parseTrialCallback(it, "s", null)) }
        assertNull(parseTrialCallback("/auth/callback?state=wrong&error=access_denied", "s", null))
        assertTrue(validTrialClientId("client_123-xyz"))
        assertTrue(!validTrialClientId(""))
    }

    @Test fun identityRejectsMalformedKeysAndClaimsAndSupportsOneAudienceInAnArray() {
        val expected = TrialIdentityExpectation("client", "nonce", 100)
        val jwks = TrialAuthorizationTest.jwks()
        val valid = TrialAuthorizationTest.token(TrialAuthorizationTest.claims())
        listOf("", "invalid", "a.b.c", valid.substringBeforeLast('.') + ".AA").forEach {
            expect(TrialFailure.AUTHENTICATION) { verifyTrialIdentity(it, jwks, expected) }
        }
        val invalidJwks = listOf(
            "invalid",
            "{}",
            """{"keys":[]}""",
            JSONObject(jwks).apply {
                getJSONArray("keys").getJSONObject(0).put("kid", "other")
            }.toString(),
            JSONObject(jwks).apply {
                getJSONArray("keys").getJSONObject(0).put("kty", "EC")
            }.toString(),
            JSONObject(jwks).apply {
                getJSONArray("keys").getJSONObject(0).put("use", "enc")
            }.toString(),
            JSONObject(jwks).apply {
                getJSONArray("keys").getJSONObject(0).put("n", "AQAB")
            }.toString()
        )
        invalidJwks.forEach {
            expect(TrialFailure.AUTHENTICATION) {
                verifyTrialIdentity(valid, it, expected)
            }
        }
        val mutations = listOf(
            TrialAuthorizationTest.claims().put("aud", JSONArray().put("client").put("other")),
            TrialAuthorizationTest.claims().put("aud", 4),
            TrialAuthorizationTest.claims().put("aud", JSONArray().put("other")),
            TrialAuthorizationTest.claims().put("exp", "200")
        )
        mutations.forEach {
            expect(TrialFailure.AUTHENTICATION) {
                verifyTrialIdentity(TrialAuthorizationTest.token(it), jwks, expected)
            }
        }
        val email = TrialAuthorizationTest.claims().apply {
            remove("name")
            put("email", "test@example.invalid")
        }
        assertEquals(
            "test@example.invalid",
            verifyTrialIdentity(TrialAuthorizationTest.token(email), jwks, expected).displayName
        )
        email.remove("email")
        email.put("aud", JSONArray().put("client"))
        assertEquals(
            "Compte ChatGPT",
            verifyTrialIdentity(TrialAuthorizationTest.token(email), jwks, expected).displayName
        )
        val algorithm = Base64.getUrlEncoder().withoutPadding().encodeToString(
            "{\"alg\":\"HS256\",\"kid\":\"key\"}".toByteArray()
        )
        expect(TrialFailure.AUTHENTICATION) {
            verifyTrialIdentity(algorithm + valid.substring(valid.indexOf('.')), jwks, expected)
        }
    }

    @Test fun tokenGrantsMustBeBoundedBearerCredentialsWithFiniteExpiry() {
        fun grant() = JSONObject().put("token_type", "Bearer").put("access_token", "a")
            .put("refresh_token", "r").put("expires_in", 3600)
        val invalid = listOf(
            grant().put("token_type", "Basic"),
            grant().put("access_token", ""),
            grant().put("access_token", "x".repeat(TrialLimits.TOKEN_BYTES + 1)),
            grant().put("refresh_token", ""),
            grant().put("refresh_token", "x".repeat(TrialLimits.TOKEN_BYTES + 1)),
            grant().put("expires_in", 30),
            grant().put("expires_in", 86401)
        )
        invalid.forEach { expect(TrialFailure.AUTHENTICATION) { parseTrialGrant(it.toString()) } }
        expect(TrialFailure.AUTHENTICATION) { parseTrialGrant("{}") }
        expect(TrialFailure.INVALID_RESPONSE) { parseTrialGrant("""{"token_type":"Bearer"}""") }
        val refreshed = parseTrialGrant(grant().toString(), TRIAL_PLAN_SCOPE)
        assertEquals(null, refreshed.idToken)
        assertTrue(refreshed.planEnabled)
    }

    private fun expect(kind: TrialFailure, action: () -> Unit) {
        val failure = assertThrows(ChatGptTrialException::class.java, action)
        assertEquals(kind, failure.kind)
    }

    private companion object {
        val JPEG = byteArrayOf(-1, -40, 1, -1, -39)
    }
}
