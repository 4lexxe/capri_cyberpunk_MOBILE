package com.capri.companion.auth

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.math.BigInteger
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

/** Public-client OAuth contract, adapted from Display CW's ChatConnection. */
object ChatGptProtocol {
    const val AUTH = "https://auth.openai.com"
    const val API = "https://api.openai.com/v1"
    const val DIRECT_SCOPE = "chatgpt.tokens.use.direct"
    const val SCOPES = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
    fun random(): String = encode(ByteArray(48).also { SecureRandom().nextBytes(it) })
    fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    fun challenge(verifier: String): String = encode(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    fun constant(a: String?, b: String?): Boolean = a != null && b != null &&
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    fun form(fields: Map<String, String>): String = fields.entries.joinToString("&") {
        URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
    }

    fun authOrigin(url: String): String {
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host == "auth.openai.com" && uri.port in listOf(-1, 443) &&
            uri.userInfo == null && uri.fragment == null) { "Destino de autenticación no autorizado." }
        return url
    }

    fun callback(target: String, state: String): Map<String, String>? = runCatching {
        val uri = URI(target)
        if (uri.isAbsolute || uri.rawAuthority != null || uri.path != "/auth/callback" || uri.fragment != null) return null
        val result = linkedMapOf<String, String>()
        for (pair in uri.rawQuery.orEmpty().split('&')) {
            val parts = pair.split('=', limit = 2)
            if (parts.size != 2) return null
            val key = URLDecoder.decode(parts[0], "UTF-8")
            if (result.containsKey(key)) return null
            result[key] = URLDecoder.decode(parts[1], "UTF-8")
        }
        result.takeIf { constant(it["state"], state) }
    }.getOrNull()

    fun validateId(token: String, client: String, nonce: String?, keys: JSONArray, now: Long): JSONObject {
        try {
            require(token.length <= 32768)
            val parts = token.split('.')
            require(parts.size == 3)
            fun decode(s: String) = Base64.getUrlDecoder().decode(s)
            val header = JSONObject(String(decode(parts[0]), Charsets.UTF_8))
            require(header.getString("alg") == "RS256" && !header.has("crit"))
            val kid = header.getString("kid")
            var valid = false
            for (i in 0 until keys.length()) {
                val k = keys.getJSONObject(i)
                if (k.optString("kid") != kid || k.optString("kty") != "RSA" ||
                    k.optString("use", "sig") != "sig" || k.optString("alg", "RS256") != "RS256") continue
                val modulus = BigInteger(1, decode(k.getString("n")))
                require(modulus.bitLength() >= 2048)
                val publicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, BigInteger(1, decode(k.getString("e")))))
                val signature = Signature.getInstance("SHA256withRSA").apply {
                    initVerify(publicKey); update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
                }
                if (signature.verify(decode(parts[2]))) { valid = true; break }
            }
            require(valid)
            val claims = JSONObject(String(decode(parts[1]), Charsets.UTF_8))
            val audience = claims.get("aud")
            val audiences = if (audience is JSONArray) (0 until audience.length()).map { audience.getString(it) } else listOf(audience as String)
            require(client in audiences && (audiences.size == 1 || claims.optString("azp") == client))
            require(claims.getString("iss") == AUTH && claims.getLong("exp") > now &&
                claims.getLong("iat") <= now + 60 && claims.optLong("nbf", 0) <= now + 60 && claims.getString("sub").isNotBlank())
            if (nonce != null) require(constant(nonce, claims.optString("nonce")))
            return claims
        } catch (_: Exception) { throw IOException("No se pudo verificar la identidad de ChatGPT. Iniciá sesión de nuevo.") }
    }
}

object ChatGptErrors {
    fun failure(status: Int, code: String): IOException = IOException(when (code) {
        "invalid_grant" -> "La autorización de ChatGPT venció o fue revocada. Volvé a conectar la cuenta."
        "subscription_sharing_usage_limit_exceeded" -> "Alcanzaste el límite de tu plan para Capri. Revisá ChatGPT → Configuración → Uso."
        "subscription_sharing_usage_unavailable" -> "El uso de tu plan ChatGPT no está disponible para esta solicitud. Revisá el acceso de Capri."
        "subscription_sharing_user_not_eligible" -> "La cuenta o el espacio seleccionado no está habilitado para usar el plan ChatGPT en Capri. Revisá la elegibilidad en ChatGPT."
        "subscription_sharing_unsupported_capability" -> "ChatGPT no admite alguna opción de esta solicitud. Actualizá Capri o probá otro modelo de tu cuenta."
        "subscription_sharing_route_not_supported" -> "ChatGPT no habilitó esta ruta para la cuenta. No hace falta agregar crédito de API; hay que revisar la conexión."
        "chatpass_v2_scope_not_authorized", "chatpass_v2_invalid_authorization_context" -> "El permiso firmado de ChatGPT no autoriza esta operación. Reconectá la cuenta y autorizá el uso del plan."
        "subscription_sharing_invalid_user" -> "ChatGPT no pudo validar la cuenta para esta solicitud. Volvé a conectar la cuenta."
        "subscription_sharing_user_unavailable" -> "ChatGPT no pudo consultar la cuenta en este momento. Reintentá más tarde."
        "credit_balance_exhausted", "insufficient_quota" -> "No hay crédito disponible para esta solicitud. Revisá el uso del plan en ChatGPT."
        else -> when (status) {
            401 -> "ChatGPT rechazó la sesión (401). Volvé a conectar la cuenta."
            403 -> "ChatGPT no autorizó esta conexión (403). Revisá los permisos y la disponibilidad de tu plan."
            429 -> "ChatGPT alcanzó un límite temporal o del plan (429). Esperá y revisá Configuración → Uso."
            404 -> "Este modelo no está disponible para tu cuenta. Actualizá la lista de modelos."
            else -> "ChatGPT no pudo completar la solicitud (HTTP $status)."
        }
    })
    fun code(json: JSONObject): String {
        val error = json.opt("error")
        return if (error is JSONObject) error.optString("code") else error as? String ?: json.optString("code")
    }
}
