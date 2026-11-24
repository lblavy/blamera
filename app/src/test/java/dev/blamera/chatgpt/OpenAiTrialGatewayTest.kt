package dev.blamera.chatgpt

import dev.blamera.chatgpt.ui.ChatGptTrialViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OpenAiTrialGatewayTest {
    @Test
    fun richCatalogBeyond256KiBPassesThroughTheRealHttpAdapterAndKeepsListOrder() = runBlocking {
        val payload = """{"metadata":"${"unused".repeat(60 * 1024)}","models":[
            {"slug":"zeta","display_name":"Zêta","visibility":"list"},
            {"slug":"hidden","display_name":"Hidden","visibility":"hidden"},
            {"slug":"alpha","display_name":"Alpha","visibility":"list"}]}"""
        val bytes = payload.toByteArray(Charsets.UTF_8)
        assertTrue(bytes.size > 256 * 1024)
        val connection = CatalogHttps(java.io.ByteArrayInputStream(bytes))
        val adapter = HttpsTrialHttp { connection }
        http.modelsTransport = adapter
        try {
            gateway.connect { }
            assertEquals(
                listOf(TrialModel("zeta", "Zêta"), TrialModel("alpha", "Alpha")),
                gateway.models()
            )
            assertEquals("Bearer access", connection.getRequestProperty("Authorization"))
            assertTrue(connection.disconnected)
            assertFalse(http.requests.any { it.url.endsWith("responses") })
        } finally {
            adapter.close()
            gateway.close()
        }
    }

    @Test fun callbackWhilePausedWaitsForReturnBeforeOneTokenExchangeAndCatalog() = runBlocking {
        val foreground = TrialAppForeground()
        val callback = CompletableDeferred<Unit>()
        val subject = OpenAiTrialGateway(
            registration,
            http,
            { listener },
            { now },
            { "random" },
            foreground::await
        )
        val phases = mutableListOf<TrialConnectionPhase>()
        val connection = async {
            subject.connect(onPhase = {
                phases.add(it)
                if (it == TrialConnectionPhase.CALLBACK_RECEIVED) callback.complete(Unit)
            }) { foreground.update(false) }
        }
        try {
            callback.await()
            yield()
            assertFalse("No token exchange while the Activity is paused", hasTokenRequest())
            assertFalse(connection.isCompleted)
            assertEquals("client", registration.issuedClientId)
            assertEquals("WAITING_APP_RETURN", phases.last().name)
            foreground.update(true)
            assertTrue(connection.await().planUsageEnabled)
            assertEquals(1, http.requests.count { it.url.endsWith("oauth/token") })
            assertEquals(listOf(TrialModel("vision", "Vision")), subject.models())
            assertFalse(http.requests.any { it.url.endsWith("responses") })
        } finally {
            connection.cancelAndJoin()
            subject.close()
        }
    }

    @Test fun cancelAndCloseWhileWaitingCannotExchangeWhenTheActivityLaterResumes() = runBlocking {
        for (close in listOf(false, true)) {
            val foreground = TrialAppForeground()
            val callback = CompletableDeferred<Unit>()
            val subject = OpenAiTrialGateway(
                registration,
                http,
                { listener },
                { now },
                { "random" },
                foreground::await
            )
            val connection = async {
                subject.connect(onPhase = {
                    if (it == TrialConnectionPhase.CALLBACK_RECEIVED) callback.complete(Unit)
                }) { }
            }
            try {
                callback.await()
                yield()
                assertFalse("Token exchange must still be waiting", hasTokenRequest())
                if (close) subject.close() else subject.cancel()
                connection.join()
                assertTrue(connection.isCancelled)
                assertTrue(listener.closed)
                foreground.update(true)
                yield()
                assertFalse(hasTokenRequest())
            } finally {
                connection.cancelAndJoin()
                subject.close()
            }
        }
    }

    private fun hasTokenRequest() = http.requests.any { it.url.endsWith("oauth/token") }

    @Test fun cancellationAtExchangeAnnouncementStillPreventsTheTokenPost() = runBlocking {
        assertFailure(TrialFailure.CANCELLED) {
            gateway.connect(onPhase = {
                if (it == TrialConnectionPhase.EXCHANGING_CODE) gateway.cancel()
            }) { }
        }
        assertFalse(hasTokenRequest())
        assertTrue(listener.closed)
    }

    @Test fun globalDeadlineWhileWaitingForAppReturnPreventsAnyLateTokenOrCatalog() = runBlocking {
        val foreground = TrialAppForeground()
        val callback = CompletableDeferred<Unit>()
        val subject = OpenAiTrialGateway(
            registration,
            http,
            { listener },
            { now },
            { "random" },
            foreground::await
        )
        var expire: (() -> Unit)? = null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val model = ChatGptTrialViewModel(
            subject,
            scope,
            timeoutScheduler = TrialTimeoutScheduler { millis, action ->
                assertEquals(300_000L, millis)
                expire = action
                AutoCloseable { }
            },
            diagnostics = {
                if (it.phase == TrialConnectionPhase.CALLBACK_RECEIVED) callback.complete(Unit)
            },
            appForeground = foreground
        )
        try {
            model.connect { }
            callback.await()
            yield()
            assertFalse("Global budget must cover the foreground wait", hasTokenRequest())
            assertTrue(model.state.busy)
            expire!!.invoke()
            assertFalse(model.state.busy)
            assertEquals(TrialFailure.TIMEOUT.message, model.state.error)
            foreground.update(true)
            yield()
            assertFalse(hasTokenRequest())
            assertFalse(http.requests.any { it.url.endsWith("models") })
            assertFalse(http.requests.any { it.url.endsWith("responses") })
            assertEquals(null, model.state.account)
            assertTrue(listener.closed)
        } finally {
            model.close()
            scope.cancel()
        }
    }

    @Test fun elapsedBudgetRejectsResumeEvenWhenTheOsHasNotDeliveredTheWatchdog() = runBlocking {
        var elapsed = 0L
        val foreground = TrialAppForeground { elapsed }
        val callback = CompletableDeferred<Unit>()
        val subject = OpenAiTrialGateway(
            registration,
            http,
            { listener },
            { now },
            { "random" },
            foreground::await
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val model = ChatGptTrialViewModel(
            subject,
            scope,
            timeoutScheduler = TrialTimeoutScheduler { _, _ -> AutoCloseable { } },
            diagnostics = {
                if (it.phase == TrialConnectionPhase.CALLBACK_RECEIVED) callback.complete(Unit)
            },
            appForeground = foreground
        )
        try {
            // Time on the trial screen before Connect must not consume its budget.
            elapsed = 600_000L
            model.connect { }
            callback.await()
            yield()
            assertFalse(hasTokenRequest())
            elapsed += 300_000L
            foreground.update(true)
            withTimeout(5_000L) {
                scope.coroutineContext[Job]!!.children.toList().joinAll()
            }
            assertFalse("Expired elapsed budget must forbid the token POST", hasTokenRequest())
            assertEquals(TrialFailure.TIMEOUT.message, model.state.error)
            assertFalse(model.state.busy)
            assertFalse(http.requests.any { it.url.endsWith("models") })
            assertTrue(listener.closed)
        } finally {
            model.close()
            scope.cancel()
        }
    }

    @Test fun publicServiceCheckPrecedesBrowserAndIsReusedForIdentityValidation() = runBlocking {
        val phases = mutableListOf<TrialConnectionPhase>()
        gateway.connect(onPhase = phases::add) {
            assertTrue(http.requests.any { it.url.endsWith("openid-configuration") })
            val discovery = http.requests.single { it.url.endsWith("openid-configuration") }
            assertEquals("GET", discovery.method)
            assertEquals(null, discovery.bearer)
            assertEquals(null, discovery.body)
            assertFalse(http.requests.any { it.url.endsWith("oauth/token") })
        }
        assertEquals(TrialConnectionPhase.CHECKING_SERVICE, phases.first())
        assertEquals(1, http.requests.count { it.url.endsWith("openid-configuration") })
        assertTrue(http.requests.first().url.endsWith("openid-configuration"))
        assertFalse(http.requests.any { it.url.endsWith("responses") })
    }

    @Test fun aPublicServiceFailureDoesNotOpenTheBrowserOrRegisterAClient() = runBlocking {
        var opened = false
        http.after = { request, response ->
            if (request.url.endsWith(
                    "openid-configuration"
                )
            ) {
                TrialHttpResponse(503, "{}")
            } else {
                response
            }
        }
        assertFailure(TrialFailure.SERVICE_UNAVAILABLE) { gateway.connect { opened = true } }
        assertFalse(opened)
        assertEquals(null, registration.issuedClientId)
        assertEquals(1, http.requests.size)
    }

    @Test fun connectionReportsVerifiedMilestonesBeforeAnyImageRequest() = runBlocking {
        val phases = mutableListOf<TrialConnectionPhase>()
        gateway.connect(onPhase = phases::add) { }
        assertEquals(
            listOf(
                TrialConnectionPhase.CHECKING_SERVICE,
                TrialConnectionPhase.PREPARING_CALLBACK,
                TrialConnectionPhase.WAITING_BROWSER,
                TrialConnectionPhase.CALLBACK_RECEIVED,
                TrialConnectionPhase.WAITING_APP_RETURN,
                TrialConnectionPhase.EXCHANGING_CODE,
                TrialConnectionPhase.VERIFYING_ACCOUNT
            ),
            phases
        )
        assertFalse(http.requests.any { it.url.endsWith("responses") })
    }

    private val registration = MemoryRegistration()
    private val http = FakeHttp()
    private val listener = FakeListener()
    private var now = 100L
    private val gateway =
        OpenAiTrialGateway(registration, http, { listener }, { now }, { "random" })

    @Test fun connectSavesIssuedClientBeforeExchangeAndNeverSendsAnImage() = runBlocking {
        http.before =
            { request ->
                if (request.url.endsWith(
                        "oauth/token"
                    )
                ) {
                    assertEquals("client", registration.issuedClientId)
                }
            }
        val urls = mutableListOf<String>()
        assertEquals(
            ConnectedAccount("Test account", true),
            gateway.connect(openBrowser = urls::add)
        )
        assertEquals(1, urls.size)
        assertTrue(urls.single().startsWith("https://auth.openai.com/api/accounts/authorize?"))
        assertTrue(listener.closed)
        assertFalse(http.requests.any { it.url.endsWith("responses") })
        val tokenRequest = http.requests.single { it.url.endsWith("oauth/token") }
        assertTrue(tokenRequest.body!!.contains("client_id=client"))
        assertFalse(tokenRequest.body.contains("client_id=dynamic_agent_client"))
    }

    @Test fun accountModelAndImageFlowUsesOnlyPublicEndpointAndExplicitKnownModel() = runBlocking {
        gateway.connect { }
        assertEquals(listOf(TrialModel("vision", "Vision")), gateway.models())
        assertEquals("Une tasse.", gateway.analyze("vision", JPEG, "Décris."))
        val request = http.requests.last()
        assertEquals("https://api.openai.com/v1/responses", request.url)
        assertEquals("access", request.bearer)
        assertEquals(
            "Décris.",
            JSONObject(request.body!!).getJSONArray("input").getJSONObject(0)
                .getJSONArray("content").getJSONObject(0).getString("text")
        )
        val count = http.requests.size
        assertFailure(TrialFailure.MODEL_UNAVAILABLE) {
            gateway.analyze("guessed", JPEG, "Décris.")
        }
        assertEquals(count, http.requests.size)
    }

    @Test fun deniedPlanAndInvalidSignatureNeverEnableInference() = runBlocking {
        http.scope = "openid"
        assertFalse(gateway.connect { }.planUsageEnabled)
        assertFailure(TrialFailure.PLAN_PERMISSION) { gateway.models() }
        assertFalse(http.requests.any { it.url.endsWith("models") })
        gateway.close()
        assertFailure(TrialFailure.AUTHENTICATION) { gateway.models() }
    }

    @Test fun refreshRotatesBeforeInferenceAndRequiresSameVerifiedAccount() = runBlocking {
        gateway.connect { }
        gateway.models()
        now = 4000
        gateway.analyze("vision", JPEG, "Décris.")
        val refresh = http.requests.single { it.body?.contains("grant_type=refresh_token") == true }
        assertTrue(refresh.body!!.contains("refresh_token=refresh"))
        assertFalse(refresh.body.contains("scope="))
        assertEquals("renewed", http.requests.last().bearer)
    }

    @Test fun quotaNeverRetriesAndFailedRevocationClearsSession() = runBlocking {
        gateway.connect { }
        gateway.models()
        http.inference =
            "data: {\"type\":\"response.failed\",\"response\":{\"error\":{" +
            "\"code\":\"subscription_sharing_usage_limit_exceeded\",\"message\":\"SECRET\"}}}\n\n"
        assertFailure(TrialFailure.USAGE_LIMIT) { gateway.analyze("vision", JPEG, "Décris.") }
        assertEquals(1, http.requests.count { it.url.endsWith("responses") })
        http.revokeStatus = 503
        assertFailure(TrialFailure.REVOKE_FAILED) { gateway.disconnect() }
        assertFailure(TrialFailure.AUTHENTICATION) { gateway.models() }
    }

    @Test fun cancellationRejectsLateGrantAndKeepsTheIssuedRegistrationForRetry() = runBlocking {
        http.before = { if (it.url.endsWith("jwks")) gateway.cancel() }
        assertFailure(TrialFailure.CANCELLED) { gateway.connect { } }
        assertEquals("client", registration.issuedClientId)
        assertTrue(listener.closed)
        assertFailure(TrialFailure.AUTHENTICATION) { gateway.models() }
        assertTrue(http.cancelled)
    }

    @Test fun restoredRegistrationAndDisconnectNeverReconnectAutomatically() = runBlocking {
        registration.issuedClientId = "client"
        var opened = ""
        gateway.connect { opened = it }
        assertTrue(opened.contains("client_id=client"))
        assertFalse(opened.contains("agent_name_hint"))
        gateway.disconnect()
        assertTrue(http.requests.last().url.endsWith("revoke"))
        assertTrue(http.requests.last().body!!.contains("token=refresh"))
        val count = http.requests.size
        gateway.disconnect()
        assertEquals(count, http.requests.size)
        assertFailure(TrialFailure.AUTHENTICATION) { gateway.models() }
        gateway.close()
        assertFailure(TrialFailure.AUTHENTICATION) { gateway.connect { error("No browser") } }
    }

    @Test fun missingIdentityAndUntrustedDiscoveryNeverEnableTheAccount() = runBlocking {
        http.after = { request, response ->
            if (request.url.endsWith("oauth/token")) {
                val json = JSONObject(response.body).apply { remove("id_token") }
                TrialHttpResponse(200, json.toString())
            } else {
                response
            }
        }
        assertFailure(TrialFailure.AUTHENTICATION) { gateway.connect { } }
        http.after = { request, response ->
            if (request.url.endsWith("openid-configuration")) {
                TrialHttpResponse(
                    200,
                    JSONObject(response.body).put("jwks_uri", "https://evil.test/jwks").toString()
                )
            } else {
                response
            }
        }
        assertFailure(TrialFailure.INVALID_RESPONSE) { gateway.connect { } }
        assertFalse(http.requests.any { it.url.contains("evil.test") })
        http.after = { request, response ->
            if (request.url.endsWith(
                    "openid-configuration"
                )
            ) {
                TrialHttpResponse(200, "{}")
            } else {
                response
            }
        }
        assertFailure(TrialFailure.INVALID_RESPONSE) { gateway.connect { } }
    }

    @Test fun refreshCanRetainIdentityButCannotReplaceItOrRestoreACancelledSession() = runBlocking {
        gateway.connect { }
        gateway.models()
        now = 4000
        http.after = { request, response ->
            if (request.body?.contains("grant_type=refresh_token") == true) {
                TrialHttpResponse(
                    200,
                    JSONObject(response.body).apply {
                        remove("id_token")
                        remove("scope")
                    }.toString()
                )
            } else {
                response
            }
        }
        assertEquals("Une tasse.", gateway.analyze("vision", JPEG, "Décris."))
        now = 8000
        http.before = { if (it.url.endsWith("oauth/token")) gateway.cancel() }
        assertFailure(TrialFailure.CANCELLED) { gateway.analyze("vision", JPEG, "Décris.") }
        assertFailure(TrialFailure.AUTHENTICATION) { gateway.models() }
    }

    @Test fun refreshForAnotherSubjectAndMissingPlanPermissionStopTheRequest() = runBlocking {
        gateway.connect { }
        gateway.models()
        now = 4000
        http.after = { request, response ->
            if (request.body?.contains("grant_type=refresh_token") == true) {
                val claims = TrialAuthorizationTest.claims().put("exp", 10000).put("sub", "other")
                TrialHttpResponse(
                    200,
                    JSONObject(response.body)
                        .put("id_token", TrialAuthorizationTest.token(claims)).toString()
                )
            } else {
                response
            }
        }
        assertFailure(TrialFailure.AUTHENTICATION) { gateway.analyze("vision", JPEG, "Décris.") }
        assertFailure(TrialFailure.AUTHENTICATION) { gateway.models() }
        now = 100
        http.after = { _, response -> response }
        gateway.connect { }
        gateway.models()
        now = 4000
        http.scope = "openid"
        assertFailure(TrialFailure.PLAN_PERMISSION) { gateway.analyze("vision", JPEG, "Décris.") }
        assertFalse(http.requests.any { it.url.endsWith("responses") })
    }

    @Test fun statusAndStructuredErrorsAreSanitizedAndNeverRetried() = runBlocking {
        val api = TrialAccountApi(http) { now }
        val cases = listOf(
            Triple(401, "SECRET", TrialFailure.AUTHENTICATION),
            Triple(403, "{}", TrialFailure.AUTHENTICATION),
            Triple(429, "{}", TrialFailure.USAGE_LIMIT),
            Triple(503, "{}", TrialFailure.SERVICE_UNAVAILABLE),
            Triple(
                400,
                """{"error":{"code":"unsupported_image"}}""",
                TrialFailure.MODEL_UNAVAILABLE
            ),
            Triple(400, """{"error":"invalid_grant"}""", TrialFailure.AUTHENTICATION),
            Triple(400, "SECRET", TrialFailure.INVALID_RESPONSE)
        )
        cases.forEach { (status, body, expected) ->
            http.after = { _, _ -> TrialHttpResponse(status, body) }
            val before = http.requests.size
            assertFailure(expected) { api.request(TrialHttpRequest("$TRIAL_RESOURCE/models")) }
            assertEquals(before + 1, http.requests.size)
        }
        http.after = { _, _ -> TrialHttpResponse(401, "{}") }
        assertFailure(TrialFailure.AUTHENTICATION) { gateway.connect { } }
    }

    private suspend fun assertFailure(kind: TrialFailure, action: suspend () -> Unit) {
        try {
            action()
            throw AssertionError("Expected $kind")
        } catch (
            error: ChatGptTrialException
        ) {
            assertEquals(kind, error.kind)
            assertFalse(error.message!!.contains("SECRET"))
        }
    }

    private class MemoryRegistration : TrialRegistrationStore {
        override val hostId = "urn:uuid:host"
        override var issuedClientId: String? = null
    }

    private class FakeListener : TrialCallbackListener {
        override val redirectUri = "http://127.0.0.1:54321/auth/callback"
        var closed = false
        override suspend fun await(state: String, issuedClientId: String?) =
            TrialAuthCode("code", "client")
        override fun close() {
            closed = true
        }
    }

    private class FakeHttp : TrialHttp {
        var modelsTransport: TrialHttp? = null
        val requests = mutableListOf<TrialHttpRequest>()
        var before: (TrialHttpRequest) -> Unit = { }
        var after: (
            TrialHttpRequest,
            TrialHttpResponse
        ) -> TrialHttpResponse = { _, response -> response }
        var scope = "openid chatgpt.tokens.use.direct"
        var cancelled = false
        var revokeStatus = 200
        var inference =
            "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Une tasse.\"}\n\n" +
                "data: {\"type\":\"response.completed\"}\n\n"
        override suspend fun execute(request: TrialHttpRequest): TrialHttpResponse {
            requests.add(request)
            before(request)
            if (request.url.endsWith("models")) modelsTransport?.let { return it.execute(request) }
            val body = when {
                request.url.endsWith("oauth/token") -> {
                    val refresh = request.body!!.contains("grant_type=refresh_token")
                    JSONObject().put("access_token", if (refresh) "renewed" else "access")
                        .put("refresh_token", if (refresh) "rotated" else "refresh")
                        .put(
                            "id_token",
                            TrialAuthorizationTest.token(
                                TrialAuthorizationTest.claims().put(
                                    "nonce",
                                    "random"
                                ).put("exp", 10000)
                            )
                        )
                        .put(
                            "expires_in",
                            3600
                        ).put("scope", scope).put("token_type", "Bearer").toString()
                }
                request.url.endsWith(
                    "openid-configuration"
                ) ->
                    """{"issuer":"https://auth.openai.com",
                    "jwks_uri":"https://auth.openai.com/jwks",
                    "revocation_endpoint":"https://auth.openai.com/revoke"}"""
                request.url.endsWith("jwks") -> TrialAuthorizationTest.jwks()
                request.url.endsWith(
                    "models"
                ) ->
                    """{"models":[{"slug":"vision",
                    "display_name":"Vision","visibility":"list"}]}"""
                request.url.endsWith("responses") -> inference
                request.url.endsWith("revoke") -> ""
                else -> error("Unexpected endpoint")
            }
            return after(
                request,
                TrialHttpResponse(
                    if (request.url.endsWith("revoke")) revokeStatus else 200,
                    body
                )
            )
        }
        override fun cancel() {
            cancelled = true
        }
        override fun close() {
            cancel()
        }
    }

    private companion object {
        val JPEG = byteArrayOf(-1, -40, 1, -1, -39)
    }
}
