package com.aikeybridge.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikeybridge.R
import com.aikeybridge.data.ActionMode
import com.aikeybridge.data.ActiveConfig
import com.aikeybridge.data.AppSettings
import com.aikeybridge.data.PermissionSnapshot
import com.aikeybridge.data.ShellBackend
import com.aikeybridge.data.UsageMode
import com.aikeybridge.diagnostics.AttemptSummary
import com.aikeybridge.service.RedirectState
import com.aikeybridge.shell.WatchScript
import com.aikeybridge.ui.Format
import com.aikeybridge.ui.Intents
import com.aikeybridge.ui.ScriptStatus
import com.aikeybridge.ui.SetupUiState
import com.aikeybridge.ui.StepStatus
import com.aikeybridge.ui.components.BodyText
import com.aikeybridge.ui.components.Chip
import com.aikeybridge.ui.components.Gap
import com.aikeybridge.ui.components.IconTile
import com.aikeybridge.ui.components.LucideIcon
import com.aikeybridge.ui.components.MonoText
import com.aikeybridge.ui.components.NavRow
import com.aikeybridge.ui.components.OutlineCta
import com.aikeybridge.ui.components.Panel
import com.aikeybridge.ui.components.PrimaryCta
import com.aikeybridge.ui.components.SectionLabel
import com.aikeybridge.ui.components.SegmentOption
import com.aikeybridge.ui.components.Segmented
import com.aikeybridge.ui.components.StatusDot
import com.aikeybridge.ui.components.TabHeader
import com.aikeybridge.ui.components.hairlineBottom
import com.aikeybridge.ui.theme.Accent
import com.aikeybridge.ui.theme.CommunityBg
import com.aikeybridge.ui.theme.CommunityLine
import com.aikeybridge.ui.theme.CommunityTitle
import com.aikeybridge.ui.theme.Danger
import com.aikeybridge.ui.theme.DangerBg
import com.aikeybridge.ui.theme.DangerLine
import com.aikeybridge.ui.theme.Hairline
import com.aikeybridge.ui.theme.Ink
import com.aikeybridge.ui.theme.Muted
import com.aikeybridge.ui.theme.Surface
import com.aikeybridge.ui.theme.Surface2
import com.aikeybridge.ui.theme.TextBody
import com.aikeybridge.ui.theme.TextDim
import com.aikeybridge.ui.theme.TextFaint
import com.aikeybridge.ui.theme.TextGhost
import com.aikeybridge.ui.theme.TextLabel
import com.aikeybridge.ui.theme.TextMain
import com.aikeybridge.ui.theme.Warn

@Composable
fun OverviewScreen(
    config: ActiveConfig,
    permissions: PermissionSnapshot,
    settings: AppSettings,
    setup: SetupUiState,
    summary: AttemptSummary,
    redirectState: RedirectState,
    romChanged: Boolean,
    now: Long,
    onOpenLogs: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenModes: () -> Unit,
    onOpenTriggers: () -> Unit,
    onOpenProfiles: () -> Unit = {},
    onOpenScript: () -> Unit,
    scriptStatus: ScriptStatus,
    scriptStartedThisBoot: Boolean,
    onCheckScript: () -> Unit,
    onStartScript: () -> Unit,
    onStartAndLock: () -> Unit = onStartScript,
    onStopScript: () -> Unit,
    onLockDevMode: () -> Unit = {},
    onRequestShizuku: () -> Unit = {},
    onConnectShizuku: () -> Unit = onRequestShizuku,
    onConnectBuiltInEngine: () -> Unit = onRequestShizuku,
    onStartWirelessPairing: () -> Unit = {},
    onSetShellBackend: (ShellBackend) -> Unit = {},
    onSetVoiceAction: (ActionMode) -> Unit = {},
    onSetButtonAction: (ActionMode) -> Unit = {},
    onSetShortcutAction: (ActionMode) -> Unit = {},
    onToggleRedirect: (Boolean) -> Unit,
    onToggleAutoMic: (Boolean) -> Unit,
    onToggleLockScreen: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val oneTime = settings.usageMode == UsageMode.ONE_TIME
    val scriptOn = scriptStatus.running == true || (scriptStatus.running == null && scriptStartedThisBoot)
    val bankSafe = !permissions.devOptionsEnabled && !permissions.adbEnabled
    val wakePhrase = config.profile.wakePhrase?.let { "“$it”" } ?: config.profile.assistantName

    if (config.shellWatch != null) {
        LaunchedEffect(permissions.shizukuGranted) { onCheckScript() }
    }

    val guideItems = rememberPermissionGuideItems(
        config = config,
        permissions = permissions,
        settings = settings,
        onRequestShizuku = onRequestShizuku,
        onConnectShizuku = onConnectShizuku,
        onStartWirelessPairing = onStartWirelessPairing,
        onLockDevMode = onLockDevMode,
    )
    val requiredGuideItems = guideItems.filter { it.required }
    val missingRequired = requiredGuideItems.filter { !it.granted }

    var activeGuideIndex by rememberSaveable { mutableIntStateOf(-1) }
    var editingActionSlot by rememberSaveable { mutableStateOf<String?>(null) }

    // Trạng thái đóng/mở từng bước: tự động mở bước đang cần người dùng thực hiện
    var step1Expanded by rememberSaveable(permissions.ready) { mutableStateOf(!permissions.ready) }
    var step2Expanded by rememberSaveable(permissions.ready, scriptOn) {
        mutableStateOf(permissions.ready && !scriptOn)
    }
    var step2TechExpanded by rememberSaveable { mutableStateOf(false) }
    var step3Expanded by rememberSaveable(scriptOn, bankSafe) {
        mutableStateOf(scriptOn && !bankSafe)
    }

    Column(modifier.fillMaxSize()) {
        TabHeader("AI Key Bridge", "Trang chủ") {
            Chip(
                text = config.profile.displayName,
                dot = if (config.profile.verified) Accent else Warn,
                modifier = Modifier.clickable(role = Role.Button, onClick = onOpenProfiles),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. THẺ TRẠNG THÁI TỔNG THỂ (HERO STATUS CARD)
            HeroStatusCard(
                config = config,
                permissions = permissions,
                scriptOn = scriptOn,
                scriptBusy = scriptStatus.busy,
                bankSafe = bankSafe,
                oneTime = oneTime,
                romChanged = romChanged,
                summary = summary,
                now = now,
                onPrimaryAction = when {
                    !permissions.ready -> {
                        {
                            step1Expanded = true
                            val next = missingRequired.firstOrNull()
                            if (next != null) next.onOpenSettings()
                        }
                    }
                    !oneTime && !scriptOn -> {
                        {
                            step2Expanded = true
                            val canAutoConnect = permissions.adbWifiEnabled && permissions.wirelessAdbPaired
                            val preferShizuku = settings.shellBackend == ShellBackend.SHIZUKU ||
                                (settings.shellBackend == ShellBackend.AUTO && permissions.shizukuBinderAlive && !permissions.shizukuPermissionGranted)
                            when {
                                permissions.shizukuGranted && !bankSafe -> onStartAndLock()
                                permissions.shizukuGranted -> onStartScript()
                                preferShizuku -> onConnectShizuku()
                                permissions.shizukuRunning || canAutoConnect -> onRequestShizuku()
                                else -> onStartWirelessPairing()
                            }
                        }
                    }
                    !bankSafe -> {
                        {
                            if (permissions.shizukuGranted) {
                                onLockDevMode()
                            } else {
                                Intents.openDeveloperSettings(context)
                            }
                        }
                    }
                    romChanged -> onOpenSetup
                    else -> null
                },
            )

            // 2. LỘ TRÌNH 3 BƯỚC TRỰC QUAN
            SectionLabel(
                "Lộ trình thiết lập (3 bước)",
                Modifier.padding(top = 2.dp),
                action = if (permissions.ready && scriptOn && bankSafe) "Hoàn tất 3/3 ✓" else null,
            )

            // --- BƯỚC 1: CẤP QUYỀN HỆ THỐNG ---
            StepCard(
                stepNumber = 1,
                title = "Cấp quyền cơ bản",
                subtitle = if (missingRequired.isEmpty()) {
                    "Đã bật đủ ${requiredGuideItems.size}/${requiredGuideItems.size} nhóm quyền bắt buộc"
                } else {
                    "Còn ${missingRequired.size} mục cần bật · Có ảnh minh họa từng bước"
                },
                completed = missingRequired.isEmpty(),
                badgeText = "${requiredGuideItems.size - missingRequired.size}/${requiredGuideItems.size} đạt",
                expanded = step1Expanded,
                onToggleExpand = { step1Expanded = !step1Expanded },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface2)
                        .border(1.dp, Hairline, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                ) {
                    requiredGuideItems.forEachIndexed { idx, item ->
                        val globalIndex = guideItems.indexOf(item)
                        CleanPermRow(
                            item = item,
                            divider = idx != requiredGuideItems.lastIndex,
                            onOpenSettings = item.onOpenSettings,
                            onOpenGuide = { activeGuideIndex = globalIndex },
                        )
                    }
                }
                Gap(8.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClick = onOpenPermissions)
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BodyText(
                        "Xem thêm hướng dẫn Màn hình khóa Gemini, Giọng nói & Nút AI",
                        12.sp,
                        Accent,
                        weight = FontWeight.Medium,
                    )
                    LucideIcon(R.drawable.lc_chevron_right, Accent, 14.dp)
                }
            }

            // --- BƯỚC 2: KẾT NỐI & BẬT SCRIPT NỀN ---
            val canAutoConnectWireless = permissions.adbWifiEnabled && permissions.wirelessAdbPaired
            val preferShizuku = settings.shellBackend == ShellBackend.SHIZUKU ||
                (settings.shellBackend == ShellBackend.AUTO && permissions.shizukuBinderAlive && !permissions.shizukuPermissionGranted)

            StepCard(
                stepNumber = 2,
                title = "Bật Script nền",
                subtitle = when {
                    scriptStatus.busy -> "Đang chạy lệnh kích hoạt & bật Script…"
                    scriptOn -> "Đang chạy ngầm · Thay ${config.profile.assistantName} bằng ${config.target.displayName}"
                    permissions.shizukuGranted -> "Công cụ chạy lệnh đã kết nối · Bấm để bật Script"
                    else -> "Kết nối Gỡ lỗi không dây (6 số) hoặc Shizuku để kích hoạt"
                },
                completed = scriptOn,
                badgeText = when {
                    scriptStatus.busy -> "Đang bật…"
                    scriptOn -> "Đang chạy"
                    permissions.shizukuGranted -> "Sẵn sàng"
                    else -> "Chưa bật"
                },
                expanded = step2Expanded,
                onToggleExpand = { step2Expanded = !step2Expanded },
            ) {
                BodyText(
                    when {
                        config.shellWatch == null ->
                            "${config.profile.displayName} chưa có mẫu cửa sổ mặc định. Hãy bấm Dò cửa sổ trợ lý để tự bắt cửa sổ."
                        scriptOn ->
                            "Script đang chạy độc lập trong RAM (không dùng Trợ năng). Kể cả khi bạn đóng ứng dụng hoặc tắt Gỡ lỗi, Script vẫn tiếp tục hoạt động cho đến khi khởi động lại máy."
                        permissions.shizukuGranted ->
                            "Bấm nút bên dưới: ứng dụng sẽ tự động chạy ${setup.steps.size} lệnh cấu hình hệ thống, bật Script ngầm và khóa Gỡ lỗi."
                        else ->
                            "Để bật Script lần đầu sau khi mở máy, hãy ghép nối 6 số Gỡ lỗi không dây (Engine nội bộ) hoặc dùng ứng dụng Shizuku."
                    },
                    12.5.sp,
                    Muted,
                    lineHeight = 18.sp,
                )
                Gap(12.dp)

                if (config.shellWatch == null) {
                    OutlineCta(
                        text = "Dò cửa sổ trợ lý gốc",
                        icon = R.drawable.lc_scan_search,
                        iconTint = Accent,
                        onClick = onOpenTriggers,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (permissions.shizukuGranted) {
                    val primaryText = when {
                        scriptStatus.busy -> "Đang kích hoạt & bật Script…"
                        !bankSafe && scriptOn -> "Cập nhật Script & Khóa Gỡ lỗi"
                        !bankSafe -> "Bật Script & Tự khóa Gỡ lỗi"
                        scriptOn -> "Bật lại / Cập nhật Script nền"
                        else -> "Kích hoạt & Bật Script nền"
                    }
                    PrimaryCta(
                        text = primaryText,
                        onClick = if (!bankSafe) onStartAndLock else onStartScript,
                        leadingIcon = R.drawable.lc_terminal,
                        loading = scriptStatus.busy,
                        modifier = Modifier.fillMaxWidth(),
                        height = 48.dp,
                        radius = 14.dp,
                    )
                    if (scriptOn) {
                        Gap(8.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlineCta(
                                text = "Kiểm tra lại",
                                onClick = onCheckScript,
                                modifier = Modifier.weight(1f),
                                height = 40.dp,
                                radius = 12.dp,
                            )
                            OutlineCta(
                                text = "Tắt Script",
                                onClick = onStopScript,
                                modifier = Modifier.weight(1f),
                                height = 40.dp,
                                radius = 12.dp,
                            )
                        }
                    }
                } else {
                    val connectText = when {
                        preferShizuku && permissions.shizukuBinderAlive -> "Cấp quyền Shizuku ngay"
                        preferShizuku && permissions.shizukuAppInstalled -> "Mở ứng dụng Shizuku"
                        preferShizuku -> "Cài đặt / Mở Shizuku"
                        permissions.shizukuRunning -> "Kết nối công cụ chạy lệnh"
                        canAutoConnectWireless -> "Kết nối Engine (Gỡ lỗi không dây)"
                        else -> "Ghép nối Gỡ lỗi không dây (6 số)"
                    }
                    val connectAction: () -> Unit = when {
                        preferShizuku -> onConnectShizuku
                        permissions.shizukuRunning || canAutoConnectWireless -> onRequestShizuku
                        else -> onStartWirelessPairing
                    }
                    PrimaryCta(
                        text = connectText,
                        onClick = connectAction,
                        leadingIcon = R.drawable.lc_terminal,
                        modifier = Modifier.fillMaxWidth(),
                        height = 48.dp,
                        radius = 14.dp,
                    )
                    Gap(8.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineCta(
                            text = if (preferShizuku) {
                                if (canAutoConnectWireless) "Dùng Engine nội bộ" else "Ghép nối 6 số"
                            } else {
                                if (permissions.shizukuBinderAlive) "Cấp quyền Shizuku" else "Dùng Shizuku"
                            },
                            onClick = if (preferShizuku) {
                                if (canAutoConnectWireless) onConnectBuiltInEngine else onStartWirelessPairing
                            } else {
                                onConnectShizuku
                            },
                            modifier = Modifier.weight(1f),
                            height = 40.dp,
                            radius = 12.dp,
                        )
                        val shizukuGuideIdx = guideItems.indexOfFirst { it.id == "shizuku" }
                        if (shizukuGuideIdx >= 0) {
                            OutlineCta(
                                text = "Ảnh hướng dẫn",
                                onClick = { activeGuideIndex = shizukuGuideIdx },
                                modifier = Modifier.weight(1f),
                                height = 40.dp,
                                radius = 12.dp,
                            )
                        }
                    }
                }

                // Ngăn thu gọn Chi tiết kỹ thuật (PID, cgroup, Shell Backend, 9 lệnh hệ thống)
                Gap(10.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface2)
                        .clickable(role = Role.Button) { step2TechExpanded = !step2TechExpanded }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonoText(
                        "Chi tiết kỹ thuật & ${setup.steps.size} lệnh hệ thống",
                        9.5.sp,
                        TextLabel,
                    )
                    LucideIcon(
                        if (step2TechExpanded) R.drawable.lc_chevron_down else R.drawable.lc_chevron_right,
                        TextDim,
                        14.dp,
                    )
                }

                AnimatedVisibility(visible = step2TechExpanded) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MonoText(
                            when {
                                scriptStatus.running == true && scriptStatus.detachedFromAdbd == true ->
                                    "Tiến trình: pid ${scriptStatus.pid} · đã tách khỏi adbd · ${WatchScript.PATH}"
                                scriptStatus.running == true ->
                                    "Tiến trình: pid ${scriptStatus.pid} · đã neo giữ adbd · ${WatchScript.PATH}"
                                scriptStartedThisBoot ->
                                    "Tiến trình: đã khởi chạy trong phiên bật máy này"
                                else ->
                                    "Tiến trình: chưa chạy (${WatchScript.PATH})"
                            },
                            9.sp,
                            if (scriptOn) Accent else TextFaint,
                            maxLines = 2,
                        )
                        Segmented(
                            options = listOf(
                                SegmentOption("Tự động (Cả 2)"),
                                SegmentOption("Shizuku"),
                                SegmentOption("Engine nội bộ"),
                            ),
                            selected = when (settings.shellBackend) {
                                ShellBackend.AUTO -> 0
                                ShellBackend.SHIZUKU -> 1
                                ShellBackend.BUILTIN -> 2
                            },
                            onSelect = { idx ->
                                val next = when (idx) {
                                    1 -> ShellBackend.SHIZUKU
                                    2 -> ShellBackend.BUILTIN
                                    else -> ShellBackend.AUTO
                                }
                                onSetShellBackend(next)
                            },
                        )
                        val passedCount = setup.steps.count { it.status == StepStatus.OK }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Surface2)
                                .border(1.dp, Hairline, RoundedCornerShape(10.dp))
                                .clickable(role = Role.Button, onClick = onOpenSetup)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                BodyText("Bảng ${setup.steps.size} lệnh kích hoạt & ADB máy tính", 12.5.sp, TextMain)
                                MonoText(
                                    if (passedCount > 0) "Đã xác minh $passedCount/${setup.steps.size} lệnh" else "Xem từng lệnh hoặc copy mã ADB",
                                    9.sp,
                                    TextFaint,
                                )
                            }
                            LucideIcon(R.drawable.lc_chevron_right, TextDim, 14.dp)
                        }
                    }
                }
            }

            // --- BƯỚC 3: KHÓA GỠ LỖI · BANK-SAFE ---
            StepCard(
                stepNumber = 3,
                title = "Khóa Gỡ lỗi",
                subtitle = if (bankSafe) {
                    "Đã tắt Gỡ lỗi & Tùy chọn nhà phát triển · An toàn 100% cho Ngân hàng & VNeID"
                } else {
                    "Đang bật Gỡ lỗi · Tắt để dùng Ngân hàng & VNeID"
                },
                completed = bankSafe,
                badgeText = if (bankSafe) "An toàn 100%" else "Cần khóa",
                expanded = step3Expanded,
                onToggleExpand = { step3Expanded = !step3Expanded },
            ) {
                BodyText(
                    if (bankSafe) {
                        "Tùy chọn nhà phát triển và Gỡ lỗi USB đều đang tắt. Mọi ứng dụng Ngân hàng và VNeID sẽ hoạt động bình thường trong khi Script nền vẫn chạy ngầm."
                    } else {
                        "Sau khi Script ở Bước 2 đã chạy, bạn hãy tắt Tùy chọn nhà phát triển để các ứng dụng Ngân hàng và VNeID không cảnh báo. Script nền vẫn tiếp tục hoạt động."
                    },
                    12.5.sp,
                    Muted,
                    lineHeight = 18.sp,
                )
                Gap(12.dp)
                if (!bankSafe) {
                    if (permissions.shizukuGranted) {
                        PrimaryCta(
                            text = "Tắt ngay Gỡ lỗi (Bank-Safe)",
                            onClick = onLockDevMode,
                            leadingIcon = R.drawable.lc_shield_check,
                            modifier = Modifier.fillMaxWidth(),
                            height = 46.dp,
                            radius = 13.dp,
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryCta(
                                text = "Mở Tùy chọn nhà phát triển",
                                onClick = { Intents.openDeveloperSettings(context) },
                                trailingIcon = R.drawable.lc_arrow_up_right,
                                modifier = Modifier.weight(1f),
                                height = 44.dp,
                                radius = 13.dp,
                            )
                            val devGuideIdx = guideItems.indexOfFirst { it.id == "dev_options" }
                            if (devGuideIdx >= 0) {
                                OutlineCta(
                                    text = "Ảnh",
                                    onClick = { activeGuideIndex = devGuideIdx },
                                    height = 44.dp,
                                    radius = 13.dp,
                                )
                            }
                        }
                    }
                } else {
                    OutlineCta(
                        text = "Mở Cài đặt Tùy chọn nhà phát triển",
                        onClick = { Intents.openDeveloperSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                        height = 40.dp,
                        radius = 12.dp,
                    )
                }
            }

            // 3. TÓM TẮT HÀNH VI GỌI AI NGAY TRÊN TRANG CHỦ
            SectionLabel(
                "Hành động khi gọi AI",
                Modifier.padding(top = 4.dp),
                action = "Tùy chỉnh chi tiết ›",
                onAction = onOpenModes,
            )
            Panel {
                NavRow(
                    title = "Khẩu lệnh ($wakePhrase)",
                    subtitle = actionModeSummary(settings.voiceAction, config.voiceAction, config.target.displayName),
                    subColor = if (settings.voiceAction == ActionMode.DEFAULT) TextFaint else Accent,
                    trailing = "đổi",
                    trailingColor = Accent,
                    leading = { IconTile(R.drawable.lc_mic, Accent) },
                    onClick = { editingActionSlot = SLOT_VOICE },
                )
                NavRow(
                    title = "Nút cứng / Phím cạnh AI",
                    subtitle = actionModeSummary(settings.buttonAction, config.buttonAction, config.target.displayName),
                    subColor = if (settings.buttonAction == ActionMode.DEFAULT) TextFaint else Accent,
                    trailing = "đổi",
                    trailingColor = Accent,
                    leading = { IconTile(R.drawable.lc_sparkles, Accent) },
                    onClick = { editingActionSlot = SLOT_BUTTON },
                )
                NavRow(
                    title = "Lối tắt “Gọi AI” & Chạm mặt lưng",
                    subtitle = actionModeSummary(settings.shortcutAction, config.shortcutAction, config.target.displayName),
                    subColor = if (settings.shortcutAction == ActionMode.DEFAULT) TextFaint else Accent,
                    trailing = "đổi",
                    trailingColor = Accent,
                    leading = { IconTile(R.drawable.lc_rocket, Accent) },
                    onClick = { editingActionSlot = SLOT_SHORTCUT },
                    divider = false,
                )
            }
        }
    }

    if (activeGuideIndex in guideItems.indices) {
        val currentItem = guideItems[activeGuideIndex]
        PermissionGuideDialog(
            item = currentItem,
            stepIndex = activeGuideIndex,
            totalSteps = guideItems.size,
            onDismiss = { activeGuideIndex = -1 },
            onPrev = if (activeGuideIndex > 0) {
                { activeGuideIndex-- }
            } else {
                null
            },
            onNext = if (activeGuideIndex < guideItems.lastIndex) {
                { activeGuideIndex++ }
            } else {
                null
            },
        )
    }

    editingActionSlot?.let { slot ->
        val (dialogTitle, currentMode, onSave) = when (slot) {
            SLOT_VOICE -> Triple("Khẩu lệnh ($wakePhrase)", settings.voiceAction, onSetVoiceAction)
            SLOT_BUTTON -> Triple("Nút cứng / Phím cạnh AI", settings.buttonAction, onSetButtonAction)
            else -> Triple("Lối tắt “Gọi AI”", settings.shortcutAction, onSetShortcutAction)
        }
        ActionModeDialog(
            title = dialogTitle,
            current = currentMode,
            onConfirm = { chosen ->
                editingActionSlot = null
                if (chosen != currentMode) onSave(chosen)
            },
            onDismiss = { editingActionSlot = null },
        )
    }
}

@Composable
private fun HeroStatusCard(
    config: ActiveConfig,
    permissions: PermissionSnapshot,
    scriptOn: Boolean,
    scriptBusy: Boolean,
    bankSafe: Boolean,
    oneTime: Boolean,
    romChanged: Boolean,
    summary: AttemptSummary,
    now: Long,
    onPrimaryAction: (() -> Unit)?,
) {
    val allGood = permissions.ready && (scriptOn || oneTime) && bankSafe && !romChanged
    val stateColor = when {
        allGood -> Accent
        !permissions.ready -> Warn
        !scriptOn && !oneTime -> Warn
        !bankSafe -> Warn
        else -> Warn
    }
    val eyebrow = when {
        scriptBusy -> "ĐANG XỬ LÝ…"
        allGood -> "HOẠT ĐỘNG HOÀN HẢO · BANK-SAFE ✓"
        !permissions.ready -> "CẦN THIẾT LẬP · BƯỚC 1 / 3"
        !scriptOn && !oneTime -> "CẦN KÍCH HOẠT · BƯỚC 2 / 3"
        !bankSafe -> "BƯỚC CUỐI · CẦN KHÓA GỠ LỖI (BƯỚC 3)"
        romChanged -> "CẬP NHẬT HỆ THỐNG · CẦN KIỂM TRA LẠI"
        else -> "SẴN SÀNG"
    }
    val headline = when {
        scriptBusy -> "Đang kích hoạt & bật Script…"
        allGood -> "${config.profile.assistantName} → ${config.target.displayName}"
        !permissions.ready -> "Bật đủ quyền cơ bản để bắt đầu"
        !scriptOn && !oneTime -> "Bật Script nền để thay ${config.profile.assistantName}"
        !bankSafe -> "Script đã chạy · Hãy khóa Gỡ lỗi"
        else -> "Kiểm tra lại cấu hình sau cập nhật ROM"
    }
    val description = when {
        allGood ->
            "Script đang chạy ngầm và Gỡ lỗi đã tắt sạch. Gọi khẩu lệnh hoặc nhấn nút AI sẽ mở ngay ${config.target.displayName}."
        !permissions.ready ->
            "Còn ${permissions.coreTotalCount - permissions.coreGrantedCount} mục quyền ở Bước 1 cần bật để lớp phủ AI hoạt động ổn định."
        !scriptOn && !oneTime ->
            "Đã cấp đủ quyền cơ bản! Thực hiện Bước 2 bên dưới để kích hoạt Script ngầm."
        !bankSafe ->
            "Tắt Tùy chọn nhà phát triển để dùng ứng dụng Ngân hàng & VNeID bình thường (Script vẫn chạy)."
        else ->
            "Bản cập nhật hệ thống có thể đã đặt lại trợ lý mặc định."
    }
    val ctaLabel = when {
        scriptBusy -> null
        !permissions.ready -> "Mở quyền còn thiếu ngay"
        !scriptOn && !oneTime -> when {
            permissions.shizukuGranted && !bankSafe -> "Bật Script & Tự khóa Gỡ lỗi"
            permissions.shizukuGranted -> "Kích hoạt & Bật Script nền"
            permissions.adbWifiEnabled && permissions.wirelessAdbPaired -> "Kết nối Engine & Bật Script"
            else -> "Ghép nối 6 số & Bật Script"
        }
        !bankSafe -> if (permissions.shizukuGranted) {
            "Khóa Gỡ lỗi ngay (Bank-Safe)"
        } else {
            "Tắt Tùy chọn nhà phát triển"
        }
        romChanged -> "Chạy lại kích hoạt"
        else -> null
    }

    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (allGood) CommunityBg else Surface)
            .border(
                1.dp,
                if (allGood) CommunityLine else stateColor.copy(alpha = 0.45f),
                shape,
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(stateColor, 8.dp)
            MonoText(
                eyebrow,
                9.5.sp,
                stateColor,
                letterSpacing = 0.8.sp,
                weight = FontWeight.Medium,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BodyText(
                headline,
                21.sp,
                if (allGood) CommunityTitle else TextMain,
                weight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                lineHeight = 26.sp,
            )
            BodyText(
                description,
                12.5.sp,
                if (allGood) TextBody else Muted,
                lineHeight = 18.sp,
            )
        }

        if (ctaLabel != null && onPrimaryAction != null) {
            PrimaryCta(
                text = ctaLabel,
                onClick = onPrimaryAction,
                trailingIcon = R.drawable.lc_arrow_right,
                modifier = Modifier.fillMaxWidth(),
                height = 46.dp,
                radius = 13.dp,
            )
        }

        // Thanh thống kê gọn
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Surface2)
                .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniStat("Hôm nay", "${summary.today} lượt")
            StatDivider()
            MiniStat("Tỉ lệ đạt", summary.rate?.let { "$it%" } ?: "—")
            StatDivider()
            MiniStat(
                "Gần nhất",
                summary.last?.let { Format.relative(it.at, now) } ?: "chưa có",
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        MonoText(value, 10.5.sp, TextMain, weight = FontWeight.Medium)
        MonoText(label, 8.5.sp, TextFaint)
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(22.dp)
            .background(Hairline),
    )
}

@Composable
private fun StepCard(
    stepNumber: Int,
    title: String,
    subtitle: String,
    completed: Boolean,
    badgeText: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val statusColor = if (completed) Accent else Warn
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface)
            .border(
                1.dp,
                if (!completed && expanded) Warn.copy(alpha = 0.45f) else Hairline,
                shape,
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggleExpand)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f))
                    .border(1.dp, statusColor.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (completed) {
                    LucideIcon(R.drawable.lc_check, Accent, 14.dp)
                } else {
                    MonoText(stepNumber.toString(), 11.sp, Warn, weight = FontWeight.Medium)
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BodyText(title, 14.5.sp, TextMain, weight = FontWeight.SemiBold, maxLines = 1)
                BodyText(
                    subtitle,
                    11.5.sp,
                    if (completed) TextDim else Warn,
                    maxLines = 2,
                    lineHeight = 16.sp,
                )
            }

            MonoText(
                badgeText,
                9.sp,
                statusColor,
                Modifier
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            )

            LucideIcon(
                if (expanded) R.drawable.lc_chevron_down else R.drawable.lc_chevron_right,
                TextGhost,
                15.dp,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .height(1.dp)
                        .background(Hairline),
                )
                content()
            }
        }
    }
}
