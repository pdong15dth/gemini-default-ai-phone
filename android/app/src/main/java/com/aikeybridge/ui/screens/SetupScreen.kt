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
import com.aikeybridge.ui.components.LucideIcon
import com.aikeybridge.ui.components.MonoText
import com.aikeybridge.ui.components.PrimaryCta
import com.aikeybridge.ui.components.ScreenTitle
import com.aikeybridge.ui.components.StatusDot
import com.aikeybridge.ui.components.SubHeader
import com.aikeybridge.ui.components.hairlineBottom
import com.aikeybridge.ui.theme.Accent
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
    onFinish: () -> Unit,
    onMessage: (String) -> Unit,
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

    Column(modifier.fillMaxSize()) {
        SubHeader(onBack = onBack) {
            val (label, color) = when {
                permissions.shizukuGranted -> "shizuku · kết nối" to Accent
                permissions.shizukuRunning -> "shizuku · chưa cấp quyền" to Warn
                permissions.shizukuInstalled -> "shizuku · chưa chạy" to Warn
                else -> "shizuku · chưa cài" to TextFaint
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
                        "Không bắt buộc. Chưa chạy thì app mở ${config.target.displayName} toàn màn hình; " +
                            "chạy xong (và có Shizuku) thì mở lớp phủ nổi, kèm vá lỗi cho ${config.profile.assistantName}."
                    } else {
                        "Không bắt buộc — chỉ vá lỗi cho ${config.profile.assistantName}."
                    },
                    12.sp,
                    TextDim,
                    lineHeight = 17.sp,
                )
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
                                .fillMaxWidth(setup.passed / total.toFloat())
                                .clip(CircleShape)
                                .background(Accent),
                        )
                    }
                    MonoText("${setup.passed} / $total", 10.5.sp, TextLabel)
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
                        CommandRow(
                            step = step,
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
                            copyToClipboard(context, "Khởi động Shizuku", command)
                            onMessage("Đã copy lệnh khởi động Shizuku.")
                        },
                        onReconnect = { restartApp(context) },
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
                val action: Triple<String, Int?, () -> Unit> = when {
                    total == 0 -> Triple(if (onboarding) "Vào app" else "Xong", R.drawable.lc_check, onFinish)
                    setup.running -> Triple("Đang chạy…", null, {})
                    !permissions.shizukuInstalled -> Triple("Tải Shizuku", R.drawable.lc_arrow_up_right) {
                        Intents.openShizuku(context)
                    }
                    !permissions.shizukuRunning -> Triple("Mở Shizuku", R.drawable.lc_arrow_up_right) {
                        Intents.openShizuku(context)
                    }
                    !permissions.shizukuGranted -> Triple("Cấp quyền Shizuku", R.drawable.lc_terminal, onRequestShizuku)
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
private fun CommandRow(step: SetupStepUi, expanded: Boolean, divider: Boolean, onClick: () -> Unit) {
    val (name, value) = remember(step.command) { Format.shortCommand(step.command) }
    val failed = step.status == StepStatus.FAILED
    Column(
        Modifier
            .fillMaxWidth()
            .hairlineBottom(divider)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
                when (step.status) {
                    StepStatus.OK -> LucideIcon(R.drawable.lc_check, Accent, 12.dp)
                    StepStatus.FAILED -> LucideIcon(R.drawable.lc_x, Danger, 12.dp)
                    StepStatus.RUNNING -> CircularProgressIndicator(Modifier.size(11.dp), color = Accent, strokeWidth = 1.5.dp)
                    StepStatus.PENDING -> StatusDot(TextFaint, 5.dp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MonoText(name, 10.5.sp, if (failed) DangerText else TextBody)
                MonoText(
                    when {
                        failed -> "$value · ${step.message.ifBlank { "lỗi" }}"
                        else -> value
                    },
                    9.sp,
                    if (failed) DangerMuted else TextFaint,
                )
            }
        }
        if (expanded) {
            Column(
                Modifier.padding(start = 22.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BodyText(step.title, 12.5.sp, TextMain, weight = FontWeight.Medium)
                BodyText(step.detail, 11.5.sp, TextDim, lineHeight = 16.sp)
                MonoText(step.command, 9.sp, TextFaint, maxLines = 4)
                if (step.message.isNotBlank()) {
                    MonoText(step.message, 9.sp, if (failed) DangerText else Accent, maxLines = 4)
                }
                if (failed && step.hint.isNotBlank()) {
                    BodyText(step.hint, 11.sp, Warn, lineHeight = 15.sp)
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
        val notRunning = permissions.shizukuInstalled && !permissions.shizukuRunning
        BodyText(
            when {
                notRunning -> "Shizuku đã cài nhưng chưa chạy"
                permissions.shizukuRunning -> "Shizuku đang chạy · cần cấp quyền"
                else -> "Chạy qua Shizuku hoặc ADB"
            },
            13.sp,
            TextMain,
            weight = FontWeight.SemiBold,
        )
        BodyText(
            when {
                notRunning ->
                    "Mở Shizuku → Khởi động qua Gỡ lỗi không dây. Có máy tính thì cắm cáp và chạy lệnh dưới đây. " +
                        "Bật xong mà app vẫn báo chưa chạy, bấm Kết nối lại."
                permissions.shizukuRunning -> "Bấm Cấp quyền Shizuku rồi chọn Cho phép trong hộp thoại."
                else -> "Shizuku chạy lệnh ngay trên máy qua Gỡ lỗi không dây. Không có Shizuku thì copy lệnh ADB và dán trên máy tính một lần."
            },
            11.5.sp,
            TextDim,
            lineHeight = 16.sp,
        )
        if (notRunning && startCommand != null) {
            Text(
                startCommand,
                color = TextBody,
                fontFamily = com.aikeybridge.ui.theme.MonoFont,
                fontSize = 9.sp,
                lineHeight = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface2)
                    .clickable(role = Role.Button) { onCopyStart(startCommand) }
                    .padding(8.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (notRunning) LinkAction("Kết nối lại", onReconnect)
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
