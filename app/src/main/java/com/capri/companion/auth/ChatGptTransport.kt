package com.capri.companion.auth

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface ChatGptTransport {
    suspend fun json(url: String, bearer: String? = null, fields: Map<String, String>? = null): JSONObject
    suspend fun complete(body: JSONObject, bearer: String): String
}

class OpenAiPlanTransport(private val http: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).callTimeout(240, TimeUnit.SECONDS)
    .followRedirects(false).followSslRedirects(false).build()) : ChatGptTransport {

    override suspend fun json(url: String, bearer: String?, fields: Map<String, String>?): JSONObject {
        require(url == "${ChatGptProtocol.API}/models" || ChatGptProtocol.authOrigin(url) == url)
        val request = Request.Builder().url(url).header("Accept", "application/json").apply {
            if (bearer != null) header("Authorization", "Bearer $bearer")
            if (fields != null) post(FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build())
        }.build()
        return execute(request) { response ->
            val bytes = readBounded(response.body?.source(), 2L * 1024 * 1024)
            if (bytes.isEmpty()) JSONObject() else try { JSONObject(bytes.toString(Charsets.UTF_8)) }
            catch (_: Exception) { throw IOException("ChatGPT devolvió un formato inesperado.") }
        }
    }

    override suspend fun complete(body: JSONObject, bearer: String): String {
        require(!body.getBoolean("store") && body.getBoolean("stream"))
        val request = Request.Builder().url("${ChatGptProtocol.API}/responses")
            .header("Authorization", "Bearer $bearer").header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        return execute(request) { response ->
            readCompletion(response)
        }
    }

    private fun readCompletion(response: Response): String {
        val source = response.body?.source() ?: throw formatError(response, "EMPTY", "respondió sin contenido")
        if (!source.request(1)) throw formatError(response, "EMPTY", "respondió sin contenido")
        // Inspect a bounded prefix without consuming the body. Some responses have a missing or
        // generic Content-Type; rejecting solely on that header hides valid data and real errors.
        val peek = source.peek()
        peek.request(1)
        val prefix = peek.buffer.clone().readUtf8(minOf(peek.buffer.size, 1024)).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        val mime = response.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase(java.util.Locale.ROOT)
        if (prefix.startsWith("data:") || prefix.startsWith("event:") || prefix.startsWith(":")) return ChatGptStream.read(source)
        if (prefix.startsWith("{")) {
            val json = try { JSONObject(readBounded(source, 12L * 1024 * 1024).toString(Charsets.UTF_8).trimStart('\uFEFF')) }
                catch (_: org.json.JSONException) { throw formatError(response, "JSON", "devolvió un JSON inválido") }
            val payload = json.optJSONObject("response") ?: json
            if (payload.opt("error") is JSONObject || payload.opt("error") is String || payload.has("detail") ||
                json.optString("type") in setOf("error", "response.failed", "response.incomplete"))
                throw PlanHttpException(response.code, ChatGptErrors.code(payload), reference(response))
            if (payload.optString("object") != "response" || payload.optString("status") != "completed" ||
                payload.optJSONArray("output") == null || !payload.isNull("incomplete_details"))
                throw formatError(response, "JSON-STATE", "devolvió JSON, pero no una respuesta completada")
            return ChatGptStream.completed(payload)
        }
        if (mime == "text/event-stream" && !prefix.startsWith("<")) return ChatGptStream.read(source)
        if (prefix.startsWith("<") || mime == "text/html")
            throw formatError(response, "HTML", "devolvió una página web en lugar de una respuesta de IA. Revisá la conexión o intentá más tarde")
        throw formatError(response, "FORMAT", "devolvió un formato de respuesta no reconocido")
    }

    private fun reference(response: Response): String = response.header("x-request-id")
        ?.takeIf { it.matches(Regex("req_[A-Za-z0-9_-]{3,96}")) }?.let { "; solicitud $it" }.orEmpty()

    private fun formatError(response: Response, code: String, explanation: String): IOException =
        IOException("ChatGPT $explanation (CG-RSP-$code; HTTP ${response.code}${reference(response)}).")

    private suspend fun <T> execute(request: Request, decode: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException("No se pudo conectar con ChatGPT. Revisá la conexión."))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (!it.isSuccessful) {
                            val code = runCatching { ChatGptErrors.code(JSONObject(readBounded(it.body?.source(), 65536).toString(Charsets.UTF_8))) }.getOrDefault("")
                            throw PlanHttpException(it.code, code)
                        }
                        val decoded = decode(it)
                        if (continuation.isActive) continuation.resume(decoded)
                    }
                } catch (e: Exception) {
                    // JSON / network diagnostics must never echo tokens, response bodies, or request URLs.
                    val safe = if (e is PlanHttpException || e is IOException && e.message?.startsWith("ChatGPT") == true) e
                        else IOException("ChatGPT interrumpió la respuesta o devolvió un formato inválido.")
                    if (continuation.isActive) continuation.resumeWithException(safe)
                }
            }
        })
    }

    private fun readBounded(source: BufferedSource?, max: Long): ByteArray {
        if (source == null) return byteArrayOf()
        val buffer = okio.Buffer()
        while (buffer.size <= max) {
            if (source.read(buffer, minOf(8192, max + 1 - buffer.size)) == -1L) break
        }
        if (buffer.size > max) throw IOException("ChatGPT envió una respuesta demasiado grande.")
        return buffer.readByteArray()
    }
}

class PlanHttpException(val status: Int, val errorCode: String, reference: String = "") :
    IOException(ChatGptErrors.failure(status, errorCode).message + reference)

object ChatGptStream {
    fun completed(response: JSONObject, streamedText: String = ""): String {
        if (response.optString("status", "completed") != "completed" || !response.isNull("error") || !response.isNull("incomplete_details"))
            throw IOException("ChatGPT no completó la respuesta.")
        val result = response.optJSONArray("output")?.let { text(it) }.orEmpty().ifBlank { streamedText }.trim()
        if (result.isBlank()) throw IOException("ChatGPT completó la solicitud sin texto. Probá otro modelo (CG-RSP-NO-TEXT).")
        return result.take(8000)
    }
    fun read(source: BufferedSource): String {
        val output = StringBuilder()
        val data = StringBuilder()
        var total = 0L
        fun event(): String? {
            if (data.isEmpty()) return null
            val raw = data.toString(); data.setLength(0)
            if (raw == "[DONE]") return null
            val json = JSONObject(raw)
            when (json.optString("type")) {
                "response.output_text.delta", "response.refusal.delta" -> {
                    output.append(json.optString("delta"))
                    if (output.length > 65536) throw IOException("ChatGPT envió demasiado texto para una respuesta de Capri.")
                }
                "response.failed", "response.incomplete", "error" -> {
                    val response = json.optJSONObject("response") ?: json
                    throw PlanHttpException(200, ChatGptErrors.code(response))
                }
                "response.completed" -> {
                    val response = json.optJSONObject("response")
                    return completed(response ?: JSONObject(), output.toString())
                }
            }
            return null
        }
        while (!source.exhausted()) {
            val line = source.readUtf8LineStrict(1024 * 1024).let { if (total == 0L) it.removePrefix("\uFEFF") else it }
            total += line.toByteArray(Charsets.UTF_8).size + 1
            if (total > 12L * 1024 * 1024) throw IOException("ChatGPT superó el tamaño de respuesta permitido.")
            if (line.isEmpty()) event()?.let { return it }
            else if (line.startsWith("data:")) {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.substring(5).removePrefix(" "))
                if (data.length > 1024 * 1024) throw IOException("ChatGPT envió un evento demasiado grande.")
            }
        }
        // The last SSE event may end at EOF without an extra empty line.
        event()?.let { return it }
        throw IOException("ChatGPT cortó la conexión antes de completar la respuesta. Volvé a intentarlo.")
    }

    private fun text(items: JSONArray): String = buildString {
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            if (item.optString("type") != "message") continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.getJSONObject(j)
                if (part.optString("type") == "output_text") append(part.optString("text"))
                else if (part.optString("type") == "refusal") append(part.optString("refusal"))
            }
        }
    }
}
