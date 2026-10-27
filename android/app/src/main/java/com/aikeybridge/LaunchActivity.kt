package com.aikeybridge

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import com.aikeybridge.data.ConfigSource
import com.aikeybridge.diagnostics.Attempt
import com.aikeybridge.diagnostics.AttemptLog
import com.aikeybridge.diagnostics.Diagnostics
import com.aikeybridge.service.AssistantAccessibilityService
import com.aikeybridge.service.AssistantLauncher

/**
 * Lối tắt "Gọi AI" để gán vào phím tắt của ROM, chạm mặt lưng hoặc màn hình chính.
 * Có Trợ năng: giao cho service (lớp phủ nếu có Shizuku, tự bật Micro).
 * Không có Trợ năng: mở thẳng app đích, người dùng tự bấm Micro.
 */
class LaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engine = AssistantAccessibilityService.activeEngine
        if (engine != null) {
            engine.launchFromShortcut()
        } else {
            Diagnostics.init(this)
            AttemptLog.init(this)
            val startedAt = SystemClock.elapsedRealtime()
            val config = runCatching { ConfigSource(this).current() }.getOrNull()
            if (config == null) {
                Diagnostics.error("shortcut", "Không tải được cấu hình.")
            } else {
                val outcome = AssistantLauncher.launch(this, config.target)
                Diagnostics.log(
                    if (outcome.ok) Diagnostics.Level.SUCCESS else Diagnostics.Level.ERROR,
                    "shortcut",
                    "Gọi AI (không Trợ năng): ${outcome.detail}",
                )
                AttemptLog.record(
                    Attempt(
                        at = System.currentTimeMillis(),
                        flow = "Lối tắt → ${config.target.displayName}",
                        ok = outcome.ok,
                        durationMs = SystemClock.elapsedRealtime() - startedAt,
                        detail = if (outcome.ok) "đã mở app · không Trợ năng" else outcome.detail,
                    ),
                )
            }
        }
        finish()
    }
}
