package com.capri.companion.context

import android.accessibilityservice.AccessibilityService
import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.os.Build
import android.os.PowerManager
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.capri.companion.CapriApplication
import com.capri.companion.MainActivity
import com.capri.companion.R
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Opt-in still images only. No MediaProjection, virtual display, video/audio or UI-tree access. */
@android.annotation.SuppressLint("NewApi") // All screenshot calls are gated by beginSession() requiring API 30.
class ScreenshotService : AccessibilityService() {
    private val app get() = application as CapriApplication
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timer: Job? = null
    private var capture: Job? = null
    private var settingsWatch: Job? = null
    private var revision = 0L
    private var started = false
    private var connected = false
    private var lastCapture = 0L
    private var captureAfterLeaving = false
    private val locked = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { endSession() }
    }
    override fun onServiceConnected() {
        super.onServiceConnected(); instance = this; connected = true
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(locked, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(locked, filter)
        scope.launch { app.appVisible.collect { visible ->
            if (started && !visible && (lastCapture == 0L || captureAfterLeaving)) schedule(500)
        } }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* No reading events or other apps' UI. */ }
    override fun onInterrupt() { endSession() }
    fun beginSession() {
        if (Build.VERSION.SDK_INT < 30 || !app.settings.value.visionEnabled) return
        endSession(); started = true; lastCapture = 0
        app.contextual.begin()
        val signature = ContextController.connectionSignature(app.settings.value)
        val token = revision
        settingsWatch = scope.launch { app.settings.collect {
            if (token != revision) throw CancellationException()
            if (ContextController.connectionSignature(it) != signature) endSession()
        } }
        notifyActive(); schedule(500)
    }
    private fun notifyActive() {
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Capturas puntuales de Capri", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 7, Intent(this, MainActivity::class.java).putExtra("environment", true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getBroadcast(this, 9, Intent(this, StopScreenshotsReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Capturas puntuales activas")
            .setContentText("Una imagen cada 3 minutos. Sin video. Toca Detener para finalizar.")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Detener", stop).build()).build()
        runCatching { notifications.notify(NOTIFICATION_ID, notification) }
    }
    private fun schedule(waitMs: Long = CaptureCadence.AUTOMATIC_INTERVAL_MS) {
        timer?.cancel()
        timer = scope.launch { delay(waitMs); captureNow() }
    }
    fun captureNow() {
        if (!started || capture?.isActive == true) return
        if (!app.contextual.needsFrame(System.currentTimeMillis())) { schedule(); return }
        if (app.appVisible.value) { captureAfterLeaving = true; schedule(); return } // Never capture Mainframe or its settings.
        if (!getSystemService(PowerManager::class.java).isInteractive || getSystemService(KeyguardManager::class.java).isKeyguardLocked) { endSession(); return }
        val token = revision
        captureAfterLeaving = false
        capture = scope.launch {
            try {
                app.hideOverlayForCapture.value = true
                delay(120) // Let the compositor remove Capri's own windows before the one screenshot.
                val result = withTimeout(6000) { suspendCancellableCoroutine<ScreenshotResult> { continuation ->
                    takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            if (continuation.isActive) continuation.resume(screenshot) { _, value, _ -> value.hardwareBuffer.close() } else screenshot.hardwareBuffer.close()
                        }
                        override fun onFailure(errorCode: Int) {
                            if (continuation.isActive) continuation.resumeWithException(IllegalStateException(
                                if (errorCode == ERROR_TAKE_SCREENSHOT_SECURE_WINDOW) "Esta pantalla está protegida; no se capturó."
                                else "Android no pudo obtener la captura puntual ($errorCode)."))
                        }
                    })
                } }
                app.hideOverlayForCapture.value = false
                val at = System.currentTimeMillis()
                val hardware = result.hardwareBuffer
                val bytes = try { withContext(Dispatchers.Default) {
                        val wrapped = Bitmap.wrapHardwareBuffer(hardware, result.colorSpace) ?: error("Captura no compatible.")
                        val bitmap = try { wrapped.copy(Bitmap.Config.ARGB_8888, false) } finally { wrapped.recycle() }
                        try {
                            val ratio = minOf(1f, 960f / maxOf(bitmap.width, bitmap.height))
                            val resized = Bitmap.createScaledBitmap(bitmap, maxOf(2, (bitmap.width * ratio).toInt()), maxOf(2, (bitmap.height * ratio).toInt()), true)
                            try { ByteArrayOutputStream().use { out -> resized.compress(Bitmap.CompressFormat.JPEG, 72, out); out.toByteArray() } }
                            finally { if (resized !== bitmap) resized.recycle() }
                        } finally { bitmap.recycle() }
                } } finally { hardware.close() }
                if (started && token == revision) { lastCapture = at; app.contextual.acceptFrame(bytes, at) }
            } catch (e: TimeoutCancellationException) { if (started && token == revision) app.contextual.captureFailed("Android no entregó la captura a tiempo.") }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (started && token == revision) app.contextual.captureFailed(e.message ?: "No se pudo obtener la captura.") }
            finally {
                app.hideOverlayForCapture.value = false
                if (started && token == revision) schedule()
            }
        }
    }
    fun endSession() {
        started = false; revision++; timer?.cancel(); capture?.cancel(); settingsWatch?.cancel(); timer = null; capture = null; settingsWatch = null
        app.hideOverlayForCapture.value = false
        captureAfterLeaving = false
        app.contextual.end()
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
    override fun onUnbind(intent: Intent?): Boolean { endSession(); if (instance === this) instance = null; return super.onUnbind(intent) }
    override fun onDestroy() {
        endSession(); if (connected) unregisterReceiver(locked); connected = false
        if (instance === this) instance = null
        scope.cancel(); super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "capri.screenshots"
        private const val NOTIFICATION_ID = 2
        @Volatile var instance: ScreenshotService? = null; private set
        fun stop() { instance?.endSession() }
    }
}

class StopScreenshotsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) { ScreenshotService.stop() }
}
