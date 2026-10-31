package com.aikeybridge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aikeybridge.shareLog
import com.aikeybridge.ui.components.BottomNav
import com.aikeybridge.ui.screens.AppPickerScreen
import com.aikeybridge.ui.screens.HardwareScreen
import com.aikeybridge.ui.screens.ModesScreen
import com.aikeybridge.ui.screens.OverviewScreen
import com.aikeybridge.ui.screens.PermissionsScreen
import com.aikeybridge.ui.screens.ProfilesScreen
import com.aikeybridge.ui.screens.ScriptScreen
import com.aikeybridge.ui.screens.SettingsScreen
import com.aikeybridge.ui.screens.ShortcutsScreen
import com.aikeybridge.ui.screens.SetupScreen
import com.aikeybridge.ui.screens.TriggersScreen
import com.aikeybridge.ui.theme.Accent
import com.aikeybridge.ui.theme.Ink
import com.aikeybridge.ui.theme.Surface
import com.aikeybridge.ui.theme.TextMain

/** Màn con mở chồng lên tab, có nút back riêng. */
private enum class Overlay { PERMISSIONS, SETUP, PROFILES, TRIGGERS, APPS, HARDWARE, SHORTCUTS, SCRIPT }

private const val TAB_OVERVIEW = 0
private const val TAB_MODES = 1
private const val TAB_SYSTEM = 2

@Composable
fun BridgeApp(viewModel: AppViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val setup by viewModel.setup.collectAsStateWithLifecycle()
    val entries by viewModel.logEntries.collectAsStateWithLifecycle()
    val attempts by viewModel.attempts.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val redirectState by viewModel.redirectState.collectAsStateWithLifecycle()
    val romChanged by viewModel.romChanged.collectAsStateWithLifecycle()
    val sniffer by viewModel.sniffer.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val logSniff by viewModel.logSniffer.collectAsStateWithLifecycle()
    val scriptStatus by viewModel.script.collectAsStateWithLifecycle()
    val logAccess by viewModel.logAccess.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val exportFile by viewModel.exportFile.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(TAB_OVERVIEW) }
    var stack by rememberSaveable { mutableStateOf(listOf<String>()) }

    // Vào thẳng Trang chủ với lộ trình 3 bước trực quan, không bắt người dùng qua 5-6 màn onboarding rời rạc
    LaunchedEffect(settings.onboardingDone) {
        if (!settings.onboardingDone) {
            viewModel.markOnboardingDone()
        }
    }

    fun push(overlay: Overlay) {
        stack = stack + overlay.name
    }

    fun pop() {
        stack = stack.dropLast(1)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(exportFile) {
        exportFile?.let {
            if (!shareLog(context, it)) {
                viewModel.showMessage("Đã lưu log tại ${it.absolutePath} (không mở được bảng chia sẻ).")
            }
            viewModel.consumeExport()
        }
    }

    BackHandler(enabled = stack.isNotEmpty()) { pop() }
    BackHandler(enabled = stack.isEmpty() && tab != TAB_OVERVIEW) { tab = TAB_OVERVIEW }

    val screen = when {
        stack.isNotEmpty() -> stack.last()
        else -> "tab:$tab"
    }

    Scaffold(
        containerColor = Ink,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (stack.isEmpty()) {
                BottomNav(selected = tab, onSelect = { tab = it })
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "screen",
            ) { key ->
                when (key) {
                    Overlay.SCRIPT.name -> ScriptScreen(
                        config = config,
                        permissions = permissions,
                        status = scriptStatus,
                        startedThisBoot = viewModel.scriptStartedThisBoot,
                        scriptContent = viewModel.scriptContent(),
                        onboarding = false,
                        onBack = ::pop,
                        onCheck = viewModel::checkScript,
                        onStart = { viewModel.startScript(autoLockDevMode = false) },
                        onStop = viewModel::stopScript,
                        onFinish = ::pop,
                        onOpenSetup = { push(Overlay.SETUP) },
                        onOpenPermissions = { push(Overlay.PERMISSIONS) },
                        onOpenTriggers = { push(Overlay.TRIGGERS) },
                        onRequestShizuku = viewModel::requestShizukuPermission,
                        onConnectShizuku = viewModel::connectOrOpenShizuku,
                        onConnectBuiltInEngine = viewModel::connectBuiltInEngine,
                        shellBackend = settings.shellBackend,
                        onSetShellBackend = viewModel::setShellBackend,
                        onStartAndLock = { viewModel.startScript(autoLockDevMode = true) },
                        onLockDevMode = viewModel::lockDeveloperMode,
                        onStartWirelessPairing = viewModel::startWirelessPairing,
                        onMessage = viewModel::showMessage,
                    )
                    Overlay.SHORTCUTS.name -> ShortcutsScreen(
                        config = config,
                        usageMode = settings.usageMode,
                        accessibilityOn = permissions.accessibility,
                        onboarding = false,
                        onBack = ::pop,
                        onFinish = ::pop,
                    )
                    Overlay.PERMISSIONS.name -> PermissionsScreen(
                        config = config,
                        permissions = permissions,
                        settings = settings,
                        onboarding = false,
                        onBack = ::pop,
                        onContinue = ::pop,
                        onSkip = ::pop,
                        onToggleAutostart = viewModel::setManualAutostart,
                        onRequestShizuku = viewModel::requestShizukuPermission,
                        onConnectShizuku = viewModel::connectOrOpenShizuku,
                        onStartWirelessPairing = viewModel::startWirelessPairing,
                        onLockDevMode = viewModel::lockDeveloperMode,
                    )
                    Overlay.SETUP.name -> SetupScreen(
                        config = config,
                        permissions = permissions,
                        setup = setup,
                        onboarding = false,
                        onBack = ::pop,
                        onRun = viewModel::runProvisioning,
                        shizukuStartCommand = viewModel.shizukuStartCommand,
                        onRequestShizuku = viewModel::requestShizukuPermission,
                        onConnectShizuku = viewModel::connectOrOpenShizuku,
                        shellBackend = settings.shellBackend,
                        onSetShellBackend = viewModel::setShellBackend,
                        onStartWirelessPairing = viewModel::startWirelessPairing,
                        onFinish = ::pop,
                        onMessage = viewModel::showMessage,
                        onOpenPermissions = { push(Overlay.PERMISSIONS) },
                    )
                    Overlay.PROFILES.name -> ProfilesScreen(
                        config = config,
                        device = viewModel.device,
                        profiles = viewModel.catalog.profiles,
                        onBack = ::pop,
                        onSelect = viewModel::selectProfile,
                        onOpenTriggers = { push(Overlay.TRIGGERS) },
                    )
                    Overlay.TRIGGERS.name -> TriggersScreen(
                        config = config,
                        customTriggers = settings.customTriggers,
                        sniffer = sniffer,
                        accessibilityOn = permissions.shizukuGranted || permissions.accessibility,
                        onBack = ::pop,
                        onStart = viewModel::startSniffer,
                        onStop = viewModel::stopSniffer,
                        onSave = viewModel::saveTrigger,
                        onRemove = viewModel::removeTrigger,
                    )
                    Overlay.APPS.name -> AppPickerScreen(
                        apps = apps,
                        selectedPackage = settings.customTargetPackage.takeIf { config.target.id == "custom" },
                        onLoad = viewModel::loadApps,
                        onBack = ::pop,
                        onPick = {
                            viewModel.selectCustomTarget(it)
                            pop()
                        },
                    )
                    Overlay.HARDWARE.name -> HardwareScreen(
                        config = config,
                        settings = settings,
                        permissions = permissions,
                        logAccess = logAccess,
                        sniff = logSniff,
                        readLogsCommand = viewModel.readLogsCommand,
                        onBack = ::pop,
                        onSetMode = viewModel::setHardwareMode,
                        onGrantReadLogs = viewModel::grantReadLogs,
                        onStartSniff = viewModel::startLogSniffer,
                        onStopSniff = viewModel::stopLogSniffer,
                        onSave = viewModel::saveLogPattern,
                        onRemove = viewModel::removeLogPattern,
                        onMessage = viewModel::showMessage,
                    )
                    "tab:$TAB_MODES" -> ModesScreen(
                        config = config,
                        settings = settings,
                        permissions = permissions,
                        targets = viewModel.catalog.targets,
                        onOpenLogs = { tab = TAB_SYSTEM },
                        onSelectTarget = viewModel::selectTarget,
                        onPickCustomApp = { push(Overlay.APPS) },
                        onToggleRedirect = viewModel::setRedirectEnabled,
                        onToggleAutoMic = viewModel::setAutoMic,
                        onToggleLockScreen = viewModel::setLockScreen,
                        onToggleDedupe = viewModel::setDedupe,
                        onNudgeDismiss = viewModel::nudgeDismissDelay,
                        onNudgeMicTimeout = viewModel::nudgeMicTimeout,
                        onResetTuning = viewModel::resetTuning,
                        onOpenHardware = { push(Overlay.HARDWARE) },
                        onSetUsageMode = viewModel::setUsageMode,
                        onOpenShortcuts = { push(Overlay.SHORTCUTS) },
                        onOpenScript = { push(Overlay.SCRIPT) },
                        onOpenProfiles = { push(Overlay.PROFILES) },
                        onOpenTriggers = { push(Overlay.TRIGGERS) },
                        scriptStatus = scriptStatus,
                        scriptStartedThisBoot = viewModel.scriptStartedThisBoot,
                        onCheckScript = viewModel::checkScript,
                        onSetVoiceAction = viewModel::setVoiceAction,
                        onSetButtonAction = viewModel::setButtonAction,
                        onSetShortcutAction = viewModel::setShortcutAction,
                    )
                    "tab:$TAB_SYSTEM" -> SettingsScreen(
                        config = config,
                        settings = settings,
                        permissions = permissions,
                        setup = setup,
                        summary = summary,
                        attempts = attempts,
                        entries = entries,
                        romChanged = romChanged,
                        entryCount = entries.size,
                        attemptCount = attempts.size,
                        now = now,
                        onOpenProfiles = { push(Overlay.PROFILES) },
                        onOpenTriggers = { push(Overlay.TRIGGERS) },
                        onOpenPermissions = { push(Overlay.PERMISSIONS) },
                        onOpenShortcuts = { push(Overlay.SHORTCUTS) },
                        onOpenScript = { push(Overlay.SCRIPT) },
                        onOpenSetup = { push(Overlay.SETUP) },
                        scriptStatus = scriptStatus,
                        scriptStartedThisBoot = viewModel.scriptStartedThisBoot,
                        onCheckScript = viewModel::checkScript,
                        onCheckRom = viewModel::checkRom,
                        onExport = viewModel::exportLogs,
                        onClear = viewModel::clearLogs,
                        onRestore = viewModel::restoreDefaults,
                        onSetThemeMode = viewModel::setThemeMode,
                        onSetShellBackend = viewModel::setShellBackend,
                    )
                    else -> OverviewScreen(
                        config = config,
                        permissions = permissions,
                        settings = settings,
                        setup = setup,
                        summary = summary,
                        redirectState = redirectState,
                        romChanged = romChanged,
                        now = now,
                        onOpenLogs = { tab = TAB_SYSTEM },
                        onOpenPermissions = { push(Overlay.PERMISSIONS) },
                        onOpenSetup = { push(Overlay.SETUP) },
                        onOpenModes = { tab = TAB_MODES },
                        onOpenTriggers = { push(Overlay.TRIGGERS) },
                        onOpenProfiles = { push(Overlay.PROFILES) },
                        onOpenScript = { push(Overlay.SCRIPT) },
                        scriptStatus = scriptStatus,
                        scriptStartedThisBoot = viewModel.scriptStartedThisBoot,
                        onCheckScript = viewModel::checkScript,
                        onStartScript = { viewModel.startScript(autoLockDevMode = false) },
                        onStartAndLock = { viewModel.startScript(autoLockDevMode = true) },
                        onStopScript = viewModel::stopScript,
                        onLockDevMode = viewModel::lockDeveloperMode,
                        onRequestShizuku = viewModel::requestShizukuPermission,
                        onConnectShizuku = viewModel::connectOrOpenShizuku,
                        onConnectBuiltInEngine = viewModel::connectBuiltInEngine,
                        onStartWirelessPairing = viewModel::startWirelessPairing,
                        onSetShellBackend = viewModel::setShellBackend,
                        onSetVoiceAction = viewModel::setVoiceAction,
                        onSetButtonAction = viewModel::setButtonAction,
                        onSetShortcutAction = viewModel::setShortcutAction,
                        onToggleRedirect = viewModel::setRedirectEnabled,
                        onToggleAutoMic = viewModel::setAutoMic,
                        onToggleLockScreen = viewModel::setLockScreen,
                    )
                }
            }
            // Thông báo hiện ở trên: nút hành động đều nằm ở đáy màn, để dưới sẽ che mất.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    modifier = Modifier.pointerInput(data) {
                        // Vuốt lên để tắt sớm nếu nó che nút back/tiêu đề.
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -8f) data.dismiss()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    containerColor = Surface,
                    contentColor = TextMain,
                    actionColor = Accent,
                )
            }
        }
    }
}
