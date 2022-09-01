package com.qwen.tts.studio.engine

internal object CpuFeatures {
    init { System.loadLibrary("capri_cpu_features") }
    external fun supportsFastArm(): Boolean
    val optimized: Boolean by lazy {
        val fast = supportsFastArm() && runCatching { System.loadLibrary("qwen3_tts_jni_fast") }.isSuccess
        if (!fast) System.loadLibrary("qwen3_tts_jni")
        fast
    }
}
