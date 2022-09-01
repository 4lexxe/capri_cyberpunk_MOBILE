package com.capri.companion

import com.capri.companion.auth.*
import com.capri.companion.brain.BrainClient
import com.capri.companion.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicInteger

class ChatGptConnectionTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @After fun stop() { scope.cancel() }
    private class Vault(var value: String? = null) : ChatGptVault {
        override fun readAuth() = value
        override fun writeAuth(value: String) { this.value = value }
    }
    private class Transport : ChatGptTransport {
        var tokens = JSONObject()
        var nonce = ""
        var client = "issued-client"
        var subject = "user-1"
        var grantScopes = ChatGptProtocol.SCOPES
        var refreshFailure = false
        var revokeFails = false
        val refreshes = AtomicInteger()
        val browserRequests = mutableListOf<Map<String, String>>()
        val tokenRequests = mutableListOf<Map<String, String>>()
        val apiBearers = mutableListOf<String>()
        var completeBody: JSONObject? = null
        var blocker: CompletableDeferred<Unit>? = null
        override suspend fun json(url: String, bearer: String?, fields: Map<String, String>?): JSONObject = when {
            url.endsWith("/.well-known/openid-configuration") -> JSONObject().put("issuer", ChatGptProtocol.AUTH)
                .put("jwks_uri", "${ChatGptProtocol.AUTH}/jwks").put("revocation_endpoint", "${ChatGptProtocol.AUTH}/revoke")
            url.endsWith("/jwks") -> JSONObject().put("keys", JwtFixture.keys)
            url.endsWith("/oauth/token") -> {
                synchronized(tokenRequests) { tokenRequests.add(fields!!) }
                if (fields!!["grant_type"] == "refresh_token") {
                    refreshes.incrementAndGet(); delay(30)
                    if (refreshFailure) throw PlanHttpException(400, "invalid_grant")
                    JSONObject().put("access_token", "new-access").put("refresh_token", "new-refresh").put("expires_in", 3600)
                } else JSONObject().put("access_token", "access-secret").put("refresh_token", "refresh-secret").put("scope", grantScopes)
                    .put("expires_in", 3600).put("token_type", "Bearer").put("id_token", JwtFixture.token(JwtFixture.claims(client, nonce, subject)))
            }
            url.endsWith("/models") -> {
                synchronized(apiBearers) { apiBearers.add(bearer!!) }
                JSONObject().put("models", JSONArray().put(JSONObject().put("slug", "account-model").put("display_name", "Modelo de cuenta").put("visibility", "list"))
                    .put(JSONObject().put("slug", "hidden-model").put("visibility", "hide")))
            }
            url.endsWith("/revoke") -> { if (revokeFails) throw java.io.IOException("ChatGPT sin conexión"); JSONObject() }
            else -> throw AssertionError("Unexpected endpoint: $url")
        }
        override suspend fun complete(body: JSONObject, bearer: String): String {
            completeBody = body; synchronized(apiBearers) { apiBearers.add(bearer) }
            blocker?.await()
            return "Acá estoy, choom."
        }
    }
    private fun saved(expired: Boolean = false): Vault = Vault(JSONObject().put("host", "urn:uuid:11111111-1111-4111-8111-111111111111")
        .put("active", "issued-client").put("accounts", JSONArray().put(JSONObject().put("client_id", "issued-client").put("sub", "user-1")
            .put("email", "test@example.invalid").put("scope", ChatGptProtocol.SCOPES).put("access_token", "old-access")
            .put("refresh_token", "old-refresh").put("expires_at", if (expired) 1 else System.currentTimeMillis() + 3600000))).toString())

    private fun browser(transport: Transport, returning: Boolean = false): (String) -> Unit = { url ->
        val query = URI(url).rawQuery.split('&').associate {
            val pair = it.split('=', limit = 2); URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
        }
        transport.browserRequests.add(query)
        transport.nonce = query.getValue("nonce")
        scope.launch(Dispatchers.IO) {
            val suffix = if (returning) "" else "&client_id=${transport.client}"
            val target = query.getValue("redirect_uri") + "?state=${query.getValue("state")}&code=test-code$suffix"
            val c = URL(target).openConnection() as HttpURLConnection
            try { assertEquals(200, c.responseCode); c.inputStream.close() } finally { c.disconnect() }
        }
    }
    private suspend fun settled(connection: ChatGptConnection) = withTimeout(6000) {
        connection.state.first { !it.authorizing && !it.working }
    }

    @Test fun realLoopbackRegistersWithPkceAndLoadsAccountModels() = runBlocking {
        val vault = Vault(); val transport = Transport(); val connection = ChatGptConnection(vault, scope, transport)
        connection.signIn(null, browser(transport)); val state = settled(connection)
        assertTrue(state.ready)
        assertEquals(listOf("account-model"), state.models.map { it.id })
        val auth = transport.browserRequests.single(); val exchange = transport.tokenRequests.single()
        assertEquals("dynamic_agent_client", auth["client_id"])
        assertEquals("Capri Android", auth["agent_name_hint"])
        assertEquals("issued-client", exchange["client_id"])
        assertEquals(auth["redirect_uri"], exchange["redirect_uri"])
        assertEquals(auth["code_challenge"], ChatGptProtocol.challenge(exchange.getValue("code_verifier")))
        assertEquals("access-secret", transport.apiBearers.single())
        assertFalse(state.toString().contains("access-secret"))
        assertFalse(state.toString().contains("refresh-secret"))
        assertTrue(JSONObject(vault.value!!).getString("host").startsWith("urn:uuid:"))
    }
    @Test fun returningSignInReusesRegistrationAndRejectsAnotherIdentity() = runBlocking {
        val vault = saved(); val transport = Transport(); val connection = ChatGptConnection(vault, scope, transport)
        val host = JSONObject(vault.value!!).getString("host")
        transport.subject = "other-user"
        connection.signIn("issued-client", browser(transport, true)); settled(connection)
        assertEquals("issued-client", transport.browserRequests.single()["client_id"])
        assertFalse(transport.browserRequests.single().containsKey("agent_name_hint"))
        assertEquals(host, transport.browserRequests.single()["ext_agent_host_id"])
        val account = JSONObject(vault.value!!).getJSONArray("accounts").getJSONObject(0)
        assertEquals("user-1", account.getString("sub")); assertEquals("old-access", account.getString("access_token"))
        assertTrue(connection.state.value.status.contains("identidad no coincide"))
    }
    @Test fun cancelingThenRestartingSignInDoesNotCloseTheNewListener() = runBlocking {
        val transport = Transport(); val connection = ChatGptConnection(Vault(), scope, transport)
        val opened = CompletableDeferred<String>()
        connection.signIn(null) { opened.complete(it) }
        withTimeout(2000) { opened.await() }
        connection.cancelSignIn()
        assertFalse(connection.state.value.authorizing)
        connection.signIn(null, browser(transport))
        assertTrue(settled(connection).ready)
        assertTrue(transport.tokenRequests.isNotEmpty())
    }
    @Test fun identityWithoutPlanConsentIsRetainedButCannotInfer() = runBlocking {
        val vault = Vault(); val transport = Transport().apply { grantScopes = "openid profile email" }
        val connection = ChatGptConnection(vault, scope, transport)
        connection.signIn(null, browser(transport)); settled(connection)
        assertTrue(connection.state.value.active!!.connected)
        assertFalse(connection.state.value.ready)
        assertTrue(runCatching { connection.complete(JSONObject().put("model", "account-model")) }.isFailure)
        assertNull(transport.completeBody)
    }
    @Test fun refreshIsSerializedAndRotatingCredentialsAreStoredTogether() = runBlocking {
        val vault = saved(true); val transport = Transport(); val connection = ChatGptConnection(vault, scope, transport)
        coroutineScope { awaitAll(async { connection.refreshModels() }, async { connection.refreshModels() }) }
        assertEquals(1, transport.refreshes.get())
        val account = JSONObject(vault.value!!).getJSONArray("accounts").getJSONObject(0)
        assertEquals("new-access", account.getString("access_token")); assertEquals("new-refresh", account.getString("refresh_token"))
        assertTrue(transport.apiBearers.all { it == "new-access" })
    }
    @Test fun revokedRefreshStopsRequestsAndRetainsRegistration() = runBlocking {
        val vault = saved(true); val transport = Transport().apply { refreshFailure = true }
        val connection = ChatGptConnection(vault, scope, transport)
        assertTrue(runCatching { connection.refreshModels() }.isFailure)
        assertFalse(connection.state.value.ready)
        assertEquals("issued-client", connection.state.value.activeId)
        assertFalse(JSONObject(vault.value!!).getJSONArray("accounts").getJSONObject(0).has("refresh_token"))
        assertTrue(transport.apiBearers.isEmpty())
    }
    @Test fun brainUsesPlanForTextAndImagesWithoutKeyOrUnsupportedFields() = runBlocking {
        val transport = Transport(); val connection = ChatGptConnection(saved(), scope, transport)
        val brain = BrainClient(chatgpt = connection)
        assertEquals("Acá estoy, choom.", brain.complete(AiProvider.CHATGPT, "https://ignored.invalid/v1", "account-model", "", "Español",
            listOf(Message("assistant", "Antes"), Message("user", "¿Qué ves?")), byteArrayOf(1, 2, 3)))
        val body = transport.completeBody!!
        assertTrue(body.getBoolean("stream")); assertFalse(body.getBoolean("store"))
        assertFalse(body.has("max_output_tokens")); assertFalse(body.has("temperature"))
        assertEquals("Antes", body.getJSONArray("input").getJSONObject(0).getString("content"))
        assertEquals("input_image", body.getJSONArray("input").getJSONObject(1).getJSONArray("content").getJSONObject(1).getString("type"))
        assertTrue(runCatching { brain.complete(AiProvider.CHATGPT, ChatGptProtocol.API, "hidden-model", "", "s", emptyList()) }.isFailure)
    }
    @Test fun signOutDropsPendingInferenceAndReportsUnconfirmedRevocation() = runBlocking {
        val transport = Transport().apply { revokeFails = true; blocker = CompletableDeferred() }
        val connection = ChatGptConnection(saved(), scope, transport)
        connection.refreshModels()
        val pending = async { connection.complete(JSONObject().put("model", "account-model")) }
        withTimeout(2000) { while (transport.completeBody == null) delay(10) }
        connection.signOut()
        withTimeout(2000) { connection.state.first { it.status.contains("No se confirmó") } }
        transport.blocker!!.complete(Unit)
        assertTrue(runCatching { pending.await() }.exceptionOrNull() is CancellationException)
        assertFalse(connection.state.value.ready)
        assertEquals("issued-client", connection.state.value.activeId)
    }
}
