package com.capri.companion

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.capri.companion.avatar.Pose
import com.capri.companion.data.CapriStore
import com.capri.companion.data.Message
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetsAndStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun originalSpriteStripsHaveCompleteFrames() {
        Pose.entries.forEach { pose ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open("sprites/${pose.asset}.png").use { BitmapFactory.decodeStream(it, null, options) }
            assertTrue("${pose.asset} must decode", options.outWidth > 0)
            assertEquals("${pose.asset} must have whole frames", 0, options.outWidth % pose.frames)
            assertEquals(384, options.outHeight)
        }
    }
    @Test fun keystoreRoundTripDoesNotPersistPlaintextKeys() {
        val store = CapriStore(context)
        val old = store.loadSettings()
        try {
            store.save(old.copy(apiKey = "instrumentation-secret", elevenKey = "voice-secret"))
            assertEquals("instrumentation-secret", store.loadSettings().apiKey)
            assertEquals("voice-secret", store.loadSettings().elevenKey)
            val raw = context.getSharedPreferences("capri", 0).all.toString()
            assertFalse(raw.contains("instrumentation-secret"))
            assertFalse(raw.contains("voice-secret"))
        } finally { store.save(old) }
    }
    @Test fun historyRoundTripsAndClears() {
        val store = CapriStore(context)
        val old = store.messages()
        try {
            store.saveMessages(listOf(Message("user", "¿Me escuchás?", 1), Message("assistant", "Sí, choom.", 2)))
            assertEquals("Sí, choom.", store.messages().last().text)
            store.saveMessages(emptyList())
            assertTrue(store.messages().isEmpty())
        } finally { store.saveMessages(old) }
    }
    @Test fun chatGptSessionIsEncryptedAndRestoredFromKeystore() {
        val store = CapriStore(context)
        val old = store.readAuth()!!
        try {
            val session = """{"host":"urn:uuid:test","accounts":[{"access_token":"oauth-access-fixture","refresh_token":"oauth-refresh-fixture"}]}"""
            store.writeAuth(session)
            assertEquals(session, CapriStore(context).readAuth())
            val raw = context.getSharedPreferences("capri", 0).all.toString()
            assertFalse(raw.contains("oauth-access-fixture"))
            assertFalse(raw.contains("oauth-refresh-fixture"))
        } finally { store.writeAuth(old) }
    }
}
