package com.capri.companion

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.capri.companion.context.ContextMemoryStore
import com.capri.companion.context.ActivityMemory
import com.qwen.tts.studio.engine.QwenEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContextFlowTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as CapriApplication

    @Test fun noSessionMeansNoContextOrFrames() {
        instrumentation.runOnMainSync {
            app.contextual.end()
            assertFalse(app.contextual.needsFrame(System.currentTimeMillis()))
            assertNull(app.contextual.contextForChat())
        }
    }
    @Test fun hiddenAndStoppedSessionsDiscardObservations() {
        instrumentation.runOnMainSync {
            app.contextual.begin()
            assertTrue(app.contextual.state.value.active)
            app.contextual.visibility(false)
            assertFalse(app.contextual.needsFrame(System.currentTimeMillis()))
            assertNull(app.contextual.contextForChat())
            app.contextual.end()
            assertFalse(app.contextual.state.value.active)
        }
    }
    @Test fun activityMemoryExpiresAndCanBeErased() {
        val store = ContextMemoryStore(app)
        val old = store.load()
        val now = System.currentTimeMillis()
        try {
            store.save(listOf(ActivityMemory("old", now - ContextMemoryStore.RETENTION - 1, "old", "expired"),
                ActivityMemory("new", now, "videos", "Se ven videos cortos")), now)
            assertEquals(listOf("new"), store.load(now).map { it.id })
            store.save(emptyList()); assertTrue(store.load().isEmpty())
        } finally { store.save(old) }
    }
    @Test fun nativeEngineLinksAndHandlesCancellationWithoutModels() {
        QwenEngine().use {
            assertFalse(it.getModelCapabilities()?.loaded == true)
            it.cancel(); it.resetCancel()
            assertTrue(it.setCpuThreads(2))
            assertTrue(it.getCompiledBackendMask() != 0)
        }
    }
}
