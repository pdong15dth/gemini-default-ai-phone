package com.aikeybridge.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aikeybridge.data.ActionMode
import com.aikeybridge.data.ActiveConfig
import com.aikeybridge.data.ConfigResolver
import com.aikeybridge.data.ConfigSource
import com.aikeybridge.data.CustomTrigger
import com.aikeybridge.data.HardwareMode
import com.aikeybridge.data.ThemeMode
import com.aikeybridge.data.UsageMode
import com.aikeybridge.data.PermissionSnapshot
import com.aikeybridge.data.PermissionsReader
import com.aikeybridge.data.SettingsRepository
import com.aikeybridge.data.ShellBackend
import com.aikeybridge.diagnostics.AttemptLog
import com.aikeybridge.diagnostics.AttemptStats
import com.aikeybridge.diagnostics.AttemptSummary
import com.aikeybridge.diagnostics.Diagnostics
import com.aikeybridge.model.AssistantTarget
import com.aikeybridge.model.OemProfile
import com.aikeybridge.service.LogSniffer
import com.aikeybridge.service.LogStatus
import com.aikeybridge.service.RedirectState
import com.aikeybridge.service.RuntimeStatus
import com.aikeybridge.service.Sniffer
import com.aikeybridge.shell.BuiltInWirelessAdb
import com.aikeybridge.shell.EngineBinderProvider
import com.aikeybridge.shell.EngineDaemonMain
import com.aikeybridge.shell.ProvisionReport
import com.aikeybridge.shell.Provisioner
import com.aikeybridge.shell.ShizukuShell
import com.aikeybridge.shell.StepPhase
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

/** Trạng thái script nền. [running] = null nghĩa là chưa kiểm tra được (thiếu Shizuku). */
data class ScriptStatus(
    val running: Boolean? = null,
    val busy: Boolean = false,
    val pid: String = "",
    val detachedFromAdbd: Boolean? = null,
)

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
    val wirelessAdb = BuiltInWirelessAdb.state
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

    private val _script = MutableStateFlow(ScriptStatus())
    val script: StateFlow<ScriptStatus> = _script.asStateFlow()

    /**
     * Nhịp sống script ghi mỗi [WatchScript.ALIVE_INTERVAL_S] giây vào thư mục của app, đọc được cả khi Shizuku đã dừng.
     * true = còn chạy, false = đã dừng trong lần khởi động này, null = chưa chạy từ khi bật máy.
     */
    private fun scriptAlive(): Boolean? {
        val alive = scriptEventLog?.parentFile?.let { File(it, WatchScript.ALIVE_FILE) } ?: return null
        val now = System.currentTimeMillis()
        val bootAt = now - android.os.SystemClock.elapsedRealtime()
        val modified = alive.lastModified()
        if (modified < bootAt) return null
        return now - modified < (WatchScript.ALIVE_INTERVAL_S * 2 + ALIVE_GRACE_S) * 1000L
    }

    /** Script đã bật sau lần khởi động máy gần nhất thì nhiều khả năng còn chạy. */
    val scriptStartedThisBoot: Boolean
        get() {
            val bootAt = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()
            return settingsRepository.state.value.scriptStartedAt > bootAt
        }

    /** File script nền ghi mỗi lần chuyển hướng; nằm trong thư mục riêng của app để app đọc được. */
    private val scriptEventLog: File?
        get() = app.getExternalFilesDir(null)?.let { File(it, "script-events.log") }

    fun scriptContent(): String? {
        val active = config.value
        val watch = active.shellWatch ?: return null
        val log = scriptEventLog?.absolutePath ?: "/dev/null"
        val mic = active.target.overlayMic ?: catalog.target(AssistantTarget.GEMINI)?.overlayMic
        return WatchScript.content(
            watch = watch,
            keyevent = active.target.shellKeyevent ?: GEMINI_ASSIST_KEY,
            eventLog = log,
            dismissDelayMs = active.dismissDelayMs,
            mic = mic,
            voiceAction = active.voiceAction,
            buttonAction = active.buttonAction,
        )
    }

    private val scriptImportLock = Any()

    /** Đưa các lượt script nền đã ghi vào Nhật ký, rồi xoá file. */
    fun importScriptEvents() {
        val file = scriptEventLog ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val lines = synchronized(scriptImportLock) {
                val read = runCatching { if (file.exists()) file.readLines() else emptyList() }.getOrDefault(emptyList())
                if (read.isNotEmpty()) {
                    runCatching { file.writeText("") }
                }
                read
            }
            if (lines.isEmpty()) return@launch
            val active = config.value
            lines.mapNotNull(WatchScript::parseEvent).forEach { (at, kind) ->
                val mode = if (kind == "voice") active.voiceAction else active.buttonAction
                val dst = when (mode) {
                    ActionMode.GOOGLE_CTS -> "Google · Circle to Search"
                    ActionMode.GOOGLE_APP -> "Google"
                    ActionMode.GEMINI_SCREEN -> "Gemini · Hỏi màn hình"
                    ActionMode.GEMINI_VOICE -> "Gemini"
                    ActionMode.DEFAULT -> active.target.displayName
                }
                AttemptLog.record(
                    com.aikeybridge.diagnostics.Attempt(
                        at = at,
                        flow = if (kind == "voice") "${active.profile.assistantName} → $dst" else "Nút → $dst",
                        ok = true,
                        durationMs = SCRIPT_FLOW_MS,
                        detail = "script nền · ${if (kind == "voice") "khẩu lệnh" else "nút"}",
                    ),
                )
            }
            Diagnostics.info("script", "Nhập ${lines.size} lượt từ script nền.")
        }
    }

    fun checkScript() {
        refreshPermissions()
        if (!_permissions.value.shizukuGranted) {
            // Không có Shizuku thì không xem được tiến trình: dựa vào nhịp sống script tự ghi.
            _script.update { it.copy(running = scriptAlive(), pid = "", detachedFromAdbd = null) }
            return
        }
        viewModelScope.launch {
            val result = shell.exec(WatchScript.statusCommand())
            val pid = result.stdout.trim().lineSequence().firstOrNull()?.trim()?.substringBefore(' ').orEmpty()
            val running = result.stdout.isNotBlank()
            val detached = if (running && pid.isNotEmpty()) {
                val cg = shell.exec(WatchScript.cgroupCommand(pid))
                if (cg.ok && cg.stdout.isNotBlank()) WatchScript.processGroups(cg.stdout).isEmpty() else null
            } else {
                null
            }
            _script.update { it.copy(running = running, pid = pid, detachedFromAdbd = detached) }
        }
    }

    fun startScript(autoLockDevMode: Boolean = false) {
        val content = scriptContent() ?: run {
            _message.value = "${config.value.profile.displayName} chưa có mẫu cửa sổ cho script nền. Hãy bấm Dò trợ lý."
            return
        }
        if (_script.value.busy) return
        scriptEventLog?.parentFile?.mkdirs()
        viewModelScope.launch {
            _script.update { it.copy(busy = true) }
            refreshPermissions()
            if (!_permissions.value.shizukuGranted) {
                val backend = settingsRepository.state.value.shellBackend
                if (backend == ShellBackend.SHIZUKU) {
                    _script.update { it.copy(busy = false) }
                    connectOrOpenShizuku()
                    return@launch
                }
                val connected = BuiltInWirelessAdb.connectAndBootstrapDaemon(app)
                refreshPermissions()
                if (!connected && !_permissions.value.shizukuGranted) {
                    _script.update { it.copy(busy = false) }
                    _message.value = "Chưa kết nối được Engine hoặc Shizuku. Hãy ghép nối 6 số Gỡ lỗi không dây hoặc bật ứng dụng Shizuku."
                    return@launch
                }
            }
            // Đảm bảo EngineDaemonMain (UID 2000) đã chạy trong RAM và cập nhật lên bản mới nhất
            shell.exec(EngineDaemonMain.bootstrapCommand(app))
            val active = config.value
            if (active.provisioning.isNotEmpty()) {
                _setup.value = SetupUiState.from(active.provisioning, active.restore).copy(running = true)
                Diagnostics.info("setup", "Tự chạy ${active.provisioning.size} lệnh kích hoạt trước khi bật script · ${active.flowLabel}.")
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
            val install = shell.exec(WatchScript.installCommand(content))
            if (!install.ok) {
                Diagnostics.error("script", "Ghi script lỗi: ${install.output}")
                _message.value = "Không ghi được script: ${install.output.ifBlank { "lệnh trả về ${install.exitCode}" }}"
                _script.update { it.copy(busy = false) }
                return@launch
            }
            shell.exec(WatchScript.stopCommand())
            shell.exec(WatchScript.startCommand())
            delay(700)
            val status = shell.exec(WatchScript.statusCommand())
            val running = status.stdout.isNotBlank()
            if (running) settingsRepository.setScriptStartedAt(System.currentTimeMillis())
            val pid = status.stdout.trim().lineSequence().firstOrNull()?.trim()?.substringBefore(' ').orEmpty()
            val detached = if (running && pid.isNotEmpty()) logScriptCgroup(pid) else null

            // Đọc lại bảng quyền Vivo vào bộ nhớ đệm trước khi khóa Gỡ lỗi
            val vivoRes = shell.exec(PermissionsReader.VIVO_QUERY_CMD)
            if (vivoRes.ok) {
                PermissionsReader.updateFromVivoShell(app, vivoRes.stdout)
            }

            if (running && autoLockDevMode) {
                shell.exec(EngineDaemonMain.LOCK_DEV_MODE_CMD)
                Diagnostics.success("script", "Đã tự động tắt Tùy chọn nhà phát triển & Gỡ lỗi để dùng app ngân hàng / VNeID.")
            }

            _script.value = ScriptStatus(
                running = running,
                busy = false,
                pid = pid,
                detachedFromAdbd = detached,
            )
            refreshPermissions()
            Diagnostics.log(
                if (running) Diagnostics.Level.SUCCESS else Diagnostics.Level.ERROR,
                "script",
                if (running) "Script nền đang chạy (${WatchScript.PATH})." else "Script nền không chạy được.",
            )
            _message.value = when {
                running && autoLockDevMode ->
                    "Đã bật Script nền & tự tắt Gỡ lỗi / Tùy chọn nhà phát triển! Máy sạch 100% cho app ngân hàng & VNeID."
                running ->
                    "Script nền đang chạy. Bấm “Tắt ngay Gỡ lỗi & Tùy chọn nhà phát triển” bên dưới để dùng app ngân hàng — script vẫn chạy."
                else ->
                    "Script không chạy được — xem Nhật ký."
            }
        }
    }

    /**
     * Đảm bảo `akb-gemini.sh` đã được cài và chạy ngầm khi đang ở chế độ [UsageMode.SCRIPT].
     * Giúp người dùng dù bấm "Tắt Tùy chọn nhà phát triển" ngay ở bước 3 hay vừa chạy xong 9 lệnh ở bước 4
     * thì script nền vẫn luôn được cài đặt và kích hoạt đầy đủ.
     */
    private suspend fun ensureWatchScriptStartedQuietly() {
        if (settingsRepository.state.value.usageMode != UsageMode.SCRIPT) return
        val content = scriptContent() ?: return
        scriptEventLog?.parentFile?.mkdirs()
        if (!_permissions.value.readLogs) {
            shell.exec("pm grant ${app.packageName} android.permission.READ_LOGS")
        }
        val install = shell.exec(WatchScript.installCommand(content))
        if (!install.ok) return
        shell.exec(WatchScript.stopCommand())
        shell.exec(WatchScript.startCommand())
        delay(350)
        val status = shell.exec(WatchScript.statusCommand())
        val running = status.stdout.isNotBlank()
        if (running) {
            settingsRepository.setScriptStartedAt(System.currentTimeMillis())
            val pid = status.stdout.trim().lineSequence().firstOrNull()?.trim()?.substringBefore(' ').orEmpty()
            val detached = if (pid.isNotEmpty()) logScriptCgroup(pid) else null
            _script.value = ScriptStatus(running = true, busy = false, pid = pid, detachedFromAdbd = detached)
            Diagnostics.success("script", "Đã tự động cài đặt & khởi chạy Script nền (${WatchScript.PATH}).")
        }
    }

    /**
     * Tắt sạch Gỡ lỗi không dây, Gỡ lỗi USB và Tùy chọn nhà phát triển bằng 1 chạm qua Engine nội bộ.
     * Cả [WatchScript] và [EngineDaemonMain] đều đã tách khỏi cgroup của `adbd` nên vẫn tiếp tục chạy ngầm.
     */
    fun lockDeveloperMode() {
        viewModelScope.launch {
            shell.exec(EngineDaemonMain.bootstrapCommand(app))
            if (_script.value.running != true) {
                ensureWatchScriptStartedQuietly()
            }
            val res = shell.exec(EngineDaemonMain.LOCK_DEV_MODE_CMD)
            delay(200)
            refreshPermissions()
            if (res.ok || (!_permissions.value.devOptionsEnabled && !_permissions.value.adbEnabled)) {
                Diagnostics.success("security", "Đã tắt Tùy chọn nhà phát triển & Gỡ lỗi — an toàn 100% cho app ngân hàng & VNeID.")
                _message.value = "Đã bật Script nền & tắt sạch Tùy chọn nhà phát triển / Gỡ lỗi! Dùng app ngân hàng & VNeID bình thường."
            } else {
                Intents.openDeveloperSettings(app)
                _message.value = "Hãy gạt tắt công tắc Tùy chọn nhà phát triển ở đầu trang."
            }
        }
    }

    /** Kết nối Engine nội bộ qua Gỡ lỗi không dây (hoặc đánh thức daemon sẵn có trong RAM). */
    fun connectBuiltInEngine() {
        if (settingsRepository.state.value.shellBackend == ShellBackend.SHIZUKU) {
            settingsRepository.setShellBackend(ShellBackend.AUTO)
        }
        viewModelScope.launch {
            EngineBinderProvider.requestDaemonReconnect(app)
            delay(180)
            if (EngineBinderProvider.isAlive(app)) {
                shell.exec(EngineDaemonMain.bootstrapCommand(app))
                if (_script.value.running != true) ensureWatchScriptStartedQuietly()
                refreshPermissions()
                _message.value = "Đã kết nối Engine nội bộ (UID 2000) trong RAM!"
                return@launch
            }
            val ok = BuiltInWirelessAdb.connectAndBootstrapDaemon(app)
            if (ok && _script.value.running != true) {
                ensureWatchScriptStartedQuietly()
            }
            refreshPermissions()
            if (ok) {
                _message.value = "Đã kết nối Engine nội bộ qua Gỡ lỗi không dây!"
            } else {
                startWirelessPairing()
            }
        }
    }

    /** Mở ứng dụng Shizuku hoặc xin quyền Shizuku (dự phòng ổn định cho Engine nội bộ). */
    fun connectOrOpenShizuku() {
        val shizukuRawRunning = runCatching { !Shizuku.isPreV11() && Shizuku.pingBinder() }.getOrDefault(false)
        val shizukuRawGranted = shizukuRawRunning && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        if (shizukuRawGranted) {
            if (settingsRepository.state.value.shellBackend == ShellBackend.BUILTIN) {
                settingsRepository.setShellBackend(ShellBackend.AUTO)
            }
            refreshPermissions()
            _message.value = "Shizuku đã cấp quyền & sẵn sàng chạy lệnh!"
            return
        }
        if (shizukuRawRunning) {
            if (settingsRepository.state.value.shellBackend == ShellBackend.BUILTIN) {
                settingsRepository.setShellBackend(ShellBackend.AUTO)
            }
            runCatching { Shizuku.requestPermission(SHIZUKU_REQUEST_CODE) }
                .onFailure { _message.value = "Không gửi được yêu cầu quyền Shizuku: ${it.message}" }
            return
        }
        Intents.openShizuku(app)
        _message.value = if (_permissions.value.shizukuAppInstalled) {
            "Hãy mở Shizuku → bấm Start (Gỡ lỗi không dây), sau đó quay lại AI Key Bridge."
        } else {
            "Hãy cài đặt ứng dụng Shizuku, bấm Start rồi quay lại AI Key Bridge."
        }
    }

    fun startWirelessPairing() {
        BuiltInWirelessAdb.startPairingFlow(app)
        Intents.openWirelessDebuggingSettings(app)
        _message.value = "Mở Gỡ lỗi không dây → Ghép nối thiết bị bằng mã ghép nối, rồi nhập 6 số vào thanh thông báo."
    }

    fun stopWirelessPairing() {
        BuiltInWirelessAdb.stopPairingFlow(app)
    }

    fun submitWirelessPairingCode(code: String) {
        BuiltInWirelessAdb.submitPairingCode(app, code) { ok, msg ->
            viewModelScope.launch {
                if (ok && _script.value.running != true) {
                    ensureWatchScriptStartedQuietly()
                }
                refreshPermissions()
                _message.value = msg
            }
        }
    }

    /** Script còn trong nhóm tiến trình của adbd (như Android 16) thì neo giữ adbd qua adb_wifi_enabled=1. */
    private suspend fun logScriptCgroup(pid: String): Boolean? {
        val result = shell.exec(WatchScript.cgroupCommand(pid))
        if (!result.ok || result.stdout.isBlank()) {
            Diagnostics.warn("script", "Không đọc được cgroup của script (pid $pid): ${result.output}")
            return null
        }
        val groups = WatchScript.processGroups(result.stdout)
        return if (groups.isEmpty()) {
            Diagnostics.success("script", "Script đã ra nhóm gốc · tắt Gỡ lỗi không làm script dừng.")
            true
        } else {
            Diagnostics.info(
                "script",
                "Script trong nhóm ${groups.joinToString()} (Android 16) · đã neo giữ adbd (adb_wifi=1) để tắt Gỡ lỗi USB / Nhà phát triển không làm script dừng.",
            )
            false
        }
    }

    fun stopScript() {
        refreshPermissions()
        if (!_permissions.value.shizukuGranted) {
            _message.value = "Cần Shizuku để tắt script. Hoặc khởi động lại máy."
            return
        }
        viewModelScope.launch {
            _script.update { it.copy(busy = true) }
            runCatching {
                scriptEventLog?.parentFile?.let { File(it, WatchScript.ENABLED_FILE).delete() }
            }
            shell.exec(WatchScript.stopCommand())
            settingsRepository.setScriptStartedAt(0)
            delay(300)
            val status = shell.exec(WatchScript.statusCommand())
            _script.value = ScriptStatus(running = status.stdout.isNotBlank(), busy = false)
            Diagnostics.info("script", "Đã tắt script nền.")
            _message.value = "Đã tắt script nền."
        }
    }
    private var logSnifferJob: Job? = null

    private val binderReceived = Shizuku.OnBinderReceivedListener { refreshPermissions() }
    private val binderDead = Shizuku.OnBinderDeadListener { refreshPermissions() }
    private val engineListener: () -> Unit = {
        viewModelScope.launch { refreshPermissions() }
    }

    init {
        Diagnostics.init(app)
        AttemptLog.init(app)
        BuiltInWirelessAdb.init(app)
        refreshPermissions()
        EngineBinderProvider.addListener(engineListener)
        runCatching { Shizuku.addBinderReceivedListenerSticky(binderReceived) }
        runCatching { Shizuku.addBinderDeadListener(binderDead) }

        viewModelScope.launch {
            while (true) {
                delay(30_000)
                importScriptEvents()
            }
        }

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

    private val vivoQueryRunning = java.util.concurrent.atomic.AtomicBoolean(false)

    fun refreshPermissions() {
        importScriptEvents()
        val active = config.value
        val snap = PermissionsReader.read(
            context = app,
            settings = settingsRepository.state.value,
            targetPackages = active.target.packages,
            assistantRequired = false,
            nativeAssistantPackages = active.profile.assistantPackages,
        )
        _permissions.value = snap
        if (snap.shizukuGranted && !_setup.value.running && !_script.value.busy && !exporting &&
            vivoQueryRunning.compareAndSet(false, true)
        ) {
            viewModelScope.launch {
                try {
                    val res = shell.exec(PermissionsReader.VIVO_QUERY_CMD)
                    if (res.ok && PermissionsReader.updateFromVivoShell(app, res.stdout)) {
                        val latestActive = config.value
                        _permissions.value = PermissionsReader.read(
                            context = app,
                            settings = settingsRepository.state.value,
                            targetPackages = latestActive.target.packages,
                            assistantRequired = false,
                            nativeAssistantPackages = latestActive.profile.assistantPackages,
                        )
                    }
                } finally {
                    vivoQueryRunning.set(false)
                }
            }
        }
    }

    fun markOnboardingDone() {
        settingsRepository.setOnboardingDone(true)
        settingsRepository.setOnboardingStep(0)
    }

    fun setOnboardingStep(step: Int) {
        if (settingsRepository.state.value.onboardingStep != step) settingsRepository.setOnboardingStep(step)
    }

    /**
     * Lệnh khởi động Engine nội bộ (UID 2000) qua ADB cho máy này — hoàn toàn không cần cài Shizuku.
     */
    val shizukuStartCommand: String
        get() = "adb shell \"${EngineDaemonMain.bootstrapCommand(app)}\""

    fun setAutoMic(value: Boolean) {
        settingsRepository.setAutoMic(value)
        syncRunningScriptIfNeeded()
    }

    fun setLockScreen(value: Boolean) = settingsRepository.setLockScreen(value)

    fun setRedirectEnabled(value: Boolean) = settingsRepository.setRedirectEnabled(value)

    fun setDedupe(value: Boolean) = settingsRepository.setDedupe(value)

    fun setThemeMode(mode: ThemeMode) = settingsRepository.setThemeMode(mode)

    fun setShellBackend(backend: ShellBackend) {
        settingsRepository.setShellBackend(backend)
        refreshPermissions()
        _message.value = "Công cụ chạy lệnh: ${backend.label}."
    }

    fun setVoiceAction(mode: ActionMode) {
        settingsRepository.setVoiceAction(mode)
        syncRunningScriptIfNeeded(announce = "Khẩu lệnh: ${mode.title}")
    }

    fun setButtonAction(mode: ActionMode) {
        settingsRepository.setButtonAction(mode)
        syncRunningScriptIfNeeded(announce = "Nút cứng: ${mode.title}")
    }

    fun setShortcutAction(mode: ActionMode) {
        settingsRepository.setShortcutAction(mode)
        _message.value = "Lối tắt Gọi AI: ${mode.title}."
    }

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
        syncRunningScriptIfNeeded()
    }

    private fun syncRunningScriptIfNeeded(announce: String? = null) {
        viewModelScope.launch {
            delay(80)
            val content = scriptContent()
            val runningOrAlive = _script.value.running == true || scriptAlive() == true
            if (content != null && runningOrAlive && ShizukuShell.isEngineOrShizukuReady(app)) {
                val install = shell.exec(WatchScript.installCommand(content))
                if (install.ok) {
                    shell.exec(WatchScript.stopCommand())
                    shell.exec(WatchScript.startCommand())
                    Diagnostics.info("script", "Đã tự cập nhật Script nền theo tùy chọn mới.")
                    if (announce != null) {
                        _message.value = "$announce · đã cập nhật Script nền."
                    }
                    return@launch
                }
            }
            if (announce != null) {
                _message.value = announce
            }
        }
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
        if (announce) {
            _message.value = when (mode) {
                UsageMode.ONE_TIME -> "Đã chuyển sang Cấu hình một lần — không cần Trợ năng."
                UsageMode.SCRIPT -> "Đã chuyển sang Script nền — bật script trong màn Script nền."
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
        val backend = settingsRepository.state.value.shellBackend
        val snapshot = _permissions.value
        if (backend == ShellBackend.SHIZUKU) {
            connectOrOpenShizuku()
            return
        }
        if (backend == ShellBackend.BUILTIN) {
            if (snapshot.engineDaemonRunning) {
                _message.value = "Engine nội bộ (UID 2000) đã kết nối sẵn sàng!"
            } else {
                connectBuiltInEngine()
            }
            return
        }
        if (snapshot.shizukuBinderAlive && !snapshot.shizukuPermissionGranted) {
            connectOrOpenShizuku()
            return
        }
        if (snapshot.shizukuPermissionGranted || snapshot.engineDaemonRunning) {
            _message.value = when {
                snapshot.shizukuPermissionGranted && snapshot.engineDaemonRunning ->
                    "Cả Shizuku và Engine nội bộ đều đã sẵn sàng!"
                snapshot.shizukuPermissionGranted ->
                    "Shizuku đã cấp quyền & sẵn sàng!"
                else ->
                    "Engine nội bộ (UID 2000) đã kết nối sẵn sàng!"
            }
            return
        }
        connectBuiltInEngine()
    }

    fun onShizukuPermissionResult(granted: Boolean) {
        refreshPermissions()
        if (granted) {
            viewModelScope.launch {
                if (!EngineBinderProvider.isAlive(app)) {
                    shell.exec(EngineDaemonMain.bootstrapCommand(app))
                }
                if (_script.value.running != true) {
                    ensureWatchScriptStartedQuietly()
                }
                refreshPermissions()
            }
            _message.value = "Đã cấp quyền Shizuku & sẵn sàng chạy ${_setup.value.steps.size} lệnh."
        } else {
            _message.value = "Bạn đã từ chối quyền Shizuku."
        }
    }

    fun runProvisioning() {
        if (_setup.value.running) return
        val active = config.value
        if (active.provisioning.isEmpty()) {
            settingsRepository.setProvisionedFingerprint(device.fingerprint)
            _message.value = "${active.target.displayName} không cần lệnh shell nào."
            return
        }

        viewModelScope.launch {
            _setup.value = SetupUiState.from(active.provisioning, active.restore).copy(running = true)
            if (!_permissions.value.shizukuGranted) {
                val backend = settingsRepository.state.value.shellBackend
                if (backend == ShellBackend.SHIZUKU) {
                    _setup.update { it.copy(running = false) }
                    connectOrOpenShizuku()
                    return@launch
                }
                BuiltInWirelessAdb.connectAndBootstrapDaemon(app)
                refreshPermissions()
                if (!_permissions.value.shizukuGranted) {
                    _setup.update { it.copy(running = false) }
                    _message.value = "Chưa kết nối được Engine hoặc Shizuku. Hãy ghép nối 6 số Gỡ lỗi không dây hoặc bật ứng dụng Shizuku."
                    return@launch
                }
            }
            shell.exec(EngineDaemonMain.bootstrapCommand(app))
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
            if (_script.value.running != true) {
                ensureWatchScriptStartedQuietly()
            }
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
        EngineBinderProvider.removeListener(engineListener)
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
        const val ALIVE_GRACE_S = 15
        const val SNIFF_WINDOW_MS = 8_000L
        /**
         * KEYCODE_ASSIST, như giữ nút nguồn: lớp phủ Gemini kèm ảnh chụp màn hình nên khoanh tròn để hỏi được.
         * 231 (VOICE_ASSIST) bật Micro ngay nhưng không khoanh được, chạm màn hình là lớp phủ tắt.
         */
        const val GEMINI_ASSIST_KEY = 219

        /** Ước lượng thời gian script gọi trợ lý (không đo được từ trong script). */
        const val SCRIPT_FLOW_MS = 600L

        /** Khoá settings mang định danh người dùng/máy — không đưa vào log xuất. */
        const val SETTINGS_PRIVATE = "android_id|bluetooth_address|bluetooth_name|device_name|token|account|ssid|password|email|phone|imei|serial"
    }
}
