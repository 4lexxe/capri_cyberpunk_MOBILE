package com.capri.companion

import com.capri.companion.avatar.Pose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationTest {
    @Test fun loopsIdleAndHoldsLastFrameForOneShot() {
        assertEquals(0, Pose.IDLE.frame(0))
        assertEquals(15, Pose.IDLE.frame(1999))
        assertEquals(0, Pose.IDLE.frame(2000))
        assertEquals(3, Pose.BLINK.frame(5000))
        assertEquals(0, Pose.IDLE.frame(-100))
    }
    @Test fun allAnimationsStayInsideTheirStrip() {
        Pose.entries.forEach { pose ->
            (0L..10_000L step 17).forEach { time -> assertTrue(pose.frame(time) in 0 until pose.frames) }
        }
    }
}
