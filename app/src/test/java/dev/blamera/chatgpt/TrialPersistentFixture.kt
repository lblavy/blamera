package dev.blamera.chatgpt

import dev.blamera.chatgpt.storage.TrialSessionStore
import org.json.JSONObject

internal class SessionFixture {
    var now = 100L
    val registration = object : TrialRegistrationStore {
        override val hostId = "urn:uuid:synthetic-host"
        override var issuedClientId: String? = null
        override var sessionBlocked = false
    }
    val store = MemorySessionStore()
    var activeStore: TrialSessionStore = store
    var streamingTransport: TrialHttp? = null
    val coordination = TrialSessionCoordination()
    val requests = mutableListOf<TrialHttpRequest>()
    var before: suspend (TrialHttpRequest) -> Unit = { }
    var alter: (
        TrialHttpRequest,
        TrialHttpResponse
    ) -> TrialHttpResponse = { _, response -> response }
    var browserCount = 0
    var refreshCount = 0
    val scope = "openid offline_access resource.invoke $TRIAL_PLAN_SCOPE"

    fun gateway(
        foreground: suspend () -> Unit = { },
        refreshForeground: suspend () -> Unit = foreground,
        persistent: Boolean = true
    ): OpenAiTrialGateway = OpenAiTrialGateway(
        registration,
        object : TrialHttp {
            override suspend fun executeStreaming(
                request: TrialHttpRequest,
                onChunk: suspend (ByteArray) -> Unit
            ): TrialHttpResponse {
                val transport =
                    streamingTransport ?: return super.executeStreaming(request, onChunk)
                requests.add(request)
                before(request)
                return transport.executeStreaming(request, onChunk)
            }
            override suspend fun execute(request: TrialHttpRequest): TrialHttpResponse {
                requests.add(request)
                before(request)
                val result = when {
                    request.url.endsWith("openid-configuration") ->
                        """{"issuer":"$TRIAL_AUTH_ORIGIN",
                        "jwks_uri":"$TRIAL_AUTH_ORIGIN/jwks",
                        "revocation_endpoint":"$TRIAL_AUTH_ORIGIN/revoke"}"""
                    request.url.endsWith("oauth/token") -> {
                        val refreshing = request.body!!.contains("grant_type=refresh_token")
                        if (refreshing) refreshCount++
                        JSONObject().put("access_token", "access-$refreshCount")
                            .put("refresh_token", "refresh-$refreshCount")
                            .put("token_type", "Bearer").put("expires_in", 3600)
                            .put("scope", scope)
                            .put(
                                "id_token",
                                TrialAuthorizationTest.token(
                                    TrialAuthorizationTest.claims().put(
                                        "nonce",
                                        "random"
                                    ).put("exp", 100_000)
                                )
                            ).toString()
                    }
                    request.url.endsWith("jwks") -> TrialAuthorizationTest.jwks()
                    request.url.endsWith("models") ->
                        """{"models":[
                        {"slug":"first","display_name":"First","visibility":"list"},
                        {"slug":"preferred","display_name":"Preferred","visibility":"list"}]}"""
                    request.url.endsWith("responses") ->
                        "data: {\"type\":\"response.output_text.delta\"," +
                            "\"delta\":\"Fixture\"}\n\n" +
                            "data: {\"type\":\"response.completed\"}\n\n"
                    request.url.endsWith("revoke") -> ""
                    else -> error("Unexpected synthetic endpoint")
                }
                return alter(request, TrialHttpResponse(200, result))
            }
            override fun cancel() = Unit
            override fun close() = Unit
        },
        {
            object : TrialCallbackListener {
                override val redirectUri = "http://127.0.0.1:54321/auth/callback"
                override suspend fun await(state: String, issuedClientId: String?) =
                    TrialAuthCode("synthetic-code", "client")
                override fun close() = Unit
            }
        },
        TrialGatewayEnvironment({ now }, { "random" }, foreground, refreshForeground),
        if (persistent) TrialSessionPersistence(activeStore, registration, coordination) else null
    )

    suspend fun connect(gateway: ChatGptTrialGateway) = gateway.connect { browserCount++ }
}

internal class MemorySessionStore : TrialSessionStore {
    var value: String? = null
    var beforeSave: suspend () -> Unit = { }
    var beforeLoad: suspend () -> Unit = { }
    var beforeClear: suspend () -> Unit = { }
    override suspend fun load(): String? {
        beforeLoad()
        return value
    }
    override suspend fun save(value: String) {
        beforeSave()
        this.value = value
    }
    override suspend fun clear() {
        beforeClear()
        value = null
    }
}
