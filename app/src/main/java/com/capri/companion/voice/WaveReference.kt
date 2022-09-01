package com.capri.companion.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt

/** Converts bounded PCM/float WAV references to the native encoder's mono 24 kHz PCM16 input. */
object WaveReference {
    const val RATE = 24000
    const val MAX_BYTES = 30 * 1024 * 1024
    fun normalize(wav: ByteArray, maxSeconds: Int = 12): FloatArray {
        require(wav.size in 44..MAX_BYTES && String(wav, 0, 4) == "RIFF" && String(wav, 8, 4) == "WAVE") { "Elegí un archivo WAV PCM o float válido (hasta 30 MB)." }
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        var format = 0; var channels = 0; var rate = 0; var bits = 0
        var data = -1; var length = 0; var p = 12
        while (p + 8 <= wav.size) {
            val n = b.getInt(p + 4)
            require(n >= 0 && p + 8L + n <= wav.size) { "El WAV está incompleto." }
            when (String(wav, p, 4)) {
                "fmt " -> {
                    require(n >= 16)
                    format = b.getShort(p + 8).toInt() and 65535; channels = b.getShort(p + 10).toInt()
                    rate = b.getInt(p + 12); bits = b.getShort(p + 22).toInt()
                    if (format == 65534 && n >= 40) format = b.getShort(p + 32).toInt() and 65535
                }
                "data" -> if (data == -1) { data = p + 8; length = n }
            }
            p += 8 + n + (n and 1)
        }
        require(channels in 1..2 && rate in 8000..96000 && data >= 0 &&
            (format == 1 && bits in listOf(16, 24, 32) || format == 3 && bits == 32)) {
            "Usá WAV mono o estéreo PCM de 16/24/32 bits o float32, entre 8 y 96 kHz."
        }
        val stride = channels * (bits / 8)
        require(length % stride == 0)
        val frames = minOf(length / stride, rate * 30)
        require(frames >= rate * 2) { "La referencia necesita al menos 2 segundos de voz clara." }
        val mono = FloatArray(frames) { i ->
            var sum = 0f
            repeat(channels) { c ->
                val at = data + i * stride + c * (bits / 8)
                val value = when {
                    format == 3 -> b.getFloat(at)
                    bits == 16 -> b.getShort(at) / 32768f
                    bits == 32 -> b.getInt(at) / 2147483648f
                    else -> (((wav[at].toInt() and 255) or ((wav[at + 1].toInt() and 255) shl 8) or (wav[at + 2].toInt() shl 16))) / 8388608f
                }
                require(value.isFinite()) { "El WAV contiene muestras inválidas." }
                sum += value.coerceIn(-1f, 1f)
            }
            sum / channels
        }
        val peak = mono.maxOf { abs(it) }
        require(peak > 0.008f) { "No se detectó voz audible en el WAV." }
        val threshold = maxOf(0.005f, peak * .025f)
        val first = (mono.indexOfFirst { abs(it) > threshold } - rate / 10).coerceAtLeast(0)
        val last = (mono.indexOfLast { abs(it) > threshold } + rate / 10).coerceAtMost(frames)
        val end = minOf(last, first + rate * maxSeconds)
        require(end - first >= rate) { "La referencia tiene muy poca voz después del silencio." }
        val count = ((end - first).toLong() * RATE / rate).toInt()
        val gain = minOf(2f, .9f / peak)
        return FloatArray(count) { i ->
            val position = first + i.toDouble() * rate / RATE
            val left = position.toInt().coerceAtMost(end - 1)
            val fraction = (position - left).toFloat()
            val sample = (mono[left] * (1 - fraction) + mono[minOf(left + 1, end - 1)] * fraction) * gain
            val fade = minOf(1f, i / 240f, (count - 1 - i) / 240f)
            sample.coerceIn(-1f, 1f) * fade.coerceAtLeast(0f)
        }
    }

    fun encode(samples: FloatArray): ByteArray {
        val b = ByteBuffer.allocate(44 + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + samples.size * 2).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(samples.size * 2)
        samples.forEach { b.putShort((it.coerceIn(-1f, 1f) * 32767).roundToInt().toShort()) }
        return b.array()
    }
}
