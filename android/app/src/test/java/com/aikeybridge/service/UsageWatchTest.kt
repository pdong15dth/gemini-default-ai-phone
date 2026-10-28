package com.aikeybridge.service

import com.aikeybridge.model.UsageWatch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageWatchTest {

    // Đúng sự kiện thấy trên Vivo Android 17 mỗi lần hô "Hi Jovi" (dumpsys usagestats).
    private val vivo = UsageWatch("com.vivo.ai.copilot", Regex(".*\\.ForegroundService"))

    @Test
    fun matchesJoviForegroundServiceOnly() {
        assertTrue(vivo.matches("com.vivo.ai.copilot", "com.vivo.ai.copilot.base.tool.foreground.ForegroundService"))
        assertFalse(vivo.matches("com.vivo.ai.copilot", "com.vivo.ai.copilot.framework.wakeup.WakeupService"))
        assertFalse(vivo.matches("com.vivo.SmartKey", "com.vivo.SmartKey.actionbutton.ex.ActionButtonService"))
        assertFalse(vivo.matches("com.vivo.ai.copilot", null))
    }

    @Test
    fun noClassRegexMatchesAnyServiceOfPackage() {
        val any = UsageWatch("com.vivo.ai.copilot", null)

        assertTrue(any.matches("com.vivo.ai.copilot", null))
        assertFalse(any.matches("com.vivo.agent", "x"))
    }
}
