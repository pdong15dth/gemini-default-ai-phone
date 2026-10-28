package com.aikeybridge.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class LogAccess {
    /** Chưa chạy (chế độ A, hoặc service chưa bật). */
    OFF,

    /** Chưa được cấp READ_LOGS. */
    NO_PERMISSION,

    /** Đang chạy nhưng chưa thấy dòng log nào của tiến trình khác. */
    WAITING,

    /** Chỉ đọc được log của chính app — Android chặn hoặc người dùng từ chối hộp thoại truy cập log. */
    OWN_ONLY,

    /** Đọc được log hệ thống. */
    SYSTEM,
}

object LogStatus {
    private val _access = MutableStateFlow(LogAccess.OFF)
    val access: StateFlow<LogAccess> = _access.asStateFlow()

    fun set(value: LogAccess) {
        _access.value = value
    }

    private val _grantTick = MutableStateFlow(0)

    /** Tăng mỗi khi quyền READ_LOGS có thể vừa đổi, để service kiểm tra lại. */
    val grantTick: StateFlow<Int> = _grantTick.asStateFlow()

    fun permissionChanged() {
        _grantTick.update { it + 1 }
    }

    fun hasReadLogs(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED
}

/**
 * Đọc logcat liên tục và đẩy từng dòng ra [onLine]. Tự chạy lại khi tiến trình logcat chết.
 * Android 13+ hỏi quyền truy cập log mỗi lần mở logcat mới và tự từ chối khi app không ở màn hình trước,
 * nên quyền đọc log hệ thống được xét lại cho từng tiến trình logcat.
 */
class LogcatWatcher(
    private val buffers: List<String> = DEFAULT_BUFFERS,
    private val ownOnlyAfterMs: Long = OWN_ONLY_AFTER_MS,
    private val onLine: (String) -> Unit,
) {

    private var job: Job? = null

    @Volatile
    private var process: java.lang.Process? = null

    val running: Boolean get() = job?.isActive == true

    fun start(scope: CoroutineScope) {
        if (running) return
        LogStatus.set(LogAccess.WAITING)
        job = scope.launch(Dispatchers.IO) {
            val ownPid = Process.myPid()
            var backoff = MIN_BACKOFF_MS
            while (isActive) {
                val seen = AtomicBoolean(false)
                LogStatus.set(LogAccess.WAITING)
                val watchdog = launch {
                    delay(ownOnlyAfterMs)
                    if (!seen.get()) LogStatus.set(LogAccess.OWN_ONLY)
                }
                runCatching {
                    val command = listOf("logcat", "-v", "brief", "-T", "1") + buffers.flatMap { listOf("-b", it) }
                    val proc = ProcessBuilder(command).redirectErrorStream(true).start()
                    process = proc
                    proc.inputStream.bufferedReader().useLines { lines ->
                        for (line in lines) {
                            if (!isActive) break
                            if (!seen.get()) {
                                val pid = PID.find(line)?.groupValues?.get(1)?.toIntOrNull()
                                if (pid != null && pid != ownPid && seen.compareAndSet(false, true)) {
                                    LogStatus.set(LogAccess.SYSTEM)
                                }
                            }
                            onLine(line)
                        }
                    }
                }
                watchdog.cancel()
                process?.destroy()
                process = null
                if (!isActive) break
                if (!seen.get()) LogStatus.set(LogAccess.OWN_ONLY)
                if (seen.get()) backoff = MIN_BACKOFF_MS
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        process?.destroy()
        process = null
        LogStatus.set(LogAccess.OFF)
    }

    private companion object {
        val PID = Regex("^[VDIWEF]/[^(]*\\(\\s*(\\d+)\\)")
        val DEFAULT_BUFFERS = listOf("main", "system", "events")
        const val MIN_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val OWN_ONLY_AFTER_MS = 6_000L
    }
}

/**
 * Dò dòng log của nút cứng: trong lúc bật, ghi lại các dòng "giống sự kiện phím"
 * để người dùng chọn làm mẫu cho chế độ B.
 */
object LogSniffer {

    data class Line(val key: String, val sample: String, val hits: Int, val lastAt: Long)

    data class State(
        val active: Boolean = false,
        val endsAt: Long = 0,
        val lines: List<Line> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val isActive: Boolean
        get() = _state.value.active && SystemClock.elapsedRealtime() < _state.value.endsAt

    fun start(durationMs: Long) {
        _state.value = State(active = true, endsAt = SystemClock.elapsedRealtime() + durationMs)
    }

    fun stop() {
        _state.update { it.copy(active = false) }
    }

    fun observe(line: String) {
        if (!isActive || !KEY_HINT.containsMatchIn(line) || NOISE.containsMatchIn(line)) return
        val key = patternFor(line) ?: return
        val now = System.currentTimeMillis()
        _state.update { state ->
            val existing = state.lines.firstOrNull { it.key == key }
            val lines = if (existing == null) {
                state.lines + Line(key, line.take(200), 1, now)
            } else {
                state.lines.map { if (it === existing) it.copy(hits = it.hits + 1, lastAt = now, sample = line.take(200)) else it }
            }
            state.copy(lines = lines.takeLast(MAX_LINES))
        }
    }

    /**
     * Biến một dòng log thành regex ổn định: bỏ mức/pid, giữ tag + nội dung,
     * thay các con số (thời gian, id) bằng \d+.
     */
    fun patternFor(line: String): String? {
        val match = BRIEF.find(line) ?: return null
        val tag = match.groupValues[1].trim()
        val message = match.groupValues[2].trim().take(120)
        if (tag.isEmpty() || message.isEmpty()) return null
        val parts = Regex("\\d+").split(message).map { Regex.escape(it) }
        return Regex.escape(tag) + ".*" + parts.joinToString("\\d+")
    }

    private val BRIEF = Regex("^[VDIWEF]/([^(]*)\\(\\s*\\d+\\):\\s?(.*)$")
    private val KEY_HINT = Regex(
        "(?i)ACTION_BUTTON|keycode|key_?event|smart_?key|shortcut|sidekey|side_key|ai_?key|" +
            "start_functions|copilot|jovi|voice_?assist|longpress|long_press|interceptKey",
    )
    private val NOISE = Regex("(?i)aikeybridge|viewroot_draw_event|ime_|InputMethod")
    private const val MAX_LINES = 30
}
