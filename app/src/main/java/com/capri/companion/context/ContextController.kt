package com.capri.companion.context

import com.capri.companion.CapriApplication
import com.capri.companion.brain.BrainClient
import com.capri.companion.data.CapriSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class ObservationState(val active: Boolean = false, val visible: Boolean = false,
    val analyzing: Boolean = false, val status: String = "Observación apagada", val lastAnalyzedAt: Long = 0)

class ContextController(private val app: CapriApplication) {
    val state = MutableStateFlow(ObservationState())
    val snapshot = MutableStateFlow<ContextSnapshot?>(null)
    val memories = MutableStateFlow<List<ActivityMemory>>(emptyList())
    private val memoryStore = ContextMemoryStore(app)
    private val policy = InitiativePolicy()
    private var session: String? = null
    private var task: Job? = null
    private var lastAttempt = 0L
    @Volatile private var memoryRevision = 0L
    private var generation = 0L
    private var manual = false

    init { refreshMemories() }
    fun refreshMemories() { app.scope.launch {
        val revision = memoryRevision
        val loaded = withContext(Dispatchers.IO) { memoryStore.load() }
        if (revision == memoryRevision) memories.value = loaded
    } }

    fun begin() {
        end()
        session = UUID.randomUUID().toString()
        state.value = ObservationState(active = true, visible = true, status = "Esperando contenido compartido")
    }
    fun beginManual() {
        begin(); manual = true
        state.value = state.value.copy(status = "Obteniendo una captura manual")
    }
    fun end() {
        session = null; generation++; task?.cancel(); task = null
        snapshot.value = null; manual = false; lastAttempt = 0
        state.value = ObservationState()
        app.cancelInitiative()
    }
    fun visibility(visible: Boolean) {
        if (!state.value.active) return
        if (!visible) {
            generation++; task?.cancel(); task = null; snapshot.value = null; app.cancelInitiative()
        }
        state.value = state.value.copy(visible = visible, analyzing = false,
            status = if (visible) "Observando la app compartida" else "En pausa: contenido no visible")
    }
    fun lookNow() {
        if (com.capri.companion.BuildConfig.MANUAL_CAPTURE) { ManualCaptureActivity.request(app); return }
        if (!state.value.active) { app.notice.value = "Primero compartí una app con Capri desde Entorno."; return }
        if (!state.value.visible) { app.notice.value = "La app compartida no está visible."; return }
        if (!CaptureCadence.due(System.currentTimeMillis(), lastAttempt, true)) { app.notice.value = "Espera unos segundos antes de pedir otra captura."; return }
        manual = true
        state.value = state.value.copy(status = "Voy a mirar el próximo cuadro")
        ScreenshotService.instance?.captureNow()
    }
    fun captureFailed(message: String) { if (state.value.active) state.value = state.value.copy(status = message, analyzing = false) }
    fun userInteracted() { app.cancelInitiative() }
    fun contextForChat(): String? = snapshot.value?.takeIf { state.value.active && state.value.visible && it.valid(System.currentTimeMillis()) }?.json()
    fun memoryForChat(): String? {
        if (!app.settings.value.rememberActivities) return null
        val now = System.currentTimeMillis()
        val recent = memories.value.filter { now - it.at in 0..ContextMemoryStore.RETENTION }.takeLast(5)
        if (recent.isEmpty()) return null
        return org.json.JSONArray().apply { recent.forEach {
            put(org.json.JSONObject().put("observedAt", java.time.Instant.ofEpochMilli(it.at).toString())
                .put("activity", it.activity).put("summary", it.summary))
        } }.toString()
    }

    fun needsFrame(now: Long): Boolean = state.value.active && state.value.visible && task?.isActive != true &&
        CaptureCadence.due(now, lastAttempt, manual)

    fun acceptFrame(jpeg: ByteArray, capturedAt: Long) {
        if (!needsFrame(capturedAt)) return
        val currentSession = session ?: return
        val requested = manual; manual = false
        lastAttempt = capturedAt
        val token = ++generation
        val config = app.settings.value
        state.value = state.value.copy(analyzing = true, status = "Interpretando el cuadro compartido")
        app.avatarCue.tryEmit(com.capri.companion.avatar.Pose.BLINK_GLITCH)
        task = app.scope.launch {
            try {
                val result = app.brain.observe(config, jpeg)
                if (session != currentSession || token != generation || !state.value.visible) return@launch
                if (connectionSignature(config) != connectionSignature(app.settings.value)) return@launch
                val now = System.currentTimeMillis()
                val current = ContextSnapshot(currentSession, capturedAt, result)
                if (!current.valid(now)) {
                    snapshot.value = null
                    state.value = state.value.copy(status = if (result.sensitive) "Sin comentarios: contenido privado o sensible" else "El cuadro ya no está vigente")
                    return@launch
                }
                snapshot.value = current
                state.value = state.value.copy(status = "Contexto actualizado", lastAnalyzedAt = now)
                if (config.rememberActivities && result.confidence != "low") remember(current)
                val occupied = app.busy.value || app.speaking.value || app.voice.preparing.value || app.listening.value || app.composing.value
                if (requested && !occupied && result.reaction.isNotBlank()) app.sayContext(current)
                else if (app.settings.value.autoComments && policy.canSpeak(current, now, occupied)) {
                    if (app.sayContext(current)) policy.record(current, now)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (session == currentSession && token == generation) {
                    snapshot.value = null
                    state.value = state.value.copy(status = e.message ?: "No se pudo analizar la pantalla")
                    if (requested) app.notice.value = state.value.status
                }
            } finally {
                if (session == currentSession && token == generation) state.value = state.value.copy(analyzing = false)
            }
        }
    }

    private suspend fun remember(current: ContextSnapshot) {
        if (!app.settings.value.rememberActivities) return
        val previous = memories.value.lastOrNull()
        if (previous?.activity == current.result.activity && current.capturedAt - previous.at < 300_000) return
        val revision = memoryRevision
        val entry = ActivityMemory(UUID.randomUUID().toString(), current.capturedAt, current.result.activity, current.result.observation)
        val updated = (memories.value + entry).filter { System.currentTimeMillis() - it.at in 0..ContextMemoryStore.RETENTION }.takeLast(150)
        withContext(Dispatchers.IO) {
            synchronized(memoryStore) { if (revision == memoryRevision) memoryStore.save(updated) }
        }
        if (revision == memoryRevision) memories.value = updated
    }

    fun eraseMemories() {
        memoryRevision++
        memories.value = emptyList()
        app.scope.launch(Dispatchers.IO) {
            memoryStore.save(emptyList())
        }
    }
    companion object {
        fun connectionSignature(settings: CapriSettings): String = listOf(settings.visionEnabled, settings.visionUseBrain,
            if (settings.visionUseBrain) settings.provider else settings.visionProvider,
            if (settings.visionUseBrain) settings.endpoint else settings.visionEndpoint,
            if (settings.visionUseBrain) settings.apiKey else settings.visionApiKey,
            settings.visionModel.ifBlank { if (settings.visionUseBrain) settings.model else "" }).joinToString("\u0000")
    }
}

