package com.capri.companion

import com.capri.companion.brain.CapriPersona
import com.capri.companion.data.CapriSettings
import com.capri.companion.data.VoiceMode
import org.junit.Assert.*
import org.junit.Test

class ReleaseDefaultsTest {
    @Test fun releaseVoicePresetMatchesRequestedValues() {
        val settings = CapriSettings()
        assertEquals(VoiceMode.QWEN, settings.voiceMode)
        assertEquals(1.09f, settings.qwenSpeed, 0f)
        assertEquals(.182f, settings.qwenRobotic, .00001f)
        assertEquals(.73f, settings.volume, 0f)
        assertEquals("", settings.nickname)
        assertEquals(100, settings.sizeDp)
    }
    @Test fun nicknamesAreRemovedWithoutBreakingOrdinaryWords() {
        assertEquals("¡Hola!", CapriPersona.clean("¡Hola, choom!"))
        assertEquals("Vamos a comer chocolate.", CapriPersona.clean("Vamos a comer chocolate."))
        assertFalse(CapriPersona.clean("Chom, aquí estoy.").contains("Chom"))
    }
}
