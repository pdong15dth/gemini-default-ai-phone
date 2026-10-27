package com.aikeybridge.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun secondsUseVietnameseDecimalComma() {
        assertEquals("1,1s", Format.seconds(1100))
        assertEquals("1,5s", Format.seconds(1460))
        assertEquals("0,0s", Format.seconds(0))
        assertEquals("—", Format.seconds(null))
    }

    @Test
    fun relativeTime() {
        assertEquals("vừa xong", Format.relative(1_000, 30_000))
        assertEquals("2 phút trước", Format.relative(0, 125_000))
        assertEquals("3 giờ trước", Format.relative(0, 3 * 3_600_000L + 5))
        assertEquals("2 ngày trước", Format.relative(0, 49 * 3_600_000L))
    }

    @Test
    fun shortensProvisioningCommands() {
        assertEquals(
            "appops vivo.agent" to "RUN_IN_BACKGROUND · allow",
            Format.shortCommand("cmd appops set com.vivo.agent RUN_IN_BACKGROUND allow"),
        )
        assertEquals(
            "secure assistant" to "→ Google",
            Format.shortCommand(
                "settings put secure assistant com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService",
            ),
        )
        assertEquals(
            "locale ai.copilot" to "en-US",
            Format.shortCommand("cmd locale set-app-locales com.vivo.ai.copilot --locales en-US"),
        )
        assertEquals(
            "role assistant" to "→ Google",
            Format.shortCommand(
                "cmd role remove-role-holder android.app.role.ASSISTANT com.google.android.googlequicksearchbox; " +
                    "cmd role add-role-holder android.app.role.ASSISTANT com.google.android.googlequicksearchbox",
            ),
        )
        assertEquals(
            "role assistant" to "bỏ Google",
            Format.shortCommand("cmd role remove-role-holder android.app.role.ASSISTANT com.google.android.googlequicksearchbox"),
        )
        assertEquals(
            "locale ai.copilot" to "xoá locale riêng",
            Format.shortCommand("cmd locale set-app-locales com.vivo.ai.copilot --locales \"\""),
        )
        assertEquals(
            "appops googlequicksearchbox" to "SYSTEM_ALERT_WINDOW · allow",
            Format.shortCommand("cmd appops set com.google.android.googlequicksearchbox SYSTEM_ALERT_WINDOW allow"),
        )
    }
}
