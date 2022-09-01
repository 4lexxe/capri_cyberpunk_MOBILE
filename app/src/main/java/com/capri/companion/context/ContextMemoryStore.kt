package com.capri.companion.context

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ContextMemoryStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "activity-memory.json"))
    @Synchronized fun load(now: Long = System.currentTimeMillis()): List<ActivityMemory> = runCatching {
        val rows = JSONArray(file.readFully().toString(Charsets.UTF_8))
        val entries = (0 until rows.length()).map { i -> rows.getJSONObject(i).let {
            ActivityMemory(it.getString("id"), it.getLong("at"), it.getString("activity"), it.getString("summary"))
        } }
        val recent = entries.filter { now - it.at in 0..RETENTION }.takeLast(150)
        if (recent.size != entries.size) save(recent, now)
        recent
    }.getOrDefault(emptyList())
    @Synchronized fun save(memories: List<ActivityMemory>, now: Long = System.currentTimeMillis()) {
        val rows = JSONArray().apply { memories.filter { now - it.at in 0..RETENTION }.takeLast(150).forEach {
            put(JSONObject().put("id", it.id).put("at", it.at).put("activity", it.activity).put("summary", it.summary))
        } }
        val stream = file.startWrite()
        try { stream.write(rows.toString().toByteArray()); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    companion object { const val RETENTION = 7 * 24 * 3_600_000L }
}
