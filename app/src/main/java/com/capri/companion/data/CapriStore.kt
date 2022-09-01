package com.capri.companion.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CapriStore(context: Context) : com.capri.companion.auth.ChatGptVault {
    private val prefs = context.getSharedPreferences("capri", Context.MODE_PRIVATE)
    init {
        if (!prefs.getBoolean("voicePreset.release1", false)) {
            prefs.edit().putFloat("qwenSpeed", 1.09f).putFloat("qwenRobotic", .182f).putFloat("volume", .73f)
                .putString("voice", if (android.os.Build.VERSION.SDK_INT >= 31) "QWEN" else "SYSTEM")
                .putBoolean("voicePreset.release1", true).commit()
        }
    }
    private val history = AtomicFile(File(context.filesDir, "conversation.json"))

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("capri.credentials", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("capri.credentials", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun seal(value: String): String {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun unseal(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        return try {
            val bytes = Base64.decode(value, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) { "" }
    }

    @Synchronized override fun readAuth(): String? {
        val saved = prefs.getString("chatgpt.oauth", null) ?: return null
        return unseal(saved).takeIf { it.isNotEmpty() }
            ?: throw java.io.IOException("No se pudo abrir la sesión cifrada de ChatGPT.")
    }

    @Synchronized override fun writeAuth(value: String) {
        check(prefs.edit().putString("chatgpt.oauth", seal(value)).commit()) {
            "No se pudo guardar la sesión cifrada de ChatGPT."
        }
    }

    fun loadSettings() = CapriSettings(
        nickname = (prefs.getString("nickname", "") ?: "").takeUnless { it.lowercase() in setOf("choom", "chom", "chum") }.orEmpty(),
        endpoint = prefs.getString("endpoint", CapriSettings().endpoint)!!,
        model = prefs.getString("model", "")!!,
        apiKey = unseal(prefs.getString("apiKey", null)),
        voiceMode = runCatching { VoiceMode.valueOf(prefs.getString("voice", "QWEN")!!) }.getOrDefault(VoiceMode.QWEN),
        elevenKey = unseal(prefs.getString("elevenKey", null)),
        elevenVoice = prefs.getString("elevenVoice", "")!!,
        elevenModel = prefs.getString("elevenModel", "eleven_multilingual_v2")!!,
        sizeDp = prefs.getInt("size", 100).coerceIn(100, 240),
        volume = prefs.getFloat("volume", 0.8f).coerceIn(0f, 1f),
        sleepMinutes = prefs.getInt("sleep", 5).coerceIn(0, 30),
        seated = prefs.getBoolean("seated", false),
        personality = prefs.getString("personality", CapriSettings.DEFAULT_PERSONALITY)!!
            .takeUnless { it == CapriSettings.LEGACY_PERSONALITY || it == CapriSettings.LEGACY_PERSONALITY_V05 } ?: CapriSettings.DEFAULT_PERSONALITY,
        provider = enumValue("provider", AiProvider.COMPATIBLE),
        visionEnabled = prefs.getBoolean("visionEnabled", false),
        visionUseBrain = prefs.getBoolean("visionUseBrain", true),
        visionProvider = enumValue("visionProvider", AiProvider.GEMINI),
        visionEndpoint = prefs.getString("visionEndpoint", AiProvider.GEMINI.defaultEndpoint)!!,
        visionModel = prefs.getString("visionModel", "")!!,
        visionApiKey = unseal(prefs.getString("visionKey", null)),
        autoComments = prefs.getBoolean("autoComments", false),
        initiative = enumValue("initiative", Initiative.NORMAL),
        rememberActivities = prefs.getBoolean("rememberActivities", false),
        qwenSpeed = prefs.getFloat("qwenSpeed", 1.09f).takeIf { it.isFinite() }?.coerceIn(1f, 1.2f) ?: 1.09f,
        qwenRobotic = prefs.getFloat("qwenRobotic", .182f).takeIf { it.isFinite() }?.coerceIn(0f, .35f) ?: .182f,
    )

    private inline fun <reified T : Enum<T>> enumValue(name: String, fallback: T): T =
        runCatching { enumValueOf<T>(prefs.getString(name, fallback.name)!!) }.getOrDefault(fallback)

    @Synchronized fun save(settings: CapriSettings) {
        val api = seal(settings.apiKey)
        val eleven = seal(settings.elevenKey)
        val vision = seal(settings.visionApiKey)
        check(prefs.edit().putString("nickname", settings.nickname).putString("endpoint", settings.endpoint)
            .putString("model", settings.model).putString("apiKey", api).putString("voice", settings.voiceMode.name)
            .putString("elevenKey", eleven).putString("elevenVoice", settings.elevenVoice)
            .putString("elevenModel", settings.elevenModel).putInt("size", settings.sizeDp)
            .putFloat("volume", settings.volume).putInt("sleep", settings.sleepMinutes)
            .putBoolean("seated", settings.seated).putString("personality", settings.personality)
            .putString("provider", settings.provider.name).putBoolean("visionEnabled", settings.visionEnabled)
            .putBoolean("visionUseBrain", settings.visionUseBrain).putString("visionProvider", settings.visionProvider.name)
            .putString("visionEndpoint", settings.visionEndpoint).putString("visionModel", settings.visionModel)
            .putString("visionKey", vision).putBoolean("autoComments", settings.autoComments)
            .putString("initiative", settings.initiative.name).putBoolean("rememberActivities", settings.rememberActivities)
            .putFloat("qwenSpeed", settings.qwenSpeed).putFloat("qwenRobotic", settings.qwenRobotic).commit()) {
            "No se pudieron guardar los ajustes."
        }
    }

    fun messages(): List<Message> = runCatching {
        val rows = JSONArray(history.readFully().toString(Charsets.UTF_8))
        (0 until rows.length()).map { i -> rows.getJSONObject(i).let {
            Message(it.getString("role"), it.getString("text"), it.getLong("id"))
        } }.filter { it.role in setOf("user", "assistant") }.takeLast(80)
    }.getOrDefault(emptyList())

    fun saveMessages(messages: List<Message>) {
        val json = JSONArray().apply { messages.takeLast(80).forEach {
            put(JSONObject().put("role", it.role).put("text", it.text).put("id", it.id))
        } }
        val out = history.startWrite()
        try { out.write(json.toString().toByteArray()); history.finishWrite(out) }
        catch (e: Exception) { history.failWrite(out); throw e }
    }

    var overlayX: Int
        get() = prefs.getInt("overlayX", Int.MIN_VALUE)
        set(value) { prefs.edit().putInt("overlayX", value).apply() }
    var overlayY: Int
        get() = prefs.getInt("overlayY", Int.MIN_VALUE)
        set(value) { prefs.edit().putInt("overlayY", value).apply() }
}

