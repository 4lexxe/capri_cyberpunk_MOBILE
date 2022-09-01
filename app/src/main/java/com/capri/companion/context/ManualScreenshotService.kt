package com.capri.companion.context

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.WindowManager
import com.capri.companion.BuildConfig
import com.capri.companion.CapriApplication
import com.capri.companion.R
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

/** No timer or reusable consent token: resources are closed before one JPEG is sent to the model. */
class ManualScreenshotService : Service() {
    private val app get() = application as CapriApplication
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var timeout: Job? = null
    private var settingsWatch: Job? = null
    private var taken = false
    private var closed = false
    private var receiverRegistered = false
    private val handler = Handler(Looper.getMainLooper())
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { if (!closed) fail("Android detuvo la captura. No se enviará ninguna imagen.") }
    }
    private val locked = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { fail("Captura cancelada al bloquear el teléfono.") }
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!BuildConfig.MANUAL_CAPTURE || intent == null) { stopSelf(); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY
        running = true
        try {
            val notifications = getSystemService(NotificationManager::class.java)
            notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Captura manual de Capri", NotificationManager.IMPORTANCE_LOW))
            val notification = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Tomando una captura")
                .setContentText("Se cerrará al obtener una imagen.").setOngoing(true).build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(3, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            else startForeground(3, notification)
            @Suppress("DEPRECATION")
            val data = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra("data", Intent::class.java) else intent.getParcelableExtra<Intent>("data")
            check(intent.getIntExtra("resultCode", 0) == Activity.RESULT_OK && data != null) { "Falta autorizar esta captura en Android." }
            val manager = getSystemService(MediaProjectionManager::class.java)
            projection = manager.getMediaProjection(Activity.RESULT_OK, data) ?: error("No se pudo abrir la captura.")
            projection!!.registerCallback(callback, handler)
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(locked, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(locked, filter)
            receiverRegistered = true
            app.contextual.beginManual()
            val signature = ContextController.connectionSignature(app.settings.value)
            settingsWatch = scope.launch { app.settings.collect {
                if (ContextController.connectionSignature(it) != signature) fail("La configuración visual cambió. Repite la captura.")
            } }
            app.hideOverlayForCapture.value = true
            timeout = scope.launch { delay(10_000); fail("Android no entregó la captura a tiempo.") }
            scope.launch {
                try {
                    delay(400) // Let Android dismiss consent and Capri's task/overlay.
                    repeat(30) { if (app.appVisible.value) delay(50) }
                    if (closed) return@launch
                    check(!app.appVisible.value) { "Sal de Capri antes de tomar la captura." }
                    check(getSystemService(PowerManager::class.java).isInteractive && !getSystemService(KeyguardManager::class.java).isKeyguardLocked) { "Desbloquea el teléfono para tomar una captura." }
                    createDisplay()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { if (!closed) fail(e.message ?: "No se pudo tomar la captura.") }
            }
        } catch (e: Exception) { fail(e.message ?: "No se pudo tomar la captura.") }
        return START_NOT_STICKY
    }
    @Suppress("DEPRECATION")
    private fun createDisplay() {
        val wm = getSystemService(WindowManager::class.java)
        val size = if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds.let { android.util.Size(it.width(), it.height()) }
            else android.util.DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }.let { android.util.Size(it.widthPixels, it.heightPixels) }
        val ratio = minOf(1f, 960f / maxOf(size.width, size.height))
        val width = maxOf(2, (size.width * ratio).toInt())
        val height = maxOf(2, (size.height * ratio).toInt())
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ source ->
                val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                if (taken || closed) { image.close(); return@setOnImageAvailableListener }
                taken = true
                val bitmap = try { copyBitmap(image) } catch (e: Exception) { fail(e.message ?: "Imagen no compatible."); return@setOnImageAvailableListener }
                finally { image.close() }
                val at = System.currentTimeMillis()
                closeCapture() // Releases virtual display, surface and projection before encoding/network.
                scope.launch {
                    try {
                        val jpeg = withContext(Dispatchers.Default) { ByteArrayOutputStream().use { out ->
                            try { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 72, out)); out.toByteArray() } finally { bitmap.recycle() }
                        } }
                        app.contextual.acceptFrame(jpeg, at)
                    } catch (e: CancellationException) { bitmap.recycle(); throw e }
                    catch (e: Exception) { fail(e.message ?: "No se pudo preparar la imagen.") }
                    finally { stopSelf() }
                }
            }, handler)
        }
        display = projection!!.createVirtualDisplay("Capri-one-image", width, height, resources.configuration.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler)
    }
    private fun copyBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val paddedWidth = image.width + (plane.rowStride - plane.pixelStride * image.width) / plane.pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        try {
            padded.copyPixelsFromBuffer(plane.buffer)
            return Bitmap.createBitmap(padded, 0, 0, image.width, image.height).let {
                if (it === padded) padded.copy(Bitmap.Config.ARGB_8888, false) else it
            }
        } finally { padded.recycle() }
    }
    private fun fail(message: String) {
        app.notice.value = message
        app.contextual.end()
        closeCapture(); stopSelf()
    }
    private fun closeCapture() {
        if (closed) return
        closed = true
        timeout?.cancel(); settingsWatch?.cancel()
        reader?.setOnImageAvailableListener(null, null)
        display?.release(); display = null
        reader?.close(); reader = null
        projection?.unregisterCallback(callback); projection?.stop(); projection = null
        app.hideOverlayForCapture.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
    override fun onDestroy() {
        closeCapture()
        if (receiverRegistered) unregisterReceiver(locked)
        if (!taken) app.contextual.end()
        running = false; scope.cancel(); super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "capri.manual-capture"
        @Volatile var running = false; private set
        fun stop(context: Context) { context.stopService(Intent(context, ManualScreenshotService::class.java)) }
    }
}
