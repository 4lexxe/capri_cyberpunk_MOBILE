package com.capri.companion.voice

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class VoiceReferenceState(val selected: String = "combined", val customAvailable: Boolean = false,
    val label: String = "Capri latino · ambos audios")

class VoiceReferences(private val context: Context) {
    private val folder = File(context.filesDir, "voice-references").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("capri.voice.reference", Context.MODE_PRIVATE)
    private val custom = File(folder, "custom.wav")
    val state = MutableStateFlow(VoiceReferenceState())
    private var prepared: Pair<File, String>? = null
    init { select("combined") }

    @Synchronized fun select(id: String) {
        require(id in setOf("combined", "latino1", "latino2", "custom"))
        val effective = if (id == "custom" && !custom.isFile) "combined" else id
        check(prefs.edit().putString("selected", effective).commit())
        prepared = null
        state.value = VoiceReferenceState(effective, custom.isFile, when (effective) {
            "latino1" -> "Capri latino · audio 1"
            "latino2" -> "Capri latino · audio 2"
            "custom" -> "Mi voz WAV importada"
            else -> "Capri latino · ambos audios"
        })
    }

    suspend fun import(uri: Uri) = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: error("No se pudo abrir el audio.")
        val raw = input.use { source ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16384)
            while (true) {
                val count = source.read(buffer); if (count == -1) break
                require(output.size() + count <= WaveReference.MAX_BYTES) { "El WAV supera 30 MB." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val normalized = WaveReference.encode(WaveReference.normalize(raw))
        synchronized(this@VoiceReferences) {
            val atomic = AtomicFile(custom); val output = atomic.startWrite()
            try { output.write(normalized); atomic.finishWrite(output) }
            catch (e: Exception) { atomic.failWrite(output); throw e }
            select("custom")
        }
    }

    @Synchronized fun reference(): Pair<File, String> {
        prepared?.let { return it }
        val id = state.value.selected
        val bytes = if (id == "custom") custom.readBytes() else {
            fun clip(name: String, seconds: Int) = context.assets.open("voice/$name.wav").use { WaveReference.normalize(it.readBytes(), seconds) }
            val samples = when (id) {
                "latino1" -> clip("capri-latino-1", 12)
                "latino2" -> clip("capri-latino-2", 12)
                else -> clip("capri-latino-1", 6) + FloatArray(2400) + clip("capri-latino-2", 6)
            }
            WaveReference.encode(samples)
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(24)
        val file = File(folder, "reference-$hash.wav")
        if (!file.isFile) file.writeBytes(bytes)
        return (file to hash).also { prepared = it }
    }
}
