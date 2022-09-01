package com.capri.companion.avatar

enum class Pose(val asset: String, val frames: Int, val fps: Int, val loops: Boolean = true) {
    IDLE("idle", 16, 8), BLINK("blink", 4, 8, false),
    BLINK_GLITCH("blink_glitch", 4, 8, false), GRIN("grin", 16, 8, false),
    GLITCH("glitch", 10, 15, false), DRAG("dangle", 24, 10),
    SLEEP("sleep", 16, 6), WAKE("wake", 13, 15, false),
    PERCH("perch", 16, 8), PERCH_BLINK("perch_blink", 4, 8, false),
    PERCH_BLINK_GLITCH("perch_blink_glitch", 4, 8, false),
    PERCH_GLITCH("perch_glitch", 10, 15, false),
    ARRIVE("arrive", 14, 15, false), LEAVE("leave", 12, 15, false),
    PERCH_ARRIVE("perch_arrive", 14, 15, false), PERCH_LEAVE("perch_leave", 12, 15, false),
    WALK("walk", 12, 12), DANCE("dance", 16, 8), PERCH_DANCE("perch_dance", 16, 8),
    MOUTH("mouth", 5, 12);

    fun frame(elapsedMs: Long): Int {
        val step = (elapsedMs.coerceAtLeast(0) * fps / 1000)
        return if (loops) (step % frames).toInt() else step.coerceAtMost(frames - 1L).toInt()
    }
    val durationMs: Long get() = frames * 1000L / fps
}

