package com.capri.companion

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.capri.companion.data.VoiceMode
import com.capri.companion.voice.VoiceController
import com.qwen.tts.studio.engine.QwenEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class QwenPlaybackTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CapriApplication
    private fun pcm() = FloatArray(24000) { (kotlin.math.sin(it * 2 * Math.PI * 220 / 24000) * .1).toFloat() }

    @Test fun slowProducerFinishesBeforeOneContinuousPlaybackStarts() = runBlocking {
        val produced = AtomicBoolean(false)
        val speaking = MutableStateFlow(false)
        val starts = AtomicInteger()
        var startedAt = 0L; var endedAt = 0L
        val errors = MutableStateFlow<String?>(null)
        val voice = withContext(Dispatchers.Main) {
            VoiceController(app, app.scope, { active ->
                if (active) { assertTrue(produced.get()); starts.incrementAndGet(); startedAt = android.os.SystemClock.elapsedRealtime() }
                else if (speaking.value) endedAt = android.os.SystemClock.elapsedRealtime()
                speaking.value = active
            }, { errors.value = it }, app.qwen, {}, makeQwenSpeech = {
                delay(250); delay(250); produced.set(true)
                QwenEngine.NativeResult(pcm(), 24000, true, null, 500)
            })
        }
        try {
            // Keep this duration test at its original 1x; the new happy-rate default is 1.08x.
            withContext(Dispatchers.Main) { voice.speak("Prueba de continuidad", app.settings.value.copy(voiceMode = VoiceMode.QWEN, qwenSpeed = 1f)) }
            delay(150); assertFalse(speaking.value); assertTrue(voice.preparing.value)
            withTimeout(6000) { while (endedAt == 0L && errors.value == null) delay(20) }
            assertNull(errors.value)
            assertEquals(1, starts.get())
            assertTrue("The full one-second clip must play without early release", endedAt - startedAt >= 900)
        } finally { withContext(Dispatchers.Main) { voice.stop() } }
    }

    @Test fun cancelledPreparationNeverStartsLatePlayback() = runBlocking {
        val starts = AtomicInteger()
        val speaking = MutableStateFlow(false)
        val voice = withContext(Dispatchers.Main) {
            VoiceController(app, app.scope, { speaking.value = it; if (it) starts.incrementAndGet() }, {}, app.qwen, {}, makeQwenSpeech = {
                withContext(NonCancellable) { delay(400); QwenEngine.NativeResult(pcm(), 24000, true, null, 400) }
            })
        }
        withContext(Dispatchers.Main) { voice.speak("No debe sonar después de detener", app.settings.value.copy(voiceMode = VoiceMode.QWEN)) }
        delay(80)
        withContext(Dispatchers.Main) { voice.stop() }
        delay(650)
        assertEquals(0, starts.get()); assertFalse(speaking.value); assertFalse(voice.preparing.value)
    }
}
