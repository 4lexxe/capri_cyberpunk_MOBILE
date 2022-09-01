package com.capri.companion.voice

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import android.speech.*
import com.capri.companion.CapriApplication
import com.capri.companion.R
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.coroutines.resume

data class OverlayVoiceState(val active: Boolean = false, val status: String = "", val partial: String = "")

/** Explicit, bounded, turn-based voice conversation. No boot restart or always-on wake word. */
class OverlayVoiceService : Service() {
    private val app get() = application as CapriApplication
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recognizer: SpeechRecognizer? = null
    private var session: Job? = null
    private var ownsTurn = false
    private var registered = false
    private val locked = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { stopSelf() }
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP || intent == null) { stopSelf(); return START_NOT_STICKY }
        if (session?.isActive == true) return START_NOT_STICKY
        try {
            check(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "Falta el permiso de micrófono." }
            getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Chat de voz con Capri", NotificationManager.IMPORTANCE_LOW))
            val stop = PendingIntent.getService(this, 20, Intent(this, OverlayVoiceService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Chat de voz con Capri")
                .setContentText("Micrófono por turnos. Toca Detener para finalizar.")
                .setOngoing(true).addAction(Notification.Action.Builder(null, "Detener voz", stop).build()).build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(4, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(4, notification)
            recognizer = recognizerFactory(this)
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(locked, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(locked, filter)
            registered = true
            app.voice.stop(); app.notice.value = null
            app.voiceChat.value = OverlayVoiceState(true, "Conectando voz…")
            session = scope.launch {
                try {
                    withTimeout(5 * 60_000L) {
                        while (isActive) {
                            val words = withTimeoutOrNull(20_000) { listenOnce() }
                            app.listening.value = false
                            if (words.isNullOrBlank()) break
                            ownsTurn = true
                            app.voiceChat.value = OverlayVoiceState(true, "Pensando…")
                            app.send(words)
                            do {
                                delay(80)
                                val status = if (app.busy.value) "Pensando…" else if (app.voice.preparing.value) "Preparando voz…" else "Capri está hablando…"
                                if (app.voiceChat.value.status != status) app.voiceChat.value = OverlayVoiceState(true, status)
                            } while (app.busy.value || app.voice.preparing.value || app.speaking.value)
                            ownsTurn = false
                            if (app.notice.value != null) break
                            delay(250) // Avoid feeding the speaker tail back into recognition.
                        }
                    }
                } catch (_: TimeoutCancellationException) { app.notice.value = "Terminó la sesión de voz. Toca el micrófono para iniciar otra." }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { app.notice.value = e.message ?: "No se pudo continuar el chat de voz." }
                finally { stopSelf() }
            }
        } catch (e: Exception) { app.notice.value = e.message ?: "No hay un servicio de reconocimiento de voz disponible."; stopSelf() }
        return START_NOT_STICKY
    }
    private suspend fun listenOnce(): String? = suspendCancellableCoroutine { continuation ->
        val input = recognizer ?: error("No hay reconocimiento de voz disponible.")
        app.voiceChat.value = OverlayVoiceState(true, "Escuchando…")
        fun complete(text: String?) { app.listening.value = false; if (continuation.isActive) continuation.resume(text) }
        input.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { if (continuation.isActive) app.listening.value = true }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { app.listening.value = false }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(results: Bundle?) {
                if (continuation.isActive) app.voiceChat.value = OverlayVoiceState(true, "Escuchando…",
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().take(160))
            }
            override fun onResults(results: Bundle?) { complete(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()) }
            override fun onError(error: Int) {
                if (!continuation.isActive) return
                app.notice.value = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No escuché otra frase. Toca el micrófono para volver a hablar."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "El reconocimiento necesita conexión en este teléfono."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Android retiró el permiso de micrófono."
                    else -> "El servicio de voz se detuvo ($error). Toca el micrófono para reintentar."
                }
                complete(null)
            }
        })
        val locale = Locale.getDefault().takeIf { it.language == "es" }?.toLanguageTag() ?: "es-MX"
        continuation.invokeOnCancellation { Handler(Looper.getMainLooper()).post { runCatching { input.cancel() } } }
        try { input.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)) }
        catch (e: Exception) { app.notice.value = e.message ?: "No se pudo abrir el micrófono."; complete(null) }
    }
    override fun onDestroy() {
        scope.cancel()
        runCatching { recognizer?.cancel(); recognizer?.destroy() }; recognizer = null
        if (registered) unregisterReceiver(locked)
        if (ownsTurn) app.cancel()
        app.listening.value = false; app.voiceChat.value = OverlayVoiceState()
        app.notice.value?.let { android.widget.Toast.makeText(this, it, android.widget.Toast.LENGTH_LONG).show() }
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy()
    }
    companion object {
        const val STOP = "com.capri.companion.STOP_VOICE"
        private const val CHANNEL = "capri.voice-chat"
        internal var recognizerFactory: (Context) -> SpeechRecognizer = {
            check(SpeechRecognizer.isRecognitionAvailable(it)) { "Instala o habilita un servicio de reconocimiento de voz en Android." }
            SpeechRecognizer.createSpeechRecognizer(it)
        }
        fun stop(context: Context) { context.stopService(Intent(context, OverlayVoiceService::class.java)) }
    }
}
