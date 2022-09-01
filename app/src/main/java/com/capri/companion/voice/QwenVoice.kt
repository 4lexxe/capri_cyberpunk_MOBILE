package com.capri.companion.voice

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import com.qwen.tts.studio.engine.QwenEngine
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
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class QwenState(val installed: Boolean = false, val working: Boolean = false,
    val progress: Float = 0f, val status: String = "Modelos sin descargar")
data class QwenMetrics(val firstAudioMs: Long = 0, val generationMs: Long = 0, val audioMs: Long = 0, val cached: Boolean = false) {
    val realTimeFactor: Double get() = if (audioMs > 0) generationMs.toDouble() / audioMs else 0.0
}

class QwenVoice(private val context: Context, private val scope: CoroutineScope) {
    private val folder = File(context.filesDir, "qwen-models").apply { mkdirs() }
    val state = MutableStateFlow(QwenState(installed = installed(), status = if (installed()) "Modelos disponibles" else "Descarga inicial: 884 MB"))
    private val lock = Mutex()
    private val generation = AtomicLong()
    @Volatile private var engine: QwenEngine? = null
    @Volatile private var downloadCall: Call? = null
    private var download: Job? = null
    private var warming: Job? = null
    private data class CachedSpeech(val text: String, val embedding: String, val maxTokens: Int, val result: QwenEngine.NativeResult)
    private var lastSpeech: CachedSpeech? = null
    fun installBundled() = scope.launch {
        if (installed()) { if (context is com.capri.companion.CapriApplication && context.settings.value.voiceMode == com.capri.companion.data.VoiceMode.QWEN) warmUp(); return@launch }
        if (runCatching { context.assets.list("qwen-bundle").orEmpty().isEmpty() }.getOrDefault(true)) return@launch
        state.value = QwenState(false, true, status = "Instalando la voz incluida…")
        try {
            withContext(Dispatchers.IO) { lock.withLock {
                require(StatFs(folder.absolutePath).availableBytes > TOTAL + (64L shl 20)) { "Libera al menos 950 MB para preparar la voz incluida." }
                var done = 0L
                for (model in MODELS) {
                    val target = File(folder, model.name)
                    if (target.length() == model.bytes && sha(target) == model.sha) { done += model.bytes; continue }
                    val temporary = File(folder, model.name + ".part")
                    try {
                        context.assets.open("qwen-bundle/${model.name}").use { input -> temporary.outputStream().use { output ->
                            val digest = MessageDigest.getInstance("SHA-256")
                            val buffer = ByteArray(1024 * 1024)
                            var size = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = input.read(buffer); if (n < 0) break
                                output.write(buffer, 0, n); digest.update(buffer, 0, n); size += n
                                state.value = QwenState(false, true, (done + size).toFloat() / TOTAL, "Preparando voz incluida: ${((done + size) * 100 / TOTAL)}%")
                            }
                            check(size == model.bytes && hex(digest.digest()) == model.sha) { "El modelo incluido no pasó la verificación." }
                        } }
                        java.nio.file.Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    } finally { temporary.delete() }
                    done += model.bytes
                }
            } }
            state.value = QwenState(true, status = "Voz Capri lista · sin conexión")
            if (context is com.capri.companion.CapriApplication && context.settings.value.voiceMode == com.capri.companion.data.VoiceMode.QWEN) warmUp()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { state.value = QwenState(installed(), status = e.message ?: "No se pudo preparar la voz incluida.") }
    }
    val references = VoiceReferences(context)
    val metrics = MutableStateFlow(QwenMetrics())

    fun installed() = MODELS.all { File(folder, it.name).length() == it.bytes }
    fun cancel() { generation.incrementAndGet(); engine?.cancel(); warming?.cancel() }
    fun cancelDownload() { download?.cancel(); downloadCall?.cancel() }

    fun downloadModels() {
        if (download?.isActive == true) return
        download = scope.launch {
            state.value = QwenState(installed(), true, 0f, "Descargando modelos Qwen…")
            try {
                withContext(Dispatchers.IO) {
                    require(StatFs(folder.absolutePath).availableBytes > TOTAL + (128L shl 20)) { "Necesitás al menos 1 GB libre para descargar Qwen." }
                    val http = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).connectTimeout(20, TimeUnit.SECONDS).build()
                    var done = 0L
                    for (model in MODELS) {
                        currentCoroutineContext().ensureActive()
                        val target = File(folder, model.name)
                        if (target.length() == model.bytes && sha(target) == model.sha) { done += model.bytes; continue }
                        val temporary = File(folder, model.name + ".part")
                        val request = Request.Builder().url("https://huggingface.co/Serveurperso/Qwen3-TTS-GGUF/resolve/$REVISION/${model.name}").build()
                        val call = http.newCall(request); downloadCall = call
                        try {
                            call.execute().use { response ->
                                check(response.isSuccessful) { "No se pudo descargar el modelo (HTTP ${response.code})." }
                                val digest = MessageDigest.getInstance("SHA-256")
                                var count = 0L
                                var reported = 0L
                                response.body!!.byteStream().use { input -> temporary.outputStream().use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val n = input.read(buffer); if (n == -1) break
                                        count += n; check(count <= model.bytes) { "El archivo descargado es más grande de lo esperado." }
                                        output.write(buffer, 0, n); digest.update(buffer, 0, n)
                                        if (count - reported > 1024 * 1024) {
                                            reported = count
                                            state.value = QwenState(false, true, (done + count).toFloat() / TOTAL,
                                                "Descargando ${(done + count) / 1_000_000} / 884 MB")
                                        }
                                    }
                                } }
                                check(count == model.bytes && hex(digest.digest()) == model.sha) { "El modelo no pasó la verificación de integridad. Volvé a descargarlo." }
                            }
                            java.nio.file.Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                            done += model.bytes
                        } finally { downloadCall = null }
                    }
                }
                state.value = QwenState(true, false, 1f, "Qwen descargado y verificado")
                warmUp()
            } catch (e: kotlinx.coroutines.CancellationException) {
                state.value = QwenState(installed(), false, status = "Descarga detenida; podés volver a iniciarla")
                throw e
            } catch (e: Exception) {
                state.value = QwenState(installed(), false, status = e.message ?: "No se pudo descargar Qwen")
            }
        }
    }

    fun warmUp() {
        if (!installed() || warming?.isActive == true) return
        warming = scope.launch {
            try { prepared { _, _ -> Unit } }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { state.value = state.value.copy(status = e.message ?: "No se pudo preparar Qwen") }
        }
    }

    fun selectReference(id: String) {
        cancel(); references.select(id); metrics.value = QwenMetrics(); warmUp()
    }

    fun importReference(uri: android.net.Uri) {
        cancel()
        scope.launch {
            try { references.import(uri); metrics.value = QwenMetrics(); warmUp() }
            catch (e: Exception) { state.value = state.value.copy(status = e.message ?: "No se pudo importar la referencia WAV") }
        }
    }

    suspend fun synthesize(text: String, maxTokens: Int = 320): QwenEngine.NativeResult {
        val token = generation.get()
        val start = android.os.SystemClock.elapsedRealtime()
        val words = text.take(600)
        return prepared { runtime, embedding ->
            ensureGeneration(token)
            val saved = lastSpeech?.takeIf { it.text == words && it.embedding == embedding.name && it.maxTokens == maxTokens }
            if (saved != null) {
                metrics.value = QwenMetrics(audioMs = saved.result.audio!!.size * 1000L / saved.result.sampleRate, cached = true)
                saved.result
            } else {
                state.value = state.value.copy(status = "Preparando la frase completa para hablar sin cortes…")
                runtime.synthesize(words, speakerEmbeddingPath = embedding.absolutePath,
                    params = QwenEngine.NativeParams(languageId = 2054, maxAudioTokens = maxTokens)).also {
                    ensureGeneration(token)
                    check(it.success && it.audio?.isNotEmpty() == true) { it.errorMsg ?: "Qwen no pudo generar audio" }
                    val elapsed = android.os.SystemClock.elapsedRealtime() - start
                    metrics.value = QwenMetrics(elapsed, elapsed, it.audio!!.size * 1000L / it.sampleRate)
                    lastSpeech = CachedSpeech(words, embedding.name, maxTokens, it)
                }
            }
        }
    }

    suspend fun stream(text: String, maxTokens: Int = 320, onAudio: (FloatArray, Int) -> Boolean): QwenMetrics {
        val start = android.os.SystemClock.elapsedRealtime()
        var first = 0L; var count = 0L; var rate = 24000
        val token = generation.get()
        return prepared { runtime, embedding ->
            state.value = state.value.copy(status = "Generando y reproduciendo voz por bloques…")
            val result = runtime.synthesizeStreaming(text.take(600), embedding.absolutePath,
                QwenEngine.NativeParams(languageId = 2054, maxAudioTokens = maxTokens), callback = { samples, sampleRate ->
                    if (token != generation.get()) false else {
                        if (first == 0L) first = android.os.SystemClock.elapsedRealtime() - start
                        count += samples.size; rate = sampleRate
                        onAudio(samples, sampleRate)
                    }
                })
            ensureGeneration(token)
            check(result.success && count > 0) { result.errorMsg ?: "Qwen no pudo generar audio" }
            QwenMetrics(first, android.os.SystemClock.elapsedRealtime() - start, count * 1000 / rate).also { metrics.value = it }
        }
    }

    private suspend fun <T> prepared(action: (QwenEngine, File) -> T): T {
        require(Build.VERSION.SDK_INT >= 31) { "La voz Qwen requiere Android 12 o posterior." }
        require(Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "x86_64" }) { "La voz Qwen requiere un dispositivo de 64 bits compatible." }
        require(installed()) { "Descargá los modelos Qwen desde Mainframe → Voz." }
        val token = generation.get()
        return lock.withLock {
            withContext(Dispatchers.Default) {
                currentCoroutineContext().ensureActive()
                ensureGeneration(token)
                val memory = ActivityManager.MemoryInfo()
                context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
                require(engine != null || memory.availMem >= 1200L * 1024 * 1024) { "No hay memoria disponible suficiente para cargar Qwen. Cerrá otras apps e intentá otra vez." }
                state.value = QwenState(true, true, status = "Cargando Qwen local…")
                try {
                    val runtime = engine ?: QwenEngine().also { engine = it }
                    runtime.resetCancel()
                    ensureGeneration(token)
                    runtime.setBackendPreference(QwenEngine.BACKEND_CPU)
                    runtime.setCpuThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
                    if (runtime.getModelCapabilities()?.loaded != true)
                        check(runtime.loadModels(folder.absolutePath, MODELS[0].name)) { runtime.getLastError() ?: "No se pudo cargar Qwen" }
                    currentCoroutineContext().ensureActive()
                    ensureGeneration(token)
                    val (reference, hash) = references.reference()
                    val embedding = File(folder, "speaker-latino-$hash.json")
                    if (!embedding.isFile) {
                        state.value = QwenState(true, true, status = "Preparando el perfil de voz de referencia…")
                        val bundled = runCatching { context.assets.open("voice/profiles/$hash.json") }.getOrNull()
                        if (bundled != null) bundled.use { input -> embedding.outputStream().use { input.copyTo(it) } }
                        else check(runtime.extractSpeakerEmbedding(reference.absolutePath, embedding.absolutePath)) { runtime.getLastError() ?: "No se pudo preparar el perfil de voz" }
                    }
                    currentCoroutineContext().ensureActive()
                    ensureGeneration(token)
                    val result = action(runtime, embedding)
                    currentCoroutineContext().ensureActive()
                    ensureGeneration(token)
                    result
                } finally { state.value = QwenState(installed(), false, status = "Qwen local · ${references.state.value.label}") }
            }
        }
    }

    fun deleteModels() {
        cancelDownload(); cancel()
        scope.launch {
            download?.join()
            lock.withLock { withContext(Dispatchers.IO) {
                engine?.close(); engine = null; lastSpeech = null
                MODELS.forEach { File(folder, it.name).delete(); File(folder, it.name + ".part").delete() }
                File(folder, "capri-speaker.bin").delete()
                File(folder, "capri-speaker.json").delete()
                File(folder, "capri-reference.wav").delete()
                folder.listFiles()?.filter { it.name.startsWith("speaker-latino-") && it.extension == "json" }?.forEach { it.delete() }
            } }
            state.value = QwenState(status = "Modelos eliminados")
        }
    }
    private fun ensureGeneration(token: Long) {
        if (token != generation.get()) throw kotlinx.coroutines.CancellationException("Síntesis cancelada")
    }

    private fun sha(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val b = ByteArray(65536); while (true) { val n = input.read(b); if (n == -1) break; digest.update(b, 0, n) } }
        return hex(digest.digest())
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    private data class Model(val name: String, val bytes: Long, val sha: String)
    companion object {
        const val TOTAL = 883879808L
        private const val REVISION = "b7ee2e8c7459c3bea99da23e3d178125a7d1713c"
        private val MODELS = listOf(
            Model("qwen-talker-0.6b-base-Q4_K_M.gguf", 628905056L, "4b468ec7b1f62b90ef4ca316c0aa57deadfd54b2cf9651703ea753cedaf04226"),
            Model("qwen-tokenizer-12hz-Q4_K_M.gguf", 254974752L, "cf3788b4d50aaa665fb6e57c170396aae03a3555fea52d2b5d0cda902d658039"))
    }
}
