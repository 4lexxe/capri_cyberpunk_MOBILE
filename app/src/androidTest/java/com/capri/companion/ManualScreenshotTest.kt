package com.capri.companion

import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.capri.companion.context.ManualCaptureActivity
import com.capri.companion.context.ManualScreenshotService
import com.capri.companion.data.AiProvider
import com.capri.companion.data.VoiceMode
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class ManualScreenshotTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as CapriApplication
    private val device get() = UiDevice.getInstance(instrumentation)

    @Test fun manifestHasNoAccessibilityService() {
        assumeTrue(BuildConfig.MANUAL_CAPTURE)
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SERVICES or PackageManager.GET_PERMISSIONS)
        assertFalse(info.services.orEmpty().any { it.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE" })
        assertTrue(info.requestedPermissions.orEmpty().contains("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"))
    }

    @Test fun cancelSystemConsentSendsNothing() = runBlocking {
        assumeTrue(BuildConfig.MANUAL_CAPTURE)
        val old = app.settings.value
        val server = MockWebServer().apply { start(java.net.InetAddress.getByName("127.0.0.1"), 0) }
        try {
            configure(server)
            launchConsent()
            device.pressBack()
            delay(700)
            assertFalse(ManualScreenshotService.running)
            assertFalse(app.hideOverlayForCapture.value)
            assertEquals(0, server.requestCount)
        } finally { withContext(Dispatchers.Main) { app.stopScreenContext(); app.settings.value = old }; server.shutdown() }
    }

    @Test fun oneImageClosesProjectionAndRequiresFreshConsent() = runBlocking {
        assumeTrue(BuildConfig.MANUAL_CAPTURE)
        val old = app.settings.value
        val server = MockWebServer().apply { start(java.net.InetAddress.getByName("127.0.0.1"), 0) }
        try {
            configure(server)
            val result = JSONObject().put("observation", "Captura manual de prueba").put("activity", "pantalla de prueba")
                .put("confidence", "high").put("sensitive", false).put("shouldSpeak", false).put("reaction", "").toString()
            server.enqueue(MockResponse().setBody(JSONObject().put("choices", org.json.JSONArray().put(JSONObject()
                .put("message", JSONObject().put("content", result)))).toString()))
            launchConsent()
            // API 35 AOSP offers a single-app/whole-screen selector. Choose the whole screen for this test.
            val spinner = device.wait(Until.findObject(By.res("com.android.systemui:id/screen_share_mode_spinner")), 1500)
                ?: device.findObject(By.text(Pattern.compile("(?i).*single app.*")))
            if (spinner != null) {
                spinner.click()
                val entire = device.wait(Until.findObject(By.text(Pattern.compile("(?i).*entire screen.*"))), 3000)
                assertNotNull("Whole-screen option missing", entire); entire!!.click()
            }
            val start = device.wait(Until.findObject(By.res("android:id/button1")), 5000)
                ?: device.findObject(By.text(Pattern.compile("(?i)start( now| recording| sharing)?")))
            assertNotNull("Android consent button missing", start); start!!.click()
            val request = withContext(Dispatchers.IO) { server.takeRequest(20, TimeUnit.SECONDS) }
            assertNotNull("No manual image: ${app.notice.value}; ${app.contextual.state.value.status}", request)
            val messages = JSONObject(request!!.body.readUtf8()).getJSONArray("messages")
            val parts = messages.getJSONObject(messages.length()-1).getJSONArray("content")
            val imageUrl = parts.getJSONObject(1).getJSONObject("image_url").getString("url")
            assertTrue(imageUrl.startsWith("data:image/jpeg;base64,/9j/"))
            val bytes = android.util.Base64.decode(imageUrl.substringAfter(','), android.util.Base64.DEFAULT)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertNotNull(bitmap); assertTrue(maxOf(bitmap.width, bitmap.height) <= 960)
            val colors = mutableSetOf<Int>()
            for (x in 1..8) for (y in 1..8) colors += bitmap.getPixel(bitmap.width*x/9, bitmap.height*y/9)
            assertTrue("Capture is a blank frame", colors.size > 4)
            java.io.File(app.getExternalFilesDir(null), "manual-capture-test.jpg").writeBytes(bytes)
            bitmap.recycle()
            withTimeout(5000) { while (ManualScreenshotService.running || app.contextual.snapshot.value == null) delay(50) }
            assertFalse(app.hideOverlayForCapture.value)
            val projections = device.executeShellCommand("dumpsys media_projection")
            assertTrue("Projection still active: $projections", projections.trim().endsWith("null"))
            assertNull(withContext(Dispatchers.IO) { server.takeRequest(3, TimeUnit.SECONDS) })
            // Asking again opens Android consent again, rather than reusing the token.
            launchConsent(); device.pressBack(); delay(500)
            assertEquals(1, server.requestCount)
        } finally { withContext(Dispatchers.Main) { app.stopScreenContext(); app.settings.value = old }; server.shutdown() }
    }
    private suspend fun configure(server: MockWebServer) = withContext(Dispatchers.Main) {
        app.settings.value = app.settings.value.copy(visionEnabled = true, visionUseBrain = true,
            provider = AiProvider.COMPATIBLE, endpoint = "http://127.0.0.1:${server.port}/v1", model = "test-vision",
            visionModel = "test-vision", apiKey = "", voiceMode = VoiceMode.OFF, autoComments = false)
    }
    private suspend fun launchConsent() {
        device.executeShellCommand("am start -W -n ${app.packageName}/.MainActivity")
        withContext(Dispatchers.Main) { ManualCaptureActivity.request(app) }
        val disclosure = device.wait(Until.findObject(By.text("Elegir pantalla")), 5000)
        assertNotNull("Capri disclosure missing: ${app.notice.value}", disclosure); disclosure!!.click()
        assertTrue("Android consent missing", device.wait(Until.hasObject(By.pkg("com.android.systemui")), 5000))
    }
}
