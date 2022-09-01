package com.capri.companion.context

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.capri.companion.BuildConfig
import com.capri.companion.CapriApplication
import com.capri.companion.data.AiProvider

/** Visible user action -> disclosure -> fresh Android permission for exactly one still image. */
class ManualCaptureActivity : ComponentActivity() {
    private val app get() = application as CapriApplication
    private var ownsRequest = false
    private var awaitingPermission = false
    private var connectionSignature = ""
    private val permission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (connectionSignature != com.capri.companion.context.ContextController.connectionSignature(app.settings.value)) {
            app.notice.value = "La conexión visual cambió. Repite la captura para autorizar el nuevo destino."
            finish(); return@registerForActivityResult
        }
        if (result.resultCode == RESULT_OK && data != null) {
            try {
                startForegroundService(Intent(this, ManualScreenshotService::class.java)
                    .putExtra("resultCode", result.resultCode).putExtra("data", data))
                // A whole-screen request must capture the app behind Capri, never its settings.
                moveTaskToBack(true)
            } catch (_: Exception) { app.notice.value = "Android no permitió iniciar la captura. Vuelve a intentarlo." }
        } else app.notice.value = "Captura cancelada. No se envió ninguna imagen."
        finish()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.MANUAL_CAPTURE || (pending && savedInstanceState == null)) { finish(); return }
        ownsRequest = true; pending = true
        awaitingPermission = savedInstanceState?.getBoolean("awaitingPermission") ?: false
        connectionSignature = savedInstanceState?.getString("connectionSignature") ?: ContextController.connectionSignature(app.settings.value)
        if (awaitingPermission) return // ActivityResultRegistry delivers the already open system prompt.
        val config = app.settings.value
        val provider = if (config.visionUseBrain) config.provider else config.visionProvider
        val endpoint = if (config.visionUseBrain) config.endpoint else config.visionEndpoint
        val host = runCatching { java.net.URI(endpoint).host }.getOrNull().orEmpty()
        AlertDialog.Builder(this).setTitle("Enviar una captura a Capri")
            .setMessage("Android te permitirá elegir una app o tu pantalla. Se enviará una sola imagen a ${provider.label} ($host) para que Capri la comente. Puede incluir información personal visible. No se guardará la imagen.\n\nLa captura se cierra al obtener la imagen y no se repite automáticamente.")
            .setPositiveButton("Elegir pantalla") { _, _ ->
                awaitingPermission = true
                permission.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
            }.setNegativeButton("Cancelar") { _, _ -> finish() }
            .setOnCancelListener { finish() }.show()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("awaitingPermission", awaitingPermission)
        outState.putString("connectionSignature", connectionSignature)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        if (ownsRequest && !isChangingConfigurations) pending = false
        super.onDestroy()
    }
    companion object {
        @Volatile private var pending = false
        fun request(context: Context) {
            if (!BuildConfig.MANUAL_CAPTURE) return
            val app = context.applicationContext as CapriApplication
            val config = app.settings.value
            val provider = if (config.visionUseBrain) config.provider else config.visionProvider
            val model = config.visionModel.ifBlank { if (config.visionUseBrain) config.model else "" }
            if (!config.visionEnabled || model.isBlank()) { app.notice.value = "Activa y guarda un modelo visual en Entorno antes de tomar una captura."; return }
            if (provider == AiProvider.CHATGPT && !app.chatgpt.state.value.ready) { app.notice.value = "Conecta tu cuenta ChatGPT antes de tomar una captura."; return }
            if (pending || ManualScreenshotService.running || app.contextual.state.value.analyzing) { app.notice.value = "Espera a que termine la captura actual."; return }
            try { context.startActivity(Intent(context, ManualCaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (_: Exception) { app.notice.value = "Abre Capri → Entorno para tomar una captura." }
        }
    }
}
