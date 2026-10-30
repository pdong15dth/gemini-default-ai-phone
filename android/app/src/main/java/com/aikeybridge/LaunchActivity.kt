package com.aikeybridge

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import com.aikeybridge.data.ActionMode
import com.aikeybridge.data.ConfigSource
import com.aikeybridge.diagnostics.Attempt
import com.aikeybridge.diagnostics.AttemptLog
import com.aikeybridge.diagnostics.Diagnostics
import com.aikeybridge.service.AssistantAccessibilityService
import com.aikeybridge.service.AssistOverlay
import com.aikeybridge.service.AssistantLauncher

/**
 * Lối tắt "Gọi AI" / "Circle to Search" để gán vào phím tắt của ROM, chạm mặt lưng hoặc màn hình chính.
 * Có Trợ năng: giao cho service (lớp phủ nếu có Shizuku, tự bật Micro).
 * Không có Trợ năng: gọi lớp phủ trợ lý mặc định hoặc Google Circle to Search; không được thì mở thẳng app đích.
 */
class LaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)

        val extraMode = intent?.getStringExtra(EXTRA_ACTION_MODE)
            ?.let { name -> ActionMode.entries.firstOrNull { it.name == name } }
            ?: if (intent?.action == ACTION_CTS) ActionMode.GOOGLE_CTS else null
        val fromScript = intent?.getBooleanExtra(EXTRA_FROM_SCRIPT, false) == true
        val config = runCatching { ConfigSource(this).current() }.getOrNull()
        val mode = extraMode ?: config?.shortcutAction ?: ActionMode.DEFAULT

        val engine = AssistantAccessibilityService.activeEngine
        if (engine != null && !fromScript && (mode == ActionMode.DEFAULT || mode == ActionMode.GEMINI_VOICE)) {
            engine.launchFromShortcut(mode)
        } else {
            Diagnostics.init(this)
            AttemptLog.init(this)
            val startedAt = SystemClock.elapsedRealtime()
            val (outcome, flowName) = when (mode) {
                ActionMode.GOOGLE_CTS -> {
                    val cts = AssistOverlay.launchCircleToSearch(this)
                    val res = if (cts.ok) cts else AssistOverlay.launch(this)
                    res to "Google · Circle to Search"
                }
                ActionMode.GOOGLE_APP -> {
                    val appRes = AssistantLauncher.launchPackage(this, AssistantLauncher.GOOGLE_PACKAGE, "Google")
                    appRes to "Google"
                }
                ActionMode.GEMINI_SCREEN -> {
                    val assist = AssistOverlay.launch(this)
                    val res = if (assist.ok || config == null) assist else AssistantLauncher.launch(this, config.target)
                    res to "Gemini · Hỏi màn hình"
                }
                ActionMode.GEMINI_VOICE, ActionMode.DEFAULT -> {
                    if (config == null) {
                        val assist = AssistOverlay.launch(this)
                        assist to "Gemini"
                    } else {
                        val assist = AssistOverlay.launch(this)
                        val res = if (assist.ok) assist else AssistantLauncher.launch(this, config.target)
                        res to config.target.displayName
                    }
                }
            }
            Diagnostics.log(
                if (outcome.ok) Diagnostics.Level.SUCCESS else Diagnostics.Level.ERROR,
                "shortcut",
                "Gọi AI ($flowName): ${outcome.detail}",
            )
            if (!fromScript) {
                AttemptLog.record(
                    Attempt(
                        at = System.currentTimeMillis(),
                        flow = "Lối tắt → $flowName",
                        ok = outcome.ok,
                        durationMs = SystemClock.elapsedRealtime() - startedAt,
                        detail = outcome.detail,
                    ),
                )
            }
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_ACTION_MODE = "action_mode"
        const val EXTRA_FROM_SCRIPT = "from_script"
        const val ACTION_CTS = "com.aikeybridge.action.CTS"
    }
}
