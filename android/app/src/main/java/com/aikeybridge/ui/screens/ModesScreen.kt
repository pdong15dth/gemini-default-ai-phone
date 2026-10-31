package com.aikeybridge.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikeybridge.LaunchActivity
import com.aikeybridge.R
import com.aikeybridge.data.ActionMode
import com.aikeybridge.data.ActiveConfig
import com.aikeybridge.data.AppSettings
import com.aikeybridge.data.ConfigResolver
import com.aikeybridge.data.HardwareMode
import com.aikeybridge.data.PermissionSnapshot
import com.aikeybridge.data.UsageMode
import com.aikeybridge.model.AssistantTarget
import com.aikeybridge.ui.Format
import com.aikeybridge.ui.Intents
import com.aikeybridge.ui.ScriptStatus
import com.aikeybridge.ui.components.BodyText
import com.aikeybridge.ui.components.Gap
import com.aikeybridge.ui.components.IconTile
import com.aikeybridge.ui.components.LucideIcon
import com.aikeybridge.ui.components.MonoText
import com.aikeybridge.ui.components.NavRow
import com.aikeybridge.ui.components.OutlineCta
import com.aikeybridge.ui.components.Panel
import com.aikeybridge.ui.components.SectionLabel
import com.aikeybridge.ui.components.StatusDot
import com.aikeybridge.ui.components.Stepper
import com.aikeybridge.ui.components.TabHeader
import com.aikeybridge.ui.components.TinyTag
import com.aikeybridge.ui.components.ToggleRow
import com.aikeybridge.ui.components.hairlineBottom
import com.aikeybridge.ui.theme.Accent
import com.aikeybridge.ui.theme.Danger
import com.aikeybridge.ui.theme.Hairline
import com.aikeybridge.ui.theme.Muted
import com.aikeybridge.ui.theme.Surface
import com.aikeybridge.ui.theme.Surface2
import com.aikeybridge.ui.theme.TextBody
import com.aikeybridge.ui.theme.TextDim
import com.aikeybridge.ui.theme.TextFaint
import com.aikeybridge.ui.theme.TextGhost
import com.aikeybridge.ui.theme.TextLabel
import com.aikeybridge.ui.theme.TextMain

@Composable
fun ModesScreen(
    config: ActiveConfig,
    settings: AppSettings,
    permissions: PermissionSnapshot,
    targets: List<AssistantTarget>,
    onOpenLogs: () -> Unit,
    onSelectTarget: (String) -> Unit,
    onPickCustomApp: () -> Unit,
    onToggleRedirect: (Boolean) -> Unit,
    onToggleAutoMic: (Boolean) -> Unit,
    onToggleLockScreen: (Boolean) -> Unit,
    onToggleDedupe: (Boolean) -> Unit,
    onNudgeDismiss: (Long) -> Unit,
    onNudgeMicTimeout: (Long) -> Unit,
    onResetTuning: () -> Unit,
    onOpenHardware: () -> Unit,
    onSetUsageMode: (UsageMode) -> Unit,
    onOpenShortcuts: () -> Unit,
    onOpenScript: () -> Unit,
    onOpenProfiles: () -> Unit = {},
    onOpenTriggers: () -> Unit = {},
    scriptStatus: ScriptStatus,
    scriptStartedThisBoot: Boolean,
    onCheckScript: () -> Unit,
    onSetVoiceAction: (ActionMode) -> Unit = {},
    onSetButtonAction: (ActionMode) -> Unit = {},
    onSetShortcutAction: (ActionMode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val target = config.target
    val vivo = config.profile.oem == "vivo"
    var confirmUsage by rememberSaveable { mutableStateOf(false) }
    var editingActionSlot by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var aliasOn by remember { mutableStateOf(Intents.isLaunchAliasEnabled(context)) }

    if (settings.usageMode == UsageMode.SCRIPT) {
        LaunchedEffect(Unit) { onCheckScript() }
    }

    val wakeLabel = config.profile.wakePhrase?.let { "“$it”" } ?: config.profile.assistantName

    Column(modifier.fillMaxSize()) {
        TabHeader("Hành vi & Phím tắt", "Tùy chỉnh AI")

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 28.dp),
        ) {
            // 1. KHI GỌI AI SẼ MỞ GÌ?
            SectionLabel("Khi gọi AI sẽ mở gì?")
            BodyText(
                "Chọn hành động riêng cho từng thao tác. Chạm vào từng dòng để đổi kiểu gọi.",
                12.5.sp,
                Muted,
                Modifier.padding(bottom = 10.dp),
                lineHeight = 18.sp,
            )
            Panel {
                ActionSlotRow(
                    icon = R.drawable.lc_mic,
                    title = "Khẩu lệnh ($wakeLabel)",
                    subtitle = actionModeSummary(settings.voiceAction, config.voiceAction, target.displayName),
                    highlighted = settings.voiceAction != ActionMode.DEFAULT,
                    onClick = { editingActionSlot = SLOT_VOICE },
                )
                ActionSlotRow(
                    icon = R.drawable.lc_sparkles,
                    title = "Nút cứng / Phím cạnh AI",
                    subtitle = actionModeSummary(settings.buttonAction, config.buttonAction, target.displayName),
                    highlighted = settings.buttonAction != ActionMode.DEFAULT,
                    onClick = { editingActionSlot = SLOT_BUTTON },
                )
                ActionSlotRow(
                    icon = R.drawable.lc_rocket,
                    title = "Lối tắt “Gọi AI” & Cử chỉ",
                    subtitle = actionModeSummary(settings.shortcutAction, config.shortcutAction, target.displayName),
                    highlighted = settings.shortcutAction != ActionMode.DEFAULT,
                    onClick = { editingActionSlot = SLOT_SHORTCUT },
                    divider = false,
                )
            }
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineCta(
                    text = "Cài đặt Giọng nói",
                    icon = R.drawable.lc_mic,
                    iconTint = TextBody,
                    onClick = { Intents.openVoiceWakeSettings(context) },
                    modifier = Modifier.weight(1f),
                    height = 40.dp,
                    radius = 12.dp,
                )
                OutlineCta(
                    text = "Cài đặt Nút AI",
                    icon = R.drawable.lc_sparkles,
                    iconTint = TextBody,
                    onClick = { Intents.openSmartKey(context) },
                    modifier = Modifier.weight(1f),
                    height = 40.dp,
                    radius = 12.dp,
                )
            }

            // 2. ỨNG DỤNG AI MẶC ĐỊNH & TƯƠNG TÁC
            SectionLabel("Ứng dụng AI mặc định", Modifier.padding(top = 14.dp))
            Panel {
                targets.forEach { item ->
                    TargetRow(
                        icon = when (item.id) {
                            AssistantTarget.GEMINI -> R.drawable.lc_audio_lines
                            AssistantTarget.GOOGLE_CTS -> R.drawable.lc_scan_search
                            else -> R.drawable.lc_message_circle
                        },
                        name = item.displayName,
                        subtitle = item.launchLabel,
                        selected = target.id == item.id,
                        beta = item.beta,
                        onClick = { onSelectTarget(item.id) },
                    )
                }
                val customSelected = target.id == AssistantTarget.CUSTOM
                TargetRow(
                    icon = R.drawable.lc_app_window,
                    name = if (customSelected) target.displayName else "Ứng dụng khác",
                    subtitle = if (customSelected) "${target.launchLabel} · chạm để đổi" else "chọn app bất kỳ trên máy",
                    selected = customSelected,
                    beta = false,
                    divider = false,
                    onClick = onPickCustomApp,
                )
            }
            if (target.id != AssistantTarget.GEMINI && !permissions.targetInstalled) {
                MonoText(
                    "Chưa cài ${target.displayName} trên máy này.",
                    9.5.sp,
                    Danger,
                    Modifier.padding(start = 2.dp, top = 8.dp),
                )
            }

            Gap(10.dp)
            Panel {
                ToggleRow(
                    title = "Tự động bật Micro",
                    subtitle = if (target.voiceUi != null) {
                        "Tự bật nghe ngay khi ${target.displayName} mở lên"
                    } else {
                        "${target.displayName} không hỗ trợ tự bật Micro"
                    },
                    checked = settings.autoMic && target.voiceUi != null,
                    onCheckedChange = onToggleAutoMic,
                    enabled = target.voiceUi != null,
                    icon = R.drawable.lc_mic,
                )
                ToggleRow(
                    title = "Cho phép trên màn hình khóa",
                    subtitle = "Gọi trợ lý ngay cả khi chưa mở khóa máy",
                    checked = settings.lockScreen,
                    onCheckedChange = onToggleLockScreen,
                    icon = R.drawable.lc_lock_keyhole,
                    divider = false,
                )
            }

            // 3. PHÍM TẮT NHANH & CHẠM MẶT LƯNG (Hợp nhất từ ShortcutsScreen)
            SectionLabel("Chạm mặt lưng & Lối tắt nhanh", Modifier.padding(top = 14.dp))
            Panel {
                ToggleRow(
                    title = "Hiện “Gọi AI” trong danh sách app",
                    subtitle = "Dùng cho Chạm mặt lưng & Khởi chạy nhanh",
                    checked = aliasOn,
                    onCheckedChange = {
                        Intents.setLaunchAlias(context, it)
                        aliasOn = Intents.isLaunchAliasEnabled(context)
                    },
                )
                if (vivo) {
                    NavRow(
                        title = "Gán vào Chạm mặt lưng",
                        subtitle = "Cài đặt → Chạm mặt lưng → Mở ứng dụng → Gọi AI",
                        trailing = "mở ↗",
                        trailingColor = Accent,
                        onClick = { Intents.openBackTap(context) },
                    )
                    NavRow(
                        title = "Khởi chạy nhanh khi tắt màn hình",
                        subtitle = "Giữ nút Giảm âm lượng → Mở ứng dụng → Gọi AI",
                        trailing = "mở ↗",
                        trailingColor = Accent,
                        onClick = { Intents.openQuickLaunch(context) },
                        divider = false,
                    )
                } else {
                    NavRow(
                        title = "Cài đặt phím tắt & cử chỉ của máy",
                        subtitle = "Gán phím cạnh hoặc chạm mặt lưng → Mở ứng dụng “Gọi AI”",
                        trailing = "mở ↗",
                        trailingColor = Accent,
                        onClick = { Intents.openSmartKey(context) },
                        divider = false,
                    )
                }
            }
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineCta(
                    text = "Thử Gọi AI",
                    icon = R.drawable.lc_rocket,
                    iconTint = Accent,
                    onClick = {
                        context.startActivity(
                            Intent(context, LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    modifier = Modifier.weight(1f),
                    height = 42.dp,
                    radius = 12.dp,
                )
                OutlineCta(
                    text = "Thử Khoanh tròn",
                    icon = R.drawable.lc_scan_search,
                    iconTint = Accent,
                    onClick = {
                        context.startActivity(
                            Intent(context, LaunchActivity::class.java)
                                .setAction(LaunchActivity.ACTION_CTS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    modifier = Modifier.weight(1f),
                    height = 42.dp,
                    radius = 12.dp,
                )
            }

            // 4. NHẬN DIỆN MÁY & DÒ CỬA SỔ
            SectionLabel("Nhận diện máy & Cửa sổ trợ lý", Modifier.padding(top = 14.dp))
            Panel {
                NavRow(
                    title = "Hãng điện thoại (Profile ROM)",
                    subtitle = "${config.profile.displayName} · ${config.profile.os} · " +
                        if (config.manualProfile) "đã chọn tay" else "tự nhận diện",
                    trailing = "đổi hãng",
                    onClick = onOpenProfiles,
                )
                NavRow(
                    title = "Dò cửa sổ trợ lý gốc (${config.profile.assistantName})",
                    subtitle = if (settings.customTriggers.isNotEmpty()) {
                        "Đang dùng ${config.triggers.size} mẫu cửa sổ (${settings.customTriggers.size} tự dò)"
                    } else {
                        "Tự bắt cửa sổ khi bấm nút AI hoặc khẩu lệnh"
                    },
                    trailing = "dò 8s",
                    trailingColor = Accent,
                    onClick = onOpenTriggers,
                    divider = false,
                )
            }

            // 5. TINH CHỈNH KỸ THUẬT NÂNG CAO (Thu gọn mặc định)
            Gap(14.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface)
                    .border(1.dp, Hairline, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button) { showAdvanced = !showAdvanced }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LucideIcon(R.drawable.lc_sliders_horizontal, TextDim, 15.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BodyText("Tinh chỉnh kỹ thuật nâng cao", 13.5.sp, TextMain, weight = FontWeight.Medium)
                    MonoText("Độ trễ đóng trợ lý, chờ Micro & chế độ nút cứng", 9.sp, TextFaint)
                }
                LucideIcon(
                    if (showAdvanced) R.drawable.lc_chevron_down else R.drawable.lc_chevron_right,
                    TextDim,
                    15.dp,
                )
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val modeB = settings.hardwareMode == HardwareMode.LOGCAT
                    Panel {
                        TuneRow(
                            title = "Độ trễ sau khi đóng trợ lý gốc",
                            subtitle = "Chờ nhả micro trước khi mở AI mới",
                            value = "${config.dismissDelayMs} ms",
                            onMinus = { onNudgeDismiss(-DISMISS_STEP) },
                            onPlus = { onNudgeDismiss(DISMISS_STEP) },
                            minusEnabled = config.dismissDelayMs > ConfigResolver.MIN_DISMISS_DELAY,
                            plusEnabled = config.dismissDelayMs < ConfigResolver.MAX_DISMISS_DELAY,
                        )
                        TuneRow(
                            title = "Thời gian chờ mở ${if (target.voiceUi != null) "Micro" else "ứng dụng"}",
                            subtitle = "Tự bỏ qua nếu ${target.displayName} mở chậm",
                            value = "${config.micTimeoutMs} ms",
                            onMinus = { onNudgeMicTimeout(-MIC_STEP) },
                            onPlus = { onNudgeMicTimeout(MIC_STEP) },
                            minusEnabled = config.micTimeoutMs > ConfigResolver.MIN_MIC_TIMEOUT,
                            plusEnabled = config.micTimeoutMs < ConfigResolver.MAX_MIC_TIMEOUT,
                        )
                        if (settings.dismissDelayMs != null || settings.micTimeoutMs != null) {
                            NavRow(
                                title = "Khôi phục độ trễ mặc định",
                                subtitle = "Trả về thông số chuẩn của ${config.profile.displayName}",
                                trailing = "đặt lại",
                                trailingColor = Accent,
                                onClick = onResetTuning,
                            )
                        }
                        ToggleRow(
                            title = "Chống trùng lặp sự kiện",
                            subtitle = "Bỏ qua lệnh gọi trùng trong ${Format.seconds(config.cooldownMs)}",
                            checked = settings.dedupe,
                            onCheckedChange = onToggleDedupe,
                        )
                        NavRow(
                            title = "Phương thức bắt nút cứng",
                            subtitle = if (modeB) "Chế độ B · Đọc event log" else "Chế độ A · Bắt cửa sổ (Khuyên dùng)",
                            trailing = "cấu hình",
                            onClick = onOpenHardware,
                        )
                        NavRow(
                            title = "Chế độ vận hành",
                            subtitle = when (settings.usageMode) {
                                UsageMode.ONE_TIME -> "Chỉ dùng lối tắt (Không chạy Script nền)"
                                else -> "Script nền Bank-Safe (Khuyên dùng)"
                            },
                            trailing = "đổi",
                            onClick = { confirmUsage = true },
                            divider = false,
                        )
                    }
                }
            }
        }
    }

    if (confirmUsage) {
        UsageModeDialog(
            current = settings.usageMode,
            scriptAvailable = config.shellWatch != null && config.target.shellKeyevent != null,
            onConfirm = {
                confirmUsage = false
                if (it != settings.usageMode) onSetUsageMode(it)
            },
            onDismiss = { confirmUsage = false },
        )
    }

    editingActionSlot?.let { slot ->
        val (dialogTitle, currentMode, onSave) = when (slot) {
            SLOT_VOICE -> Triple("Khẩu lệnh ($wakeLabel)", settings.voiceAction, onSetVoiceAction)
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

internal const val SLOT_VOICE = "voice"
internal const val SLOT_BUTTON = "button"
internal const val SLOT_SHORTCUT = "shortcut"

internal fun actionModeSummary(raw: ActionMode, effective: ActionMode, targetName: String): String =
    if (raw == ActionMode.DEFAULT) {
        val label = if (effective == ActionMode.DEFAULT) targetName else effective.shortLabel
        "Mặc định ($label)"
    } else {
        raw.title
    }

@Composable
private fun ActionSlotRow(
    icon: Int,
    title: String,
    subtitle: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    divider: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .hairlineBottom(divider)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, if (highlighted) Accent else TextDim)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BodyText(title, 13.5.sp, TextMain, weight = FontWeight.Medium, maxLines = 1)
            MonoText(
                subtitle,
                9.5.sp,
                if (highlighted) Accent else TextFaint,
                maxLines = 1,
            )
        }
        MonoText(
            "Đổi",
            9.5.sp,
            Accent,
            Modifier
                .clip(CircleShape)
                .background(Accent.copy(alpha = 0.12f))
                .border(1.dp, Accent.copy(alpha = 0.35f), CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun TargetRow(
    icon: Int,
    name: String,
    subtitle: String,
    selected: Boolean,
    beta: Boolean,
    onClick: () -> Unit,
    divider: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .hairlineBottom(divider)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, if (selected) Accent else TextDim)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                BodyText(name, 14.sp, if (selected) TextMain else TextLabel, weight = FontWeight.Medium, maxLines = 1)
                if (beta) TinyTag("beta")
            }
            MonoText(subtitle, 9.sp, TextFaint)
        }
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (selected) Accent else Hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) StatusDot(Accent, 9.dp)
        }
    }
}

@Composable
private fun TuneRow(
    title: String,
    subtitle: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean,
    plusEnabled: Boolean,
    divider: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .hairlineBottom(divider)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BodyText(title, 13.5.sp, TextBody, maxLines = 1)
            MonoText(subtitle, 9.sp, TextFaint)
        }
        Stepper(value, onMinus, onPlus, minusEnabled, plusEnabled, label = title)
    }
}

private const val DISMISS_STEP = 100L
private const val MIC_STEP = 250L

@Composable
private fun UsageModeDialog(
    current: UsageMode,
    scriptAvailable: Boolean,
    onConfirm: (UsageMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var choice by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { BodyText("Chế độ vận hành", 18.sp, TextMain, weight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOfNotNull(
                    Triple(
                        UsageMode.SCRIPT,
                        "Script nền (Bank-Safe)",
                        "Khuyên dùng · Thay thế trợ lý gốc · Tương thích mọi app ngân hàng",
                    ).takeIf { scriptAvailable },
                    Triple(
                        UsageMode.ONE_TIME,
                        "Chỉ dùng lối tắt",
                        "Không chạy nền · Chỉ mở AI khi bấm lối tắt Gọi AI",
                    ),
                ).forEach { (mode, title, sub) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(role = Role.RadioButton) { choice = mode }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, if (choice == mode) Accent else Hairline, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (choice == mode) StatusDot(Accent, 9.dp)
                        }
                        Column(Modifier.weight(1f)) {
                            BodyText(title, 14.sp, TextMain, weight = FontWeight.Medium)
                            MonoText(sub, 9.sp, TextFaint, maxLines = 2)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(choice) }) {
                BodyText("Lưu", 14.sp, Accent, weight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { BodyText("Huỷ", 14.sp, Muted) }
        },
    )
}

@Composable
internal fun ActionModeDialog(
    title: String,
    current: ActionMode,
    onConfirm: (ActionMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var choice by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { BodyText(title, 18.sp, TextMain, weight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ActionMode.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(role = Role.RadioButton) { choice = mode }
                            .padding(vertical = 9.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, if (choice == mode) Accent else Hairline, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (choice == mode) StatusDot(Accent, 9.dp)
                        }
                        Column(Modifier.weight(1f)) {
                            BodyText(mode.title, 14.sp, TextMain, weight = FontWeight.Medium)
                            MonoText(mode.subtitle, 9.sp, TextFaint, maxLines = 2)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(choice) }) {
                BodyText("Lưu", 14.sp, Accent, weight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { BodyText("Huỷ", 14.sp, Muted) }
        },
    )
}
