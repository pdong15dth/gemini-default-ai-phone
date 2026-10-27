package com.aikeybridge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.aikeybridge.BuildConfig
import com.aikeybridge.R
import com.aikeybridge.data.ActiveConfig
import com.aikeybridge.data.AppSettings
import com.aikeybridge.data.PermissionSnapshot
import com.aikeybridge.ui.Format
import com.aikeybridge.ui.Intents
import com.aikeybridge.ui.ScriptStatus
import com.aikeybridge.ui.SetupUiState
import com.aikeybridge.ui.components.StatusDot
import com.aikeybridge.ui.components.BodyText
import com.aikeybridge.ui.components.Gap
import com.aikeybridge.ui.components.LucideIcon
import com.aikeybridge.ui.components.MonoText
import com.aikeybridge.ui.components.NavRow
import com.aikeybridge.ui.components.Panel
import com.aikeybridge.ui.components.SectionLabel
import com.aikeybridge.ui.components.TabHeader
import com.aikeybridge.ui.theme.Accent
import com.aikeybridge.ui.theme.CommunityBg
import com.aikeybridge.ui.theme.CommunityLine
import com.aikeybridge.ui.theme.CommunitySub
import com.aikeybridge.ui.theme.CommunityTitle
import com.aikeybridge.ui.theme.Danger
import com.aikeybridge.ui.theme.DangerMuted
import com.aikeybridge.ui.theme.DangerText
import com.aikeybridge.ui.theme.Muted
import com.aikeybridge.ui.theme.Surface
import com.aikeybridge.ui.theme.TextFaint
import com.aikeybridge.ui.theme.TextGhost
import com.aikeybridge.ui.theme.TextMain
import com.aikeybridge.ui.theme.Warn

@Composable
fun SettingsScreen(
    config: ActiveConfig,
    settings: AppSettings,
    permissions: PermissionSnapshot,
    setup: SetupUiState,
    romChanged: Boolean,
    entryCount: Int,
    attemptCount: Int,
    now: Long,
    onOpenProfiles: () -> Unit,
    onOpenTriggers: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenShortcuts: () -> Unit,
    onOpenScript: () -> Unit,
    scriptStatus: ScriptStatus,
    scriptStartedThisBoot: Boolean,
    onCheckScript: () -> Unit,
    onCheckRom: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    val profile = config.profile
    val granted = listOf(
        permissions.accessibility,
        settings.manualAutostart,
        permissions.batteryUnrestricted,
        permissions.targetInstalled,
    ).count { it }

    Column(modifier.fillMaxSize()) {
        TabHeader("AI Key Bridge", "Cài đặt")
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 16.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CommunityBg)
                    .border(1.dp, CommunityLine, RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button) { Intents.openUrl(context, BuildConfig.REPO_URL) }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LucideIcon(R.drawable.lc_git_branch, Accent, 16.dp)
                    BodyText(
                        "Bản build cộng đồng",
                        14.sp,
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
                    "v${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE} · ${BuildConfig.GIT_SHA} · MIT",
                    9.sp,
                    CommunitySub,
                )
            }

            SectionLabel("Thiết bị", Modifier.padding(top = 8.dp, bottom = 3.dp))
            Panel {
                NavRow(
                    title = "Profile ROM",
                    subtitle = "${profile.oem} · ${profile.os.lowercase()} · " +
                        when {
                            config.manualProfile -> "chọn tay"
                            profile.isGeneric -> "chưa nhận diện được"
                            else -> "tự nhận diện"
                        },
                    trailing = when {
                        profile.isGeneric -> "chọn"
                        profile.verified -> "đổi"
                        else -> "beta"
                    },
                    trailingColor = if (profile.verified || profile.isGeneric) TextFaint else Warn,
                    onClick = onOpenProfiles,
                )
                NavRow(
                    title = "Trợ lý gốc & trigger",
                    subtitle = "${profile.assistantName} · ${config.triggers.size} trigger" +
                        if (settings.customTriggers.isNotEmpty()) " · ${settings.customTriggers.size} tự dò" else "",
                    trailing = "dò",
                    onClick = onOpenTriggers,
                )
                if (config.profile.shellWatch != null) {
                    LaunchedEffect(Unit) { onCheckScript() }
                    val state = scriptState(scriptStatus, scriptStartedThisBoot)
                    NavRow(
                        title = "Script nền",
                        subtitle = state.detail,
                        subColor = state.color,
                        trailing = state.label,
                        trailingColor = state.color,
                        loading = scriptStatus.busy,
                        onClick = onOpenScript,
                        leading = { StatusDot(state.color, 7.dp) },
                    )
                }
                NavRow(
                    title = "Gán phím & lối tắt",
                    subtitle = "Gọi AI · chạm mặt lưng · phím tắt ROM",
                    trailing = "gán",
                    onClick = onOpenShortcuts,
                )
                NavRow(
                    title = "Quyền hệ thống",
                    subtitle = "$granted / 4 đã bật",
                    trailing = if (permissions.ready) null else "thiếu",
                    trailingColor = Danger,
                    onClick = onOpenPermissions,
                )
                NavRow(
                    title = "Kiểm tra thay đổi ROM",
                    subtitle = when {
                        romChanged -> "ROM đã cập nhật · nên kích hoạt lại"
                        settings.lastRomCheckAt > 0 -> "lần cuối ${Format.relative(settings.lastRomCheckAt, now)}"
                        else -> "chưa kiểm tra"
                    },
                    subColor = if (romChanged) Warn else TextFaint,
                    trailing = "chạy",
                    onClick = onCheckRom,
                    divider = false,
                )
            }

            SectionLabel("Dữ liệu", Modifier.padding(top = 8.dp, bottom = 3.dp))
            Panel {
                NavRow(
                    title = "Xuất log chẩn đoán",
                    subtitle = if (permissions.shizukuGranted) {
                        ".zip · log app + log toàn máy qua Shizuku"
                    } else {
                        ".zip · log app · bật Shizuku để kèm log toàn máy"
                    },
                    onClick = onExport,
                )
                NavRow(
                    title = "Xoá nhật ký",
                    subtitle = "$entryCount dòng log · $attemptCount lượt chạy",
                    onClick = { confirmClear = true },
                    divider = false,
                )
            }

            SectionLabel("Khôi phục", Modifier.padding(top = 8.dp, bottom = 3.dp))
            Panel {
                NavRow(
                    title = "Khôi phục mặc định",
                    subtitle = "trả trợ lý về ${profile.assistantName}, bỏ các bản vá",
                    titleColor = DangerText,
                    subColor = DangerMuted,
                    trailing = if (setup.restoring) null else "chạy",
                    trailingColor = Danger,
                    chevronColor = DangerMuted,
                    loading = setup.restoring,
                    onClick = { confirmRestore = true },
                    divider = false,
                )
            }
            BodyText(
                "Cần Shizuku. Chuyển hướng sẽ tắt; Trợ năng vẫn bật cho tới khi bạn tự tắt trong Cài đặt.",
                11.sp,
                TextFaint,
                Modifier.padding(start = 2.dp, top = 9.dp),
                lineHeight = 16.sp,
            )

            Gap(28.dp)
            Row(Modifier.fillMaxWidth()) {
                MonoText("AI Key Bridge ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", 9.sp, TextGhost, Modifier.weight(1f))
                MonoText("Shizuku · Apache-2.0", 9.sp, TextGhost)
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
            body = "Chạy ${setup.restoreSteps.size} lệnh trả trợ lý mặc định, appops và locale về như cũ, rồi tắt chuyển hướng. " +
                "Sau đó chọn lại ${profile.assistantName} trong Cài đặt → Ứng dụng mặc định.",
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
