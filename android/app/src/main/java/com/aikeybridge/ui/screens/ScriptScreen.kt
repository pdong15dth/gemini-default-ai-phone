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
import com.aikeybridge.service.LogAccess
import com.aikeybridge.shell.WatchGrants
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

/**
 * Script nền (Bank-Safe): app tự nghe event log bằng READ_LOGS, không cần Trợ năng.
 * Shizuku chỉ cần một lần để cấp quyền; tắt Gỡ lỗi / Tuỳ chọn nhà phát triển sau đó vẫn chạy.
 */
@Composable
fun ScriptScreen(
    config: ActiveConfig,
    permissions: PermissionSnapshot,
    status: ScriptStatus,
    watchReady: Boolean,
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
    val state = scriptState(status)
    val usage = config.profile.usageWatch != null

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
                    "Bấm bật một lần: app tự chạy các lệnh kích hoạt qua Shizuku rồi tự nghe $wake ngay trong app. " +
                    "Dùng app ngân hàng thì bắt buộc tắt Tuỳ chọn nhà phát triển và Gỡ lỗi USB — nghe nền vẫn chạy.",
                13.5.sp,
                Muted,
                lineHeight = 20.sp,
            )

            if (!watchReady) {
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
                        StatusDot(state.color, 7.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            BodyText(
                                when {
                                    status.busy -> "Đang kích hoạt & bật nghe nền…"
                                    status.listening -> "Đang nghe $wake trong app"
                                    status.running && status.access == LogAccess.OWN_ONLY -> "Android chưa cho đọc log"
                                    status.running && status.access == LogAccess.NO_PERMISSION -> "Thiếu quyền để nghe"
                                    status.running -> "Đang mở event log…"
                                    else -> "Đã tắt"
                                },
                                14.sp,
                                com.aikeybridge.ui.theme.TextMain,
                            )
                            MonoText(
                                when {
                                    status.listening -> "tắt Gỡ lỗi vẫn chạy · tự bật lại sau khi khởi động máy"
                                    status.running && status.access == LogAccess.OWN_ONLY ->
                                        "bấm Bật lại và chọn Cho phép · không thấy hộp thoại thì đợi 1 phút rồi bấm lại"
                                    status.running && status.access == LogAccess.NO_PERMISSION ->
                                        if (usage) "bật Truy cập dữ liệu sử dụng ở mục bên dưới" else "bấm Kích hoạt (cần Shizuku một lần)"
                                    status.running -> "Android hỏi quyền truy cập log thì chọn Cho phép"
                                    else -> "hô $wake sẽ vẫn mở ${config.profile.assistantName}"
                                },
                                9.sp,
                                if (status.listening) Accent else TextFaint,
                            )
                        }
                        if (status.running) {
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

                if (usage) {
                    // Vivo không cho shell bật hộ hai quyền này cho app cài ngoài: người dùng bật tay một lần.
                    SectionLabel("Quyền bật tay một lần", Modifier.padding(top = 12.dp))
                    Panel {
                        NavRow(
                            title = "Truy cập dữ liệu sử dụng",
                            subtitle = if (permissions.usageAccess) {
                                "đã bật · nghe ${config.profile.assistantName} không cần đọc log"
                            } else {
                                "bắt buộc · chạm để bật cho AI Key Bridge"
                            },
                            subColor = if (permissions.usageAccess) Accent else Warn,
                            trailing = if (permissions.usageAccess) null else "bật",
                            onClick = if (permissions.usageAccess) null else { { Intents.openUsageAccess(context) } },
                        )
                        NavRow(
                            title = "Hiển thị trên ứng dụng khác",
                            subtitle = if (permissions.overlay) {
                                "đã bật · mở ${config.target.displayName} được khi đã tắt Gỡ lỗi"
                            } else {
                                "cần để mở ${config.target.displayName} khi đã tắt Gỡ lỗi · chạm để bật"
                            },
                            subColor = if (permissions.overlay) Accent else Warn,
                            trailing = if (permissions.overlay) null else "bật",
                            onClick = if (permissions.overlay) null else { { Intents.openOverlaySettings(context) } },
                            divider = config.profile.oem != "vivo",
                        )
                        if (config.profile.oem == "vivo") {
                            // Không đọc được thiết lập này: "Kiểm soát thông minh" mặc định làm Vivo đóng băng app sau vài giây chạy nền.
                            NavRow(
                                title = "Pin dưới nền",
                                subtitle = "Pin → Kiểm soát pin dưới nền → AI Key Bridge → Cho phép sử dụng pin ngầm",
                                subColor = Warn,
                                trailing = "mở",
                                onClick = { Intents.openVivoBackgroundPower(context) },
                                divider = false,
                            )
                        }
                    }
                }

                if (status.listening) {
                    // Chỉ hiện khi đã nghe được: tắt Gỡ lỗi trước lúc đó là Shizuku dừng, chưa kịp cấp quyền đọc log.
                    SectionLabel("Bước cuối · dùng app ngân hàng", Modifier.padding(top = 12.dp))
                    Panel {
                        NavRow(
                            title = "Tắt Tuỳ chọn nhà phát triển & Gỡ lỗi USB",
                            subtitle = "bắt buộc, app ngân hàng chặn khi còn bật · nghe nền vẫn chạy",
                            subColor = Warn,
                            trailing = "mở",
                            onClick = { Intents.openDeveloperSettings(context) },
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
                    "Lưu ý: Tắt Gỡ lỗi là Shizuku dừng, nhưng nghe nền vẫn chạy vì nằm trong app và vẫn mở lớp phủ " +
                        "${config.target.displayName} như giữ nút nguồn. Khoá AI Key Bridge trong đa nhiệm để ROM không dọn app.",
                    11.sp,
                    TextFaint,
                    Modifier.padding(horizontal = 2.dp),
                    lineHeight = 16.sp,
                )

                SectionLabel("Không có Shizuku · cấp quyền từ máy tính", Modifier.padding(top = 12.dp))
                val commands = WatchGrants.adbCommands(context.packageName).joinToString("\n")
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
                            onMessage("Đã copy lệnh ADB cấp quyền. Chạy xong mở lại app và bấm Bật nghe nền.")
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
            if (watchReady) {
                val ctaText = when {
                    status.busy -> "Đang kích hoạt & bật nghe nền…"
                    permissions.shizukuGranted -> if (status.running) "Kích hoạt & Bật lại" else "Kích hoạt & Bật nghe nền"
                    permissions.readLogs || (usage && permissions.usageAccess) ->
                        if (status.running) "Bật lại nghe nền" else "Bật nghe nền"
                    !permissions.shizukuInstalled -> "Tải Shizuku"
                    !permissions.shizukuRunning -> "Mở Shizuku"
                    else -> "Cấp quyền Shizuku"
                }
                val ctaAction: () -> Unit = when {
                    permissions.shizukuGranted || permissions.readLogs || (usage && permissions.usageAccess) -> onStart
                    !permissions.shizukuInstalled || !permissions.shizukuRunning -> {
                        { Intents.openShizuku(context) }
                    }
                    else -> onRequestShizuku
                }
                PrimaryCta(
                    text = ctaText,
                    onClick = ctaAction,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = R.drawable.lc_terminal,
                    loading = status.busy,
                )
                if (status.running) {
                    OutlineCta(text = "Tắt nghe nền", onClick = onStop, modifier = Modifier.fillMaxWidth())
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

internal fun scriptState(status: ScriptStatus): ScriptStateUi = when {
    status.busy -> ScriptStateUi("…", "đang xử lý", TextDim)
    status.listening -> ScriptStateUi("đang nghe", "trong app · tắt Gỡ lỗi vẫn chạy", Accent)
    status.running && status.access == LogAccess.OWN_ONLY ->
        ScriptStateUi("chưa được đọc log", "mở màn Script nền, bấm Bật lại, chọn Cho phép", Danger)
    status.running && status.access == LogAccess.NO_PERMISSION ->
        ScriptStateUi("thiếu quyền", "mở màn Script nền để bật quyền", Danger)
    status.running -> ScriptStateUi("đang mở log", "chọn Cho phép nếu Android hỏi", Warn)
    else -> ScriptStateUi("đã tắt", "không cần Trợ năng", TextDim)
}
