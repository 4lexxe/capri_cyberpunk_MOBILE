package com.capri.companion

import com.capri.companion.auth.*
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey

internal object JwtFixture {
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val pub = pair.public as RSAPublicKey
    val keys get() = JSONArray().put(JSONObject().put("kid", "test-key").put("kty", "RSA").put("use", "sig")
        .put("n", ChatGptProtocol.encode(pub.modulus.toByteArray())).put("e", ChatGptProtocol.encode(pub.publicExponent.toByteArray())))
    fun claims(client: String = "issued-client", nonce: String = "nonce", subject: String = "user-1") = JSONObject()
        .put("iss", ChatGptProtocol.AUTH).put("sub", subject).put("email", "test@example.invalid").put("aud", client)
        .put("nonce", nonce).put("iat", System.currentTimeMillis() / 1000 - 5).put("exp", System.currentTimeMillis() / 1000 + 3600)
    fun token(claims: JSONObject, algorithm: String = "RS256"): String {
        val header = ChatGptProtocol.encode(JSONObject().put("alg", algorithm).put("kid", "test-key").toString().toByteArray())
        val body = ChatGptProtocol.encode(claims.toString().toByteArray())
        val signing = "$header.$body"
        val signature = Signature.getInstance("SHA256withRSA").apply { initSign(pair.private); update(signing.toByteArray(Charsets.US_ASCII)) }.sign()
        return "$signing.${ChatGptProtocol.encode(signature)}"
    }
}

class ChatGptProtocolTest {
    private fun verify(claims: JSONObject, algorithm: String = "RS256") = ChatGptProtocol.validateId(
        JwtFixture.token(claims, algorithm), "issued-client", "nonce", JwtFixture.keys, System.currentTimeMillis() / 1000)

    @Test fun pkceMatchesPublishedRfc7636Vector() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", ChatGptProtocol.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        assertEquals(64, ChatGptProtocol.random().length)
    }
    @Test fun callbackRejectsWrongStatePathAbsoluteTargetsAndDuplicates() {
        assertEquals("code", ChatGptProtocol.callback("/auth/callback?state=s&code=code", "s")!!["code"])
        for (target in listOf("/auth/callback?state=wrong&code=code", "/callback?state=s&code=code", "http://evil/auth/callback?state=s",
            "//evil/auth/callback?state=s", "/auth/callback?state=s&state=s", "/auth/callback?state=s&code=a&code=b"))
            assertNull(target, ChatGptProtocol.callback(target, "s"))
        assertEquals("access_denied", ChatGptProtocol.callback("/auth/callback?state=s&error=access_denied", "s")!!["error"])
    }
    @Test fun acceptsSignedIdentityAndRejectsTampering() {
        assertEquals("user-1", verify(JwtFixture.claims()).getString("sub"))
        val token = JwtFixture.token(JwtFixture.claims())
        assertTrue(runCatching { ChatGptProtocol.validateId(token.dropLast(4) + "AAAA", "issued-client", "nonce", JwtFixture.keys, System.currentTimeMillis() / 1000) }.isFailure)
    }
    @Test fun rejectsInvalidIssuerAudienceNonceExpiryAndAlgorithm() {
        for ((key, value) in listOf("iss" to "https://evil.invalid", "aud" to "other-client", "nonce" to "other-nonce", "exp" to 1L,
            "iat" to System.currentTimeMillis() / 1000 + 999, "nbf" to System.currentTimeMillis() / 1000 + 999, "sub" to ""))
            assertTrue(key, runCatching { verify(JwtFixture.claims().put(key, value)) }.isFailure)
        assertTrue(runCatching { verify(JwtFixture.claims(), "HS256") }.isFailure)
        val multiple = JwtFixture.claims().put("aud", JSONArray().put("issued-client").put("other-client"))
        assertTrue(runCatching { verify(multiple) }.isFailure)
        assertEquals("user-1", verify(multiple.put("azp", "issued-client")).getString("sub"))
    }
    @Test fun discoveryCannotRedirectCredentialsToAnotherOrigin() {
        assertEquals("https://auth.openai.com/jwks", ChatGptProtocol.authOrigin("https://auth.openai.com/jwks"))
        for (url in listOf("http://auth.openai.com/keys", "https://auth.openai.com.evil.invalid/keys", "https://auth.openai.com@evil.invalid/keys", "https://auth.openai.com:1234/keys"))
            assertTrue(url, runCatching { ChatGptProtocol.authOrigin(url) }.isFailure)
    }
    private fun stream(vararg events: JSONObject): String = events.joinToString("") { "data: $it\n\n" }
    private fun delta(text: String) = JSONObject().put("type", "response.output_text.delta").put("delta", text)
    private fun completed() = JSONObject().put("type", "response.completed")
    @Test fun completedUnicodeStreamProducesOneFinalReply() {
        val source = Buffer().writeUtf8(": heartbeat\n\nevent: response.output_text.delta\n" + stream(delta("¡Hola, "), delta("choom! 👋"), completed()))
        assertEquals("¡Hola, choom! 👋", ChatGptStream.read(source))
    }
    @Test fun cutStreamAndDoneMarkerNeverCountAsSuccess() {
        for (body in listOf(stream(delta("parcial")), stream(delta("parcial")) + "data: [DONE]\n\n", "data: {bad}\n\n"))
            assertTrue(runCatching { ChatGptStream.read(Buffer().writeUtf8(body)) }.isFailure)
    }
    @Test fun latePlanErrorNeverSpeaksPartialReply() {
        val error = JSONObject().put("type", "response.failed").put("response", JSONObject().put("error", JSONObject()
            .put("code", "subscription_sharing_usage_limit_exceeded").put("message", "echo secret-do-not-show")))
        val failure = runCatching { ChatGptStream.read(Buffer().writeUtf8(stream(delta("parcial"), error))) }.exceptionOrNull()!!
        assertTrue(failure.message!!.contains("límite de tu plan"))
        assertFalse(failure.message!!.contains("secret"))
        val incomplete = JSONObject().put("type", "response.incomplete")
        assertTrue(runCatching { ChatGptStream.read(Buffer().writeUtf8(stream(delta("parcial"), incomplete))) }.isFailure)
    }
    @Test fun completedOutputWorksWithoutDeltaEventsAndEmptyOutputFails() {
        val response = JSONObject().put("status", "completed").put("output", JSONArray().put(JSONObject().put("type", "message")
            .put("content", JSONArray().put(JSONObject().put("type", "output_text").put("text", "Hola desde el final")))))
        assertEquals("Hola desde el final", ChatGptStream.read(Buffer().writeUtf8(stream(completed().put("response", response)))))
        assertTrue(runCatching { ChatGptStream.read(Buffer().writeUtf8(stream(completed()))) }.isFailure)
    }
}
