package com.aikeybridge.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.provider.Settings
import com.aikeybridge.BuildConfig
import com.aikeybridge.data.ActiveConfig
import com.aikeybridge.data.AppSettings
import com.aikeybridge.data.PermissionSnapshot
import com.aikeybridge.data.ProfileRepository
import com.aikeybridge.data.DeviceInfo
import com.aikeybridge.shell.WatchScript
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object Diagnostics {

    enum class Level { INFO, SUCCESS, WARN, ERROR }

    data class Entry(
        val id: Long,
        val at: Long,
        val level: Level,
        val tag: String,
        val message: String,
    )

    private const val MAX_ENTRIES = 200
    private const val HISTORY_FILE = "diagnostics.log"
    private const val HISTORY_OLD_FILE = "diagnostics.1.log"
    private const val HISTORY_MAX_BYTES = 512 * 1024L

    private val lock = Any()
    private var nextId = 0L
    private val io = Executors.newSingleThreadExecutor()

    /** Bản ghi trên đĩa: [entries] chỉ sống trong RAM, mất khi ROM giết tiến trình — đúng lúc cần log nhất. */
    @Volatile
    private var historyDir: File? = null

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun init(context: Context) {
        if (historyDir != null) return
        synchronized(lock) {
            if (historyDir != null) return
            historyDir = context.applicationContext.filesDir
        }
        info("process", "Tiến trình khởi động (pid ${android.os.Process.myPid()}).")
    }

    fun log(level: Level, tag: String, message: String) {
        synchronized(lock) {
            val entry = Entry(
                id = nextId++,
                at = System.currentTimeMillis(),
                level = level,
                tag = tag,
                message = message,
            )
            _entries.update { current ->
                (current + entry).takeLast(MAX_ENTRIES)
            }
            appendHistory(entry)
        }
    }

    private fun appendHistory(entry: Entry) {
        val dir = historyDir ?: return
        val line = "${fullTime(entry.at)} | ${entry.level} | ${entry.tag} | ${entry.message.replace('\n', ' ')}\n"
        io.execute {
            runCatching {
                val file = File(dir, HISTORY_FILE)
                if (file.length() > HISTORY_MAX_BYTES) file.renameTo(File(dir, HISTORY_OLD_FILE))
                file.appendText(line)
            }
        }
    }

    private fun globalSetting(context: Context, name: String): String =
        runCatching { Settings.Global.getString(context.contentResolver, name) }.getOrNull() ?: "null"

    private fun fullTime(at: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(at))

    fun info(tag: String, message: String) = log(Level.INFO, tag, message)

    fun success(tag: String, message: String) = log(Level.SUCCESS, tag, message)

    fun warn(tag: String, message: String) = log(Level.WARN, tag, message)

    fun error(tag: String, message: String) = log(Level.ERROR, tag, message)

    fun clear() {
        _entries.value = emptyList()
        AttemptLog.clear()
        val dir = historyDir ?: return
        io.execute {
            File(dir, HISTORY_FILE).delete()
            File(dir, HISTORY_OLD_FILE).delete()
        }
    }

    fun timeLabel(at: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(at))

    fun textSnapshot(): String = buildString {
        appendLine("AI Key Bridge ${BuildConfig.VERSION_NAME} · log chẩn đoán")
        appendLine("Không chứa nội dung màn hình, âm thanh hay thông tin cá nhân.")
        appendLine("Thời gian | Mức | Nguồn | Nội dung")
        appendLine("-".repeat(72))
        _entries.value.forEach { entry ->
            appendLine("${fullTime(entry.at)} | ${entry.level} | ${entry.tag} | ${entry.message}")
        }
    }

    /**
     * Thông tin lấy được qua Shizuku (quyền shell) lúc xuất. Null khi không có Shizuku.
     * [logcatGz] là logcat toàn máy đã gzip — gửi nén để lọt giới hạn ~1MB của binder.
     */
    class ShellReport(
        val sections: List<Pair<String, String>>,
        val logcatGz: ByteArray?,
        val logcatText: String?,
    )

    fun export(
        context: Context,
        device: DeviceInfo,
        config: ActiveConfig,
        settings: AppSettings,
        permissions: PermissionSnapshot,
        shellReport: ShellReport?,
    ): File {
        // Ghi nốt các dòng đang chờ trên luồng io trước khi đọc file lịch sử.
        runCatching { io.submit {}.get(2, TimeUnit.SECONDS) }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "ai-key-bridge-$stamp.zip")

        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.writeText("log.txt", textSnapshot())
            zip.writeText(
                "device.txt",
                buildString {
                    appendLine("Hãng: ${device.manufacturer}")
                    appendLine("Nhánh: ${device.brand}")
                    appendLine("Máy: ${device.model}${device.marketName?.let { " ($it)" } ?: ""}")
                    appendLine("ROM: ${device.romVersion ?: "không đọc được"}")
                    appendLine("Android: ${device.androidRelease} (API ${device.sdk})")
                    appendLine("Fingerprint: ${device.fingerprint}")
                    val source = if (config.manualProfile) "chọn tay" else "tự nhận diện"
                    appendLine("Profile: ${config.profile.id} · schema ${config.profile.schema} · $source")
                    appendLine("Đích: ${config.target.id} (${config.target.packages.joinToString()})")
                    appendLine("Trigger: ${config.triggers.joinToString { "${it.packageName}/${it.classRegex.pattern}" }}")
                    appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                },
            )
            zip.writeText(
                "settings.txt",
                buildString {
                    appendLine("redirectEnabled=${settings.redirectEnabled}")
                    appendLine("usageMode=${settings.usageMode}")
                    appendLine("hardwareMode=${settings.hardwareMode}")
                    appendLine("autoMic=${settings.autoMic}")
                    appendLine("lockScreen=${settings.lockScreen}")
                    appendLine("manualAutostart=${settings.manualAutostart}")
                    appendLine("dismissDelayMs=${config.dismissDelayMs}")
                    appendLine("micTimeoutMs=${config.micTimeoutMs}")
                    appendLine("dedupe=${config.dedupe}")
                    appendLine("attempts=${AttemptLog.attempts.value.size}")
                    appendLine()
                    appendLine("accessibility=${permissions.accessibility}")
                    appendLine("assistantDefault=${permissions.assistantDefault}")
                    appendLine("voiceInteraction=${permissions.voiceInteraction}")
                    appendLine("batteryUnrestricted=${permissions.batteryUnrestricted}")
                    appendLine("targetInstalled=${permissions.targetInstalled}")
                    appendLine("geminiInstalled=${permissions.geminiInstalled}")
                    appendLine("nativeAssistantInstalled=${permissions.nativeAssistantInstalled}")
                    appendLine("shizuku=installed:${permissions.shizukuInstalled} running:${permissions.shizukuRunning} granted:${permissions.shizukuGranted}")
                    appendLine("readLogs=${permissions.readLogs}")
                    appendLine()
                    // Đọc thẳng Settings.Global, không cần Shizuku — vẫn có khi Gỡ lỗi đã tắt và Shizuku đã chết.
                    appendLine("developerOptions=${globalSetting(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)}")
                    appendLine("adbEnabled=${globalSetting(context, Settings.Global.ADB_ENABLED)}")
                    appendLine("adbWifiEnabled=${globalSetting(context, "adb_wifi_enabled")}")
                },
            )
            historyDir?.let { dir ->
                File(dir, HISTORY_OLD_FILE).takeIf { it.exists() }?.let { zip.writeFile("history.1.log", it) }
                File(dir, HISTORY_FILE).takeIf { it.exists() }?.let { zip.writeFile("history.log", it) }
            }
            zip.writeText("exit-reasons.txt", exitReasons(context))
            zip.writeText("script-state.txt", scriptState(context))
            zip.writeText("packages.txt", packageVersions(context, config))
            // Không có READ_LOGS thì logcat chỉ trả log của chính app — vẫn có ích (crash, service).
            zip.writeLogcat("logcat-app.txt")
            shellReport?.let { report ->
                zip.writeText(
                    "system-state.txt",
                    report.sections.joinToString("\n\n") { (title, body) -> "### $title\n${body.trim()}" },
                )
                report.logcatGz?.let { gz ->
                    zip.putNextEntry(ZipEntry("logcat-system.txt"))
                    // Bản nén có thể bị cắt cụt — giữ phần đã giải nén được thay vì làm hỏng cả file zip.
                    runCatching { GZIPInputStream(gz.inputStream()).use { it.copyTo(zip) } }
                        .onFailure { zip.write("\n[giải nén dở: $it]\n".toByteArray()) }
                    zip.closeEntry()
                } ?: report.logcatText?.let { zip.writeText("logcat-system.txt", it) }
            }
            ProfileRepository.rawProfile(context, config.profile.id)?.let { zip.writeText("profile.json", it) }
            zip.writeText(
                "attempts.txt",
                AttemptLog.attempts.value.joinToString("\n") { a ->
                    val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(a.at))
                    "$time | ${if (a.ok) "OK" else "FAIL"} | ${a.durationMs}ms | ${a.flow} | ${a.detail}"
                },
            )
            zip.writeText(
                "README.txt",
                "log.txt, history*.log, attempts.txt: log của AI Key Bridge — tên gói, tên lớp cửa sổ,\n" +
                    "trạng thái lệnh và thời lượng. Không chứa văn bản trên màn hình, âm thanh, danh bạ hay vị trí.\n" +
                    "exit-reasons.txt: các lần app bị tắt và lý do (vuốt khỏi đa nhiệm, ROM dọn RAM, crash...).\n" +
                    "script-state.txt: script nền lúc khởi động (pid, cgroup) và lần cuối còn sống.\n" +
                    "packages.txt: phiên bản app liên quan (Shizuku, Gemini, trợ lý gốc).\n" +
                    "logcat-*.txt, system-state.txt: log hệ thống của cả máy. Có thể chứa tên app khác,\n" +
                    "tên Wi-Fi hay tài khoản — chỉ gửi riêng cho người phát triển, không đăng công khai.\n",
            )
        }

        dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".zip") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(3)
            ?.forEach { it.delete() }

        log(Level.INFO, "export", "Đã tạo ${file.name}")
        return file
    }

    private fun ZipOutputStream.writeText(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.writeFile(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    private fun ZipOutputStream.writeLogcat(name: String) {
        putNextEntry(ZipEntry(name))
        runCatching {
            val process = ProcessBuilder(
                "logcat", "-d", "-v", "threadtime",
                "-b", "main", "-b", "system", "-b", "crash", "-b", "events",
            ).redirectErrorStream(true).start()
            process.inputStream.use { it.copyTo(this) }
            process.waitFor(5, TimeUnit.SECONDS)
        }.onFailure { write("Không đọc được logcat: $it\n".toByteArray()) }
        closeEntry()
    }

    /**
     * File script nền tự ghi, đọc được cả khi Shizuku đã chết: trạng thái lúc khởi động (pid, cgroup)
     * và lần cuối còn sống. Nhịp sống cũ hơn vài chu kỳ = script đã chết khoảng lúc đó.
     */
    private fun scriptState(context: Context): String = buildString {
        val dir = context.getExternalFilesDir(null)
        if (dir == null) return "Không có thư mục ngoài của app."
        appendLine("Bây giờ: ${fullTime(System.currentTimeMillis())}")
        val alive = File(dir, WatchScript.ALIVE_FILE)
        if (alive.exists()) {
            appendLine("Còn sống lần cuối: ${runCatching { alive.readText().trim() }.getOrDefault("?")} (script ghi mỗi ${WatchScript.ALIVE_INTERVAL_S}s)")
            appendLine("File sửa lúc: ${fullTime(alive.lastModified())}")
        } else {
            appendLine("Chưa có nhịp sống — script chưa chạy bản mới lần nào.")
        }
        appendLine()
        appendLine("### Lúc khởi động script")
        val state = File(dir, WatchScript.STATE_FILE)
        appendLine(if (state.exists()) runCatching { state.readText().trim() }.getOrDefault("?") else "(không có)")
    }

    /** Phiên bản các app liên quan — Shizuku, đích, trợ lý gốc. Đọc bằng PackageManager, không cần Shizuku. */
    private fun packageVersions(context: Context, config: ActiveConfig): String = buildString {
        val packages = (listOf(context.packageName, "moe.shizuku.privileged.api") +
            config.target.packages + config.profile.assistantPackages).distinct()
        packages.forEach { pkg ->
            val info = runCatching { context.packageManager.getPackageInfo(pkg, 0) }.getOrNull()
            if (info == null) {
                appendLine("$pkg: không thấy")
            } else {
                append("$pkg: ${info.versionName} (${info.longVersionCode})")
                append(" · cập nhật ${fullTime(info.lastUpdateTime)}")
                appendLine(if (info.applicationInfo?.enabled == false) " · đã tắt" else "")
            }
        }
    }

    /** Lý do các lần tiến trình bị tắt gần đây — phân biệt vuốt khỏi đa nhiệm (force-stop) với ROM dọn RAM. */
    private fun exitReasons(context: Context): String = buildString {
        val manager = context.getSystemService(ActivityManager::class.java)
        val infos = runCatching { manager.getHistoricalProcessExitReasons(context.packageName, 0, 30) }
            .getOrElse { return "Không đọc được: $it" }
        if (infos.isEmpty()) appendLine("Chưa có lần tắt nào được ghi lại.")
        infos.forEach { info ->
            append("${fullTime(info.timestamp)} | ${exitReasonName(info.reason)}")
            append(" | ${info.processName} pid ${info.pid} | importance ${info.importance}")
            info.description?.let { append(" | $it") }
            appendLine()
        }
    }

    private fun exitReasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED (force-stop / vuốt khỏi đa nhiệm)"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_OTHER -> "OTHER (thường là ROM tự dọn)"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "PACKAGE_STATE_CHANGE"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED (cài bản mới)"
        else -> "reason=$reason"
    }
}
