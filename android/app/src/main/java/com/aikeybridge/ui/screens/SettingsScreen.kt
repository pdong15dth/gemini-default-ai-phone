package com.aikeybridge.ui.screens

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikeybridge.BuildConfig
import com.aikeybridge.R
import com.aikeybridge.data.ActiveConfig
import com.aikeybridge.data.AppSettings
import com.aikeybridge.data.PermissionSnapshot
import com.aikeybridge.data.ShellBackend
import com.aikeybridge.data.ThemeMode
import com.aikeybridge.diagnostics.Attempt
import com.aikeybridge.diagnostics.AttemptSummary
import com.aikeybridge.diagnostics.Diagnostics
import com.aikeybridge.ui.Format
import com.aikeybridge.ui.Intents
import com.aikeybridge.ui.ScriptStatus
import com.aikeybridge.ui.SetupUiState
import com.aikeybridge.ui.components.BodyText
import com.aikeybridge.ui.components.Gap
import com.aikeybridge.ui.components.LucideIcon
import com.aikeybridge.ui.components.MonoText
import com.aikeybridge.ui.components.NavRow
import com.aikeybridge.ui.components.OutlineCta
import com.aikeybridge.ui.components.Panel
import com.aikeybridge.ui.components.SectionLabel
import com.aikeybridge.ui.components.SegmentOption
import com.aikeybridge.ui.components.Segmented
import com.aikeybridge.ui.components.TabHeader
import com.aikeybridge.ui.components.hairlineBottom
import com.aikeybridge.ui.theme.Accent
import com.aikeybridge.ui.theme.Bar
import com.aikeybridge.ui.theme.CommunityBg
import com.aikeybridge.ui.theme.CommunityLine
import com.aikeybridge.ui.theme.CommunitySub
import com.aikeybridge.ui.theme.CommunityTitle
import com.aikeybridge.ui.theme.Danger
import com.aikeybridge.ui.theme.DangerMuted
import com.aikeybridge.ui.theme.DangerText
import com.aikeybridge.ui.theme.DangerTitle
import com.aikeybridge.ui.theme.Hairline
import com.aikeybridge.ui.theme.Muted
import com.aikeybridge.ui.theme.Surface
import com.aikeybridge.ui.theme.TextDim
import com.aikeybridge.ui.theme.TextFaint
import com.aikeybridge.ui.theme.TextGhost
import com.aikeybridge.ui.theme.TextIcon
import com.aikeybridge.ui.theme.TextLabel
import com.aikeybridge.ui.theme.TextMain
import com.aikeybridge.ui.theme.TextSoft
import com.aikeybridge.ui.theme.Warn

@Composable
fun SettingsScreen(
    config: ActiveConfig,
    settings: AppSettings,
    permissions: PermissionSnapshot,
    setup: SetupUiState,
    summary: AttemptSummary,
    attempts: List<Attempt>,
    entries: List<Diagnostics.Entry>,
    romChanged: Boolean,
    entryCount: Int,
    attemptCount: Int,
    now: Long,
    onOpenProfiles: () -> Unit,
    onOpenTriggers: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenShortcuts: () -> Unit,
    onOpenScript: () -> Unit,
    onOpenSetup: () -> Unit = {},
    scriptStatus: ScriptStatus,
    scriptStartedThisBoot: Boolean,
    onCheckScript: () -> Unit,
    onCheckRom: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
    onRestore: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit = {},
    onSetShellBackend: (ShellBackend) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    var showRawLogs by rememberSaveable { mutableStateOf(false) }
    var expandAllLogs by rememberSaveable { mutableStateOf(false) }

    val profile = config.profile
    if (config.shellWatch != null) {
        LaunchedEffect(Unit) { onCheckScript() }
    }

    val previewLimit = if (expandAllLogs) 40 else 5
    val recentAttempts = remember(attempts, previewLimit) { attempts.asReversed().take(previewLimit) }
    val recentEntries = remember(entries, previewLimit) { entries.asReversed().take(previewLimit) }

    Column(modifier.fillMaxSize()) {
        TabHeader("Nhật ký & Quản trị", "Hệ thống")

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 28.dp),
        ) {
            // 1. THỐNG KÊ & NHẬT KÝ HOẠT ĐỘNG (Hợp nhất từ LogsScreen)
            SectionLabel("Hoạt động 7 ngày")
            Panel {
                Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top) {
                                BodyText(
                                    summary.rate?.toString() ?: "—",
                                    34.sp,
                                    TextMain,
                                    weight = FontWeight.Bold,
                                    letterSpacing = (-1.5).sp,
                                    lineHeight = 38.sp,
                                )
                                if (summary.rate != null) {
                                    BodyText("%", 16.sp, Accent, Modifier.padding(top = 6.dp), weight = FontWeight.SemiBold)
                                }
                            }
                            MonoText("tỉ lệ chuyển hướng thành công", 9.sp, TextFaint)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            MonoText("${summary.total} lượt · ${Format.seconds(summary.avgMs)} tb", 9.5.sp, TextIcon)
                            MonoText(
                                if (summary.failed == 0) "không có lỗi" else "${summary.failed} lỗi trong 7 ngày",
                                9.sp,
                                if (summary.failed == 0) Accent else Danger,
                            )
                        }
                    }

                    SystemWeekChart(summary)
                }
            }

            Gap(12.dp)
            Segmented(
                options = listOf(
                    SegmentOption("Lịch sử gọi AI ($attemptCount)"),
                    SegmentOption("Log kỹ thuật ($entryCount)"),
                ),
                selected = if (showRawLogs) 1 else 0,
                onSelect = { showRawLogs = it == 1 },
            )
            Gap(8.dp)
            Panel {
                if (showRawLogs) {
                    if (recentEntries.isEmpty()) {
                        BodyText("Chưa có dòng log kỹ thuật nào.", 12.sp, TextFaint, Modifier.padding(vertical = 16.dp))
                    } else {
                        recentEntries.forEachIndexed { idx, entry ->
                            SystemEntryRow(
                                entry = entry,
                                divider = idx != recentEntries.lastIndex || entries.size > previewLimit,
                            )
                        }
                        if (entries.size > 5) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button) { expandAllLogs = !expandAllLogs }
                                    .padding(vertical = 11.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BodyText(
                                    if (expandAllLogs) "Thu gọn danh sách" else "Xem thêm (${entries.size - 5} dòng)",
                                    12.sp,
                                    Accent,
                                    weight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                } else {
                    if (recentAttempts.isEmpty()) {
                        BodyText(
                            "Chưa có lượt gọi AI nào được ghi nhận. Hãy thử gọi trợ lý gốc hoặc bấm nút AI.",
                            12.sp,
                            TextFaint,
                            Modifier.padding(vertical = 16.dp),
                            lineHeight = 17.sp,
                        )
                    } else {
                        recentAttempts.forEachIndexed { idx, attempt ->
                            SystemAttemptRow(
                                attempt = attempt,
                                divider = idx != recentAttempts.lastIndex || attempts.size > previewLimit,
                            )
                        }
                        if (attempts.size > 5) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button) { expandAllLogs = !expandAllLogs }
                                    .padding(vertical = 11.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BodyText(
                                    if (expandAllLogs) "Thu gọn danh sách" else "Xem thêm (${attempts.size - 5} lượt)",
                                    12.sp,
                                    Accent,
                                    weight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineCta(
                    text = "Xuất chẩn đoán",
                    icon = R.drawable.lc_file_down,
                    iconTint = Accent,
                    onClick = onExport,
                    modifier = Modifier.weight(1f),
                    height = 42.dp,
                    radius = 12.dp,
                )
                OutlineCta(
                    text = "Xoá nhật ký",
                    icon = R.drawable.lc_trash_2,
                    iconTint = TextDim,
                    onClick = { confirmClear = true },
                    modifier = Modifier.weight(1f),
                    height = 42.dp,
                    radius = 12.dp,
                )
            }

            // 2. GIAO DIỆN & CÔNG CỤ HỆ THỐNG
            SectionLabel("Giao diện hiển thị", Modifier.padding(top = 14.dp, bottom = 4.dp))
            Segmented(
                options = listOf(
                    SegmentOption("Theo hệ thống"),
                    SegmentOption("Sáng"),
                    SegmentOption("Tối"),
                ),
                selected = when (settings.themeMode) {
                    ThemeMode.SYSTEM -> 0
                    ThemeMode.LIGHT -> 1
                    ThemeMode.DARK -> 2
                },
                onSelect = { index ->
                    val mode = when (index) {
                        1 -> ThemeMode.LIGHT
                        2 -> ThemeMode.DARK
                        else -> ThemeMode.SYSTEM
                    }
                    onSetThemeMode(mode)
                },
            )

            SectionLabel("Công cụ chạy lệnh (Shell & Shizuku dự phòng)", Modifier.padding(top = 14.dp, bottom = 4.dp))
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
                onSelect = { index ->
                    val backend = when (index) {
                        1 -> ShellBackend.SHIZUKU
                        2 -> ShellBackend.BUILTIN
                        else -> ShellBackend.AUTO
                    }
                    onSetShellBackend(backend)
                },
            )

            // 3. BẢO TRÌ & KHÔI PHỤC
            SectionLabel("Bảo trì & Khôi phục", Modifier.padding(top = 14.dp, bottom = 4.dp))
            Panel {
                NavRow(
                    title = "Bảng ${setup.steps.size} lệnh kích hoạt & ADB máy tính",
                    subtitle = "Xem từng lệnh hệ thống hoặc lấy mã ADB",
                    trailing = "mở",
                    onClick = onOpenSetup,
                )
                NavRow(
                    title = "Danh sách quyền & Ảnh hướng dẫn",
                    subtitle = "Đạt ${permissions.coreGrantedCount}/${permissions.coreTotalCount} quyền cốt lõi · kèm ảnh chụp màn hình",
                    trailing = "xem",
                    onClick = onOpenPermissions,
                )
                NavRow(
                    title = "Kiểm tra thay đổi ROM",
                    subtitle = when {
                        romChanged -> "ROM đã cập nhật · nên chạy lại kích hoạt"
                        settings.lastRomCheckAt > 0 -> "Lần cuối ${Format.relative(settings.lastRomCheckAt, now)}"
                        else -> "Kiểm tra cấu hình sau khi cập nhật ROM"
                    },
                    subColor = if (romChanged) Warn else TextFaint,
                    trailing = "kiểm tra",
                    onClick = onCheckRom,
                )
                NavRow(
                    title = "Khôi phục mặc định ban đầu",
                    subtitle = "Trả trợ lý về ${profile.assistantName} và tắt Script nền",
                    titleColor = DangerText,
                    subColor = DangerMuted,
                    trailing = if (setup.restoring) null else "khôi phục",
                    trailingColor = Danger,
                    chevronColor = DangerMuted,
                    loading = setup.restoring,
                    onClick = { confirmRestore = true },
                    divider = false,
                )
            }

            Gap(16.dp)
            // Thẻ Open Source gọn ở cuối trang
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CommunityBg)
                    .border(1.dp, CommunityLine, RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) { Intents.openUrl(context, BuildConfig.REPO_URL) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LucideIcon(R.drawable.lc_git_branch, Accent, 15.dp)
                    BodyText(
                        "AI Key Bridge · Bản build cộng đồng",
                        13.5.sp,
                        CommunityTitle,
                        Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                        weight = FontWeight.SemiBold,
                    )
                    MonoText(
                        "OPEN SOURCE",
                        7.5.sp,
                        Accent,
                        Modifier
                            .clip(CircleShape)
                            .background(Accent.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        letterSpacing = 0.6.sp,
                    )
                }
                MonoText(
                    "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.GIT_SHA} · MIT License",
                    9.sp,
                    CommunitySub,
                )
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Xoá nhật ký?",
            body = "Xoá $entryCount dòng log và $attemptCount lượt chạy đã lưu. Biểu đồ 7 ngày sẽ về trống.",
            confirm = "Xoá",
            onConfirm = {
                confirmClear = false
                onClear()
            },
            onDismiss = { confirmClear = false },
        )
    }
    if (confirmRestore) {
        ConfirmDialog(
            title = "Khôi phục mặc định?",
            body = "Chạy ${setup.restoreSteps.size} lệnh trả trợ lý mặc định, appops và locale về như cũ, rồi tắt Script nền. " +
                "Sau đó bạn có thể chọn lại ${profile.assistantName} trong Cài đặt → Ứng dụng mặc định.",
            confirm = "Khôi phục",
            onConfirm = {
                confirmRestore = false
                onRestore()
            },
            onDismiss = { confirmRestore = false },
        )
    }
}

@Composable
private fun SystemWeekChart(summary: AttemptSummary) {
    val max = summary.days.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
    Column(
        Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = summary.days.joinToString { "${it.label}: ${it.total} lượt" }
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            summary.days.forEach { day ->
                val fraction = if (day.total == 0) 0.06f else 0.14f + 0.86f * day.total / max
                Box(
                    Modifier
                        .weight(1f)
                        .height(60.dp * fraction)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            when {
                                day.today -> Accent
                                day.failed > 0 && day.failed * 2 >= day.total -> Warn.copy(alpha = 0.5f)
                                else -> Bar
                            },
                        ),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            summary.days.forEach { day ->
                MonoText(day.label, 8.5.sp, if (day.today) Accent else TextGhost, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SystemAttemptRow(attempt: Attempt, divider: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .hairlineBottom(divider)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoText(Format.clock(attempt.at), 9.5.sp, TextFaint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BodyText(attempt.flow, 12.5.sp, if (attempt.ok) TextSoft else DangerTitle, maxLines = 1)
            MonoText(attempt.detail, 8.5.sp, TextGhost)
        }
        MonoText(Format.seconds(attempt.durationMs), 9.5.sp, if (attempt.ok) TextLabel else Danger)
    }
}

@Composable
private fun SystemEntryRow(entry: Diagnostics.Entry, divider: Boolean) {
    val color = when (entry.level) {
        Diagnostics.Level.SUCCESS -> Accent
        Diagnostics.Level.WARN -> Warn
        Diagnostics.Level.ERROR -> Danger
        Diagnostics.Level.INFO -> TextLabel
    }
    Row(
        Modifier
            .fillMaxWidth()
            .hairlineBottom(divider)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MonoText(Format.clockSeconds(entry.at), 8.5.sp, TextFaint)
        MonoText(entry.tag, 8.5.sp, color, Modifier.padding(end = 2.dp))
        BodyText(entry.message, 11.5.sp, TextSoft, Modifier.weight(1f), lineHeight = 16.sp, maxLines = 3)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { BodyText(title, 18.sp, TextMain, weight = FontWeight.SemiBold) },
        text = { BodyText(body, 13.sp, Muted, lineHeight = 19.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) { BodyText(confirm, 14.sp, Danger, weight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { BodyText("Huỷ", 14.sp, Muted) }
        },
    )
}
