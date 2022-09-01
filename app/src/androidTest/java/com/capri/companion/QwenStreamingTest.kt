package com.capri.companion

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.capri.companion.data.VoiceMode
import com.capri.companion.voice.WaveReference
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class QwenStreamingTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as CapriApplication

    @Test fun importedWavIsLocalAndCanBeSelected() = runBlocking {
        val references = com.capri.companion.voice.VoiceReferences(app)
        val old = references.state.value.selected
        val custom = File(app.filesDir, "voice-references/custom.wav")
        val saved = custom.takeIf { it.isFile }?.readBytes()
        val input = File(app.cacheDir, "voice-import-fixture.wav")
        try {
            app.assets.open("voice/capri-latino-2.wav").use { source -> input.outputStream().use { source.copyTo(it) } }
            references.import(android.net.Uri.fromFile(input))
            assertEquals("custom", references.state.value.selected)
            assertTrue(references.state.value.customAvailable)
            assertTrue(references.reference().first.isFile)
        } finally {
            input.delete()
            if (saved == null) custom.delete() else custom.writeBytes(saved)
            references.select(old)
        }
    }

    @Test fun referenceClipsAreConvertedAndSelectedByContentHash() {
        val references = com.capri.companion.voice.VoiceReferences(app)
        val old = references.state.value.selected
        try {
            val hashes = mutableSetOf<String>()
            for (id in listOf("latino1", "latino2", "combined")) {
                references.select(id)
                val (file, hash) = references.reference()
                hashes.add(hash)
                val b = java.nio.ByteBuffer.wrap(file.readBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                assertEquals(24000, b.getInt(24)); assertEquals(1, b.getShort(22).toInt())
                assertTrue(file.length() in 48044..650000)
            }
            assertEquals(3, hashes.size)
        } finally { references.select(old) }
    }

    @Test fun pcmArrivesBeforeSynthesisFinishesAndMetricsAreMeasured() = runBlocking {
        assumeTrue("Requires downloaded GGUF models", app.qwen.installed())
        val text = "Hola, choom. ¿Qué estás mirando?"
        val start = android.os.SystemClock.elapsedRealtime()
        val batch = app.qwen.synthesize(text, 32)
        val batchWait = android.os.SystemClock.elapsedRealtime() - start
        assertTrue(batch.success)
        var chunks = 0; var samples = 0; var finished = false
        val audio = ArrayList<Float>()
        val metrics = app.qwen.stream(text, 32) { block, rate ->
            assertFalse(finished); assertEquals(24000, rate)
            assertTrue(block.isNotEmpty() && block.all { it.isFinite() })
            chunks++; samples += block.size; audio.addAll(block.toList()); true
        }
        finished = true
        assertTrue(chunks > 1); assertTrue(samples > 2400)
        assertTrue(metrics.firstAudioMs in 1 until metrics.generationMs)
        val report = JSONObject().put("text", text).put("batchWaitMs", batchWait).put("firstStreamAudioMs", metrics.firstAudioMs)
            .put("streamGenerationMs", metrics.generationMs).put("streamAudioMs", metrics.audioMs)
            .put("realTimeFactor", metrics.realTimeFactor).put("chunks", chunks)
        File(app.filesDir, "qwen-stream-benchmark.json").writeText(report.toString(2))
        File(app.filesDir, "qwen-stream-latino.wav").writeBytes(WaveReference.encode(audio.toFloatArray()))
        instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", report.toString()) })
    }

    @Test fun playbackStartsAfterGenerationAndStopDoesNotResumeLate() = runBlocking {
        assumeTrue("Requires downloaded GGUF models", app.qwen.installed())
        val errors = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
        val voice = withContext(Dispatchers.Main) {
            com.capri.companion.voice.VoiceController(app, app.scope, { app.speaking.value = it }, { errors.value = it }, app.qwen, {},
                makeQwenSpeech = { app.qwen.synthesize(it, 32) })
        }
        withContext(Dispatchers.Main) { voice.speak("Hola, choom.", app.settings.value.copy(voiceMode = VoiceMode.QWEN)) }
        withTimeout(120_000) { while (!app.speaking.value) delay(25) }
        assertFalse(app.qwen.state.value.working)
        assertNull(errors.value)
        withContext(Dispatchers.Main) { voice.stop() }
        assertFalse(app.speaking.value); assertFalse(voice.preparing.value)
        withTimeout(30_000) { while (app.qwen.state.value.working) delay(50) }
        delay(300)
        assertFalse(app.speaking.value); assertFalse(voice.preparing.value)
    }

    @Test fun repeatingTheSameSpeechReusesPcm() = runBlocking {
        assumeTrue("Requires downloaded GGUF models", app.qwen.installed())
        val text = "Hola, choom."
        val original = app.qwen.synthesize(text, 32)
        val repeated = app.qwen.synthesize(text, 32)
        assertSame(original.audio, repeated.audio)
        assertTrue(app.qwen.metrics.value.cached)
        assertTrue(app.qwen.metrics.value.audioMs > 0)
    }
}
