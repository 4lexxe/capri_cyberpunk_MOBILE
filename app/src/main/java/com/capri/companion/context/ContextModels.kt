package com.capri.companion.context

import org.json.JSONObject

data class ScreenObservation(val observation: String, val activity: String, val confidence: String,
    val sensitive: Boolean, val shouldSpeak: Boolean, val reaction: String) {
    fun json() = JSONObject().put("observation", observation).put("activity", activity).put("confidence", confidence)
        .put("sensitive", sensitive).put("shouldSpeak", shouldSpeak).put("reaction", reaction).toString()
    companion object {
        fun parse(raw: String): ScreenObservation {
            val plain = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val value = try { JSONObject(plain) } catch (_: Exception) { throw IllegalArgumentException("El análisis visual no devolvió un resumen válido.") }
            val confidence = value.optString("confidence", "low").lowercase()
            require(confidence in setOf("low", "medium", "high")) { "El análisis visual no indicó una confianza válida." }
            return ScreenObservation(value.getString("observation").take(500), value.getString("activity").take(120), confidence,
                value.optBoolean("sensitive", true), value.optBoolean("shouldSpeak", false), value.optString("reaction").take(250))
        }
    }
}

data class ContextSnapshot(val session: String, val capturedAt: Long, val result: ScreenObservation) {
    fun valid(now: Long) = now >= capturedAt && now - capturedAt <= 120_000 && !result.sensitive
    fun json() = JSONObject().put("observedAt", java.time.Instant.ofEpochMilli(capturedAt).toString())
        .put("expiresAfterSeconds", 120).put("screen", JSONObject(result.json())).toString()
}

data class ActivityMemory(val id: String, val at: Long, val activity: String, val summary: String)

class InitiativePolicy {
    private val spokenAt = ArrayDeque<Long>()
    private var lastSpoken = Long.MIN_VALUE
    private var lastReaction = ""

    fun canSpeak(snapshot: ContextSnapshot, now: Long, occupied: Boolean): Boolean {
        while (spokenAt.isNotEmpty() && now - spokenAt.first() >= 3_600_000) spokenAt.removeFirst()
        if (occupied || !snapshot.valid(now) || snapshot.result.confidence == "low" || snapshot.result.sensitive ||
            !snapshot.result.shouldSpeak || snapshot.result.reaction.isBlank()) return false
        if (lastSpoken != Long.MIN_VALUE && snapshot.capturedAt - lastSpoken < CaptureCadence.AUTOMATIC_INTERVAL_MS) return false
        if (spokenAt.size >= 20) return false
        if (snapshot.result.reaction.equals(lastReaction, true)) return false
        return true
    }
    fun record(snapshot: ContextSnapshot, now: Long) {
        lastSpoken = snapshot.capturedAt; spokenAt.addLast(now)
        lastReaction = snapshot.result.reaction
    }
}

object CaptureCadence {
    const val AUTOMATIC_INTERVAL_MS = 180_000L
    fun due(now: Long, last: Long, manual: Boolean = false): Boolean = last == 0L ||
        now - last >= if (manual) 10_000L else AUTOMATIC_INTERVAL_MS
}
