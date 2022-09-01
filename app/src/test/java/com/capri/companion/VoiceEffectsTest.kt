package com.capri.companion

import com.capri.companion.voice.QwenVoiceEffects
import com.capri.companion.overlay.OverlayTiming
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class VoiceEffectsTest {
    @Test fun disabledEffectPreservesPcmWithoutMutatingCache() {
        val original = floatArrayOf(0f, .3f, -.4f, 1f)
        val copy = original.copyOf()
        val result = QwenVoiceEffects.apply(original, 24000, 1f, 0f)
        assertArrayEquals(copy, result, 0f); assertArrayEquals(copy, original, 0f); assertNotSame(original, result)
    }
    @Test fun happyRateShortensAudioByConfiguredRatio() {
        val result = QwenVoiceEffects.apply(FloatArray(24000) { .5f }, 24000, 1.08f, .12f)
        assertEquals((24000 / 1.08f).toInt(), result.size)
        assertTrue(result.all { it.isFinite() && abs(it) <= 1f })
    }
    @Test fun roboticEffectChangesToneButRetainsSpeechEnergy() {
        val source = FloatArray(24000) { (.5 * sin(2 * PI * 440 * it / 24000)).toFloat() }
        val result = QwenVoiceEffects.apply(source, 24000, 1f, .12f)
        assertTrue(result.indices.any { abs(result[it] - source[it]) > .01 })
        val originalEnergy = source.sumOf { it.toDouble() * it }
        assertTrue(result.sumOf { it.toDouble() * it } / originalEnergy in .6..1.0)
        assertArrayEquals(result, QwenVoiceEffects.apply(source, 24000, 1f, .12f), 0f)
    }
    @Test fun silenceStaysSilentAndSettingsAreBounded() {
        assertTrue(QwenVoiceEffects.apply(FloatArray(1000), 24000, Float.NaN, Float.NaN).all { it == 0f })
        assertTrue(QwenVoiceEffects.apply(FloatArray(1000) { 2f }, 24000, 9f, 9f).all { abs(it) <= 1f })
        assertThrows(IllegalArgumentException::class.java) { QwenVoiceEffects.apply(floatArrayOf(Float.NaN), 24000, 1f, 0f) }
    }
    @Test fun balloonsAllowReadingWithoutStayingForever() {
        assertEquals(6500L, OverlayTiming.readingMs(10)); assertEquals(12000L, OverlayTiming.readingMs(200))
        assertEquals(20000L, OverlayTiming.readingMs(8000))
    }
}
