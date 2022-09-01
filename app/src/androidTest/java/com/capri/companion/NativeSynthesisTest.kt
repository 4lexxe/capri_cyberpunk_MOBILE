package com.capri.companion

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class NativeSynthesisTest {
    @Test fun cancelledGenerationDoesNotReturnLateAudio() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CapriApplication
        assumeTrue("Requires downloaded GGUF models", app.qwen.installed())
        val task = async(kotlinx.coroutines.Dispatchers.Default) { app.qwen.synthesize("Hola, choom. Quiero contarte algo largo para comprobar que puedo detenerme cuando me interrumpís.", 160) }
        withTimeout(60_000) {
            while (!app.qwen.state.value.status.startsWith("Generando") && !app.qwen.state.value.status.startsWith("Preparando la frase")) { if (task.isCompleted) task.await(); delay(50) }
        }
        app.qwen.cancel()
        withTimeout(30_000) { task.cancelAndJoin() }
        assertTrue(task.isCancelled)
        assertFalse(app.qwen.state.value.working)
    }
    @Test fun spanishSpeechIsGeneratedOnDevice() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CapriApplication
        assumeTrue("Requires downloaded GGUF models", app.qwen.installed())
        val result = app.qwen.synthesize("Hola, choom.", maxTokens = 32)
        assertTrue(result.success)
        val samples = result.audio!!
        assertTrue(samples.size > result.sampleRate / 10)
        assertTrue(samples.all { it.isFinite() })
        assertTrue(samples.any { kotlin.math.abs(it) > 0.001f })
        val pcm = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { pcm.putShort((it.coerceIn(-1f, 1f) * 32767).toInt().toShort()) }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + pcm.capacity()).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(result.sampleRate).putInt(result.sampleRate * 2).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(pcm.capacity()).array()
        File(app.filesDir, "qwen-test.wav").outputStream().use { it.write(header); it.write(pcm.array()) }
    }
}
