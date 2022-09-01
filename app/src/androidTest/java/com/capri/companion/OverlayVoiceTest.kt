package com.capri.companion

import android.content.ComponentName
import android.content.Intent
import android.speech.SpeechRecognizer
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.capri.companion.data.AiProvider
import com.capri.companion.data.VoiceMode
import com.capri.companion.overlay.CompanionService
import com.capri.companion.voice.OverlayVoiceService
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class OverlayVoiceTest {
    @Test fun middleButtonSendsVoiceInBackgroundAndStopsWithoutOpeningChat() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as CapriApplication
        val device = UiDevice.getInstance(instrumentation)
        val old = app.settings.value; val messages = app.messages.value
        val factory = OverlayVoiceService.recognizerFactory
        val x = app.store.overlayX; val y = app.store.overlayY
        val server = MockWebServer().apply { start(java.net.InetAddress.getByName("127.0.0.1"), 0) }
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"¡Hola! Aquí estoy contigo."}}]}"""))
        device.executeShellCommand("appops set ${app.packageName} SYSTEM_ALERT_WINDOW allow")
        device.executeShellCommand("pm grant ${app.packageName} android.permission.RECORD_AUDIO")
        device.executeShellCommand("pm grant ${instrumentation.context.packageName} android.permission.RECORD_AUDIO")
        try {
            device.executeShellCommand("am start -W -n ${app.packageName}/.MainActivity")
            withContext(Dispatchers.Main) {
                app.settings.value = old.copy(model = "voice-test", provider = AiProvider.COMPATIBLE,
                    endpoint = "http://127.0.0.1:${server.port}/v1", apiKey = "", voiceMode = VoiceMode.OFF, sizeDp = 100)
                OverlayVoiceService.recognizerFactory = { context -> SpeechRecognizer.createSpeechRecognizer(context,
                    ComponentName(instrumentation.context.packageName, FakeRecognitionService::class.java.name)) }
                app.store.overlayX = 90; app.store.overlayY = 700
                app.startForegroundService(Intent(app, CompanionService::class.java))
            }
            withTimeout(5000) { while (!app.overlayRunning.value) delay(30) }
            device.pressHome(); delay(500)
            val avatar = device.wait(Until.findObject(By.descStartsWith("Capri. Tocá")), 5000)
            assertNotNull(avatar)
            val widthDp = avatar!!.visibleBounds.width() / app.resources.displayMetrics.density
            assertEquals(100f, widthDp, 1f)
            avatar.click()
            val button = device.wait(Until.findObject(By.desc("Hablar con Capri")), 3000)
            assertNotNull(button); button!!.click()
            val request = withContext(Dispatchers.IO) { server.takeRequest(15, TimeUnit.SECONDS) }
            assertNotNull("Voice did not reach model: ${app.notice.value}", request)
            assertTrue(request!!.body.readUtf8().contains("Hola Capri desde el flotante"))
            withTimeout(5000) { while (app.busy.value || !app.listening.value) delay(40) }
            assertTrue(app.voiceChat.value.active)
            assertFalse("Voice button returned to MainActivity", app.appVisible.value)
            val top = device.executeShellCommand("dumpsys activity activities")
            assertFalse(top.lineSequence().filter { it.contains("topResumedActivity=") }.any { it.contains("com.capri.companion/.MainActivity") })
            assertTrue(app.messages.value.any { it.role == "assistant" && it.text.contains("Aquí estoy") })
            val stop = device.wait(Until.findObject(By.desc("Cerrar chat de voz")), 3000)
            assertNotNull(stop); stop!!.click()
            withTimeout(5000) { while (app.voiceChat.value.active) delay(30) }
            assertFalse(app.listening.value)
            assertNull(withContext(Dispatchers.IO) { server.takeRequest(1, TimeUnit.SECONDS) })
        } finally {
            withContext(Dispatchers.Main) {
                OverlayVoiceService.stop(app); app.stopService(Intent(app, CompanionService::class.java))
                app.cancel(); app.settings.value = old; app.messages.value = messages
                app.store.overlayX = x; app.store.overlayY = y
                OverlayVoiceService.recognizerFactory = factory
            }
            server.shutdown()
            device.executeShellCommand("appops set ${app.packageName} SYSTEM_ALERT_WINDOW default")
        }
    }
}
