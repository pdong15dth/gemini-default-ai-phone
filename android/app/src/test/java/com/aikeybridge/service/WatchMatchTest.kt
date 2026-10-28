package com.aikeybridge.service

import com.aikeybridge.model.ShellWatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchMatchTest {

    @Test
    fun panelIsVoiceAndButtonIsButton() {
        val match = WatchMatch(ShellWatch("CopilotPanelWindow", "ACTION_BUTTON_TO_START_FUNCTIONS"))

        assertEquals(
            WatchMatch.Kind.VOICE,
            match.kindOf("I/input_focus( 1834): [Focus request 3f2a1 com.vivo.ai.copilot/CopilotPanelWindow,reason=...]"),
        )
        assertEquals(WatchMatch.Kind.BUTTON, match.kindOf("I/sysui_action( 2210): ACTION_BUTTON_TO_START_FUNCTIONS"))
        assertNull(match.kindOf("I/am_resume_activity( 1834): [0,1,2,com.aikeybridge/.MainActivity]"))
    }

    @Test
    fun alternativesUseLogcatRegexSyntax() {
        val match = WatchMatch(ShellWatch("com.heytap.speechassist|com.coloros.speechassist", null))

        assertEquals(WatchMatch.Kind.VOICE, match.kindOf("I/wm_on_create_called( 991): [0,com.coloros.speechassist.Panel]"))
        assertNull(match.kindOf("I/wm_on_create_called( 991): [0,com.android.settings.Settings]"))
    }

    @Test
    fun invalidRegexFallsBackToLiteralText() {
        val match = WatchMatch(ShellWatch("Panel[", null))

        assertEquals(WatchMatch.Kind.VOICE, match.kindOf("I/input_focus( 1): Panel[x]"))
    }
}
