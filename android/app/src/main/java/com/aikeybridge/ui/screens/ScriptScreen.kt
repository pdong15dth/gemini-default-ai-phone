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
import com.aikeybridge.ui.Intents
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
    onOpenTriggers: () -> Unit = {},
    onRequestShizuku: () -> Unit = {},
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
                "Bản thuần Script nền — hoàn toàn không có dịch vụ Trợ năng trong APK nên dùng được mọi app ngân hàng. " +
                    "Bấm bật một lần: app tự chạy các lệnh kích hoạt + bật Gỡ lỗi USB giữ nền + chạy script ngầm trong RAM.",
                13.5.sp,
                Muted,
                lineHeight = 20.sp,
            )

            if (scriptContent == null) {
                Gap(16.dp)
                BodyText(
                    "${config.profile.displayName} chưa có mẫu cửa sổ mặc định. Bấm “Dò cửa sổ trợ lý” bên dưới để tự bắt cửa sổ bằng Shizuku.",
                    12.sp,
                    Warn,
                    lineHeight = 17.sp,
                )
                Gap(12.dp)
                OutlineCta(
                    text = "Dò cửa sổ trợ lý",
                    onClick = onOpenTriggers,
                    modifier = Modifier.fillMaxWidth(),
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
                                    status.busy -> "Đang kích hoạt & bật script…"
                                    status.running == true -> "Đang chạy ngầm trong RAM"
                                    status.running == false -> "Đã dừng"
                                    startedThisBoot -> "Đang chạy (đã tắt Shizuku / Gỡ lỗi)"
                                    else -> "Chưa bật từ lần khởi động máy này"
                                },
                                14.sp,
                                com.aikeybridge.ui.theme.TextMain,
                            )
                            MonoText(
                                when {
                                    status.running == true && status.detachedFromAdbd == true ->
                                        "pid ${status.pid} · đã tách khỏi adbd · tắt Gỡ lỗi không chết"
                                    status.running == true -> "pid ${status.pid} · ${WatchScript.PATH}"
                                    startedThisBoot -> "đã bật trong phiên khởi động này · gọi $wake để thử"
                                    status.running == null -> "bật Shizuku để kích hoạt & khởi chạy"
                                    else -> "hô $wake sẽ vẫn mở ${config.profile.assistantName}"
                                },
                                9.sp,
                                if (status.running == true || startedThisBoot) Accent else TextFaint,
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

                if (status.running == true || startedThisBoot) {
                    SectionLabel("Giữ Script sống 24/7", Modifier.padding(top = 12.dp))
                    Panel {
                        NavRow(
                            title = "Có thể tắt Gỡ lỗi không dây / tắt Wi-Fi",
                            subtitle = "NHƯNG giữ bật Gỡ lỗi USB & Tuỳ chọn nhà phát triển (tắt là Android giết script)",
                            subColor = Warn,
                            onClick = null,
                            divider = false,
                        )
                    }
                }

                SectionLabel("Cấu hình & Cửa sổ bắt", Modifier.padding(top = 12.dp))
                Panel {
                    NavRow(
                        title = "Google là trợ lý mặc định",
                        subtitle = if (permissions.assistantDefault) {
                            "đã đặt"
                        } else {
                            "tự động đặt khi bấm Bật script nền"
                        },
                        subColor = if (permissions.assistantDefault) Accent else Warn,
                        trailing = if (permissions.assistantDefault) null else "chi tiết",
                        onClick = onOpenSetup,
                    )
                    NavRow(
                        title = "Shizuku",
                        subtitle = when {
                            permissions.shizukuGranted -> "đã cấp quyền"
                            permissions.shizukuRunning -> "đang chạy · chạm để cấp quyền"
                            permissions.shizukuInstalled -> "đã cài · chạm để mở Shizuku"
                            else -> "chưa cài · chạm để tải hoặc dùng ADB bên dưới"
                        },
                        subColor = if (permissions.shizukuGranted) Accent else Warn,
                        onClick = when {
                            permissions.shizukuGranted -> null
                            permissions.shizukuRunning -> onRequestShizuku
                            else -> { { Intents.openShizuku(context) } }
                        },
                    )
                    NavRow(
                        title = "Mẫu cửa sổ (${config.profile.assistantName})",
                        subtitle = config.shellWatch?.panel ?: "chưa có",
                        subColor = TextFaint,
                        trailing = "dò thêm",
                        onClick = onOpenTriggers,
                        divider = false,
                    )
                }
                Gap(10.dp)
                BodyText(
                    "Lưu ý: Không tắt Gỡ lỗi USB hoặc Tuỳ chọn nhà phát triển (tắt là Android dừng adbd và giết cả Shizuku lẫn script nền). Bạn có thể tắt Gỡ lỗi không dây hoặc ngắt Wi-Fi dùng 4G bình thường vì app đã tự giữ công tắc Gỡ lỗi USB.",
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
                val ctaText = when {
                    status.busy -> "Đang kích hoạt & bật script…"
                    !permissions.shizukuInstalled -> "Tải Shizuku"
                    !permissions.shizukuRunning -> "Mở Shizuku"
                    !permissions.shizukuGranted -> "Cấp quyền Shizuku"
                    status.running == true -> "Kích hoạt & Bật lại script"
                    else -> "Kích hoạt & Bật script nền"
                }
                val ctaAction: () -> Unit = when {
                    !permissions.shizukuInstalled || !permissions.shizukuRunning -> {
                        { Intents.openShizuku(context) }
                    }
                    !permissions.shizukuGranted -> onRequestShizuku
                    else -> onStart
                }
                PrimaryCta(
                    text = ctaText,
                    onClick = ctaAction,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = R.drawable.lc_terminal,
                    loading = status.busy,
                )
                if (status.running == true && permissions.shizukuGranted) {
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
