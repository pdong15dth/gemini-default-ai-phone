package com.aikeybridge.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.aikeybridge.data.ConfigSource
import com.aikeybridge.data.HardwareMode
import com.aikeybridge.diagnostics.AttemptLog
import com.aikeybridge.diagnostics.Diagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class AssistantAccessibilityService : AccessibilityService() {

    private var engine: TriggerEngine? = null
    private var scope: CoroutineScope? = null
    private var watcher: LogcatWatcher? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Diagnostics.init(this)
        AttemptLog.init(this)
        val source = runCatching { ConfigSource(this) }
            .onFailure { Diagnostics.error("profile", it.message ?: it.toString()) }
            .getOrNull()
        if (source == null) {
            Diagnostics.error("service", "Không tải được profile — service sẽ không chuyển hướng.")
            RuntimeStatus.setConnected(false)
            return
        }
        val newEngine = TriggerEngine(this, source)
        engine = newEngine
        activeEngine = newEngine
        RuntimeStatus.setConnected(true)
        val config = source.current()
        Diagnostics.info(
            "service",
            "Đã kết nối · ${config.profile.displayName} → ${config.target.displayName} · ${config.triggers.size} trigger",
        )

        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = serviceScope
        val logWatcher = LogcatWatcher { line ->
            LogSniffer.observe(line)
            newEngine.onLogLine(line)
        }
        watcher = logWatcher
        serviceScope.launch {
            combine(source.settings.state, LogSniffer.state, LogStatus.grantTick) { settings, sniff, tick ->
                val wanted = (settings.redirectEnabled && settings.hardwareMode == HardwareMode.LOGCAT) || sniff.active
                wanted to tick
            }.distinctUntilChanged().collect { (wanted, _) ->
                when {
                    !wanted -> logWatcher.stop()
                    !LogStatus.hasReadLogs(this@AssistantAccessibilityService) -> {
                        logWatcher.stop()
                        LogStatus.set(LogAccess.NO_PERMISSION)
                        Diagnostics.warn("logcat", "Chế độ B cần quyền READ_LOGS — cấp qua Shizuku hoặc ADB.")
                    }
                    else -> {
                        logWatcher.start(serviceScope)
                        Diagnostics.info("logcat", "Bắt đầu đọc event log.")
                    }
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (Sniffer.isActive) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                Sniffer.observe(event.packageName?.toString(), event.className?.toString(), packageName)
            }
            return
        }
        engine?.onAccessibilityEvent(event)
    }

    override fun onInterrupt() {
        Diagnostics.warn("service", "onInterrupt")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        Diagnostics.warn("service", "Đã ngắt kết nối.")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        Diagnostics.warn("service", "Đã dừng.")
        super.onDestroy()
    }

    private fun shutdown() {
        watcher?.stop()
        watcher = null
        scope?.cancel()
        scope = null
        engine?.destroy()
        engine = null
        activeEngine = null
        RuntimeStatus.setConnected(false)
    }

    companion object {
        /** Engine của service đang chạy; lối tắt "Gọi AI" dùng để mở đích và bật Micro. */
        @Volatile
        var activeEngine: TriggerEngine? = null
            private set
    }
}
