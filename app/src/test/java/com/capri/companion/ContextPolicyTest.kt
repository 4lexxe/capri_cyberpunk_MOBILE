package com.capri.companion

import com.capri.companion.context.ContextSnapshot
import com.capri.companion.context.CaptureCadence
import com.capri.companion.context.InitiativePolicy
import com.capri.companion.context.ScreenObservation
import org.junit.Assert.*
import org.junit.Test

class ContextPolicyTest {
    private val now = 1_000_000L
    private fun sample(at: Long = now, activity: String = "videos", reaction: String = "¿Qué mirás?") =
        ContextSnapshot("s", at, ScreenObservation("Se ve un gato", activity, "high", false, true, reaction))

    @Test fun expiresOldAndFutureObservations() {
        assertTrue(sample().valid(now))
        assertFalse(sample(now - 120_001).valid(now))
        assertFalse(sample(now + 1).valid(now))
    }
    @Test fun keepsSilentWhenBusyUncertainOrSensitive() {
        val policy = InitiativePolicy()
        assertFalse(policy.canSpeak(sample(), now, true))
        assertFalse(policy.canSpeak(sample().copy(result = sample().result.copy(confidence = "low")), now, false))
        assertFalse(policy.canSpeak(sample().copy(result = sample().result.copy(sensitive = true)), now, false))
    }
    @Test fun continuesEveryThreeMinutesWithoutRequiringAnAnswer() {
        val policy = InitiativePolicy()
        for (i in 0..3) {
            val shot = sample(now + i * 180_000L, "same-app", "Comentario $i")
            assertTrue(policy.canSpeak(shot, shot.capturedAt, false)); policy.record(shot, shot.capturedAt)
            assertFalse(policy.canSpeak(sample(shot.capturedAt + 100_000, reaction = "Antes de tiempo"), shot.capturedAt + 100_000, false))
        }
        assertFalse(policy.canSpeak(sample(now + 900_000, reaction = "Comentario 3"), now + 900_000, false))
    }
    @Test fun allowsTwentyCommentsPerHourAtThreeMinuteIntervals() {
        val policy = InitiativePolicy()
        for (i in 0..19) {
            val shot = sample(now + i * 180_000, "activity$i", "reaction$i")
            assertTrue(policy.canSpeak(shot, shot.capturedAt, false)); policy.record(shot, shot.capturedAt)
        }
        val extra = sample(now + 3_500_000, "next", "new")
        assertFalse(policy.canSpeak(extra, extra.capturedAt, false))
        assertTrue(policy.canSpeak(extra.copy(capturedAt = now + 3_600_000), now + 3_600_000, false))
    }
    @Test fun missingSensitiveFlagFailsClosedAndRejectsBadSchema() {
        val missing = ScreenObservation.parse("""{"observation":"x","activity":"videos","confidence":"high","shouldSpeak":true}""")
        assertTrue(missing.sensitive)
        assertThrows(IllegalArgumentException::class.java) { ScreenObservation.parse("not json") }
        assertThrows(IllegalArgumentException::class.java) { ScreenObservation.parse("""{"confidence":"certain"}""") }
    }
    @Test fun capturesEveryThreeMinutesAndManualLookKeepsATenSecondMinimum() {
        assertTrue(CaptureCadence.due(now, 0))
        assertFalse(CaptureCadence.due(now + 179_999, now))
        assertTrue(CaptureCadence.due(now + 180_000, now))
        assertFalse(CaptureCadence.due(now + 9_999, now, true))
        assertTrue(CaptureCadence.due(now + 10_000, now, true))
    }
    @Test fun varyingModelLatencyDoesNotSkipTheNextScheduledComment() {
        val policy = InitiativePolicy()
        val first = sample()
        policy.record(first, now + 10_000)
        val next = sample(now + 180_000, reaction = "Nuevo comentario")
        assertTrue(policy.canSpeak(next, now + 181_000, false))
    }
}
