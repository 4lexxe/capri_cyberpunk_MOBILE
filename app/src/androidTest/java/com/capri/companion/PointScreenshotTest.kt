package com.capri.companion

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import com.capri.companion.context.ScreenshotService
import com.capri.companion.data.AiProvider
import com.capri.companion.data.VoiceMode
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class PointScreenshotTest {
    @Test fun oneOptedInScreenshotAndNoProjectionOrContinuousFrames() = runBlocking {
        org.junit.Assume.assumeFalse(BuildConfig.MANUAL_CAPTURE)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        Configurator.getInstance().uiAutomationFlags = UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        val device = UiDevice.getInstance(instrumentation)
        val app = instrumentation.targetContext.applicationContext as CapriApplication
        val previous = device.executeShellCommand("settings get secure enabled_accessibility_services").trim()
        val previousFlag = device.executeShellCommand("settings get secure accessibility_enabled").trim()
        val settings = app.settings.value
        val activity = ActivityScenario.launch(MainActivity::class.java)
        val server = MockWebServer().apply { start(java.net.InetAddress.getByName("127.0.0.1"), 0) }
        try {
            val reaction = JSONObject().put("observation", "Imagen puntual de prueba").put("activity", "pantalla de prueba")
                .put("confidence", "high").put("sensitive", false).put("shouldSpeak", false).put("reaction", "").toString()
            server.enqueue(MockResponse().setBody(JSONObject().put("choices", org.json.JSONArray().put(JSONObject()
                .put("message", JSONObject().put("content", reaction)))).toString()))
            withContext(Dispatchers.Main) { app.settings.value = settings.copy(visionEnabled = true, visionUseBrain = true,
                provider = AiProvider.COMPATIBLE, endpoint = "http://127.0.0.1:${server.port}/v1", model = "test-vision",
                visionModel = "test-vision", apiKey = "", voiceMode = VoiceMode.OFF, autoComments = false) }
            device.executeShellCommand("settings put secure enabled_accessibility_services ${app.packageName}/.context.ScreenshotService")
            device.executeShellCommand("settings put secure accessibility_enabled 1")
            withTimeout(12000) { while (ScreenshotService.instance == null) delay(50) }
            val service = ScreenshotService.instance!!
            val capabilities = service.serviceInfo.capabilities
            assertEquals(0, capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT)
            assertTrue(capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT != 0)
            withContext(Dispatchers.Main) { service.beginSession() }
            delay(1200); assertEquals(0, server.requestCount) // Mainframe is never captured.
            device.pressHome()
            val request = withContext(Dispatchers.IO) { server.takeRequest(20, TimeUnit.SECONDS) }
            assertNotNull("A single still image must reach only the opted-in mock endpoint: ${app.contextual.state.value.status}", request)
            val messages = JSONObject(request!!.body.readUtf8()).getJSONArray("messages")
            val content = messages.getJSONObject(messages.length()-1).getJSONArray("content")
            assertTrue(content.getJSONObject(1).getJSONObject("image_url").getString("url").startsWith("data:image/jpeg;base64,/9j/"))
            withTimeout(5000) { while (app.contextual.snapshot.value == null) delay(25) }
            assertEquals("Imagen puntual de prueba", app.contextual.snapshot.value!!.result.observation)
            assertNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
            withContext(Dispatchers.Main) { service.endSession() }
            assertFalse(app.contextual.state.value.active); assertNull(app.contextual.snapshot.value)
            assertNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
            val permissions = app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
            assertFalse(permissions.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"))
        } finally {
            withContext(Dispatchers.Main) { ScreenshotService.stop(); app.settings.value = settings }
            if (previous == "null" || previous.isBlank()) device.executeShellCommand("settings delete secure enabled_accessibility_services")
            else device.executeShellCommand("settings put secure enabled_accessibility_services '${previous.replace("'", "")}'")
            device.executeShellCommand("settings put secure accessibility_enabled ${if (previousFlag == "1") 1 else 0}")
            server.shutdown(); activity.close()
            Configurator.getInstance().uiAutomationFlags = 0
        }
    }
}
