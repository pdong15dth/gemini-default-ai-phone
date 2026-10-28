package com.aikeybridge.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aikeybridge.data.ActiveConfig
import com.aikeybridge.data.ConfigResolver
import com.aikeybridge.data.ConfigSource
import com.aikeybridge.data.CustomTrigger
import com.aikeybridge.data.HardwareMode
import com.aikeybridge.data.UsageMode
import com.aikeybridge.data.PermissionSnapshot
import com.aikeybridge.data.PermissionsReader
import com.aikeybridge.data.SettingsRepository
import com.aikeybridge.diagnostics.AttemptLog
import com.aikeybridge.diagnostics.AttemptStats
import com.aikeybridge.diagnostics.AttemptSummary
import com.aikeybridge.diagnostics.Diagnostics
import com.aikeybridge.model.AssistantTarget
import com.aikeybridge.model.OemProfile
import com.aikeybridge.service.LogAccess
import com.aikeybridge.service.LogSniffer
import com.aikeybridge.service.LogStatus
import com.aikeybridge.service.RedirectState
import com.aikeybridge.service.RuntimeStatus
import com.aikeybridge.service.Sniffer
import com.aikeybridge.service.WatchService
import com.aikeybridge.service.WatchStatus
import com.aikeybridge.shell.ProvisionReport
import com.aikeybridge.shell.Provisioner
import com.aikeybridge.shell.ShizukuShell
import com.aikeybridge.shell.StepPhase
import com.aikeybridge.shell.WatchGrants
import com.aikeybridge.shell.WatchScript
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class AppEntry(val packageName: String, val label: String)

/** Trạng thái nghe nền trong app (chế độ Script nền). */
data class ScriptStatus(
    val running: Boolean = false,
    val busy: Boolean = false,
    val access: LogAccess = LogAccess.OFF,
) {
    /** Service đang chạy và Android đã cho đọc log hệ thống. */
    val listening: Boolean get() = running && access == LogAccess.SYSTEM
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val settingsRepository = SettingsRepository(app)
    private val source = ConfigSource(app, settingsRepository)
    private val shell = ShizukuShell(app)

    val catalog = source.catalog
    val device = source.device
    val settings = settingsRepository.state

    val config: StateFlow<ActiveConfig> = settings
        .map(source::resolve)
        .stateIn(viewModelScope, SharingStarted.Eagerly, source.current())

    private val _permissions = MutableStateFlow(PermissionSnapshot())
    val permissions: StateFlow<PermissionSnapshot> = _permissions.asStateFlow()

    private val _setup = MutableStateFlow(config.value.let { SetupUiState.from(it.provisioning, it.restore) })
    val setup: StateFlow<SetupUiState> = _setup.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _exportFile = MutableStateFlow<File?>(null)
    val exportFile: StateFlow<File?> = _exportFile.asStateFlow()

    private val _apps = MutableStateFlow<List<AppEntry>?>(null)
    val apps: StateFlow<List<AppEntry>?> = _apps.asStateFlow()

    val logEntries = Diagnostics.entries
    val attempts = AttemptLog.attempts
    val redirectState: StateFlow<RedirectState> = RuntimeStatus.state
    val serviceConnected: StateFlow<Boolean> = RuntimeStatus.connected
    val sniffer = Sniffer.state
    val logSniffer = LogSniffer.state
    val logAccess = LogStatus.access
    val readLogsCommand: String = "adb shell pm grant ${application.packageName} android.permission.READ_LOGS"

    private val minuteTicker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    val now: StateFlow<Long> = minuteTicker
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    val summary: StateFlow<AttemptSummary> = combine(attempts, minuteTicker) { list, now ->
        AttemptStats.summarize(list, now)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AttemptStats.summarize(emptyList(), System.currentTimeMillis()),
    )

    val romChanged: StateFlow<Boolean> = settings
        .map { it.provisionedFingerprint != null && it.provisionedFingerprint != device.fingerprint }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var snifferJob: Job? = null

    private val scriptBusy = MutableStateFlow(false)
    val script: StateFlow<ScriptStatus> = combine(WatchStatus.running, LogStatus.access, scriptBusy) { running, access, busy ->
        ScriptStatus(running = running, busy = busy, access = if (running) access else LogAccess.OFF)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ScriptStatus())

    /** Mở app: nếu người dùng đã bật nghe nền thì bật lại / mở lại logcat để Android hỏi quyền đọc log. */
    fun checkScript() {
        refreshPermissions()
        if (WatchService.wanted(settingsRepository)) WatchService.start(app)
    }

    /**
     * Kích hoạt nghe nền: có Shizuku thì chạy lệnh kích hoạt của profile + cấp quyền cho app (một lần,
     * còn nguyên khi tắt Gỡ lỗi), dừng script shell cũ nếu còn, rồi bật service nghe trong app.
     * Không còn ép bật Gỡ lỗi USB — app ngân hàng chặn đúng công tắc đó.
     */
    fun startScript() {
        if (config.value.shellWatch == null) {
            _message.value = "${config.value.profile.displayName} chưa có mẫu cửa sổ để nghe. Hãy bấm Dò trợ lý."
            return
        }
        refreshPermissions()
        if (!_permissions.value.shizukuGranted && !_permissions.value.readLogs) {
            _message.value = "Cần Shizuku để cấp quyền đọc log lần đầu. Không có Shizuku thì copy lệnh ADB bên dưới."
            return
        }
        if (scriptBusy.value) return
        viewModelScope.launch {
            scriptBusy.value = true
            if (_permissions.value.shizukuGranted) {
                val active = config.value
                if (active.provisioning.isNotEmpty()) {
                    _setup.value = SetupUiState.from(active.provisioning, active.restore).copy(running = true)
                    Diagnostics.info("setup", "Tự chạy ${active.provisioning.size} lệnh kích hoạt trước khi bật nghe nền · ${active.flowLabel}.")
                    val report = Provisioner(shell, active.provisioning, active.restore).runProvisioning { step, phase, detail ->
                        _setup.update { state ->
                            state.copy(
                                steps = state.steps.map { item ->
                                    if (item.id == step.id) item.copy(status = phase.toStatus(), message = detail) else item
                                },
                            )
                        }
                    }
                    _setup.update { it.copy(running = false, provisioningReport = report) }
                    if (report.passed > 0) settingsRepository.setProvisionedFingerprint(device.fingerprint)
                }
                WatchGrants.commands(app.packageName).forEach { command ->
                    val result = shell.exec(command)
                    if (!result.ok) Diagnostics.warn("watch", "$command → ${result.output.ifBlank { "lỗi ${result.exitCode}" }}")
                }
                // Script shell của bản cũ còn chạy thì mỗi lần hô sẽ gọi trợ lý hai lần.
                shell.exec(WatchScript.stopCommand())
                refreshPermissions()
            }
            if (!_permissions.value.readLogs) {
                Diagnostics.error("watch", "Chưa có READ_LOGS sau khi cấp qua Shizuku.")
                _message.value = "Không cấp được quyền đọc log — xem Nhật ký."
                scriptBusy.value = false
                return@launch
            }
            settingsRepository.setWatchEnabled(true)
            val started = WatchService.start(app)
            scriptBusy.value = false
            _message.value = if (started) {
                "Đang nghe trong app. Android hỏi quyền truy cập log thì chọn Cho phép, rồi tắt Tuỳ chọn nhà phát triển và Gỡ lỗi USB để dùng app ngân hàng."
            } else {
                "Không bật được nghe nền — xem Nhật ký."
            }
        }
    }

    fun stopScript() {
        settingsRepository.setWatchEnabled(false)
        WatchService.stop(app)
        refreshPermissions()
        if (_permissions.value.shizukuGranted) viewModelScope.launch { shell.exec(WatchScript.stopCommand()) }
        Diagnostics.info("watch", "Đã tắt nghe nền.")
        _message.value = "Đã tắt nghe nền."
    }
    private var logSnifferJob: Job? = null

    private val binderReceived = Shizuku.OnBinderReceivedListener { refreshPermissions() }
    private val binderDead = Shizuku.OnBinderDeadListener { refreshPermissions() }

    init {
        Diagnostics.init(app)
        AttemptLog.init(app)
        refreshPermissions()
        runCatching { Shizuku.addBinderReceivedListenerSticky(binderReceived) }
        runCatching { Shizuku.addBinderDeadListener(binderDead) }

        viewModelScope.launch {
            config.collect { active ->
                refreshPermissions()
                val current = _setup.value
                val ids = active.provisioning.map { it.id }
                if (!current.running && !current.restoring && current.steps.map { it.id } != ids) {
                    _setup.value = SetupUiState.from(active.provisioning, active.restore)
                }
            }
        }
    }

    fun refreshPermissions() {
        val active = config.value
        _permissions.value = PermissionsReader.read(
            context = app,
            settings = settingsRepository.state.value,
            targetPackages = active.target.packages,
            assistantRequired = false,
            nativeAssistantPackages = active.profile.assistantPackages,
        )
    }

    fun markOnboardingDone() {
        settingsRepository.setOnboardingDone(true)
        settingsRepository.setOnboardingStep(0)
    }

    fun setOnboardingStep(step: Int) {
        if (settingsRepository.state.value.onboardingStep != step) settingsRepository.setOnboardingStep(step)
    }

    /**
     * Lệnh khởi động Shizuku qua ADB cho đúng máy này (Shizuku 13.6+ dùng libshizuku.so trong thư mục lib của app).
     * Null nếu chưa cài Shizuku.
     */
    val shizukuStartCommand: String?
        get() = runCatching {
            val info = app.packageManager.getApplicationInfo(PermissionsReader.SHIZUKU_PACKAGE, 0)
            "adb shell ${info.nativeLibraryDir}/libshizuku.so"
        }.getOrNull()

    fun setAutoMic(value: Boolean) = settingsRepository.setAutoMic(value)

    fun setLockScreen(value: Boolean) = settingsRepository.setLockScreen(value)

    fun setRedirectEnabled(value: Boolean) = settingsRepository.setRedirectEnabled(value)

    fun setDedupe(value: Boolean) = settingsRepository.setDedupe(value)

    fun setManualAutostart(value: Boolean) {
        settingsRepository.setManualAutostart(value)
        refreshPermissions()
    }

    fun selectProfile(id: String?, announce: Boolean = true) {
        settingsRepository.setProfileOverride(id)
        if (!announce) return
        val name = when (id) {
            null -> "tự nhận diện"
            OemProfile.GENERIC_ID -> "hãng khác"
            else -> catalog.profile(id)?.displayName ?: id
        }
        _message.value = "Đã chuyển profile: $name. Chạy lại Kích hoạt nếu đổi hãng."
    }

    fun selectTarget(id: String) {
        settingsRepository.setTarget(id)
    }

    fun selectCustomTarget(entry: AppEntry) {
        settingsRepository.setTarget(AssistantTarget.CUSTOM, entry.packageName, entry.label)
        _message.value = "Đích mới: ${entry.label}. App này không tự bật Micro."
    }

    fun nudgeDismissDelay(deltaMs: Long) {
        val next = (config.value.dismissDelayMs + deltaMs)
            .coerceIn(ConfigResolver.MIN_DISMISS_DELAY, ConfigResolver.MAX_DISMISS_DELAY)
        settingsRepository.setDismissDelay(next)
    }

    fun nudgeMicTimeout(deltaMs: Long) {
        val next = (config.value.micTimeoutMs + deltaMs)
            .coerceIn(ConfigResolver.MIN_MIC_TIMEOUT, ConfigResolver.MAX_MIC_TIMEOUT)
        settingsRepository.setMicTimeout(next)
    }

    fun resetTuning() {
        settingsRepository.setDismissDelay(null)
        settingsRepository.setMicTimeout(null)
        _message.value = "Đã trả độ trễ và timeout về mặc định của profile."
    }

    fun loadApps() {
        if (_apps.value != null) return
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) {
                val pm = app.packageManager
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
                    .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                    .filter { (pkg, _) -> pkg != app.packageName }
                    .distinctBy { it.first }
                    .map { (pkg, label) -> AppEntry(pkg, label) }
                    .sortedBy { it.label.lowercase() }
            }
        }
    }

    fun startSniffer() {
        refreshPermissions()
        if (!_permissions.value.shizukuGranted && (!_permissions.value.accessibility || !serviceConnected.value)) {
            _message.value = "Cần cấp quyền Shizuku trước khi dò cửa sổ."
            return
        }
        snifferJob?.cancel()
        Sniffer.start(SNIFF_WINDOW_MS)
        Diagnostics.info("sniff", "Bắt đầu dò cửa sổ trong ${SNIFF_WINDOW_MS / 1000}s.")
        snifferJob = viewModelScope.launch {
            if (_permissions.value.shizukuGranted) {
                shell.exec("logcat -b events -c")
                while (isActive && Sniffer.isActive) {
                    delay(800)
                    val res = shell.exec("logcat -b events -d")
                    parseShizukuEventWindows(res.stdout)
                }
            } else {
                delay(SNIFF_WINDOW_MS)
            }
            if (isActive) Sniffer.stop()
            Diagnostics.info("sniff", "Dò xong · ${Sniffer.state.value.candidates.size} cửa sổ.")
        }
    }

    private fun parseShizukuEventWindows(stdout: String) {
        val slashRegex = Regex("([a-zA-Z0-9_.]+)/([a-zA-Z0-9_.\$]+)")
        val focusRegex = Regex("Focus entering [^ ]+ ([^ )\\]]+)")
        for (line in stdout.lineSequence()) {
            slashRegex.findAll(line).forEach { m ->
                val pkg = m.groupValues[1]
                val cls = m.groupValues[2].let { if (it.startsWith(".")) pkg + it else it }
                Sniffer.observe(pkg, cls, app.packageName)
            }
            focusRegex.find(line)?.let { m ->
                val token = m.groupValues[1].trim()
                if ('/' !in token && token.isNotBlank() && !token.startsWith("0x")) {
                    val pkg = config.value.profile.assistantPackages.firstOrNull() ?: "event.window"
                    Sniffer.observe(pkg, token, app.packageName)
                }
            }
        }
    }

    fun stopSniffer() {
        snifferJob?.cancel()
        Sniffer.stop()
    }

    fun saveTrigger(candidate: Sniffer.Candidate) {
        settingsRepository.addCustomTrigger(CustomTrigger(candidate.packageName, candidate.className))
        Diagnostics.success("sniff", "Lưu trigger ${candidate.packageName}/${candidate.className}")
        _message.value = "Đã lưu cửa sổ vào Script nền. Bấm Bật lại script để áp dụng."
    }

    fun removeTrigger(trigger: CustomTrigger) {
        settingsRepository.removeCustomTrigger(trigger)
    }

    fun setUsageMode(mode: UsageMode, announce: Boolean = true) {
        settingsRepository.setUsageMode(mode)
        refreshPermissions()
        if (mode == UsageMode.SCRIPT) checkScript() else WatchService.stop(app)
        if (announce) {
            _message.value = when (mode) {
                UsageMode.ONE_TIME -> "Đã chuyển sang Cấu hình một lần — không cần Trợ năng."
                UsageMode.SCRIPT -> "Đã chuyển sang Script nền — bật nghe nền trong màn Script nền."
                UsageMode.FULL -> "Đã chuyển sang Đầy đủ — cần bật Trợ năng."
            }
        }
    }

    fun setHardwareMode(mode: HardwareMode) {
        if (settingsRepository.state.value.hardwareMode != mode) {
            _message.value = if (mode == HardwareMode.LOGCAT) "Nút cứng: chế độ B (đọc event log)." else "Nút cứng: chế độ A (bắt cửa sổ)."
        }
        settingsRepository.setHardwareMode(mode)
        refreshPermissions()
        LogStatus.permissionChanged()
    }

    /** Cấp READ_LOGS cho chính app bằng shell của Shizuku (pm grant). */
    fun grantReadLogs() {
        refreshPermissions()
        if (_permissions.value.readLogs) {
            LogStatus.permissionChanged()
            _message.value = "Đã có quyền READ_LOGS."
            return
        }
        if (!_permissions.value.shizukuGranted) {
            _message.value = "Cần Shizuku để tự cấp. Hoặc chạy trên máy tính: $readLogsCommand"
            return
        }
        viewModelScope.launch {
            val result = shell.exec("pm grant ${app.packageName} android.permission.READ_LOGS")
            refreshPermissions()
            LogStatus.permissionChanged()
            val ok = _permissions.value.readLogs
            Diagnostics.log(
                if (ok) Diagnostics.Level.SUCCESS else Diagnostics.Level.ERROR,
                "logcat",
                if (ok) "Đã cấp READ_LOGS qua Shizuku." else "pm grant READ_LOGS lỗi: ${result.output}",
            )
            _message.value = if (ok) {
                "Đã cấp READ_LOGS. Nếu Android hỏi quyền truy cập log, chọn Cho phép."
            } else {
                "Không cấp được READ_LOGS: ${result.output.ifBlank { "lệnh trả về ${result.exitCode}" }}"
            }
        }
    }

    fun startLogSniffer() {
        refreshPermissions()
        when {
            !_permissions.value.shizukuGranted && !_permissions.value.readLogs ->
                _message.value = "Cần cấp quyền Shizuku hoặc READ_LOGS trước khi dò log."
            else -> {
                logSnifferJob?.cancel()
                LogSniffer.start(SNIFF_WINDOW_MS)
                Diagnostics.info("sniff", "Bắt đầu dò event log trong ${SNIFF_WINDOW_MS / 1000}s.")
                logSnifferJob = viewModelScope.launch {
                    if (_permissions.value.shizukuGranted) {
                        LogStatus.set(com.aikeybridge.service.LogAccess.SYSTEM)
                        shell.exec("logcat -b main -b system -b events -c")
                        while (isActive && LogSniffer.isActive) {
                            delay(900)
                            val res = shell.exec("logcat -v brief -b main -b system -b events -d -t 300")
                            res.stdout.lineSequence().forEach(LogSniffer::observe)
                        }
                    } else {
                        delay(SNIFF_WINDOW_MS)
                    }
                    if (isActive) LogSniffer.stop()
                }
            }
        }
    }

    fun stopLogSniffer() {
        logSnifferJob?.cancel()
        LogSniffer.stop()
    }

    fun saveLogPattern(line: LogSniffer.Line) {
        settingsRepository.addLogPattern(line.key)
        Diagnostics.success("sniff", "Lưu mẫu log: ${line.key}")
        _message.value = "Đã lưu mẫu log vào Script nền. Bấm Bật lại script để áp dụng."
    }

    fun removeLogPattern(pattern: String) {
        settingsRepository.removeLogPattern(pattern)
    }

    fun requestShizukuPermission() {
        val snapshot = _permissions.value
        if (!snapshot.shizukuRunning) {
            _message.value = "Shizuku chưa chạy. Mở Shizuku rồi thử lại."
            return
        }
        runCatching { Shizuku.requestPermission(SHIZUKU_REQUEST_CODE) }
            .onFailure { _message.value = "Không gửi được yêu cầu quyền: ${it.message}" }
    }

    fun onShizukuPermissionResult(granted: Boolean) {
        refreshPermissions()
        _message.value = if (granted) {
            "Đã cấp quyền Shizuku. Có thể chạy ${_setup.value.steps.size} lệnh."
        } else {
            "Bạn đã từ chối quyền Shizuku."
        }
    }

    fun runProvisioning() {
        if (_setup.value.running) return
        if (!_permissions.value.shizukuGranted) {
            _message.value = "Cần cấp quyền Shizuku trước khi chạy lệnh."
            return
        }
        val active = config.value
        if (active.provisioning.isEmpty()) {
            settingsRepository.setProvisionedFingerprint(device.fingerprint)
            _message.value = "${active.target.displayName} không cần lệnh shell nào."
            return
        }

        viewModelScope.launch {
            _setup.value = SetupUiState.from(active.provisioning, active.restore).copy(running = true)
            Diagnostics.info("setup", "Bắt đầu chạy ${active.provisioning.size} lệnh · ${active.flowLabel}.")

            val report = Provisioner(shell, active.provisioning, active.restore).runProvisioning { step, phase, detail ->
                _setup.update { state ->
                    state.copy(
                        steps = state.steps.map { item ->
                            if (item.id == step.id) item.copy(status = phase.toStatus(), message = detail) else item
                        },
                    )
                }
            }

            _setup.update { it.copy(running = false, provisioningReport = report) }
            Diagnostics.log(
                if (report.ok) Diagnostics.Level.SUCCESS else Diagnostics.Level.WARN,
                "setup",
                "Kết quả ${report.passed}/${report.total} bước.",
            )
            if (report.passed > 0) settingsRepository.setProvisionedFingerprint(device.fingerprint)
            refreshPermissions()
            _message.value = if (report.ok) {
                "Đã xác minh đủ ${report.total} bước. Gọi ${active.profile.assistantName} để thử."
            } else {
                "Còn ${report.total - report.passed} bước chưa đạt — xem chi tiết."
            }
        }
    }

    fun restoreDefaults() {
        if (_setup.value.restoring) return
        if (!_permissions.value.shizukuGranted) {
            _message.value = "Cần cấp quyền Shizuku trước khi khôi phục."
            return
        }
        val active = config.value

        viewModelScope.launch {
            _setup.update { state ->
                state.copy(
                    restoring = true,
                    restoreReport = null,
                    restoreSteps = state.restoreSteps.map { it.copy(status = StepStatus.PENDING, message = "") },
                )
            }
            Diagnostics.info("restore", "Bắt đầu khôi phục mặc định.")

            val report: ProvisionReport =
                Provisioner(shell, active.provisioning, active.restore).runRestore { step, phase, detail ->
                    _setup.update { state ->
                        state.copy(
                            restoreSteps = state.restoreSteps.map { item ->
                                if (item.id == step.id) item.copy(status = phase.toStatus(), message = detail) else item
                            },
                        )
                    }
                }

            _setup.update { it.copy(restoring = false, restoreReport = report) }
            settingsRepository.setRedirectEnabled(false)
            settingsRepository.setProvisionedFingerprint(null)
            refreshPermissions()
            _message.value = if (report.ok) {
                "Đã khôi phục. Chọn lại ${active.profile.assistantName} trong cài đặt trợ lý."
            } else {
                "Khôi phục xong một phần — kiểm tra các bước lỗi trong nhật ký."
            }
        }
    }

    /** Đọc lại quyền + so fingerprint ROM với lần kích hoạt gần nhất. */
    fun checkRom() {
        runDiagnostics(announce = false)
        settingsRepository.setLastRomCheck(System.currentTimeMillis())
        val saved = settingsRepository.state.value.provisionedFingerprint
        val snapshot = _permissions.value
        _message.value = when {
            saved == null -> "Chưa kích hoạt lần nào trên máy này."
            saved != device.fingerprint -> "ROM đã cập nhật từ lần kích hoạt trước — nên chạy lại Kích hoạt & vá lỗi."
            !snapshot.assistantOk -> "ROM không đổi, nhưng trợ lý mặc định đã bị đặt lại. Chạy lại kích hoạt."
            else -> "ROM không đổi, cấu hình vẫn còn."
        }
    }

    fun runDiagnostics(announce: Boolean = true) {
        refreshPermissions()
        val snapshot = _permissions.value
        val active = config.value
        Diagnostics.info(
            "diag",
            "Thiết bị: ${device.manufacturer} ${device.model} · Android ${device.androidRelease} (API ${device.sdk})",
        )
        Diagnostics.info(
            "diag",
            "Profile: ${active.profile.id}${if (active.manualProfile) " (chọn tay)" else ""} · đích ${active.target.id}",
        )
        Diagnostics.log(
            if (snapshot.accessibility) Diagnostics.Level.SUCCESS else Diagnostics.Level.ERROR,
            "diag",
            "Trợ năng: ${if (snapshot.accessibility) "đã bật" else "chưa bật"}",
        )
        if (active.target.shellKeyevent != null) {
            Diagnostics.info(
                "diag",
                "Lớp phủ nổi: ${when {
                    !snapshot.assistantDefault -> "tắt (Google chưa là trợ lý mặc định) · dùng app"
                    !snapshot.shizukuGranted -> "tắt (chưa có Shizuku) · dùng app"
                    else -> "bật"
                }}",
            )
        }
        Diagnostics.info(
            "diag",
            "Nút cứng: chế độ ${if (settings.value.hardwareMode == HardwareMode.LOGCAT) "B (event log)" else "A (cửa sổ)"}" +
                " · READ_LOGS ${if (snapshot.readLogs) "đã cấp" else "chưa cấp"}",
        )
        Diagnostics.log(
            if (snapshot.nativeAssistantInstalled || active.profile.isGeneric) Diagnostics.Level.INFO else Diagnostics.Level.WARN,
            "diag",
            "Trợ lý gốc (${active.profile.assistantName}): ${if (snapshot.nativeAssistantInstalled) "có trên máy" else "không thấy gói"}",
        )
        Diagnostics.log(
            if (snapshot.batteryUnrestricted) Diagnostics.Level.SUCCESS else Diagnostics.Level.WARN,
            "diag",
            "Pin: ${if (snapshot.batteryUnrestricted) "không bị tối ưu" else "đang bị tối ưu"}",
        )
        Diagnostics.log(
            if (snapshot.shizukuGranted) Diagnostics.Level.SUCCESS else Diagnostics.Level.INFO,
            "diag",
            "Shizuku: ${when {
                snapshot.shizukuGranted -> "đã cấp quyền"
                snapshot.shizukuRunning -> "đang chạy, chưa cấp quyền"
                snapshot.shizukuInstalled -> "đã cài, chưa chạy"
                else -> "chưa cài"
            }}",
        )
        Diagnostics.log(
            if (snapshot.targetInstalled) Diagnostics.Level.SUCCESS else Diagnostics.Level.ERROR,
            "diag",
            "${active.target.displayName}: ${if (snapshot.targetInstalled) "đã cài" else "chưa cài"}",
        )
        if (announce) _message.value = "Đã chạy chẩn đoán và ghi vào nhật ký."
    }

    fun exportLogs() {
        if (exporting) return
        exporting = true
        refreshPermissions()
        val snapshot = _permissions.value
        _message.value = if (snapshot.shizukuGranted) {
            "Đang gom log hệ thống qua Shizuku…"
        } else {
            "Đang đóng gói log chẩn đoán…"
        }
        viewModelScope.launch {
            try {
                val report = if (snapshot.shizukuGranted) {
                    runCatching { collectShellReport() }.getOrNull()
                } else {
                    null
                }
                val file = withContext(Dispatchers.IO) {
                    Diagnostics.export(
                        context = app,
                        device = device,
                        config = config.value,
                        settings = settings.value,
                        permissions = snapshot,
                        shellReport = report,
                    )
                }
                _exportFile.value = file
            } catch (throwable: Throwable) {
                Diagnostics.error("export", "Xuất log lỗi: ${throwable.message ?: throwable}")
                _message.value = "Không tạo được file log: ${throwable.message ?: throwable.javaClass.simpleName}"
            } finally {
                exporting = false
            }
        }
    }

    private var exporting = false

    /** Trạng thái hệ thống và logcat toàn máy, đọc bằng quyền shell của Shizuku. */
    private suspend fun collectShellReport(): Diagnostics.ShellReport {
        val probe = shell.exec("id")
        if (probe.exitCode == -1) {
            Diagnostics.warn("export", "Bỏ qua log hệ thống vì Shizuku lỗi: ${probe.output}")
            return Diagnostics.ShellReport(
                sections = listOf("Shizuku shell" to "Không chạy được lệnh qua Shizuku: ${probe.output}"),
                logcatGz = null,
                logcatText = null,
            )
        }
        val pkg = app.packageName
        val active = config.value
        val commands = buildList {
            add("Trợ năng" to "settings get secure accessibility_enabled; settings get secure enabled_accessibility_services")
            add("Trợ lý mặc định" to "settings get secure assistant; settings get secure voice_interaction_service; cmd role get-role-holders android.app.role.ASSISTANT")
            add("Gói $pkg" to "dumpsys package $pkg | grep -E 'versionName|lastUpdateTime|stopped=|enabled=|READ_LOGS|WRITE_SECURE'")
            add("Appops $pkg" to "cmd appops get $pkg")
            add("Whitelist pin" to "dumpsys deviceidle whitelist | grep -iE '$pkg|vivo.agent|copilot|googlequicksearchbox|bard'")
            add("Tiến trình liên quan" to "ps -A -o PID,USER,NAME | grep -iE 'aikeybridge|shizuku|vivo.agent|copilot|bard|googlequicksearch'")
            add(
                "Gỡ lỗi" to "echo dev=\$(settings get global development_settings_enabled) " +
                    "adb=\$(settings get global adb_enabled) adb_wifi=\$(settings get global adb_wifi_enabled) " +
                    "adbd=\$(getprop init.svc.adbd)",
            )
            // Tiến trình còn trong nhóm uid_X/pid_Y của adbd sẽ chết khi tắt Gỡ lỗi.
            add(
                "Cgroup script nền / Shizuku" to "ps -A -o PID,ARGS | grep -E '[a]kb-gemini.sh|[s]hizuku_server' | " +
                    "while read pid rest; do echo \"== \$pid \$rest\"; cat /proc/\$pid/cgroup; done; " +
                    "ls -l ${WatchScript.ESCAPE_CGROUPS.split(' ').joinToString(" ") { "$it/cgroup.procs" }}",
            )
            add("Shell & adbd" to "ps -A -o PID,PPID,USER,LABEL,ARGS | grep -E '^ *[0-9]+ +[0-9]+ +shell |[a]dbd'; " +
                "for p in \$(pidof adbd); do echo \"== adbd \$p\"; cat /proc/\$p/cgroup 2>&1; done")
            add("Hệ thống shell" to "id; getenforce; uname -a; toybox --version; echo '-- cgroup của shell service'; cat /proc/self/cgroup")
            add("Cây cgroup" to "mount | grep cgroup; ls -ld /sys/fs/cgroup/uid_0 /sys/fs/cgroup/uid_2000 2>&1")
            add("File script" to "ls -l /data/local/tmp; echo '-- ${WatchScript.PATH}'; cat ${WatchScript.PATH}")
            add("dumpsys adb" to "dumpsys adb")
            add("Nguồn / màn hình" to "dumpsys power | grep -E 'mWakefulness=|mIsPowered=|mBatteryLevel=|mDeviceIdleMode='")
            add("Cửa sổ trợ lý đang mở" to "dumpsys window windows | grep -iE 'Window\\{.*(copilot|vivo.agent|bard|googlequicksearchbox)'")
            add("Exit-info Shizuku" to "dumpsys activity exit-info moe.shizuku.privileged.api | head -80")
            active.profile.assistantPackages.forEach { native ->
                add("Exit-info $native" to "dumpsys activity exit-info $native | head -60")
            }
            add("Deviceidle whitelist" to "dumpsys deviceidle whitelist")
            active.provisioning.forEach { add("Kiểm tra: ${it.title}" to it.verify) }
            add("dumpsys accessibility" to "dumpsys accessibility")
            add("getprop" to "getprop")
            // Bỏ các khoá định danh (android_id, tên máy, địa chỉ Bluetooth, token, tài khoản).
            listOf("global", "secure", "system").forEach { ns ->
                add("settings $ns" to "settings list $ns | grep -viE '$SETTINGS_PRIVATE'")
            }
        }
        val sections = mutableListOf<Pair<String, String>>()
        for ((title, command) in commands) {
            val result = shell.exec(command)
            sections += title to "$ $command\n${result.output.ifBlank { "(trống, exit ${result.exitCode})" }}"
            if (result.exitCode == -1) break
        }
        // Log toàn máy ~2MB vượt giới hạn binder, nên nén gzip + base64 bên shell rồi giải nén ở đây.
        val buffers = "-b main -b system -b crash -b events"
        val gz = shell.exec("logcat -d -v threadtime $buffers | gzip -c | base64 -w 0")
        val logcatGz = if (gz.ok) runCatching { android.util.Base64.decode(gz.stdout.trim(), android.util.Base64.DEFAULT) }.getOrNull()?.takeIf { it.isNotEmpty() } else null
        val logcatText = if (logcatGz == null && gz.exitCode != -1) {
            shell.exec("logcat -d -v threadtime $buffers -t 3000").takeIf { it.ok }?.stdout
        } else {
            null
        }
        return Diagnostics.ShellReport(sections, logcatGz, logcatText)
    }

    fun consumeExport() {
        _exportFile.value = null
    }

    fun clearLogs() {
        Diagnostics.clear()
        _message.value = "Đã xoá nhật ký và lịch sử lượt chạy trên máy."
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }

    override fun onCleared() {
        runCatching { Shizuku.removeBinderReceivedListener(binderReceived) }
        runCatching { Shizuku.removeBinderDeadListener(binderDead) }
        Sniffer.stop()
        LogSniffer.stop()
        shell.close()
        super.onCleared()
    }

    private fun StepPhase.toStatus(): StepStatus = when (this) {
        StepPhase.RUNNING -> StepStatus.RUNNING
        StepPhase.OK -> StepStatus.OK
        StepPhase.FAILED -> StepStatus.FAILED
    }

    companion object {
        const val SHIZUKU_REQUEST_CODE = 2311
        const val SNIFF_WINDOW_MS = 8_000L

        /** Khoá settings mang định danh người dùng/máy — không đưa vào log xuất. */
        const val SETTINGS_PRIVATE = "android_id|bluetooth_address|bluetooth_name|device_name|token|account|ssid|password|email|phone|imei|serial"
    }
}
