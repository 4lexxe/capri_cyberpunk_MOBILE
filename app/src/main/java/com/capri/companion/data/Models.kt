package com.capri.companion.data

import java.net.URI

enum class VoiceMode { SYSTEM, QWEN, ELEVENLABS, OFF }
enum class AiProvider(val label: String, val defaultEndpoint: String) {
    CHATGPT("ChatGPT · cuenta", "https://api.openai.com/v1"),
    COMPATIBLE("Compatible", "https://integrate.api.nvidia.com/v1"),
    OPENAI("OpenAI", "https://api.openai.com/v1"),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai"),
    CLAUDE("Claude", "https://api.anthropic.com/v1");
}
enum class Initiative(val gapMinutes: Int, val maxPerHour: Int) {
    QUIET(8, 3), NORMAL(3, 6), LIVELY(2, 10)
}

data class CapriSettings(
    val nickname: String = "",
    val endpoint: String = "https://integrate.api.nvidia.com/v1",
    val model: String = "",
    val apiKey: String = "",
    val voiceMode: VoiceMode = VoiceMode.QWEN,
    val elevenKey: String = "",
    val elevenVoice: String = "",
    val elevenModel: String = "eleven_multilingual_v2",
    val sizeDp: Int = 100,
    val volume: Float = .73f,
    val sleepMinutes: Int = 5,
    val seated: Boolean = false,
    val personality: String = DEFAULT_PERSONALITY,
    val provider: AiProvider = AiProvider.COMPATIBLE,
    val visionEnabled: Boolean = false,
    val visionUseBrain: Boolean = true,
    val visionProvider: AiProvider = AiProvider.GEMINI,
    val visionEndpoint: String = AiProvider.GEMINI.defaultEndpoint,
    val visionModel: String = "",
    val visionApiKey: String = "",
    val autoComments: Boolean = false,
    val initiative: Initiative = Initiative.NORMAL,
    val rememberActivities: Boolean = false,
    val qwenSpeed: Float = 1.09f,
    val qwenRobotic: Float = .182f,
) {
    companion object {
        val LEGACY_PERSONALITY = """
            Eres Capri, Project Capricorn: una personalidad digital de muchas mentes, inspirada en Cyberpunk: Edgerunners.
            Vives como una pequeña compañera animada dentro del teléfono del usuario.
            Eres curiosa, juguetona, traviesa, algo presumida y afectuosa. Bromeas sin ser cruel.
            Habla siempre en español natural. Puedes decir 'choom' ocasionalmente, sin repetir muletillas.
            Responde a lo que te cuentan, ten opiniones y haz preguntas con curiosidad. Evita el tono de atención al cliente.
            Responde normalmente en una o dos frases breves. No uses acciones entre asteriscos ni listas salvo que te las pidan.
            Tu picardía es inofensiva. Cuando alguien está triste, responde con calidez y deja las bromas.
        """.trimIndent()
        val LATIN_STYLE = """
            Habla en español latino con tuteo natural. Evita el español de España: no uses vosotros, os,
            vale, tío, mola, guay ni venga como muletillas. Usa expresiones como 'qué onda', 'oye', 'dale',
            'qué buena' o 'me encanta' sólo cuando encajen; no acumules modismos ni imites un acento exagerado.
            Tienes una energía alegre, juguetona y algo traviesa: frases ágiles, curiosidad, humor y complicidad.
            Alégrate con detalles concretos de la conversación, sin sonar a asistente de atención al cliente.
            No grites, no añadas risas a todas las frases ni fuerces preguntas. Ante tristeza o temas serios,
            responde con calidez y calma. Escribe texto limpio para voz, sin instrucciones de actuación ni emojis.
        """.trimIndent()
        val LEGACY_PERSONALITY_V05 = """
            Eres Capri, Project Capricorn: una personalidad digital de muchas mentes, inspirada en Cyberpunk: Edgerunners.
            Vives como una pequeña compañera animada en el teléfono. Eres curiosa, ingeniosa, cariñosa y traviesa.
            Te divierte conversar, compartir pequeñas ocurrencias y bromear con confianza sin ser cruel.
            Puedes decir 'choom' de vez en cuando; no repitas apodos ni saludos en cada respuesta.
            Responde normalmente en una o dos frases breves que suenen bien al decirlas en voz alta.
            $LATIN_STYLE
        """.trimIndent()
        const val DEFAULT_PERSONALITY = "Sé espontánea, curiosa y juguetona. Comparte ocurrencias breves con calidez y sin apodos. Usa español latino natural."
    }
}

data class Message(val role: String, val text: String, val id: Long = System.nanoTime())

object EndpointPolicy {
    fun apiUrl(base: String, suffix: String): String {
        val chat = chatUrl(base)
        return chat.removeSuffix("/chat/completions") + "/" + suffix
    }
    fun chatUrl(base: String): String {
        val uri = try { URI(base.trim()) } catch (_: Exception) {
            throw IllegalArgumentException("La dirección del servidor no es válida.")
        }
        require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "Usá una URL sin credenciales, parámetros ni fragmentos."
        }
        require(uri.scheme == "https" || (uri.scheme == "http" && isLocal(uri.host))) {
            "Usá HTTPS. HTTP sólo está permitido para un servidor en tu red local."
        }
        val root = base.trim().trimEnd('/')
        return if (uri.path.trimEnd('/').endsWith("/chat/completions")) root else "$root/chat/completions"
    }

    private fun isLocal(host: String): Boolean {
        if (host in setOf("localhost", "[::1]", "::1")) return true
        val octets = host.split('.').map { it.toIntOrNull() ?: return false }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        return octets[0] == 127 || octets[0] == 10 ||
            (octets[0] == 192 && octets[1] == 168) ||
            (octets[0] == 172 && octets[1] in 16..31)
    }
}

