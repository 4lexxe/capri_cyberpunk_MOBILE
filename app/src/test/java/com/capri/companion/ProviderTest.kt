package com.capri.companion

import com.capri.companion.brain.BrainClient
import com.capri.companion.data.AiProvider
import com.capri.companion.data.Message
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProviderTest {
    private lateinit var server: MockWebServer
    @Before fun start() { server = MockWebServer(); server.start(java.net.InetAddress.getByName("127.0.0.1"), 0) }
    @After fun stop() { server.shutdown() }
    private fun endpoint() = server.url("/v1").newBuilder().host("127.0.0.1").build().toString()

    @Test fun openAiUsesResponsesAndDoesNotStoreConversationRemotely() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"output":[{"type":"reasoning"},{"type":"message","content":[{"type":"output_text","text":"Hola"}]}]}"""))
        assertEquals("Hola", BrainClient().complete(AiProvider.OPENAI, endpoint(), "chosen-model", "key", "Español",
            listOf(Message("user", "¿Qué ves?")), byteArrayOf(1, 2, 3)))
        val request = server.takeRequest()
        assertEquals("/v1/responses", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertFalse(body.getBoolean("store"))
        assertEquals("Español", body.getString("instructions"))
        val image = body.getJSONArray("input").getJSONObject(0).getJSONArray("content").getJSONObject(1)
        assertEquals("input_image", image.getString("type"))
        assertEquals("data:image/jpeg;base64,AQID", image.getString("image_url"))
    }
    @Test fun claudeUsesMessagesHeaderAndImageBlocks() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"content":[{"type":"text","text":"Veo un gato"}]}"""))
        assertEquals("Veo un gato", BrainClient().complete(AiProvider.CLAUDE, endpoint(), "chosen-model", "key", "Español",
            listOf(Message("user", "¿Qué ves?")), byteArrayOf(1, 2, 3)))
        val request = server.takeRequest()
        assertEquals("/v1/messages", request.path)
        assertEquals("key", request.getHeader("x-api-key"))
        assertEquals("2023-06-01", request.getHeader("anthropic-version"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("Español", body.getString("system"))
        val image = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(0)
        assertEquals("image/jpeg", image.getJSONObject("source").getString("media_type"))
    }
    @Test fun geminiCompatibilityUsesBearerAndImageUrl() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"Hola"}}]}"""))
        assertEquals("Hola", BrainClient().complete(AiProvider.GEMINI, endpoint(), "chosen-model", "key", "Español",
            listOf(Message("user", "¿Qué ves?")), byteArrayOf(1, 2, 3)))
        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer key", request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("system", body.getJSONArray("messages").getJSONObject(0).getString("role"))
        assertEquals("image_url", body.getJSONArray("messages").getJSONObject(1).getJSONArray("content").getJSONObject(1).getString("type"))
    }
    @Test fun invalidKeysNeverReachNetwork() = runBlocking {
        val result = runCatching { BrainClient().complete(AiProvider.OPENAI, endpoint(), "model", "secret\nother", "s", emptyList()) }
        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull()!!.message!!.contains("secret"))
        assertEquals(0, server.requestCount)
    }
    @Test fun preservesAssistantHistoryInResponsesAndTrimsLeadingAssistantForClaude() = runBlocking {
        val history = listOf(Message("assistant", "Antes"), Message("user", "Ahora"))
        server.enqueue(MockResponse().setBody("""{"output":[{"type":"message","content":[{"type":"output_text","text":"Hola"}]}]}"""))
        BrainClient().complete(AiProvider.OPENAI, endpoint(), "model", "key", "system", history)
        val input = JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("input")
        assertEquals("Antes", input.getJSONObject(0).getString("content"))
        server.enqueue(MockResponse().setBody("""{"content":[{"type":"text","text":"Hola"}]}"""))
        BrainClient().complete(AiProvider.CLAUDE, endpoint(), "model", "key", "system", history)
        val messages = JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages")
        assertEquals(1, messages.length())
        assertEquals("user", messages.getJSONObject(0).getString("role"))
    }
}
