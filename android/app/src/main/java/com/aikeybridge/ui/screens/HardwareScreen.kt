package com.aikeybridge.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikeybridge.R
import com.aikeybridge.data.ActiveConfig
import com.aikeybridge.data.AppSettings
import com.aikeybridge.data.HardwareMode
import com.aikeybridge.data.PermissionSnapshot
import com.aikeybridge.service.LogAccess
import com.aikeybridge.service.LogSniffer
import com.aikeybridge.ui.AppViewModel
import com.aikeybridge.ui.components.BodyText
import com.aikeybridge.ui.components.CircleButton
import com.aikeybridge.ui.components.Gap
import com.aikeybridge.ui.components.LucideIcon
import com.aikeybridge.ui.components.MonoText
import com.aikeybridge.ui.components.NavRow
import com.aikeybridge.ui.components.Panel
import com.aikeybridge.ui.components.PrimaryCta
import com.aikeybridge.ui.components.ScreenTitle
import com.aikeybridge.ui.components.SectionLabel
import com.aikeybridge.ui.components.SegmentOption
import com.aikeybridge.ui.components.Segmented
import com.aikeybridge.ui.components.SubHeader
import com.aikeybridge.ui.components.hairlineBottom
import com.aikeybridge.ui.theme.Accent
import com.aikeybridge.ui.theme.Muted
import com.aikeybridge.ui.theme.TextBody
import com.aikeybridge.ui.theme.TextDim
import com.aikeybridge.ui.theme.TextFaint
import com.aikeybridge.ui.theme.TextLabel
import com.aikeybridge.ui.theme.TextMain
import com.aikeybridge.ui.theme.Track
import com.aikeybridge.ui.theme.Warn
import kotlinx.coroutines.delay

@Composable
fun HardwareScreen(
    config: ActiveConfig,
    settings: AppSettings,
    permissions: PermissionSnapshot,
    logAccess: LogAccess,
    sniff: LogSniffer.State,
    readLogsCommand: String,
    onBack: () -> Unit,
    onSetMode: (HardwareMode) -> Unit,
    onGrantReadLogs: () -> Unit,
    onStartSniff: () -> Unit,
    onStopSniff: () -> Unit,
    onSave: (LogSniffer.Line) -> Unit,
    onRemove: (String) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var remainingMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sniff.active, sniff.endsAt) {
        while (sniff.active) {
            remainingMs = (sniff.endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            if (remainingMs == 0L) break
            delay(100)
        }
        if (!sniff.active) remainingMs = 0
    }
    val modeB = settings.hardwareMode == HardwareMode.LOGCAT
    val saved = settings.customLogPatterns.toSet()

    Column(modifier.fillMaxSize()) {
        SubHeader(onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 16.dp),
        ) {
            ScreenTitle("Nút cứng → AI", size = 26.sp)
            Gap(10.dp)
            BodyText(
                "Chế độ A: đặt nút thành “mở ${config.profile.assistantName}”, app bắt cửa sổ đó. " +
                    "Chế độ B: đặt nút thành “không hành động”, app đọc event log để biết bạn vừa bấm — " +
                    "không còn thấy ${config.profile.assistantName} nháy lên.",
                13.5.sp,
                Muted,
                lineHeight = 20.sp,
            )
            Gap(18.dp)
            Segmented(
                options = listOf(SegmentOption("A · bắt cửa sổ"), SegmentOption("B · đọc event log")),
                selected = if (modeB) 1 else 0,
                onSelect = { onSetMode(if (it == 1) HardwareMode.LOGCAT else HardwareMode.WINDOW) },
            )

            SectionLabel("Quyền đọc log", Modifier.padding(top = 12.dp))
            Panel {
                NavRow(
                    title = "READ_LOGS",
                    subtitle = when {
                        permissions.readLogs -> "đã cấp"
                        permissions.shizukuGranted -> "chạm để cấp qua Shizuku"
                        else -> "cần Shizuku hoặc ADB · chạm để copy lệnh"
                    },
                    subColor = if (permissions.readLogs) Accent else TextFaint,
                    trailing = if (permissions.readLogs) null else "cấp",
                    trailingColor = Accent,
                    onClick = when {
                        permissions.readLogs -> null
                        permissions.shizukuGranted -> onGrantReadLogs
                        else -> {
                            {
                                context.getSystemService(ClipboardManager::class.java)
                                    ?.setPrimaryClip(ClipData.newPlainText("READ_LOGS", readLogsCommand))
                                onMessage("Đã copy: $readLogsCommand")
                            }
                        }
                    },
                )
                NavRow(
                    title = "Truy cập log hệ thống",
                    subtitle = when (logAccess) {
                        LogAccess.OFF -> if (modeB) "đang chờ service" else "chỉ chạy ở chế độ B hoặc khi dò"
                        LogAccess.NO_PERMISSION -> "chưa có READ_LOGS"
                        LogAccess.WAITING -> "đang mở logcat…"
                        LogAccess.OWN_ONLY -> "chỉ thấy log của app — Android đang chặn"
                        LogAccess.SYSTEM -> "đọc được log hệ thống"
                    },
                    subColor = when (logAccess) {
                        LogAccess.SYSTEM -> Accent
                        LogAccess.OWN_ONLY, LogAccess.NO_PERMISSION -> Warn
                        else -> TextFaint
                    },
                    onClick = null,
                    divider = false,
                )
            }
            if (logAccess == LogAccess.OWN_ONLY) {
                BodyText(
                    "Android 13+ có thể hiện hộp thoại “Cho phép truy cập tất cả nhật ký thiết bị”. Chọn Cho phép. " +
                        "Nếu đã lỡ chọn Không, tắt rồi bật lại Trợ năng của app.",
                    11.sp,
                    Warn,
                    Modifier.padding(start = 2.dp, top = 8.dp),
                    lineHeight = 16.sp,
                )
            }

            SectionLabel("Mẫu log của nút", Modifier.padding(top = 12.dp))
            Panel {
                val builtIn = config.logTriggers.filterNot { it.custom }
                if (builtIn.isEmpty() && settings.customLogPatterns.isEmpty()) {
                    BodyText(
                        "${config.profile.displayName} chưa có mẫu log. Dò log để thêm.",
                        12.sp,
                        TextFaint,
                        Modifier.padding(vertical = 14.dp),
                    )
                }
                builtIn.forEachIndexed { index, trigger ->
                    PatternRow(
                        title = trigger.label,
                        pattern = trigger.pattern.pattern,
                        divider = index != builtIn.lastIndex || settings.customLogPatterns.isNotEmpty(),
                    )
                }
                settings.customLogPatterns.forEachIndexed { index, pattern ->
                    PatternRow(
                        title = "Tự dò",
                        pattern = pattern,
                        divider = index != settings.customLogPatterns.lastIndex,
                        onRemove = { onRemove(pattern) },
                    )
                }
            }

            if (sniff.active || sniff.lines.isNotEmpty()) {
                SectionLabel(
                    if (sniff.active) "Đang dò · ${(remainingMs + 999) / 1000}s" else "Kết quả dò · ${sniff.lines.size} dòng",
                    Modifier.padding(top = 12.dp),
                )
                if (sniff.active) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Track),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(remainingMs / AppViewModel.SNIFF_WINDOW_MS.toFloat())
                                .background(Accent),
                        )
                    }
                }
                Panel {
                    if (sniff.lines.isEmpty()) {
                        BodyText(
                            "Bấm nút cứng ngay bây giờ…",
                            12.sp,
                            TextFaint,
                            Modifier.padding(vertical = 14.dp),
                        )
                    }
                    sniff.lines.asReversed().forEachIndexed { index, line ->
                        val isSaved = line.key in saved
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .hairlineBottom(index != sniff.lines.lastIndex)
                                .clickable(enabled = !isSaved, role = Role.Button) { onSave(line) }
                                .padding(vertical = 11.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                MonoText(line.sample, 9.5.sp, TextBody, maxLines = 3)
                                MonoText("${line.hits} lần", 8.5.sp, TextFaint)
                            }
                            if (isSaved) {
                                LucideIcon(R.drawable.lc_check, Accent, 14.dp)
                            } else {
                                MonoText("lưu", 9.5.sp, TextLabel)
                            }
                        }
                    }
                }
            }
            Gap(10.dp)
            BodyText(
                "Chế độ B tốn pin hơn một chút vì phải đọc log liên tục. Nếu chế độ A chạy ổn, cứ dùng A.",
                11.sp,
                TextFaint,
                Modifier.padding(horizontal = 2.dp),
                lineHeight = 16.sp,
            )
        }
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
        ) {
            val canSniff = permissions.shizukuGranted || permissions.readLogs
            if (!canSniff) {
                MonoText("Cần cấp quyền Shizuku hoặc READ_LOGS để dò log.", 9.5.sp, TextDim, Modifier.padding(start = 2.dp, bottom = 10.dp))
            }
            PrimaryCta(
                text = if (sniff.active) "Dừng dò" else "Dò log trong ${AppViewModel.SNIFF_WINDOW_MS / 1000} giây",
                onClick = if (sniff.active) onStopSniff else onStartSniff,
                leadingIcon = if (sniff.active) R.drawable.lc_x else R.drawable.lc_scan_search,
                enabled = canSniff || sniff.active,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PatternRow(title: String, pattern: String, divider: Boolean, onRemove: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .hairlineBottom(divider)
            .padding(vertical = if (onRemove == null) 13.dp else 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BodyText(title, 13.5.sp, TextMain, maxLines = 1)
            MonoText(pattern, 9.sp, TextFaint, maxLines = 3)
        }
        if (onRemove != null) {
            CircleButton(R.drawable.lc_trash_2, "Xoá mẫu", onRemove, tint = TextDim, iconSize = 14.dp)
        }
    }
}
