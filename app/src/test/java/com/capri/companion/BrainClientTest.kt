package com.capri.companion

import com.capri.companion.brain.BrainClient
import com.capri.companion.data.CapriSettings
import com.capri.companion.data.Message
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class BrainClientTest {
    private lateinit var server: MockWebServer
    @Before fun before() { server = MockWebServer(); server.start(java.net.InetAddress.getByName("127.0.0.1"), 0) }
    @After fun after() { server.shutdown() }
    private fun settings() = CapriSettings(endpoint = server.url("/v1/").newBuilder().host("127.0.0.1").build().toString(), model = "test-model", apiKey = "test-key")

    @Test fun sendsConversationAndSpanishPersonaAndReadsReply() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"Acá estoy, choom."}}]}"""))
        assertEquals("Acá estoy.", BrainClient().reply(settings(), listOf(Message("user", "Hola"))))
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("test-model", body.getString("model"))
        assertFalse(body.getBoolean("stream"))
        val messages = body.getJSONArray("messages")
        assertTrue(messages.getJSONObject(0).getString("content").contains("español"))
        assertTrue(messages.getJSONObject(0).getString("content").contains("No puedes ver la pantalla"))
        assertEquals("Hola", messages.getJSONObject(1).getString("content"))
    }
    @Test fun errorsAreUsefulWithoutEchoingProviderSecrets() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Provider echoed test-key"))
        val failure = runCatching { BrainClient().reply(settings(), emptyList()) }.exceptionOrNull()!!
        assertTrue(failure.message!!.contains("clave"))
        assertFalse(failure.message!!.contains("test-key"))
    }
    @Test fun doesNotFollowRedirectWithCredentials() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/secret-destination"))
        val failure = runCatching { BrainClient().reply(settings(), emptyList()) }.exceptionOrNull()!!
        assertTrue(failure.message!!.contains("302"))
        assertEquals(1, server.requestCount)
    }
    @Test fun rejectsEmptyAndMalformedReplies() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":" "}}]}"""))
        assertTrue(runCatching { BrainClient().reply(settings(), emptyList()) }.exceptionOrNull()!!.message!!.contains("vacía"))
        server.enqueue(MockResponse().setBody("not json"))
        assertTrue(runCatching { BrainClient().reply(settings(), emptyList()) }.exceptionOrNull()!!.message!!.contains("formato"))
    }
    @Test fun cancelsOutstandingHttpRequest() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = async { BrainClient().reply(settings(), emptyList()) }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }
}
