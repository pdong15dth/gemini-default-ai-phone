package com.aikeybridge.ui.screens

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikeybridge.R
import com.aikeybridge.data.ActiveConfig
import com.aikeybridge.data.AppSettings
import com.aikeybridge.data.ConfigResolver
import com.aikeybridge.data.HardwareMode
import com.aikeybridge.data.UsageMode
import com.aikeybridge.ui.ScriptStatus
import com.aikeybridge.data.PermissionSnapshot
import com.aikeybridge.model.AssistantTarget
import com.aikeybridge.ui.Format
import com.aikeybridge.ui.components.BodyText
import com.aikeybridge.ui.components.Chip
import com.aikeybridge.ui.components.CircleButton
import com.aikeybridge.ui.components.Gap
import com.aikeybridge.ui.components.IconTile
import com.aikeybridge.ui.components.LucideIcon
import com.aikeybridge.ui.components.MonoText
import com.aikeybridge.ui.components.NavRow
import com.aikeybridge.ui.components.Panel
import com.aikeybridge.ui.components.SectionLabel
import com.aikeybridge.ui.components.Stepper
import com.aikeybridge.ui.components.StatusDot
import com.aikeybridge.ui.components.TabHeader
import com.aikeybridge.ui.components.TinyTag
import com.aikeybridge.ui.components.ToggleRow
import com.aikeybridge.ui.components.hairlineBottom
import com.aikeybridge.ui.theme.Accent
import com.aikeybridge.ui.theme.Hairline
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
    scriptStatus: ScriptStatus,
    scriptStartedThisBoot: Boolean,
    onCheckScript: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = config.target
    var confirmUsage by rememberSaveable { mutableStateOf(false) }
    if (settings.usageMode == UsageMode.SCRIPT) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onCheckScript() }
    }
    Column(modifier.fillMaxSize()) {
        TabHeader("Cấu hình", "Chế độ") {
            CircleButton(R.drawable.lc_history, "Nhật ký", onOpenLogs)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
        ) {
            SectionLabel("Cách dùng")
            // Đổi chế độ phải qua hộp xác nhận: thanh chọn nằm trong vùng cuộn dễ bị chạm nhầm khi cuộn.
            Panel {
                NavRow(
                    title = when (settings.usageMode) {
                        UsageMode.ONE_TIME -> "Cấu hình một lần"
                        UsageMode.SCRIPT -> "Script nền"
                        UsageMode.FULL -> "Đầy đủ"
                    },
                    subtitle = when (settings.usageMode) {
                        UsageMode.ONE_TIME -> "không Trợ năng · lối tắt Gọi AI"
                        UsageMode.SCRIPT -> "script ${scriptState(scriptStatus, scriptStartedThisBoot).label} · không Trợ năng"
                        UsageMode.FULL -> "Trợ năng · thay ${config.profile.wakePhrase?.let { "“$it”" } ?: config.profile.assistantName}"
                    },
                    subColor = if (settings.usageMode == UsageMode.SCRIPT) {
                        scriptState(scriptStatus, scriptStartedThisBoot).color
                    } else {
                        TextFaint
                    },
                    trailing = "đổi",
                    onClick = { confirmUsage = true },
                    divider = false,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        role = Role.Button,
                        onClick = if (settings.usageMode == UsageMode.SCRIPT) onOpenScript else onOpenShortcuts,
                    )
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BodyText(
                    when (settings.usageMode) {
                        UsageMode.ONE_TIME -> "Không cần Trợ năng. Gán phím / chạm mặt lưng vào lối tắt “Gọi AI”."
                        UsageMode.SCRIPT -> "Script shell nền thay ${config.profile.assistantName}. Bật / tắt / kiểm tra script."
                        UsageMode.FULL -> "Trợ năng thay ${config.profile.assistantName}. Gán thêm phím vào “Gọi AI” nếu muốn."
                    },
                    11.sp,
                    TextFaint,
                    Modifier.weight(1f),
                    lineHeight = 16.sp,
                )
                LucideIcon(R.drawable.lc_chevron_right, TextGhost, 13.dp)
            }

            SectionLabel("Đích đến", Modifier.padding(top = 6.dp))
            Panel {
                targets.forEach { item ->
                    TargetRow(
                        icon = if (item.id == AssistantTarget.GEMINI) R.drawable.lc_audio_lines else R.drawable.lc_message_circle,
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
                    subtitle = if (customSelected) "${target.launchLabel} · đổi" else "chọn app bất kỳ · không tự bật Micro",
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
                    com.aikeybridge.ui.theme.Danger,
                    Modifier.padding(start = 2.dp, top = 8.dp),
                )
            }

            SectionLabel("Tự động", Modifier.padding(top = 6.dp))
            Panel {
                ToggleRow(
                    title = "Chuyển hướng",
                    subtitle = "${config.triggers.size} trigger · ${config.profile.assistantName}",
                    checked = settings.redirectEnabled,
                    onCheckedChange = onToggleRedirect,
                )
                ToggleRow(
                    title = "Tự bật Micro",
                    subtitle = if (target.voiceUi != null) "chạm Mic sau khi ${target.displayName} mở" else "${target.displayName} không hỗ trợ",
                    checked = settings.autoMic && target.voiceUi != null,
                    onCheckedChange = onToggleAutoMic,
                    enabled = target.voiceUi != null,
                )
                ToggleRow(
                    title = "Màn hình khóa",
                    subtitle = "TriggerActivity · cần 3 quyền",
                    checked = settings.lockScreen,
                    onCheckedChange = onToggleLockScreen,
                    divider = false,
                )
            }

            Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                val modeB = settings.hardwareMode == HardwareMode.LOGCAT
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BodyText("Nút cứng → AI", 14.sp, TextMain, Modifier.weight(1f), weight = FontWeight.Medium)
                    Chip(
                        if (modeB) "chế độ B" else "chế độ A",
                        Modifier
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onOpenHardware),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = onOpenHardware)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BodyText(
                        if (modeB) {
                            "Đặt nút thành “không hành động”. App đọc event log" +
                                if (permissions.readLogs) "." else " — cần cấp READ_LOGS."
                        } else {
                            "Đặt nút AI/nhấn giữ nút nguồn thành “mở ${config.profile.assistantName}”. App bắt cửa sổ đó y như khẩu lệnh."
                        },
                        11.sp,
                        if (modeB && !permissions.readLogs) com.aikeybridge.ui.theme.Warn else TextFaint,
                        Modifier.weight(1f),
                        lineHeight = 16.sp,
                    )
                    LucideIcon(R.drawable.lc_chevron_right, TextGhost, 13.dp)
                }
            }

            SectionLabel(
                "Tinh chỉnh",
                Modifier.padding(top = 6.dp),
                action = if (settings.dismissDelayMs != null || settings.micTimeoutMs != null) "mặc định" else null,
                onAction = onResetTuning,
            )
            Panel {
                TuneRow(
                    title = "Độ trễ sau Back",
                    subtitle = "chờ nhả audio focus",
                    value = "${config.dismissDelayMs} ms",
                    onMinus = { onNudgeDismiss(-DISMISS_STEP) },
                    onPlus = { onNudgeDismiss(DISMISS_STEP) },
                    minusEnabled = config.dismissDelayMs > ConfigResolver.MIN_DISMISS_DELAY,
                    plusEnabled = config.dismissDelayMs < ConfigResolver.MAX_DISMISS_DELAY,
                )
                TuneRow(
                    title = "Timeout ${if (target.voiceUi != null) "Micro" else "mở app"}",
                    subtitle = "bỏ cuộc nếu ${target.displayName} chậm",
                    value = "${config.micTimeoutMs} ms",
                    onMinus = { onNudgeMicTimeout(-MIC_STEP) },
                    onPlus = { onNudgeMicTimeout(MIC_STEP) },
                    minusEnabled = config.micTimeoutMs > ConfigResolver.MIN_MIC_TIMEOUT,
                    plusEnabled = config.micTimeoutMs < ConfigResolver.MAX_MIC_TIMEOUT,
                    divider = false,
                )
            }

            SectionLabel("Nâng cao", Modifier.padding(top = 6.dp))
            Panel {
                ToggleRow(
                    title = "Chống trùng",
                    subtitle = "bỏ qua sự kiện trùng trong ${Format.seconds(config.cooldownMs)}",
                    checked = settings.dedupe,
                    onCheckedChange = onToggleDedupe,
                    titleColor = TextBody,
                    titleSize = 13.5.sp,
                    titleWeight = FontWeight.Normal,
                    subColor = TextGhost,
                    divider = false,
                )
            }
            Gap(8.dp)
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
        containerColor = com.aikeybridge.ui.theme.Surface,
        title = { BodyText("Cách dùng", 18.sp, TextMain, weight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOfNotNull(
                    Triple(UsageMode.SCRIPT, "Script nền (Bank-Safe)", "Khuyên dùng · Shizuku · tương thích mọi app ngân hàng").takeIf { scriptAvailable },
                    Triple(UsageMode.ONE_TIME, "Cấu hình một lần", "Không chạy nền · gán phím vào lối tắt Gọi AI"),
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
            TextButton(onClick = onDismiss) { BodyText("Huỷ", 14.sp, com.aikeybridge.ui.theme.Muted) }
        },
    )
}
