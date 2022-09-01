package com.capri.companion

import com.capri.companion.data.EndpointPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EndpointPolicyTest {
    @Test fun normalizesEndpointWithoutDuplicatingChatPath() {
        assertEquals("https://example.com/v1/chat/completions", EndpointPolicy.chatUrl(" https://example.com/v1/ "))
        assertEquals("https://example.com/v1/chat/completions", EndpointPolicy.chatUrl("https://example.com/v1/chat/completions/"))
    }
    @Test fun permitsLocalServersAndRejectsPublicCleartext() {
        listOf("127.0.0.1", "localhost", "10.0.2.2", "192.168.1.10", "172.16.0.1", "[::1]").forEach {
            assertEquals("http://$it:8080/v1/chat/completions", EndpointPolicy.chatUrl("http://$it:8080/v1"))
        }
        listOf("http://example.com/v1", "http://172.32.0.1", "http://192.168.999.1", "file:///tmp/a",
            "https://secret@example.com/v1", "https://example.com/v1?api_key=secret", "https://example.com/#a").forEach { url ->
            assertThrows("Should reject $url", IllegalArgumentException::class.java) { EndpointPolicy.chatUrl(url) }
        }
    }
}
