package com.capri.companion

import com.capri.companion.voice.WaveReference
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

class WaveReferenceTest {
    @Test fun stereo44100IsResampledToMono24000AndCapped() {
        val rate = 44100; val count = rate * 4
        val bytes = ByteBuffer.allocate(44 + count * 4).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + count * 4).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(2).putInt(rate).putInt(rate * 4).putShort(4).putShort(16).put("data".toByteArray()).putInt(count * 4)
        repeat(count) { i -> val value = (sin(2 * Math.PI * i * 220 / rate) * 12000).toInt().toShort(); bytes.putShort(value).putShort(value) }
        val normalized = WaveReference.normalize(bytes.array(), 3)
        assertEquals(72000, normalized.size)
        assertTrue(normalized.all { it.isFinite() && it in -1f..1f })
        assertTrue(normalized.any { kotlin.math.abs(it) > .1f })
        val wav = WaveReference.encode(normalized)
        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(24000, header.getInt(24)); assertEquals(1, header.getShort(22).toInt())
        assertEquals(normalized.size, WaveReference.normalize(wav).size)
    }
    @Test fun rejectsSilenceMalformedUnsupportedAndTooShortWavs() {
        val silent = WaveReference.encode(FloatArray(48000))
        assertTrue(runCatching { WaveReference.normalize(silent) }.isFailure)
        assertTrue(runCatching { WaveReference.normalize(byteArrayOf(1, 2, 3)) }.isFailure)
        val short = WaveReference.encode(FloatArray(12000) { .2f })
        assertTrue(runCatching { WaveReference.normalize(short) }.isFailure)
        val truncated = silent.copyOf(silent.size - 1)
        assertTrue(runCatching { WaveReference.normalize(truncated) }.isFailure)
        val unsupported = silent.clone().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putShort(20, 7) }
        assertTrue(runCatching { WaveReference.normalize(unsupported) }.isFailure)
    }
}
