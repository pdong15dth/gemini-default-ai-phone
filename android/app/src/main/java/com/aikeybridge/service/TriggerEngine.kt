package com.aikeybridge.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.aikeybridge.TriggerActivity
import com.aikeybridge.data.ActiveConfig
import com.aikeybridge.data.ConfigSource
import com.aikeybridge.data.HardwareMode
import com.aikeybridge.data.UsageMode
import com.aikeybridge.data.PermissionsReader
import com.aikeybridge.diagnostics.Attempt
import com.aikeybridge.diagnostics.AttemptLog
import com.aikeybridge.diagnostics.Diagnostics
import com.aikeybridge.model.TriggerSpec
import com.aikeybridge.shell.ShizukuShell
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class TriggerEngine(
    private val service: AccessibilityService,
    private val source: ConfigSource,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val busy = AtomicBoolean(false)
    private val shell = ShizukuShell(service)
    private var lastFiredAt = 0L

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        val settings = source.settings.state.value
        // Chỉ chế độ Đầy đủ mới chặn khẩu lệnh; Script nền tự lo, Một lần thì để trợ lý gốc.
        if (!settings.redirectEnabled || settings.usageMode != UsageMode.FULL) return
        if (event.packageName?.toString() == service.packageName) return

        val config = source.current()
        if (config.triggers.isEmpty()) return

        val pkg = event.packageName?.toString()
        val cls = event.className?.toString()
        config.triggers.firstOrNull { it.matches(pkg, cls) }?.let { trigger ->
            fire(config, trigger, cls.orEmpty())
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED || cls == null) {
            val match = runCatching {
                service.windows.firstNotNullOfOrNull { window ->
                    val root = window.root ?: return@firstNotNullOfOrNull null
                    val rootPkg = root.packageName?.toString()
                    val rootCls = root.className?.toString()
                    config.triggers.firstOrNull { it.matches(rootPkg, rootCls) }?.let { it to rootCls.orEmpty() }
                }
            }.getOrNull()
            if (match != null) {
                fire(config, match.first, match.second)
            }
        }
    }

    fun destroy() {
        scope.cancel()
        shell.close()
    }

    /** Chế độ nút cứng B: một dòng logcat khớp mẫu của profile → mở AI, không cần Back. */
    fun onLogLine(line: String) {
        val settings = source.settings.state.value
        if (!settings.redirectEnabled || settings.usageMode != UsageMode.FULL) return
        if (settings.hardwareMode != HardwareMode.LOGCAT) return
        val config = source.current()
        val trigger = config.logTriggers.firstOrNull { it.pattern.containsMatchIn(line) } ?: return
        scope.launch { fire(config, "${trigger.label} · log", line.take(120), dismiss = false) }
    }

    /** Lối tắt "Gọi AI" (phím tắt ROM, chạm mặt lưng, icon): mở đích + bật Micro, không cần Back. */
    fun launchFromShortcut() {
        val config = source.current()
        fire(config, "Lối tắt Gọi AI", "shortcut", dismiss = false)
    }

    private fun fire(config: ActiveConfig, trigger: TriggerSpec, cls: String) =
        fire(config, trigger.label, "${trigger.packageName}/$cls", dismiss = true)

    private fun fire(config: ActiveConfig, label: String, detail: String, dismiss: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (config.dedupe && now - lastFiredAt < config.cooldownMs) return
        if (!busy.compareAndSet(false, true)) return
        lastFiredAt = now
        scope.launch {
            val outcome = try {
                Diagnostics.info("trigger", "$label · $detail")
                runFlow(config, dismiss)
            } catch (throwable: Throwable) {
                Diagnostics.error("flow", throwable.message ?: throwable.toString())
                RuntimeStatus.setState(RedirectState.FAILED)
                FlowOutcome(false, "lỗi: ${throwable.javaClass.simpleName}")
            } finally {
                busy.set(false)
            }
            AttemptLog.record(
                Attempt(
                    at = System.currentTimeMillis(),
                    flow = config.flowLabel,
                    ok = outcome.ok,
                    durationMs = SystemClock.elapsedRealtime() - now,
                    detail = outcome.detail,
                    micFailed = outcome.micFailed,
                ),
            )
        }
    }

    private data class FlowOutcome(val ok: Boolean, val detail: String, val micFailed: Boolean = false)

    private suspend fun runFlow(config: ActiveConfig, dismiss: Boolean): FlowOutcome {
        val target = config.target
        val app = source.settings.state.value
        val keyguard = service.getSystemService(KeyguardManager::class.java)
        val power = service.getSystemService(PowerManager::class.java)
        val locked = keyguard?.isKeyguardLocked == true
        if (locked && !app.lockScreen) {
            Diagnostics.warn("guard", "Máy đang khóa và chế độ màn hình khóa đang tắt.")
            RuntimeStatus.setState(RedirectState.FAILED)
            return FlowOutcome(false, "máy khóa · chế độ khóa tắt")
        }

        if (dismiss) {
            RuntimeStatus.setState(RedirectState.DISMISSING)
            val backed = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            Diagnostics.info("dismiss", if (backed) "Đã gửi Back" else "Back bị hệ thống từ chối")
            delay(config.dismissDelayMs)
        }

        RuntimeStatus.setState(RedirectState.LAUNCHING)
        val needsActivity = locked || power?.isInteractive == false
        // Lớp phủ nổi chỉ gọi được bằng phím trợ lý từ shell, và chỉ an toàn khi Google là trợ lý
        // mặc định — nếu không, phím đó sẽ mở lại trợ lý gốc.
        val overlayReady = target.shellKeyevent != null && shizukuGranted() &&
            (!target.overlayNeedsDefaultAssistant || PermissionsReader.googleAssistantDefault(service))
        var usedOverlay = overlayReady && tryShellVoice(config, wake = needsActivity)
        var launched = usedOverlay || launchDirect(target, needsActivity)
        if (!launched) {
            RuntimeStatus.setState(RedirectState.FAILED)
            return FlowOutcome(false, "không mở được ${target.displayName}")
        }

        RuntimeStatus.setState(RedirectState.WAITING)
        var appeared = waitForTarget(config, config.micTimeoutMs)
        if (!appeared && usedOverlay) {
            Diagnostics.warn("wait", "Lớp phủ không hiện — mở thẳng app ${target.displayName}.")
            RuntimeStatus.setState(RedirectState.LAUNCHING)
            usedOverlay = false
            launched = launchDirect(target, needsActivity)
            if (launched) appeared = waitForTarget(config, config.micTimeoutMs)
        }
        if (!appeared && !usedOverlay && shizukuGranted() && tryShellPackage(target, wake = needsActivity)) {
            Diagnostics.info("launch", "Mở ${target.displayName} qua quyền shell (vượt chặn chạy nền của ROM).")
            appeared = waitForTarget(config, config.micTimeoutMs)
        }

        if (!appeared) {
            val hint = if (config.profile.oem == "xiaomi") {
                " (kiểm tra Quyền khác → Mở cửa sổ mới khi chạy trong nền của AI Key Bridge)"
            } else {
                ""
            }
            Diagnostics.error("wait", "Không thấy cửa sổ ${target.displayName} sau ${config.micTimeoutMs}ms$hint.")
            RuntimeStatus.setState(RedirectState.FAILED)
            return FlowOutcome(false, "timeout ${config.micTimeoutMs}ms · không thấy ${target.displayName}")
        }

        Diagnostics.success("launch", "Đã thấy cửa sổ ${target.displayName}.")

        val voice = target.voiceUi
        val via = if (usedOverlay) "lớp phủ" else "app"
        if (!app.autoMic || voice == null) {
            RuntimeStatus.setState(RedirectState.DONE)
            return FlowOutcome(true, if (voice == null) "đã mở $via" else "$via · micro: tắt")
        }

        RuntimeStatus.setState(RedirectState.TAPPING)
        val tap = tapMicWithRetry(config)
        RuntimeStatus.setState(RedirectState.DONE)
        return if (tap.ok) {
            Diagnostics.success("mic", "Đã bật Micro · ${tap.label}.")
            FlowOutcome(true, "$via · micro: ${tap.label}")
        } else {
            Diagnostics.warn("mic", "Không bấm được Micro — người dùng cần chạm tay.")
            FlowOutcome(false, "$via · micro: ${tap.label}", micFailed = true)
        }
    }

    private fun launchDirect(target: com.aikeybridge.model.AssistantTarget, needsActivity: Boolean): Boolean {
        if (needsActivity) return launchTriggerActivity()
        val outcome = AssistantLauncher.launch(service, target)
        Diagnostics.log(
            if (outcome.ok) Diagnostics.Level.SUCCESS else Diagnostics.Level.ERROR,
            "launch",
            outcome.detail,
        )
        return outcome.ok
    }

    private fun launchTriggerActivity(): Boolean = try {
        val intent = Intent(service, TriggerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        service.startActivity(intent)
        Diagnostics.info("launch", "Mở TriggerActivity cho màn hình khóa / màn hình tắt.")
        true
    } catch (throwable: Throwable) {
        Diagnostics.error("launch", "Không mở được TriggerActivity: ${throwable.message}")
        false
    }

    private suspend fun waitForTarget(config: ActiveConfig, timeoutMs: Long): Boolean {
        val interval = config.target.voiceUi?.retryIntervalMs ?: DEFAULT_RETRY_MS
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (MicTapper.findTargetWindow(service, config.target.packages) != null) {
                return true
            }
            delay(interval)
        }
        return false
    }

    private fun shizukuGranted(): Boolean = ShizukuShell.isEngineOrShizukuReady()

    private suspend fun tryShellVoice(config: ActiveConfig, wake: Boolean): Boolean {
        val keyevent = config.target.shellKeyevent ?: return false
        if (!shizukuGranted()) return false
        if (wake) {
            shell.exec("input keyevent 224")
            delay(350)
        }
        val result = shell.exec("input keyevent $keyevent")
        if (!result.ok) {
            Diagnostics.warn("launch", "keyevent $keyevent lỗi: ${result.output}")
        }
        return result.ok
    }

    private suspend fun tryShellPackage(target: com.aikeybridge.model.AssistantTarget, wake: Boolean): Boolean {
        if (!shizukuGranted()) return false
        for (pkg in target.packages) {
            val component = service.packageManager.getLaunchIntentForPackage(pkg)?.component?.flattenToShortString()
                ?: continue
            if (wake) {
                shell.exec("input keyevent 224")
                delay(250)
            }
            val result = shell.exec(
                "am start --user 0 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $component",
            )
            if (result.ok) return true
        }
        return false
    }

    private suspend fun tapMicWithRetry(config: ActiveConfig): TapResult {
        val voice = config.target.voiceUi ?: return TapResult.NONE
        repeat(MAX_MIC_ATTEMPTS) {
            val window = MicTapper.findTargetWindow(service, config.target.packages)
            if (window != null) {
                val result = MicTapper.tap(service, window, voice)
                if (result.ok) return result
            }
            delay(MIC_RETRY_DELAY_MS)
        }
        return TapResult.NONE
    }

    private companion object {
        const val MAX_MIC_ATTEMPTS = 6
        const val MIC_RETRY_DELAY_MS = 220L
        const val DEFAULT_RETRY_MS = 150L
    }
}
