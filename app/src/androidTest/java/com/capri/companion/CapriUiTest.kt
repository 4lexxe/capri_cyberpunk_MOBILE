package com.capri.companion

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.capri.companion.overlay.CompanionService
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapriUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun manualEditionShowsCaptureActionWithoutAutomaticControls() {
        org.junit.Assume.assumeTrue(BuildConfig.MANUAL_CAPTURE)
        compose.onNodeWithTag("tab-3").performClick()
        compose.onNodeWithText("TOMAR CAPTURA").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("ACTIVAR CAPTURAS PUNTUALES").assertDoesNotExist()
        compose.onNodeWithText("Comentarios cada 3 minutos").assertDoesNotExist()
    }

    @Test fun galleryShowsOriginalAnimationMetadata() {
        compose.onNodeWithTag("tab-2").performClick()
        compose.onNodeWithText("ARCHIVO VISUAL >").assertIsDisplayed()
        compose.onNodeWithText("Glitch").performScrollTo().performClick()
        compose.onNodeWithText("GLITCH / 10 CUADROS / 15 FPS").assertIsDisplayed()
    }

    @Test fun chatGptAccountChoiceDoesNotRequireAnApiKey() {
        compose.onNodeWithText("ChatGPT · cuenta").performScrollTo().performClick()
        compose.onNodeWithTag("chatgpt-sign-in").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Clave API").assertDoesNotExist()
        compose.onNodeWithText("URL del servidor").assertDoesNotExist()
        compose.onNodeWithTag("chatgpt-model").performScrollTo().assertIsDisplayed()
    }
    @Test fun qwenShowsConfiguredVoiceAndRequestedPreset() {
        compose.onNodeWithText("QWEN", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Qwen · voz de Capri en español latino").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("CARGAR MI VOZ WAV").assertDoesNotExist()
        compose.onNodeWithText("ELEVENLABS").assertDoesNotExist()
        compose.onNodeWithText("109%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("52%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("73%").performScrollTo().assertIsDisplayed()
    }

    @Test fun conversationExplainsMissingBrainInsteadOfInventingReply() {
        compose.onNodeWithTag("tab-1").performClick()
        compose.onNodeWithText("CANAL / CAPRI").assertIsDisplayed()
        compose.onNodeWithText("¿QUIÉN SOS?").performClick()
        compose.onNodeWithText("Primero conectá un modelo en Mainframe → Cerebro.").assertIsDisplayed()
    }

    @Test fun overlayStartsAndStopsWithoutMicrophonePermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        try {
            compose.activityRule.scenario.onActivity { it.startForegroundService(Intent(it, CompanionService::class.java)) }
            compose.waitUntil(10_000) { (context.applicationContext as CapriApplication).overlayRunning.value }
            compose.activityRule.scenario.onActivity { it.stopService(Intent(it, CompanionService::class.java)) }
            compose.waitUntil(10_000) { !(context.applicationContext as CapriApplication).overlayRunning.value }
        } finally {
            context.stopService(Intent(context, CompanionService::class.java))
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW default")
        }
    }
    private fun shell(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ).use { it.readBytes() }
    }
}
