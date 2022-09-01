package com.capri.companion

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.capri.companion.data.CapriSettings
import com.capri.companion.data.CapriStore
import com.capri.companion.data.Message
import com.capri.companion.data.VoiceMode
import com.capri.companion.overlay.CompanionService
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class MinimalOverlayTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as CapriApplication
    private val device = UiDevice.getInstance(instrumentation)
    private fun avatar() = device.wait(Until.findObject(By.descStartsWith("Capri. Tocá")), 5000) ?: error("Capri no apareció")
    private fun bubble() = device.wait(Until.findObject(By.descStartsWith("Capri dice:")), 5000) ?: error("No apareció el globo")
    private suspend fun overlay(startY: Int = 700, test: suspend () -> Unit) {
        val settings = app.settings.value; val messages = app.messages.value
        val x = app.store.overlayX; val y = app.store.overlayY
        device.executeShellCommand("appops set ${app.packageName} SYSTEM_ALERT_WINDOW allow")
        val activity = ActivityScenario.launch(MainActivity::class.java)
        try {
            withContext(Dispatchers.Main) {
                app.cancel(); app.settings.value = settings.copy(seated = false, sizeDp = 160, voiceMode = VoiceMode.OFF)
                app.store.overlayX = 80; app.store.overlayY = startY
                app.startForegroundService(Intent(app, CompanionService::class.java))
            }
            withTimeout(10000) { while (!app.overlayRunning.value) delay(30) }
            device.pressHome(); delay(1300); test()
        } finally {
            app.stopService(Intent(app, CompanionService::class.java))
            withTimeout(10000) { while (app.overlayRunning.value) delay(30) }
            withContext(Dispatchers.Main) {
                app.voice.preparing.value = false; app.speaking.value = false
                app.settings.value = settings; app.messages.value = messages
            }
            withContext(Dispatchers.IO) { app.store.save(settings); app.store.overlayX = x; app.store.overlayY = y }
            activity.close()
            device.executeShellCommand("appops set ${app.packageName} SYSTEM_ALERT_WINDOW default")
        }
    }
    @Test fun messagesUseAttachedBalloonsAndControlsAreTransient() = runBlocking {
        overlay {
            val standing = avatar().visibleBounds
            assertFalse(device.hasObject(By.desc("Escribir a Capri")))
            withContext(Dispatchers.Main) {
                app.notice.value = "Ajustes guardados."
                app.messages.value = listOf(Message("assistant", "¡Qué onda, choom! Se me ocurrió una travesura. ¿Te cuento?"))
            }
            val response = bubble().visibleBounds
            assertFalse(device.hasObject(By.text("Ajustes guardados.")))
            assertEquals(standing, avatar().visibleBounds)
            val d = app.resources.displayMetrics.density
            assertTrue("El globo debe quedar junto a la cabeza", standing.top - response.bottom in (-8*d).toInt()..(16*d).toInt())
            device.takeScreenshot(File(app.getExternalFilesDir(null), "overlay-standing.png"))
            device.findObject(By.desc("Cerrar mensaje de Capri")).click()
            assertTrue(device.wait(Until.gone(By.descStartsWith("Capri dice:")), 2000))
            withContext(Dispatchers.Main) { app.messages.value = listOf(Message("assistant", "¡Aquí estoy! ¿Qué hacemos hoy?")) }
            bubble()

            avatar().click()
            val pencil = device.wait(Until.findObject(By.desc("Escribir a Capri")), 3000) ?: error("Falta lápiz")
            assertTrue(pencil.visibleBounds.height() <= 48*d)
            assertTrue(device.hasObject(By.desc("Hablar con Capri")))
            device.takeScreenshot(File(app.getExternalFilesDir(null), "overlay-actions.png"))
            device.findObject(By.desc("Más opciones de Capri")).click()
            val sit = device.wait(Until.findObject(By.text("Sentarse")), 3000) ?: error("Falta sentarse")
            sit.click()
            withTimeout(5000) { while (!app.settings.value.seated) delay(30) }; delay(400)
            withContext(Dispatchers.Main) { app.messages.value = listOf(Message("assistant", "¡Dale! Me quedo aquí cerquita, bien cómoda.")) }
            val seatedBubble = bubble().visibleBounds
            val seated = avatar().visibleBounds
            assertTrue(seated.height() < standing.height())
            assertTrue("No debe quedar un espacio grande al sentarse", seated.top - seatedBubble.bottom in (-8*d).toInt()..(16*d).toInt())
            device.takeScreenshot(File(app.getExternalFilesDir(null), "overlay-seated.png"))
            avatar().click(); assertNotNull(device.wait(Until.findObject(By.desc("Escribir a Capri")), 3000))
            assertTrue(device.wait(Until.gone(By.desc("Escribir a Capri")), 7000))
        }
    }
    @Test fun bubbleRemainsWhilePreparingVoiceAndThenDismisses() = runBlocking {
        overlay {
            withContext(Dispatchers.Main) {
                app.voice.preparing.value = true
                app.messages.value = listOf(Message("assistant", "Aquí estoy, choom."))
            }
            bubble(); delay(7200)
            assertTrue(device.hasObject(By.descStartsWith("Capri dice:")))
            withContext(Dispatchers.Main) { app.voice.preparing.value = false }
            assertTrue(device.wait(Until.gone(By.descStartsWith("Capri dice:")), 5000))
            assertNotNull(avatar())
        }
    }
    @Test fun legacyPersonalityMigratesAndCustomPersonalitySurvives() {
        val name = "test-style-${UUID.randomUUID()}"
        val wrapper = object : ContextWrapper(app) {
            override fun getSharedPreferences(key: String, mode: Int) = app.getSharedPreferences(name, Context.MODE_PRIVATE)
        }
        val prefs = app.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            prefs.edit().putString("personality", CapriSettings.LEGACY_PERSONALITY).commit()
            assertEquals(CapriSettings.DEFAULT_PERSONALITY, CapriStore(wrapper).loadSettings().personality)
            prefs.edit().putString("personality", "Personalidad personalizada").putFloat("qwenSpeed", 1.15f).putFloat("qwenRobotic", .2f).commit()
            val restored = CapriStore(wrapper).loadSettings()
            assertEquals("Personalidad personalizada", restored.personality)
            assertEquals(1.15f, restored.qwenSpeed, 0f); assertEquals(.2f, restored.qwenRobotic, 0f)
        } finally { app.deleteSharedPreferences(name) }
    }
    @Test fun menuAtTopOpensAwayFromCharacterAndDragStaysInScreen() = runBlocking {
        overlay(startY = 0) {
            avatar().longClick()
            val hide = device.wait(Until.findObject(By.text("Ocultar Capri")), 3000) ?: error("Falta ocultar")
            assertFalse("El menú no debe tapar a Capri", android.graphics.Rect.intersects(hide.visibleBounds, avatar().visibleBounds))
            device.takeScreenshot(File(app.getExternalFilesDir(null), "overlay-top-menu.png"))
            val bounds = avatar().visibleBounds
            device.swipe(bounds.centerX(), bounds.centerY(), device.displayWidth - 5, device.displayHeight / 2, 25)
            delay(500)
            val moved = avatar().visibleBounds
            assertTrue(moved.left >= 0 && moved.right <= device.displayWidth && moved.bottom <= device.displayHeight)
            assertFalse(device.hasObject(By.text("Ocultar Capri")))
        }
    }
}
