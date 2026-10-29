package com.aikeybridge.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikeybridge.R
import com.aikeybridge.restartApp
import com.aikeybridge.data.ActiveConfig
import com.aikeybridge.data.PermissionSnapshot
import com.aikeybridge.ui.Format
import com.aikeybridge.ui.Intents
import com.aikeybridge.ui.SetupStepUi
import com.aikeybridge.ui.SetupUiState
import com.aikeybridge.ui.StepStatus
import com.aikeybridge.ui.components.BodyText
import com.aikeybridge.ui.components.Chip
import com.aikeybridge.ui.components.Gap
import com.aikeybridge.ui.components.IconTile
import com.aikeybridge.ui.components.LucideIcon
import com.aikeybridge.ui.components.MonoText
import com.aikeybridge.ui.components.PrimaryCta
import com.aikeybridge.ui.components.ScreenTitle
import com.aikeybridge.ui.components.StatusDot
import com.aikeybridge.ui.components.SubHeader
import com.aikeybridge.ui.components.hairlineBottom
import com.aikeybridge.ui.theme.Accent
import com.aikeybridge.ui.theme.CommunityBg
import com.aikeybridge.ui.theme.CommunityLine
import com.aikeybridge.ui.theme.CommunityTitle
import com.aikeybridge.ui.theme.Danger
import com.aikeybridge.ui.theme.DangerBg
import com.aikeybridge.ui.theme.DangerBody
import com.aikeybridge.ui.theme.DangerLine
import com.aikeybridge.ui.theme.DangerMuted
import com.aikeybridge.ui.theme.DangerText
import com.aikeybridge.ui.theme.DangerTitle
import com.aikeybridge.ui.theme.Hairline
import com.aikeybridge.ui.theme.Muted
import com.aikeybridge.ui.theme.Surface
import com.aikeybridge.ui.theme.Surface2
import com.aikeybridge.ui.theme.TextBody
import com.aikeybridge.ui.theme.TextDim
import com.aikeybridge.ui.theme.TextFaint
import com.aikeybridge.ui.theme.TextIcon
import com.aikeybridge.ui.theme.TextLabel
import com.aikeybridge.ui.theme.TextMain
import com.aikeybridge.ui.theme.Track
import com.aikeybridge.ui.theme.Warn

@Composable
fun SetupScreen(
    config: ActiveConfig,
    permissions: PermissionSnapshot,
    setup: SetupUiState,
    onboarding: Boolean,
    onBack: () -> Unit,
    onRun: () -> Unit,
    stepLabel: String? = null,
    shizukuStartCommand: String? = null,
    onRequestShizuku: () -> Unit,
    onStartWirelessPairing: () -> Unit = {},
    onFinish: () -> Unit,
    onMessage: (String) -> Unit,
    onOpenPermissions: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showAdb by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val total = setup.steps.size
    val ran = setup.provisioningReport != null || setup.steps.any { it.status != StepStatus.PENDING }
    val copyAdb = {
        copyToClipboard(context, "Lệnh ADB AI Key Bridge", setup.adbScript)
        onMessage("Đã copy ${setup.steps.size} lệnh ADB.")
    }

    fun liveStepStatus(stepId: String): Boolean? = when (stepId) {
        "assistant" -> permissions.assistantDefault
        "voice_interaction" -> permissions.voiceInteraction
        "screen_context" -> permissions.screenContextEnabled
        "overlay_google" -> permissions.googleVivoDetail.floating ?: permissions.googleVivoGranted
        "overlay_gemini" -> permissions.geminiVivoDetail.floating ?: permissions.geminiVivoGranted
        "battery_whitelist" -> permissions.bothBatteryUnrestricted
        "gemini_bg" -> permissions.geminiBatteryUnrestricted &&
            (permissions.geminiVivoDetail.bgPopup ?: permissions.geminiVivoGranted)
        else -> null
    }

    fun manualStepAction(stepId: String): Pair<String, () -> Unit>? = when (stepId) {
        "assistant", "voice_interaction" ->
            "Mở Trợ lý mặc định" to {
                Intents.openAssistantSettings(
                    context,
                    assistantAlreadySelected = permissions.assistantDefault && !permissions.screenContextEnabled,
                )
            }
        "screen_context" ->
            "Mở Trợ lý mặc định" to {
                Intents.openAssistantSettings(context, assistantAlreadySelected = true)
            }
        "overlay_google" ->
            "Mở Cửa sổ nổi Google" to {
                Intents.openAppPermissions(context, Intents.GOOGLE_PACKAGE, perId = 10004)
            }
        "overlay_gemini" ->
            "Mở Cửa sổ nổi Gemini" to {
                Intents.openAppPermissions(context, Intents.GEMINI_PACKAGE, perId = 10004)
            }
        "gemini_bg" ->
            "Mở Bật lên dưới nền Gemini" to {
                Intents.openAppPermissions(context, Intents.GEMINI_PACKAGE, perId = 10007)
            }
        "battery_whitelist" ->
            "Mở Pin dưới nền" to {
                val targetPkg = when {
                    !permissions.googleBatteryUnrestricted -> Intents.GOOGLE_PACKAGE
                    !permissions.geminiBatteryUnrestricted -> Intents.GEMINI_PACKAGE
                    else -> Intents.GOOGLE_PACKAGE
                }
                Intents.openBackgroundBatterySettings(context, targetPkg)
            }
        "vivo_agent_bg", "copilot_locale" ->
            "Mở Đánh thức giọng nói" to { Intents.openVoiceWakeSettings(context) }
        else -> null
    }

    val effectivePassed = setup.steps.count { step ->
        step.status == StepStatus.OK || (step.status == StepStatus.PENDING && liveStepStatus(step.id) == true)
    }

    Column(modifier.fillMaxSize()) {
        SubHeader(onBack = onBack) {
            val (label, color) = when {
                permissions.engineDaemonRunning && !permissions.devOptionsEnabled && !permissions.adbEnabled ->
                    "engine · chạy ngầm (bank-safe)" to Accent
                permissions.engineDaemonRunning -> "engine · kết nối" to Accent
                permissions.shizukuGranted -> "shizuku · kết nối" to Accent
                permissions.shizukuRunning -> "engine · chờ cấp quyền" to Warn
                permissions.adbWifiEnabled -> "gỡ lỗi không dây · sẵn sàng" to Warn
                else -> "engine · chưa kết nối" to TextFaint
            }
            Chip(label, dot = color)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (stepLabel != null) MonoText(stepLabel, 10.sp, TextDim, letterSpacing = 1.2.sp)
            ScreenTitle("Kích hoạt & vá lỗi", size = 26.sp)
            MonoText(
                "${config.profile.id} · ${config.target.id} · $total lệnh · tuỳ chọn",
                9.5.sp,
                TextFaint,
            )
            if (total > 0) {
                BodyText(
                    if (config.target.shellKeyevent != null) {
                        "Chạy tự động qua Shizuku hoặc bấm “Mở cài đặt” ở từng dòng để bật thủ công."
                    } else {
                        "Không bắt buộc — chỉ vá lỗi cho ${config.profile.assistantName}."
                    },
                    12.sp,
                    TextDim,
                    lineHeight = 17.sp,
                )
            }

            if (onOpenPermissions != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (permissions.ready) CommunityBg else DangerBg)
                        .border(
                            1.dp,
                            if (permissions.ready) CommunityLine else DangerLine,
                            RoundedCornerShape(15.dp),
                        )
                        .clickable(role = Role.Button, onClick = onOpenPermissions)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconTile(R.drawable.lc_shield_check)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BodyText(
                            if (permissions.ready) {
                                "Hướng dẫn cấp quyền từng bước (kèm ảnh)"
                            } else {
                                "Đang thiếu quyền — Mở hướng dẫn từng bước"
                            },
                            13.sp,
                            if (permissions.ready) CommunityTitle else TextMain,
                            weight = FontWeight.SemiBold,
                        )
                        MonoText(
                            "${permissions.coreGrantedCount}/${permissions.coreTotalCount} nhóm đạt · kèm ảnh chụp màn hình & nút mở cài đặt",
                            9.5.sp,
                            if (permissions.ready) Accent else Warn,
                        )
                    }
                    LucideIcon(R.drawable.lc_chevron_right, if (permissions.ready) Accent else Warn, 16.dp)
                }
            }

            if (total == 0) {
                NoStepsCard(config)
            } else {
                Row(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(Track),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(effectivePassed / total.toFloat())
                                .clip(CircleShape)
                                .background(Accent),
                        )
                    }
                    MonoText("$effectivePassed / $total", 10.5.sp, TextLabel)
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface)
                        .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    setup.steps.forEachIndexed { index, step ->
                        val manual = manualStepAction(step.id)
                        CommandRow(
                            step = step,
                            liveGranted = liveStepStatus(step.id),
                            manualLabel = manual?.first,
                            onOpenManual = manual?.second,
                            onOpenGuide = onOpenPermissions,
                            expanded = expanded == step.id,
                            divider = index != setup.steps.lastIndex,
                            onClick = { expanded = if (expanded == step.id) null else step.id },
                        )
                    }
                }

                val failed = setup.failed
                if (failed.isNotEmpty()) {
                    FailCard(
                        count = failed.size,
                        body = failed.first().hint.ifBlank { failed.first().message },
                        onShowAdb = { showAdb = true },
                    )
                } else if (!permissions.shizukuGranted && !ran) {
                    ShizukuCard(
                        permissions = permissions,
                        startCommand = shizukuStartCommand,
                        onShowAdb = { showAdb = true },
                        onCopyStart = { command ->
                            copyToClipboard(context, "Khởi động Engine nội bộ", command)
                            onMessage("Đã copy lệnh khởi động Engine nội bộ.")
                        },
                        onPairWireless = onStartWirelessPairing,
                        onReconnect = onRequestShizuku,
                    )
                }

                Row(
                    Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LucideIcon(R.drawable.lc_shield_check, TextFaint, 13.dp)
                    BodyText("Mỗi lệnh đều đọc lại giá trị, không tin exit code", 11.sp, TextFaint)
                }
            }
            Gap(12.dp)
        }

        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val canAutoConnectWireless = permissions.adbWifiEnabled && permissions.wirelessAdbPaired
                val action: Triple<String, Int?, () -> Unit> = when {
                    total == 0 -> Triple(if (onboarding) "Vào app" else "Xong", R.drawable.lc_check, onFinish)
                    setup.running -> Triple("Đang chạy…", null, {})
                    !permissions.shizukuGranted && (permissions.shizukuRunning || canAutoConnectWireless) ->
                        Triple("Kết nối Engine", R.drawable.lc_terminal, onRequestShizuku)
                    !permissions.shizukuGranted ->
                        Triple("Ghép nối Gỡ lỗi không dây (6 số)", R.drawable.lc_terminal, onStartWirelessPairing)
                    setup.provisioningReport?.ok == true && onboarding -> Triple("Vào app", R.drawable.lc_check, onFinish)
                    ran -> Triple("Chạy lại từ đầu", R.drawable.lc_rotate_ccw, onRun)
                    else -> Triple("Chạy $total lệnh", R.drawable.lc_terminal, onRun)
                }
                PrimaryCta(
                    text = action.first,
                    onClick = action.third,
                    modifier = Modifier.weight(1f),
                    leadingIcon = action.second,
                    loading = setup.running,
                    height = 52.dp,
                    radius = 15.dp,
                )
                if (total > 0) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .border(1.dp, Hairline, RoundedCornerShape(15.dp))
                            .clickable(role = Role.Button, onClick = copyAdb),
                        contentAlignment = Alignment.Center,
                    ) {
                        LucideIcon(R.drawable.lc_copy, TextIcon, 16.dp, contentDescription = "Copy lệnh ADB")
                    }
                }
            }
            if (onboarding && total > 0 && setup.provisioningReport?.ok != true) {
                BodyText(
                    if (stepLabel == "BƯỚC 3 / 4") "Bỏ qua bước này" else "Làm sau, vào app",
                    13.sp,
                    TextDim,
                    Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onFinish)
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (showAdb) {
        AlertDialog(
            onDismissRequest = { showAdb = false },
            containerColor = Surface,
            title = { BodyText("Lệnh ADB", 18.sp, TextMain, weight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BodyText(
                        "Bật gỡ lỗi USB, cắm máy tính rồi dán vào PowerShell hoặc Terminal. Không cần root, không cần Shizuku.",
                        12.5.sp,
                        Muted,
                        lineHeight = 18.sp,
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Surface2)
                            .border(1.dp, Hairline, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                    ) {
                        Text(
                            setup.adbScript,
                            color = TextBody,
                            fontFamily = com.aikeybridge.ui.theme.MonoFont,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            softWrap = false,
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    copyAdb()
                    showAdb = false
                }) { BodyText("Copy", 14.sp, Accent, weight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showAdb = false }) { BodyText("Đóng", 14.sp, Muted) }
            },
        )
    }
}

@Composable
private fun CommandRow(
    step: SetupStepUi,
    liveGranted: Boolean?,
    manualLabel: String?,
    onOpenManual: (() -> Unit)?,
    onOpenGuide: (() -> Unit)?,
    expanded: Boolean,
    divider: Boolean,
    onClick: () -> Unit,
) {
    val (name, value) = remember(step.command) { Format.shortCommand(step.command) }
    val failed = step.status == StepStatus.FAILED
    val missingLive = step.status == StepStatus.PENDING && liveGranted == false
    val okLive = step.status == StepStatus.OK || (step.status == StepStatus.PENDING && liveGranted == true)
    Column(
        Modifier
            .fillMaxWidth()
            .hairlineBottom(divider)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
                when {
                    okLive -> LucideIcon(R.drawable.lc_check, Accent, 12.dp)
                    failed -> LucideIcon(R.drawable.lc_x, Danger, 12.dp)
                    step.status == StepStatus.RUNNING -> CircularProgressIndicator(Modifier.size(11.dp), color = Accent, strokeWidth = 1.5.dp)
                    missingLive -> StatusDot(Warn, 6.dp)
                    else -> StatusDot(TextFaint, 5.dp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BodyText(
                    step.title,
                    12.5.sp,
                    when {
                        failed -> DangerText
                        missingLive -> Warn
                        else -> TextMain
                    },
                    weight = FontWeight.Medium,
                    maxLines = 1,
                )
                MonoText(
                    when {
                        failed -> "$name · ${step.message.ifBlank { "lỗi" }}"
                        missingLive -> "$name · đang tắt — cần bật"
                        okLive -> "$name · đã bật"
                        else -> "$name · $value"
                    },
                    9.sp,
                    when {
                        failed -> DangerMuted
                        missingLive -> Warn
                        okLive -> Accent
                        else -> TextFaint
                    },
                )
            }
            if (onOpenManual != null) {
                MonoText(
                    if (missingLive) "Bật ↗" else "Cài đặt ↗",
                    9.sp,
                    if (missingLive) Warn else Accent,
                    Modifier
                        .clip(CircleShape)
                        .background((if (missingLive) Warn else Accent).copy(alpha = 0.12f))
                        .border(1.dp, (if (missingLive) Warn else Accent).copy(alpha = 0.35f), CircleShape)
                        .clickable(role = Role.Button, onClick = onOpenManual)
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
        }
        if (expanded) {
            Column(
                Modifier.padding(start = 22.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BodyText(step.detail, 11.5.sp, TextDim, lineHeight = 16.sp)
                MonoText(step.command, 9.sp, TextFaint, maxLines = 4)
                if (step.message.isNotBlank()) {
                    MonoText(step.message, 9.sp, if (failed) DangerText else Accent, maxLines = 4)
                }
                if ((failed || missingLive) && step.hint.isNotBlank()) {
                    BodyText(step.hint, 11.sp, Warn, lineHeight = 15.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (onOpenManual != null && manualLabel != null) {
                        LinkAction(manualLabel, onOpenManual)
                    }
                    if (onOpenGuide != null) {
                        LinkAction("Xem ảnh hướng dẫn", onOpenGuide)
                    }
                }
            }
        }
    }
}

@Composable
private fun FailCard(count: Int, body: String, onShowAdb: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DangerBg)
            .border(1.dp, DangerLine, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        BodyText("$count lệnh chưa chạy được", 13.sp, DangerTitle, weight = FontWeight.SemiBold)
        BodyText(body, 11.5.sp, DangerBody, lineHeight = 16.sp)
        LinkAction("Xem lệnh ADB", onShowAdb)
    }
}

@Composable
private fun ShizukuCard(
    permissions: PermissionSnapshot,
    startCommand: String?,
    onShowAdb: () -> Unit,
    onCopyStart: (String) -> Unit,
    onPairWireless: () -> Unit,
    onReconnect: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        BodyText(
            when {
                permissions.shizukuRunning -> "Engine / Shizuku đang chạy · cần cấp quyền"
                permissions.adbWifiEnabled -> "Gỡ lỗi không dây đang bật · chạm Kết nối Engine"
                else -> "Engine nội bộ (Không cần cài Shizuku)"
            },
            13.sp,
            TextMain,
            weight = FontWeight.SemiBold,
        )
        BodyText(
            when {
                permissions.shizukuRunning -> "Bấm Kết nối Engine ở dưới để cấp quyền chạy lệnh."
                permissions.adbWifiEnabled ->
                    "Bấm Kết nối Engine nếu đã ghép nối từ trước, hoặc bấm Ghép nối 6 số nếu là lần đầu tiên."
                else ->
                    "Bấm “Ghép nối 6 số” để kết nối trực tiếp qua Gỡ lỗi không dây ngay trên máy (không cần cài Shizuku), " +
                        "hoặc copy lệnh ADB bên dưới để chạy 1 lần từ máy tính."
            },
            11.5.sp,
            TextDim,
            lineHeight = 16.sp,
        )
        if (startCommand != null) {
            Text(
                startCommand,
                color = TextBody,
                fontFamily = com.aikeybridge.ui.theme.MonoFont,
                fontSize = 9.sp,
                lineHeight = 13.sp,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface2)
                    .clickable(role = Role.Button) { onCopyStart(startCommand) }
                    .padding(8.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            LinkAction("Ghép nối 6 số", onPairWireless)
            LinkAction("Kết nối lại", onReconnect)
            LinkAction("Xem lệnh ADB", onShowAdb)
        }
    }
}

@Composable
private fun NoStepsCard(config: ActiveConfig) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BodyText("Không cần lệnh shell", 13.sp, TextMain, weight = FontWeight.SemiBold)
        BodyText(
            "${config.target.displayName} được mở trực tiếp bằng gói ứng dụng, và ${config.profile.displayName} " +
                "không cần vá thêm. Chỉ cần Trợ năng và Tự khởi chạy.",
            11.5.sp,
            TextDim,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun LinkAction(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(top = 4.dp, bottom = 2.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BodyText(text, 12.sp, Accent, weight = FontWeight.Medium)
        LucideIcon(R.drawable.lc_arrow_up_right, Accent, 12.dp)
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
