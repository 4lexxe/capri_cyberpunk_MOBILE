package com.capri.companion.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.UUID

interface ChatGptVault {
    fun readAuth(): String?
    fun writeAuth(value: String)
}

data class ChatGptAccount(val id: String, val label: String, val connected: Boolean, val planAllowed: Boolean)
data class ChatGptModel(val id: String, val label: String)
data class ChatGptState(val accounts: List<ChatGptAccount> = emptyList(), val activeId: String = "",
    val authorizing: Boolean = false, val working: Boolean = false, val models: List<ChatGptModel> = emptyList(),
    val status: String = "Conectá tu cuenta ChatGPT para usar tu plan.") {
    val active get() = accounts.firstOrNull { it.id == activeId }
    val ready get() = active?.let { it.connected && it.planAllowed } == true
}

/** Owns only Capri's credentials. It never imports Display CW or browser sessions. */
class ChatGptConnection(private val vault: ChatGptVault, private val scope: CoroutineScope,
    private val transport: ChatGptTransport = OpenAiPlanTransport()) {
    val state = MutableStateFlow(ChatGptState())
    private val lock = Mutex()
    private var database = JSONObject()
    private var usable = false
    @Volatile private var revision = 0L
    @Volatile private var listener: ServerSocket? = null
    private var operation: Job? = null
    private var operationSerial = 0L

    init {
        try {
            database = vault.readAuth()?.let { JSONObject(it) } ?: JSONObject()
                .put("host", "urn:uuid:${UUID.randomUUID()}").put("accounts", JSONArray()).also { vault.writeAuth(it.toString()) }
            require(database.getString("host").startsWith("urn:uuid:"))
            database.getJSONArray("accounts")
            usable = true
            publish()
        } catch (_: Exception) { state.value = state.value.copy(status = "No se pudo abrir la sesión cifrada de ChatGPT. Reiniciá Capri para reintentar.") }
    }

    private fun rows(db: JSONObject = database) = db.getJSONArray("accounts")
    private fun find(id: String, db: JSONObject = database): JSONObject? = (0 until rows(db).length())
        .map { rows(db).getJSONObject(it) }.firstOrNull { it.optString("client_id") == id }
    private fun selected(): JSONObject = find(database.optString("active")) ?: throw IOException("Primero conectá una cuenta ChatGPT en Mainframe.")
    private fun allowed(account: JSONObject) = ChatGptProtocol.DIRECT_SCOPE in account.optString("scope").split(' ')
    private fun commit(db: JSONObject) { vault.writeAuth(db.toString()); database = db }
    private fun copy() = JSONObject(database.toString())
    private fun publish(status: String? = null) {
        val accounts = (0 until rows().length()).map { i -> rows().getJSONObject(i).let {
            ChatGptAccount(it.getString("client_id"), "Cuenta ${i + 1} · ${it.optString("email", "Registro pendiente").take(120)}",
                it.optString("access_token").isNotBlank(), allowed(it))
        } }
        state.value = state.value.copy(accounts = accounts, activeId = database.optString("active"),
            status = status ?: if (accounts.any { it.id == database.optString("active") && it.connected && it.planAllowed })
                "Cuenta conectada. Elegí un modelo y probá una respuesta." else "Conectá tu cuenta y autorizá el uso del plan para Capri.")
    }

    fun signIn(accountId: String? = state.value.activeId.takeIf { it.isNotBlank() }, browse: (String) -> Unit) {
        if (!usable || operation?.isActive == true) return
        val attempt = ++operationSerial
        state.value = state.value.copy(authorizing = true, status = "Completá la autorización en tu navegador. Tenés 5 minutos.")
        operation = scope.launch {
            try {
                authorize(accountId, browse)
                if (state.value.ready) refreshModels()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { currentCoroutineContext().ensureActive(); state.value = state.value.copy(status = safe(e)) }
            finally {
                if (operationSerial == attempt) { serverCleanup(); state.value = state.value.copy(authorizing = false) }
            }
        }
    }

    private suspend fun authorize(accountId: String?, browse: (String) -> Unit) {
        val previous = lock.withLock { accountId?.let { find(it)?.let { a -> JSONObject(a.toString()) } } }
        val client = previous?.getString("client_id") ?: "dynamic_agent_client"
        val stateToken = ChatGptProtocol.random(); val nonce = ChatGptProtocol.random(); val verifier = ChatGptProtocol.random()
        val server = withContext(Dispatchers.IO) { ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 1000 } }
        listener = server
        val redirect = "http://127.0.0.1:${server.localPort}/auth/callback"
        val params = linkedMapOf("client_id" to client, "ext_agent_host_id" to database.getString("host"), "response_type" to "code",
            "redirect_uri" to redirect, "scope" to ChatGptProtocol.SCOPES, "resource" to ChatGptProtocol.API,
            "state" to stateToken, "nonce" to nonce, "code_challenge_method" to "S256", "code_challenge" to ChatGptProtocol.challenge(verifier))
        if (previous == null) params["agent_name_hint"] = "Capri Android"
        previous?.optString("id_token")?.takeIf { it.isNotBlank() }?.let { params["id_token_hint"] = it }
        browse("${ChatGptProtocol.AUTH}/api/accounts/authorize?${ChatGptProtocol.form(params)}")
        currentCoroutineContext().ensureActive()
        val callback = try { withContext(Dispatchers.IO) { awaitCallback(server, stateToken) } }
            finally { server.close(); if (listener === server) listener = null }
        if (callback.containsKey("error")) throw IOException("No se autorizó el uso del plan ChatGPT para Capri. Podés reintentar y revisar los permisos.")
        val issued = callback["client_id"] ?: if (previous != null) client else ""
        if (issued.isBlank() || issued == "dynamic_agent_client" || previous != null && issued != client)
            throw IOException("ChatGPT no devolvió el registro esperado. Iniciá sesión de nuevo.")
        val code = callback["code"]?.takeIf { it.isNotBlank() } ?: throw IOException("ChatGPT no devolvió un código de autorización.")
        // Retain incomplete registrations for retry without duplicating the agent.
        lock.withLock {
            if (find(issued) == null) commit(copy().apply { rows(this).put(JSONObject().put("client_id", issued)) })
        }
        val tokens = transport.json("${ChatGptProtocol.AUTH}/api/accounts/oauth/token", fields = mapOf(
            "grant_type" to "authorization_code", "client_id" to issued, "code" to code, "code_verifier" to verifier,
            "redirect_uri" to redirect, "resource" to ChatGptProtocol.API))
        val claims = verify(tokens.getString("id_token"), issued, nonce)
        if (previous?.has("sub") == true && !ChatGptProtocol.constant(previous.getString("sub"), claims.getString("sub")))
            throw IOException("La identidad no coincide con esa cuenta. Usá Agregar cuenta para conectar otra.")
        currentCoroutineContext().ensureActive()
        lock.withLock {
            val updated = copy()
            val account = find(issued, updated)!!
            account.put("sub", claims.getString("sub")).put("email", claims.optString("email", "Cuenta ChatGPT"))
            account.put("scope", tokens.optString("scope"))
            applyTokens(account, tokens)
            updated.put("active", issued)
            commit(updated); revision++
            state.value = state.value.copy(models = emptyList())
            publish(if (allowed(account)) null else "Sesión iniciada, pero no autorizaste el uso del plan. Reconectá para habilitarlo.")
        }
    }

    private suspend fun awaitCallback(server: ServerSocket, stateToken: String): Map<String, String> {
        val until = System.nanoTime() + 300_000_000_000L
        while (System.nanoTime() < until) {
            currentCoroutineContext().ensureActive()
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 2000
                    val input = socket.getInputStream()
                    val line = StringBuilder()
                    while (line.length <= 16384) {
                        val char = input.read()
                        if (char == -1 || char == 10) break
                        line.append(char.toChar())
                    }
                    val parts = line.toString().trimEnd('\r').split(' ')
                    val callback = if (line.length <= 16384 && parts.size == 3 && parts[0] == "GET")
                        ChatGptProtocol.callback(parts[1], stateToken) else null
                    val body = if (callback != null) "<!doctype html><meta name='viewport' content='width=device-width'><h2>Capri Android</h2><p>Autorización recibida. Volvé a Capri para comprobar la conexión.</p>" else "Solicitud no válida."
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    val header = "HTTP/1.1 ${if (callback != null) "200 OK" else "400 Bad Request"}\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nConnection: close\r\nContent-Length: ${bytes.size}\r\n\r\n"
                    socket.getOutputStream().apply { write(header.toByteArray(Charsets.US_ASCII)); write(bytes); flush() }
                    if (callback != null) return callback
                }
            } catch (_: SocketTimeoutException) { /* Ignore incomplete local requests and keep the authorization pending. */ }
        }
        throw IOException("El inicio de sesión venció. Tocá Continue with ChatGPT para reintentar.")
    }

    private suspend fun verify(token: String, client: String, nonce: String?): JSONObject {
        val discovery = transport.json("${ChatGptProtocol.AUTH}/.well-known/openid-configuration")
        if (discovery.optString("issuer") != ChatGptProtocol.AUTH) throw IOException("No se pudo verificar el emisor de ChatGPT.")
        val keys = transport.json(ChatGptProtocol.authOrigin(discovery.getString("jwks_uri"))).getJSONArray("keys")
        return ChatGptProtocol.validateId(token, client, nonce, keys, System.currentTimeMillis() / 1000)
    }

    private fun applyTokens(account: JSONObject, tokens: JSONObject) {
        require(tokens.optString("token_type", "Bearer").equals("Bearer", true))
        require(tokens.getString("access_token").isNotBlank() && tokens.getString("access_token").none { it.isWhitespace() })
        val expires = tokens.optLong("expires_in", 3600)
        require(expires in 1..31_536_000)
        account.put("access_token", tokens.getString("access_token")).put("expires_at", System.currentTimeMillis() + expires * 1000)
        for (field in listOf("refresh_token", "id_token", "scope")) if (tokens.has(field)) account.put(field, tokens.getString(field))
    }

    private suspend fun access(): String = lock.withLock {
        val account = selected()
        if (!account.has("access_token")) throw IOException("Volvé a conectar tu cuenta ChatGPT en Mainframe.")
        if (!allowed(account)) throw IOException("Autorizá el uso del plan ChatGPT para Capri antes de conversar.")
        if (account.optLong("expires_at") <= System.currentTimeMillis() + 60_000) {
            val refresh = account.optString("refresh_token").takeIf { it.isNotBlank() }
                ?: throw IOException("La sesión de ChatGPT venció. Volvé a conectar la cuenta.")
            val client = account.getString("client_id")
            val tokens = try { transport.json("${ChatGptProtocol.AUTH}/api/accounts/oauth/token", fields = mapOf(
                "grant_type" to "refresh_token", "refresh_token" to refresh, "client_id" to client, "resource" to ChatGptProtocol.API))
            } catch (e: PlanHttpException) {
                if (e.errorCode == "invalid_grant") { clearTokens(client); publish("La sesión de ChatGPT venció. Volvé a conectar la cuenta.") }
                throw e
            }
            tokens.optString("id_token").takeIf { it.isNotBlank() }?.let {
                if (!ChatGptProtocol.constant(verify(it, client, null).getString("sub"), account.getString("sub")))
                    throw IOException("La renovación de ChatGPT devolvió otra identidad. Reconectá la cuenta.")
            }
            val updated = copy(); val next = find(client, updated)!!
            applyTokens(next, tokens); commit(updated); publish()
            if (!allowed(next)) throw IOException("ChatGPT retiró el permiso de uso del plan. Reconectá la cuenta.")
            next.getString("access_token")
        } else account.getString("access_token")
    }

    suspend fun refreshModels() {
        val version = revision
        val token = access()
        val json = transport.json("${ChatGptProtocol.API}/models", token).getJSONArray("models")
        val models = (0 until json.length()).map { json.getJSONObject(it) }.filter { it.optString("visibility") == "list" }
            .map { ChatGptModel(it.getString("slug"), it.optString("display_name", it.getString("slug"))) }.distinctBy { it.id }
        if (revision != version) throw CancellationException("La cuenta cambió")
        if (models.isEmpty()) throw IOException("ChatGPT no devolvió modelos disponibles para esta cuenta. Revisá tu plan y los permisos.")
        state.value = state.value.copy(models = models, status = "Modelos disponibles. Elegí uno, guardá y probá una respuesta.")
    }

    fun loadModels() = runOperation { refreshModels() }
    fun selectAccount(id: String) {
        if (operation?.isActive == true) return
        runOperation {
            lock.withLock {
                require(find(id)?.has("sub") == true)
                commit(copy().put("active", id)); revision++
                state.value = state.value.copy(models = emptyList()); publish()
            }
            if (state.value.ready) refreshModels()
        }
    }

    private fun clearTokens(client: String) {
        val updated = copy()
        find(client, updated)?.let { a -> listOf("access_token", "refresh_token", "id_token", "scope", "expires_at").forEach { a.remove(it) } }
        commit(updated)
        state.value = state.value.copy(models = emptyList())
    }

    fun signOut() {
        cancelSignIn()
        // Invalidate in-flight inference immediately, before the asynchronous remote revocation.
        revision++
        val attempt = ++operationSerial
        state.value = state.value.copy(working = true)
        operation = scope.launch {
            try {
                lock.withLock {
                    val account = selected(); val client = account.getString("client_id")
                    var revoked = false
                    try {
                        val discovery = transport.json("${ChatGptProtocol.AUTH}/.well-known/openid-configuration")
                        val endpoint = ChatGptProtocol.authOrigin(discovery.getString("revocation_endpoint"))
                        account.optString("refresh_token").takeIf { it.isNotBlank() }?.let { refresh ->
                            for (retry in 0..1) {
                                try {
                                    transport.json(endpoint, fields = mapOf("token" to refresh, "token_type_hint" to "refresh_token", "client_id" to client))
                                    revoked = true; break
                                } catch (e: IOException) {
                                    if (retry == 1 || e is PlanHttpException && e.status < 500) throw e
                                    kotlinx.coroutines.delay(750)
                                }
                            }
                        }
                    } finally {
                        clearTokens(client)
                        publish(if (revoked) "Sesión cerrada y revocada." else "Sesión cerrada en el teléfono. No se confirmó la revocación remota; desconectá Capri en Configuración de ChatGPT.")
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (state.value.active?.connected == true) state.value = state.value.copy(status = "No se pudo guardar el cierre de sesión. Reintentá y desconectá Capri desde ChatGPT.")
            }
            finally { if (operationSerial == attempt) state.value = state.value.copy(working = false) }
        }
    }

    fun cancelSignIn() {
        operationSerial++
        operation?.cancel(); serverCleanup()
        state.value = state.value.copy(authorizing = false, working = false,
            status = if (state.value.authorizing) "Inicio de sesión cancelado. Podés volver a conectar la cuenta." else state.value.status)
    }

    private fun serverCleanup() { runCatching { listener?.close() }; listener = null }

    private fun runOperation(block: suspend () -> Unit) {
        if (!usable || operation?.isActive == true) return
        val attempt = ++operationSerial
        state.value = state.value.copy(working = true)
        operation = scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { currentCoroutineContext().ensureActive(); state.value = state.value.copy(status = safe(e)) }
            finally { if (operationSerial == attempt) state.value = state.value.copy(working = false) }
        }
    }

    suspend fun complete(body: JSONObject): String {
        if (state.value.authorizing || state.value.working) throw IOException("Esperá a que termine la conexión con ChatGPT.")
        val version = revision
        if (state.value.models.isEmpty()) refreshModels()
        require(state.value.models.any { it.id == body.getString("model") }) { "Elegí un modelo disponible para tu cuenta ChatGPT en Mainframe." }
        val token = access()
        if (revision != version) throw CancellationException("La cuenta cambió")
        val result = try { transport.complete(body, token) }
        catch (e: PlanHttpException) {
            if (e.status == 401) lock.withLock {
                if (revision == version) { clearTokens(selected().getString("client_id")); revision++; publish("ChatGPT rechazó la sesión. Volvé a conectar la cuenta.") }
            }
            throw e
        }
        if (revision != version) throw CancellationException("La cuenta cambió")
        return result
    }

    companion object {
        private fun safe(e: Exception): String = if (e is IOException) e.message.orEmpty() else "No se pudo completar la conexión con ChatGPT. Volvé a intentarlo."
    }
}
