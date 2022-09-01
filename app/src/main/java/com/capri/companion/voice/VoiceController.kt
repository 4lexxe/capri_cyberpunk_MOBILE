package com.capri.companion.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.AudioTrack
import android.media.AudioFormat
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.capri.companion.brain.BrainClient
import com.capri.companion.data.CapriSettings
import com.capri.companion.data.VoiceMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale

class VoiceController(private val context: Context, private val scope: CoroutineScope,
    private val onSpeaking: (Boolean) -> Unit, private val onError: (String) -> Unit,
    private val qwen: QwenVoice, private val onAmplitude: (Float) -> Unit,
    private val makeQwenSpeech: suspend (String) -> com.qwen.tts.studio.engine.QwenEngine.NativeResult = { qwen.synthesize(it) }) {
    private var ready = false
    private var initializationFinished = false
    private var tts: TextToSpeech? = null
    private var player: MediaPlayer? = null
    private var audioFile: File? = null
    private var job: Job? = null
    @Volatile private var generation = 0L
    private var pending: (() -> Unit)? = null
    @Volatile private var track: AudioTrack? = null
    private val audioGuard = Any()
    val preparing = kotlinx.coroutines.flow.MutableStateFlow(false)

    init {
        tts = TextToSpeech(context) { status ->
            scope.launch(Dispatchers.Main) {
                ready = status == TextToSpeech.SUCCESS
                initializationFinished = true
                if (ready) pending?.invoke() else if (pending != null) { preparing.value = false; onError("No se pudo iniciar la voz del sistema.") }
                pending = null
            }
        }
    }

    fun speak(text: String, settings: CapriSettings) {
        stop()
        if (settings.voiceMode == VoiceMode.OFF || settings.volume == 0f) return
        val clean = text.replace(Regex("\\[[^]]*]"), " ").replace("*", "").trim()
        if (clean.isBlank()) return
        if (settings.voiceMode == VoiceMode.QWEN) {
            preparing.value = true
            val token = generation
            job = scope.launch {
                try {
                    // Generate one continuous waveform, then upload all of it before play().
                    // A slow CPU cannot empty this static buffer between generated chunks.
                    val result = makeQwenSpeech(clean)
                    if (token != generation) return@launch
                    val raw = result.audio ?: error("Qwen no devolvió audio.")
                    check(raw.isNotEmpty() && raw.all { it.isFinite() }) { "Qwen devolvió audio inválido." }
                    val samples = withContext(Dispatchers.Default) {
                        QwenVoiceEffects.apply(raw, result.sampleRate, settings.qwenSpeed, settings.qwenRobotic)
                    }
                    val audio = synchronized(audioGuard) {
                        if (token != generation) null else AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                                .setSampleRate(result.sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                            .setBufferSizeInBytes(samples.size * Float.SIZE_BYTES)
                            .setTransferMode(AudioTrack.MODE_STATIC).build().also { track = it; it.setVolume(settings.volume) }
                    }
                    if (audio == null) return@launch
                    val uploaded = withContext(Dispatchers.IO) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        audio.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                    }
                    check(uploaded == samples.size) { "No se pudo preparar la frase completa de Qwen." }
                    synchronized(audioGuard) {
                        if (token == generation) { audio.play(); preparing.value = false; onSpeaking(true) }
                    }
                    withContext(Dispatchers.IO) {
                        while (token == generation) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val at = audio.playbackHeadPosition.toLong() and 0xffffffffL
                            if (at >= samples.size) break
                            val start = at.toInt()
                            val end = minOf(samples.size, start + result.sampleRate / 25)
                            var squares = 0.0
                            for (i in start until end) squares += samples[i].toDouble() * samples[i]
                            val level = (kotlin.math.sqrt(squares / maxOf(1, end - start)) * 5).toFloat().coerceIn(0f, 1f)
                            scope.launch { if (token == generation) onAmplitude(level) }
                            kotlinx.coroutines.delay(40)
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { if (token == generation) onError(e.message ?: "Qwen no pudo hablar.") }
                finally { synchronized(audioGuard) { if (token == generation) { track?.release(); track = null; preparing.value = false; onSpeaking(false); onAmplitude(-1f) } } }
            }
            return
        }
        if (settings.voiceMode == VoiceMode.SYSTEM) {
            preparing.value = true
            if (ready) system(clean, settings)
            else if (initializationFinished) { preparing.value = false; onError("No hay un motor de texto a voz disponible en este teléfono.") }
            else pending = { system(clean, settings) }
            return
        }
        if (settings.elevenKey.isBlank() || settings.elevenVoice.isBlank()) {
            onError("Guardá la clave y el Voice ID de ElevenLabs en Mainframe."); return
        }
        val token = generation
        preparing.value = true
        job = scope.launch {
            try {
                val json = JSONObject().put("text", clean.take(4000)).put("model_id", settings.elevenModel)
                    .put("language_code", "es")
                val id = java.net.URLEncoder.encode(settings.elevenVoice.trim(), "UTF-8")
                val request = Request.Builder().url("https://api.elevenlabs.io/v1/text-to-speech/$id?output_format=mp3_44100_128")
                    .header("xi-api-key", settings.elevenKey.trim())
                    .post(json.toString().toRequestBody("application/json".toMediaType())).build()
                val audio = BrainClient().execute(request)
                val file = withContext(Dispatchers.IO) {
                    File.createTempFile("capri-", ".mp3", context.cacheDir).apply { writeBytes(audio) }
                }
                if (token != generation) { file.delete(); return@launch }
                audioFile = file
                val mp = MediaPlayer()
                player = mp
                mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                mp.setDataSource(file.absolutePath)
                mp.setVolume(settings.volume, settings.volume)
                mp.setOnPreparedListener { if (token == generation) { preparing.value = false; onSpeaking(true); it.start() } }
                mp.setOnCompletionListener { if (token == generation) stop() }
                mp.setOnErrorListener { _, _, _ ->
                    if (token == generation) { stop(); onError("No se pudo reproducir la voz. La respuesta sigue en el chat.") }; true
                }
                mp.prepareAsync()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { if (token == generation) { preparing.value = false; onError(e.message ?: "La voz no respondió.") } }
        }
    }

    private fun system(text: String, settings: CapriSettings) {
        val engine = tts ?: run { preparing.value = false; return }
        val locale = Locale.forLanguageTag("es-MX")
        val available = engine.setLanguage(locale)
        if (available < TextToSpeech.LANG_AVAILABLE) { preparing.value = false; onError("Instalá una voz en español en los ajustes de texto a voz de Android."); return }
        engine.setSpeechRate(1.12f)
        engine.setPitch(1.10f)
        val token = generation
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { scope.launch { if (token == generation) { preparing.value = false; onSpeaking(true) } } }
            override fun onDone(id: String?) { scope.launch { if (token == generation) { preparing.value = false; onSpeaking(false) } } }
            @Deprecated("Android callback") override fun onError(id: String?) {
                scope.launch { if (token == generation) { preparing.value = false; onSpeaking(false); onError("La voz del sistema no pudo hablar.") } }
            }
        })
        val code = engine.speak(text.take(4000), TextToSpeech.QUEUE_FLUSH,
            Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.volume) }, "capri-$token")
        if (code == TextToSpeech.ERROR) { preparing.value = false; onError("La voz del sistema no está disponible.") }
    }

    fun stop() {
        synchronized(audioGuard) {
            generation++
            track?.let { runCatching { it.pause(); it.flush(); it.release() } }; track = null
        }
        preparing.value = false
        qwen.cancel()
        pending = null
        job?.cancel(); job = null
        tts?.stop()
        player?.release(); player = null
        audioFile?.delete(); audioFile = null
        onSpeaking(false)
        onAmplitude(-1f)
    }
}
