package com.aikeybridge.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikeybridge.R
import com.aikeybridge.data.ActiveConfig
import com.aikeybridge.data.PermissionSnapshot
import com.aikeybridge.shell.WatchScript
import com.aikeybridge.ui.ScriptStatus
import com.aikeybridge.ui.components.BodyText
import com.aikeybridge.ui.components.Gap
import com.aikeybridge.ui.components.MonoText
import com.aikeybridge.ui.components.NavRow
import com.aikeybridge.ui.components.OutlineCta
import com.aikeybridge.ui.components.Panel
import com.aikeybridge.ui.components.PrimaryCta
import com.aikeybridge.ui.components.ScreenTitle
import com.aikeybridge.ui.components.SectionLabel
import com.aikeybridge.ui.components.StatusDot
import com.aikeybridge.ui.components.SubHeader
import com.aikeybridge.ui.theme.Accent
import com.aikeybridge.ui.theme.Danger
import com.aikeybridge.ui.theme.Hairline
import com.aikeybridge.ui.theme.MonoFont
import com.aikeybridge.ui.theme.Muted
import com.aikeybridge.ui.theme.Surface2
import com.aikeybridge.ui.theme.TextBody
import com.aikeybridge.ui.theme.TextDim
import com.aikeybridge.ui.theme.TextFaint
import com.aikeybridge.ui.theme.TextLabel
import com.aikeybridge.ui.theme.Warn

/** Script nền như README: chạy bằng quyền shell qua Shizuku, không cần Trợ năng. */
@Composable
fun ScriptScreen(
    config: ActiveConfig,
    permissions: PermissionSnapshot,
    status: ScriptStatus,
    startedThisBoot: Boolean,
    scriptContent: String?,
    onboarding: Boolean,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onFinish: () -> Unit,
    onOpenSetup: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LaunchedEffect(permissions.shizukuGranted) { onCheck() }
    val wake = config.profile.wakePhrase?.let { "“$it”" } ?: config.profile.assistantName
    val running = status.running ?: startedThisBoot

    Column(modifier.fillMaxSize()) {
        SubHeader(onBack = onBack) {
            if (onboarding) MonoText("BƯỚC 4 / 4", 10.sp, TextDim, letterSpacing = 1.2.sp)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 16.dp),
        ) {
            ScreenTitle("Script nền", size = 26.sp)
            Gap(10.dp)
            BodyText(
                "Giống cách trong README: một script shell đọc event log, thấy ${config.profile.assistantName} " +
                    "thì bấm Back rồi gọi ${config.target.displayName} bằng phím trợ lý. " +
                    "Không cần Trợ năng — Shizuku thay cho máy tính.",
                13.5.sp,
                Muted,
                lineHeight = 20.sp,
            )

            if (scriptContent == null) {
                Gap(16.dp)
                BodyText(
                    "${config.profile.displayName} chưa có script nền. Dùng chế độ Đầy đủ hoặc Một lần.",
                    12.sp,
                    Warn,
                    lineHeight = 17.sp,
                )
            } else {
                SectionLabel("Trạng thái", Modifier.padding(top = 12.dp))
                Panel {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(
                            when {
                                status.running == true -> Accent
                                status.running == false -> Danger
                                running -> Warn
                                else -> TextDim
                            },
                            7.dp,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            BodyText(
                                when {
                                    status.busy -> "Đang xử lý…"
                                    status.running == true -> "Đang chạy"
                                    status.running == false -> "Đã dừng"
                                    startedThisBoot -> "Có lẽ đang chạy"
                                    else -> "Chưa bật từ lần khởi động máy này"
                                },
                                14.sp,
                                com.aikeybridge.ui.theme.TextMain,
                            )
                            MonoText(
                                when {
                                    status.running == true -> "pid ${status.pid} · ${WatchScript.PATH}"
                                    status.running == null -> "cần Shizuku để kiểm tra chính xác"
                                    else -> "hô $wake sẽ vẫn mở ${config.profile.assistantName}"
                                },
                                9.sp,
                                TextFaint,
                            )
                        }
                        if (permissions.shizukuGranted) {
                            MonoText(
                                "kiểm tra",
                                9.5.sp,
                                TextLabel,
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(role = Role.Button, onClick = onCheck)
                                    .padding(6.dp),
                            )
                        }
                    }
                }

                SectionLabel("Cần có", Modifier.padding(top = 12.dp))
                Panel {
                    NavRow(
                        title = "Google là trợ lý mặc định",
                        subtitle = if (permissions.assistantDefault) "đã đặt" else "chưa đặt · phím trợ lý sẽ mở ${config.profile.assistantName}",
                        subColor = if (permissions.assistantDefault) Accent else Danger,
                        trailing = if (permissions.assistantDefault) null else "kích hoạt",
                        onClick = if (permissions.assistantDefault) null else onOpenSetup,
                    )
                    NavRow(
                        title = "Shizuku",
                        subtitle = when {
                            permissions.shizukuGranted -> "đã cấp quyền"
                            permissions.shizukuRunning -> "đang chạy · chưa cấp quyền"
                            permissions.shizukuInstalled -> "đã cài · chưa chạy"
                            else -> "chưa cài · dùng lệnh ADB bên dưới"
                        },
                        subColor = if (permissions.shizukuGranted) Accent else Warn,
                        onClick = if (permissions.shizukuGranted) null else onOpenSetup,
                        divider = false,
                    )
                }
                Gap(10.dp)
                BodyText(
                    "Script mất khi khởi động lại máy (Shizuku không root cũng vậy). Sau mỗi lần khởi động: bật Shizuku, mở app, bấm Bật script.",
                    11.sp,
                    TextFaint,
                    Modifier.padding(horizontal = 2.dp),
                    lineHeight = 16.sp,
                )

                SectionLabel("Không có Shizuku · chạy từ máy tính", Modifier.padding(top = 12.dp))
                val commands = WatchScript.adbCommands(scriptContent).joinToString("\n")
                Text(
                    commands,
                    color = TextBody,
                    fontFamily = MonoFont,
                    fontSize = 9.sp,
                    lineHeight = 13.sp,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface2)
                        .border(1.dp, Hairline, RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) {
                            context.getSystemService(ClipboardManager::class.java)
                                ?.setPrimaryClip(ClipData.newPlainText("Script nền", commands))
                            onMessage("Đã copy lệnh ADB bật script nền.")
                        }
                        .padding(10.dp),
                )
                MonoText("chạm để copy", 9.sp, TextDim, Modifier.padding(start = 2.dp, top = 6.dp))
            }
        }

        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (scriptContent != null) {
                PrimaryCta(
                    text = if (status.running == true) "Bật lại script" else "Bật script nền",
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = R.drawable.lc_terminal,
                    enabled = permissions.shizukuGranted,
                    loading = status.busy,
                )
                if (status.running == true) {
                    OutlineCta(text = "Tắt script", onClick = onStop, modifier = Modifier.fillMaxWidth())
                }
            }
            if (onboarding) {
                OutlineCta(text = "Xong · vào app", onClick = onFinish, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Nhãn ngắn cho trạng thái script nền, dùng ở Cài đặt và Chế độ. */
internal data class ScriptStateUi(val label: String, val detail: String, val color: androidx.compose.ui.graphics.Color)

internal fun scriptState(status: ScriptStatus, startedThisBoot: Boolean): ScriptStateUi = when {
    status.busy -> ScriptStateUi("…", "đang xử lý", TextDim)
    status.running == true -> ScriptStateUi("đang chạy", "pid ${status.pid} · không cần Trợ năng", Accent)
    status.running == false -> ScriptStateUi("đã dừng", "chạm để bật lại", Danger)
    startedThisBoot -> ScriptStateUi("có lẽ chạy", "cần Shizuku để kiểm tra", Warn)
    else -> ScriptStateUi("chưa bật", "như README · không cần Trợ năng", TextDim)
}
