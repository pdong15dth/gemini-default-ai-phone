package com.aikeybridge.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikeybridge.LaunchActivity
import com.aikeybridge.R
import com.aikeybridge.data.ActiveConfig
import com.aikeybridge.data.UsageMode
import com.aikeybridge.ui.Intents
import com.aikeybridge.ui.components.BodyText
import com.aikeybridge.ui.components.Gap
import com.aikeybridge.ui.components.MonoText
import com.aikeybridge.ui.components.NavRow
import com.aikeybridge.ui.components.OutlineCta
import com.aikeybridge.ui.components.Panel
import com.aikeybridge.ui.components.PrimaryCta
import com.aikeybridge.ui.components.ScreenTitle
import com.aikeybridge.ui.components.SectionLabel
import com.aikeybridge.ui.components.SubHeader
import com.aikeybridge.ui.components.ToggleRow
import com.aikeybridge.ui.theme.Accent
import com.aikeybridge.ui.theme.Muted
import com.aikeybridge.ui.theme.TextDim
import com.aikeybridge.ui.theme.TextFaint

/** Gán phím / chạm mặt lưng / màn hình chính vào lối tắt "Gọi AI". */
@Composable
fun ShortcutsScreen(
    config: ActiveConfig,
    usageMode: UsageMode,
    accessibilityOn: Boolean,
    onboarding: Boolean,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var aliasOn by remember { mutableStateOf(Intents.isLaunchAliasEnabled(context)) }
    val vivo = config.profile.oem == "vivo"
    val target = config.target.displayName

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
            ScreenTitle("Gán phím & lối tắt", size = 26.sp)
            Gap(10.dp)
            BodyText(
                "Lối tắt “Gọi AI” mở thẳng $target. " +
                    if (accessibilityOn) {
                        "Trợ năng đang bật nên app sẽ tự bấm Micro luôn."
                    } else {
                        "Không có Trợ năng thì $target mở ra và bạn tự bấm Micro."
                    },
                13.5.sp,
                Muted,
                lineHeight = 20.sp,
            )

            if (vivo) {
                Gap(10.dp)
                BodyText(
                    "OriginOS sẽ hỏi “AI Key Bridge muốn mở ${config.target.displayName}” ở lần đầu — chọn Luôn mở, nếu không mỗi lần chuyển đều bị chặn.",
                    11.5.sp,
                    com.aikeybridge.ui.theme.Warn,
                    lineHeight = 16.sp,
                )
            }
            SectionLabel("Lối tắt Gọi AI", Modifier.padding(top = 12.dp))
            Panel {
                ToggleRow(
                    title = "Hiện “Gọi AI” trong danh sách app",
                    subtitle = "để ROM chọn được khi gán “mở ứng dụng”",
                    checked = aliasOn,
                    onCheckedChange = {
                        Intents.setLaunchAlias(context, it)
                        aliasOn = Intents.isLaunchAliasEnabled(context)
                    },
                )
                NavRow(
                    title = "Thử Gọi AI ngay",
                    subtitle = "mở $target như khi bấm phím",
                    trailing = "thử",
                    trailingColor = Accent,
                    onClick = {
                        context.startActivity(
                            Intent(context, LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    divider = false,
                )
            }

            SectionLabel(if (vivo) "Gán trên Vivo / iQOO" else "Gán trên máy của bạn", Modifier.padding(top = 12.dp))
            Panel {
                if (vivo) {
                    NavRow(
                        title = "Chạm mặt lưng",
                        subtitle = "Cài đặt → tìm “Chạm mặt lưng” → Mở ứng dụng → Gọi AI",
                        trailing = "mở",
                        onClick = { Intents.openBackTap(context) },
                    )
                    NavRow(
                        title = "Khởi chạy nhanh",
                        subtitle = "giữ Giảm âm lượng khi tắt màn hình → Gọi AI",
                        trailing = "mở",
                        onClick = { Intents.openQuickLaunch(context) },
                    )
                    NavRow(
                        title = "Nút thông minh",
                        subtitle = "nhấn giữ → Trợ lý giọng nói (dùng trợ lý mặc định)",
                        trailing = "mở",
                        onClick = { Intents.openSmartKey(context) },
                        divider = false,
                    )
                } else {
                    NavRow(
                        title = "Cài đặt phím tắt của ROM",
                        subtitle = "tìm “phím tắt”, “cử chỉ” hoặc “chạm mặt lưng” → Gọi AI",
                        trailing = "mở",
                        onClick = { Intents.openSmartKey(context) },
                        divider = false,
                    )
                }
            }
            Gap(12.dp)
            BodyText(
                "Màn hình chính: nhấn giữ icon AI Key Bridge → kéo lối tắt “Gọi AI” ra ngoài.",
                11.sp,
                TextFaint,
                Modifier.padding(horizontal = 2.dp),
                lineHeight = 16.sp,
            )
            if (usageMode == UsageMode.ONE_TIME) {
                Gap(8.dp)
                BodyText(
                    "Chế độ một lần không thay được ${config.profile.wakePhrase?.let { "“$it”" } ?: config.profile.assistantName}. " +
                        "Muốn thay giọng nói, chuyển sang Đầy đủ trong Chế độ.",
                    11.sp,
                    TextFaint,
                    Modifier.padding(horizontal = 2.dp),
                    lineHeight = 16.sp,
                )
            }
        }
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
        ) {
            if (onboarding) {
                PrimaryCta(
                    text = "Xong · vào app",
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = R.drawable.lc_check,
                )
            } else {
                OutlineCta(
                    text = "Xong",
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
