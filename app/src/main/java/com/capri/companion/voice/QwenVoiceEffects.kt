package com.capri.companion.voice

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.round

/** Returns new PCM so replaying cached Qwen audio never compounds the effect. */
object QwenVoiceEffects {
    fun apply(input: FloatArray, sampleRate: Int, speed: Float, robotic: Float): FloatArray {
        require(sampleRate in 8000..96000 && input.all { it.isFinite() })
        if (input.isEmpty()) return FloatArray(0)
        val tempo = speed.takeIf { it.isFinite() }?.coerceIn(1f, 1.2f) ?: 1.08f
        val wet = robotic.takeIf { it.isFinite() }?.coerceIn(0f, .35f) ?: .12f
        val count = maxOf(1, (input.size / tempo).toInt())
        return FloatArray(count) { index ->
            val position = index * tempo.toDouble()
            val left = position.toInt().coerceAtMost(input.lastIndex)
            val right = (left + 1).coerceAtMost(input.lastIndex)
            val dry = input[left] + (input[right] - input[left]) * (position - left)
            val metallic = dry * ((1 - wet) + wet * cos(2 * PI * 85 * index / sampleRate))
            val digital = if (wet > 0) metallic * .94 + round(metallic * 2048) / 2048 * .06 else metallic
            digital.toFloat().coerceIn(-1f, 1f)
        }
    }
}
