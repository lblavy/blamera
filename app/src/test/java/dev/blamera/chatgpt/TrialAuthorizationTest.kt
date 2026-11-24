package dev.blamera.chatgpt

import java.net.URI
import java.net.URLDecoder
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrialAuthorizationTest {
    @Test fun authorizationUsesOfficialPublicClientPkceScopesAndOneCallback() {
        val attempt =
            TrialAuthAttempt(
                "urn:uuid:host",
                TRIAL_DYNAMIC_CLIENT,
                "http://127.0.0.1:54321/auth/callback",
                "state",
                "nonce",
                "verifier"
            )
        val url = URI(trialAuthorizeUrl(attempt))
        assertEquals("https", url.scheme)
        assertEquals("auth.openai.com", url.host)
        assertEquals("/api/accounts/authorize", url.path)
        val params = query(url.rawQuery)
        assertEquals("Blamera", params["agent_name_hint"])
        assertEquals(attempt.redirectUri, params["redirect_uri"])
        assertEquals("S256", params["code_challenge_method"])
        assertEquals("iMnq5o6zALKXGivsnlom_0F5_WYda32GHkxlV7mq7hQ", params["code_challenge"])
        assertTrue(params.getValue("scope").split(' ').contains(TRIAL_PLAN_SCOPE))
        assertEquals(TRIAL_RESOURCE, params["resource"])
        assertTrue(
            !query(
                URI(trialAuthorizeUrl(attempt.copy(clientId = "oaiapp_saved"))).rawQuery
            ).containsKey("agent_name_hint")
        )
        assertTrue(trialRandomValue().length >= 43)
        assertNotEquals(trialRandomValue(), trialRandomValue())
    }

    @Test fun callbackBindsStatePathAndIssuedIdRejectingDuplicatesAndUnexpectedAccounts() {
        assertEquals(
            TrialAuthCode("one", "oaiapp_new"),
            parseTrialCallback("/auth/callback?state=s&code=one&client_id=oaiapp_new", "s", null)
        )
        assertEquals(
            TrialAuthCode("two", "saved"),
            parseTrialCallback("/auth/callback?state=s&code=two", "s", "saved")
        )
        listOf(
            "/callback?state=s&code=c&client_id=id",
            "/auth/callback?state=wrong&code=c&client_id=id",
            "/auth/callback?state=s&state=s&code=c&client_id=id",
            "/auth/callback?state=s&code=c&client_id=dynamic_agent_client",
            "/auth/callback?state=s&code=c",
            "http://evil.test/auth/callback?state=s&code=c&client_id=id"
        ).forEach { assertNull(it, parseTrialCallback(it, "s", null)) }
        assertNull(
            parseTrialCallback("/auth/callback?state=s&code=c&client_id=other", "s", "saved")
        )
        assertThrows(ChatGptTrialException::class.java) {
            parseTrialCallback("/auth/callback?state=s&error=access_denied", "s", null)
        }
    }

    @Test fun signedIdentityMustMatchIssuerAudienceNonceExpirySubjectAndAlgorithm() {
        val identity = verify(token(claims()), "client", "nonce", jwks(), 100)
        assertEquals(TrialIdentity("subject", "Test account"), identity)
        val mutations = listOf(
            claims().put("iss", "https://attacker.test"),
            claims().put("aud", "other"),
            claims().put("nonce", "wrong"),
            claims().put("exp", 90),
            claims().put("sub", "")
        )
        mutations.forEach {
            assertThrows(ChatGptTrialException::class.java) {
                verify(token(it), "client", "nonce", jwks(), 100)
            }
        }
        assertThrows(ChatGptTrialException::class.java) {
            verify(token(claims(), "none"), "client", "nonce", jwks(), 100)
        }
        assertThrows(ChatGptTrialException::class.java) {
            verify(token(claims()), "client", null, jwks(), 100, "someone_else")
        }
        assertThrows(ChatGptTrialException::class.java) {
            verify(
                token(claims()).dropLast(8) + "xxxxxxxx",
                "client",
                "nonce",
                jwks(),
                100
            )
        }
        assertEquals(
            "subject",
            verify(
                token(claims().removeNonce()),
                "client",
                null,
                jwks(),
                100,
                "subject"
            ).subject
        )
    }

    private fun verify(
        token: String,
        clientId: String,
        nonce: String?,
        jwks: String,
        nowSeconds: Long,
        subject: String? = null
    ): TrialIdentity = verifyTrialIdentity(
        token,
        jwks,
        TrialIdentityExpectation(clientId, nonce, nowSeconds, subject)
    )

    private fun JSONObject.removeNonce(): JSONObject = apply { remove("nonce") }
    private fun query(raw: String): Map<String, String> = raw.split('&').associate {
        val pair = it.split('=', limit = 2)
        URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
    }

    companion object {
        private val keys = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()
        fun claims(): JSONObject = JSONObject().put("iss", TRIAL_AUTH_ORIGIN).put("aud", "client")
            .put("sub", "subject").put("nonce", "nonce").put("exp", 200).put("name", "Test account")
        private fun base64(bytes: ByteArray): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        fun token(claims: JSONObject, algorithm: String = "RS256"): String {
            val header = JSONObject().put("alg", algorithm).put("kid", "key")
            val message =
                base64(header.toString().toByteArray()) + "." +
                    base64(claims.toString().toByteArray())
            val signature = Signature.getInstance("SHA256withRSA").apply {
                initSign(keys.private)
                update(message.toByteArray())
            }.sign()
            return message + "." + base64(signature)
        }
        fun jwks(): String {
            val key = keys.public as RSAPublicKey
            fun unsigned(bytes: ByteArray): ByteArray = if (bytes[0] ==
                0.toByte()
            ) {
                bytes.drop(1).toByteArray()
            } else {
                bytes
            }
            return JSONObject().put(
                "keys",
                JSONArray().put(
                    JSONObject().put("kid", "key").put("kty", "RSA")
                        .put("n", base64(unsigned(key.modulus.toByteArray())))
                        .put("e", base64(unsigned(key.publicExponent.toByteArray())))
                )
            ).toString()
        }
    }
}
