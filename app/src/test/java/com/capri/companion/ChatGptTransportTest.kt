package com.capri.companion

import com.capri.companion.auth.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ChatGptTransportTest {
    private lateinit var server: MockWebServer
    private lateinit var transport: OpenAiPlanTransport
    @Before fun start() {
        server = MockWebServer(); server.start()
        val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .addInterceptor { chain ->
                val request = chain.request()
                // Redirect official requests to this local fixture only inside this test process.
                chain.proceed(request.newBuilder().url(server.url(request.url.encodedPath)).build())
            }.build()
        transport = OpenAiPlanTransport(client)
    }
    @After fun stop() { server.shutdown() }
    private fun body() = JSONObject().put("model", "available").put("instructions", "Español")
        .put("input", JSONArray()).put("store", false).put("stream", true)
    @Test fun publicEndpointGetsOAuthBearerAndCompleteStream() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream; charset=utf-8").setBody(
            "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Hola\"}\n\ndata: {\"type\":\"response.completed\"}\n\n"))
        assertEquals("Hola", transport.complete(body(), "test-access"))
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/v1/responses", request.path)
        assertEquals("Bearer test-access", request.getHeader("Authorization"))
        assertTrue(JSONObject(request.body.readUtf8()).getBoolean("stream"))
    }
    @Test fun tokenExchangeUsesFormAndRedirectsNeverForwardCredentials() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"access_token\":\"fixture\"}"))
        transport.json("https://auth.openai.com/api/accounts/oauth/token", fields = mapOf("code" to "a+b", "code_verifier" to "proof"))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/x-www-form-urlencoded"))
        assertTrue(request.body.readUtf8().contains("code=a%2Bb"))
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "https://evil.invalid/collect"))
        val failure = runCatching { transport.json("https://api.openai.com/v1/models", "access-secret") }.exceptionOrNull()!!
        assertTrue(failure.message!!.contains("302"))
        assertEquals(2, server.requestCount)
    }
    @Test fun structuredLimitErrorAndUnknownDetailNeverEchoSecrets() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{\"error\":{\"code\":\"subscription_sharing_usage_limit_exceeded\",\"message\":\"echo secret-key\"}}"))
        val failure = runCatching { transport.complete(body(), "secret-key") }.exceptionOrNull()!!
        assertTrue(failure.message!!.contains("límite de tu plan")); assertFalse(failure.message!!.contains("secret-key"))
        server.enqueue(MockResponse().setResponseCode(403).setBody("{\"detail\":\"secret-key secret-grant\"}"))
        val other = runCatching { transport.complete(body(), "secret-key") }.exceptionOrNull()!!
        assertTrue(other.message!!.contains("403")); assertFalse(other.message!!.contains("secret"))
    }
    @Test fun cancellationClosesTheStreamingRequest() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = async { transport.complete(body(), "test-only") }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(3, TimeUnit.SECONDS)) }
        job.cancelAndJoin(); assertTrue(job.isCancelled)
    }
    @Test fun unsupportedOriginsAreRejectedBeforeNetwork() = runBlocking {
        assertTrue(runCatching { transport.json("https://evil.invalid/keys", "secret") }.isFailure)
        assertEquals(0, server.requestCount)
    }
    @Test fun readsAValidStreamEvenWhenTheContentTypeIsMissingOrGeneric() = runBlocking {
        val stream = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Acá estoy\"}\n\ndata: {\"type\":\"response.completed\"}\n\n"
        for (mime in listOf("", "application/json", "application/octet-stream", "Text/Event-Stream; charset=utf-8")) {
            server.enqueue(MockResponse().apply { if (mime.isNotEmpty()) setHeader("Content-Type", mime) }.setBody(stream))
            assertEquals("Acá estoy", transport.complete(body(), "test-access"))
        }
    }
    @Test fun readsAnExplicitlyCompletedJsonResponseWithoutAnotherRequest() = runBlocking {
        val json = JSONObject().put("object", "response").put("status", "completed").put("error", JSONObject.NULL)
            .put("output", JSONArray().put(JSONObject().put("type", "message").put("role", "assistant").put("content",
                JSONArray().put(JSONObject().put("type", "output_text").put("text", "Hola desde JSON")))))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(json.toString()))
        assertEquals("Hola desde JSON", transport.complete(body(), "test-access"))
        assertEquals(1, server.requestCount)
        val request = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue(request.getBoolean("stream")); assertFalse(request.getBoolean("store"))
    }
    @Test fun jsonErrorsOnHttp200KeepTheSpecificPlanFailureAndNeverEchoBodies() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setHeader("x-request-id", "req_fixture123")
            .setBody("""{"error":{"code":"subscription_sharing_user_not_eligible","message":"secret-account-token"}}"""))
        val failure = runCatching { transport.complete(body(), "secret-access") }.exceptionOrNull()!!
        assertTrue(failure.message!!.contains("no está habilitado"))
        assertTrue(failure.message!!.contains("req_fixture123"))
        assertFalse(failure.message!!.contains("secret"))
        assertEquals(1, server.requestCount)
    }
    @Test fun htmlAndEmptyBodiesGetSafeDistinctDiagnostics() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html>secret-login-cookie</html>"))
        val html = runCatching { transport.complete(body(), "test") }.exceptionOrNull()!!
        assertTrue(html.message!!.contains("CG-RSP-HTML")); assertFalse(html.message!!.contains("secret"))
        server.enqueue(MockResponse().setBody(""))
        assertTrue(runCatching { transport.complete(body(), "test") }.exceptionOrNull()!!.message!!.contains("CG-RSP-EMPTY"))
    }
    @Test fun incompleteJsonIsNeverAcceptedAsACompletedReply() = runBlocking {
        for (status in listOf("in_progress", "incomplete", "failed", "cancelled", "")) {
            val json = JSONObject().put("object", "response").put("status", status)
                .put("output", JSONArray().put(JSONObject().put("type", "message").put("content",
                    JSONArray().put(JSONObject().put("type", "output_text").put("text", "partial")))))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(json.toString()))
            assertTrue(status, runCatching { transport.complete(body(), "test") }.isFailure)
        }
    }
    @Test fun malformedJsonAndMislabeledHtmlAreNotReadAsModelText() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{secret-unfinished"))
        val json = runCatching { transport.complete(body(), "test") }.exceptionOrNull()!!
        assertTrue(json.message!!.contains("CG-RSP-JSON")); assertFalse(json.message!!.contains("secret"))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("<!doctype html><p>secret</p>"))
        assertTrue(runCatching { transport.complete(body(), "test") }.exceptionOrNull()!!.message!!.contains("CG-RSP-HTML"))
    }
}
