package com.capri.companion.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.capri.companion.CapriApplication
import com.capri.companion.data.AiProvider

/** Permission gate in its own translucent task; never navigates to MainActivity/chat. */
class OverlayVoiceActivity : ComponentActivity() {
    private val app get() = application as CapriApplication
    private val handler = Handler(Looper.getMainLooper())
    private var permissionPending = false
    private var started = false
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionPending = false
        if (granted) window.decorView.post { startSession() }
        else { app.notice.value = "El micrófono está desactivado. Puedes seguir escribiendo."; finish() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionPending = savedInstanceState?.getBoolean("permissionPending") ?: false
    }
    override fun onResume() {
        super.onResume()
        if (permissionPending || started) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startSession()
        else { permissionPending = true; permission.launch(Manifest.permission.RECORD_AUDIO) }
    }
    private fun startSession() {
        if (started || isFinishing) return
        started = true
        try {
            // Stay RESUMED until the microphone foreground service is established (Android 14+).
            startForegroundService(Intent(this, OverlayVoiceService::class.java))
            val deadline = android.os.SystemClock.uptimeMillis() + 2500
            fun waitForService() {
                if (isFinishing) return
                if (app.voiceChat.value.active || android.os.SystemClock.uptimeMillis() >= deadline) finish()
                else handler.postDelayed({ waitForService() }, 40)
            }
            waitForService()
        } catch (_: Exception) { app.notice.value = "Android no permitió iniciar el chat de voz. Vuelve a tocar el micrófono."; finish() }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("permissionPending", permissionPending); super.onSaveInstanceState(outState) }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }
    companion object {
        fun request(context: Context) {
            val app = context.applicationContext as CapriApplication
            if (app.voiceChat.value.active) { OverlayVoiceService.stop(context); return }
            if (!app.overlayRunning.value) { app.notice.value = "Muestra a Capri flotante antes de iniciar el chat de voz."; return }
            if (app.settings.value.model.isBlank()) { app.notice.value = "Conecta un modelo en Mainframe antes de hablar con Capri."; return }
            if (app.settings.value.provider == AiProvider.CHATGPT && !app.chatgpt.state.value.ready) { app.notice.value = "Conecta tu cuenta ChatGPT antes de iniciar el chat de voz."; return }
            if (app.busy.value) { app.notice.value = "Espera a que Capri termine su respuesta."; return }
            try { context.startActivity(Intent(context, OverlayVoiceActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (_: Exception) { app.notice.value = "Android no permitió abrir el permiso de micrófono." }
        }
    }
}
