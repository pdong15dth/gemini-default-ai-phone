package com.aikeybridge

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.aikeybridge.data.ConfigSource
import com.aikeybridge.diagnostics.Diagnostics
import com.aikeybridge.service.AssistOverlay
import com.aikeybridge.service.AssistantLauncher

/**
 * Activity trong suốt mở từ nền (nghe nền không có Shizuku, hoặc màn hình tắt/khoá): lấy focus làm cửa sổ
 * trợ lý gốc tự đóng (Tiểu V đóng khi mất focus), rồi mới gọi lớp phủ trợ lý mặc định.
 */
class TriggerActivity : Activity() {

    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        // Không nhận được focus (ROM giữ focus ở cửa sổ khác) thì vẫn gọi sau một lúc.
        window.decorView.postDelayed(::launchTarget, FOCUS_TIMEOUT_MS)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) launchTarget()
    }

    private fun launchTarget() {
        if (launched) return
        launched = true
        val config = runCatching { ConfigSource(this).current() }.getOrNull()
        if (config == null) {
            Diagnostics.error("lock", "TriggerActivity không tải được profile.")
        } else {
            // Như giữ nút nguồn: lớp phủ trợ lý mặc định; không được thì mở app đích.
            val assist = AssistOverlay.launch(this)
            val outcome = if (assist.ok) assist else AssistantLauncher.launch(this, config.target)
            Diagnostics.log(
                if (outcome.ok) Diagnostics.Level.SUCCESS else Diagnostics.Level.ERROR,
                "lock",
                "TriggerActivity: ${outcome.detail}",
            )
        }
        window.decorView.postDelayed({ finish() }, FINISH_DELAY_MS)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        finish()
    }

    private companion object {
        const val FOCUS_TIMEOUT_MS = 600L
        const val FINISH_DELAY_MS = 250L
    }
}
