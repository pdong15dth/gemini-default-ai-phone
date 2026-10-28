package com.aikeybridge.service

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.aikeybridge.model.UsageWatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Quyền "Truy cập dữ liệu sử dụng". Vivo không cho shell bật hộ app cài ngoài: người dùng bật tay trong Cài đặt. */
object UsageAccess {
    fun granted(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }
}

/**
 * Hỏi thống kê sử dụng mỗi [POLL_MS]: trợ lý gốc bật foreground service khớp [UsageWatch] là vừa hiện cửa sổ.
 * Thay cho đọc event log trên ROM không cho app đọc log hệ thống.
 */
class UsageWatcher(
    private val context: Context,
    private val watch: () -> UsageWatch?,
    private val onTrigger: () -> Unit,
) {

    private var job: Job? = null

    val running: Boolean get() = job?.isActive == true

    fun start(scope: CoroutineScope) {
        if (running) return
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return
        job = scope.launch(Dispatchers.IO) {
            // Bỏ qua lịch sử: chỉ tính sự kiện xảy ra sau lúc bắt đầu nghe.
            var handledUntil = System.currentTimeMillis()
            val event = UsageEvents.Event()
            while (isActive) {
                delay(POLL_MS)
                val current = watch() ?: continue
                val now = System.currentTimeMillis()
                // Hỏi lùi một đoạn để không lỡ sự kiện ghi trễ so với lúc hỏi; [handledUntil] chặn đếm hai lần.
                val events = runCatching { manager.queryEvents(now - OVERLAP_MS, now + 1) }.getOrNull() ?: continue
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType != UsageEvents.Event.FOREGROUND_SERVICE_START) continue
                    if (event.timeStamp <= handledUntil) continue
                    handledUntil = event.timeStamp
                    if (current.matches(event.packageName, event.className)) onTrigger()
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val POLL_MS = 300L
        const val OVERLAP_MS = 3_000L
    }
}
