package com.capri.companion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capri.companion.overlay.CompanionService
import com.capri.companion.context.ScreenshotService
import com.capri.companion.ui.CapriScreen
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val app get() = application as CapriApplication
    private var selectedTab by mutableStateOf(0)
    private var overlayAllowed by mutableStateOf(false)
    private var partialSpeech by mutableStateOf("")
    private var recognizer: SpeechRecognizer? = null
    private var microphonePending = false
    private val overlayPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        overlayAllowed = Settings.canDrawOverlays(this)
        if (overlayAllowed) requestNotificationsAndStart()
        else app.notice.value = "Para que Capri flote, activá Mostrar sobre otras apps."
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        startCompanion()
    }
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else app.notice.value = "El micrófono está desactivado. Podés seguir escribiendo."
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(8, 11, 16))
        )
        selectedTab = savedInstanceState?.getInt("tab") ?: when {
            intent.getBooleanExtra("environment", false) -> 3
            intent.getBooleanExtra("chat", false) -> 1
            else -> 0
        }
        microphonePending = savedInstanceState == null && intent.getBooleanExtra("microphone", false)
        setContent {
            val observation by app.contextual.state.collectAsStateWithLifecycle()
            LaunchedEffect(observation.active) {
                if (observation.active) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
            CapriScreen(app, selectedTab, { selectedTab = it }, overlayAllowed, partialSpeech,
                onShow = { requestCompanion() },
                onHide = { stopService(Intent(this, CompanionService::class.java)); app.stopScreenContext() },
                onMicrophone = { requestMicrophone() }, onShareScreen = { requestScreenSession() },
                onStopObserving = { app.stopScreenContext() },
                onImportVoice = {}, onChatGptSignIn = { account ->
                    app.stopForAccountChange()
                    app.chatgpt.signIn(account) { url ->
                        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }
                        catch (_: Exception) { app.chatgpt.cancelSignIn(); app.notice.value = "No se pudo abrir un navegador. Instalá o habilitá uno para conectar ChatGPT." }
                    }
                })
        }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("tab", selectedTab); super.onSaveInstanceState(outState) }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        if (intent.getBooleanExtra("chat", false)) selectedTab = 1
        if (intent.getBooleanExtra("environment", false)) selectedTab = 3
        microphonePending = intent.getBooleanExtra("microphone", false)
    }
    override fun onResume() {
        super.onResume()
        overlayAllowed = Settings.canDrawOverlays(this)
        if (microphonePending) { microphonePending = false; window.decorView.post { requestMicrophone() } }
    }
    private fun requestCompanion() {
        if (!Settings.canDrawOverlays(this)) overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        else requestNotificationsAndStart()
    }
    private fun requestNotificationsAndStart() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else startCompanion()
    }
    private fun startCompanion() {
        try { startForegroundService(Intent(this, CompanionService::class.java)) }
        catch (_: Exception) { app.notice.value = "Android no permitió iniciar a Capri. Volvé a intentarlo desde Mainframe." }
    }
    private fun requestMicrophone() {
        com.capri.companion.voice.OverlayVoiceService.stop(this)
        app.contextual.userInteracted()
        selectedTab = 1
        if (app.listening.value) { recognizer?.cancel(); app.listening.value = false; partialSpeech = ""; return }
        if (app.busy.value) { app.notice.value = "Esperá la respuesta o tocá Detener antes de hablar."; return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        else listen()
    }
    private fun requestScreenSession() {
        if (BuildConfig.MANUAL_CAPTURE) { com.capri.companion.context.ManualCaptureActivity.request(this); return }
        if (Build.VERSION.SDK_INT < 30) { app.notice.value = "Las capturas puntuales requieren Android 11 o posterior."; return }
        val config = app.settings.value
        val model = config.visionModel.ifBlank { if (config.visionUseBrain) config.model else "" }
        if (!config.visionEnabled || model.isBlank()) { app.notice.value = "Guardá y activá un modelo visual en Entorno antes de compartir."; return }
        val provider = if (config.visionUseBrain) config.provider else config.visionProvider
        if (provider == com.capri.companion.data.AiProvider.CHATGPT && !app.chatgpt.state.value.ready) {
            app.notice.value = "Conectá tu cuenta y autorizá el uso del plan ChatGPT antes de compartir la pantalla."; return
        }
        val screenshots = ScreenshotService.instance
        if (screenshots == null) {
            app.notice.value = "Habilita Capturas puntuales de Capri en Accesibilidad y vuelve a Entorno para iniciar."
            val settings = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(settings)
        } else screenshots.beginSession()
    }
    override fun onStart() {
        super.onStart(); app.appVisible.value = true
        com.capri.companion.voice.OverlayVoiceService.stop(this)
    }
    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            app.notice.value = "No hay reconocimiento de voz instalado. Podés escribir o instalar un servicio de voz."; return
        }
        app.voice.stop(); app.notice.value = null; partialSpeech = ""
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { app.listening.value = true }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onError(error: Int) {
                    app.listening.value = false; partialSpeech = ""
                    app.notice.value = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No llegué a escucharte. Tocá Hablar para intentarlo otra vez."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Revisá el permiso del micrófono en Android."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "El reconocimiento necesita conexión en este teléfono."
                        SpeechRecognizer.ERROR_CLIENT -> null
                        else -> "No se pudo reconocer la voz. Probá otra vez."
                    }
                }
                override fun onPartialResults(results: Bundle?) {
                    partialSpeech = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                }
                override fun onResults(results: Bundle?) {
                    app.listening.value = false; partialSpeech = ""
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { app.send(it) }
                }
            })
            val locale = Locale.getDefault().takeIf { it.language == "es" }?.toLanguageTag() ?: "es-MX"
            try {
                app.listening.value = true
                startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1))
            } catch (_: Exception) { app.listening.value = false; app.notice.value = "El micrófono no pudo iniciarse." }
        }
    }
    override fun onPause() {
        recognizer?.cancel(); recognizer?.destroy(); recognizer = null
        if (!app.voiceChat.value.active) app.listening.value = false
        super.onPause()
    }
    override fun onStop() {
        recognizer?.cancel(); recognizer?.destroy(); recognizer = null
        if (!app.voiceChat.value.active) app.listening.value = false
        partialSpeech = ""
        app.composing.value = false
        app.appVisible.value = false
        super.onStop()
    }
}
