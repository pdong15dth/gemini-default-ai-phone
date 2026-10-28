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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikeybridge.R
import com.aikeybridge.data.ActiveConfig
import com.aikeybridge.data.UsageMode
import com.aikeybridge.ui.components.BodyText
import com.aikeybridge.ui.components.Gap
import com.aikeybridge.ui.components.LucideIcon
import com.aikeybridge.ui.components.MonoText
import com.aikeybridge.ui.components.PrimaryCta
import com.aikeybridge.ui.components.ScreenTitle
import com.aikeybridge.ui.components.StatusDot
import com.aikeybridge.ui.components.SubHeader
import com.aikeybridge.ui.theme.Accent
import com.aikeybridge.ui.theme.Hairline
import com.aikeybridge.ui.theme.Muted
import com.aikeybridge.ui.theme.Surface
import com.aikeybridge.ui.theme.TextBody
import com.aikeybridge.ui.theme.TextDim
import com.aikeybridge.ui.theme.TextFaint
import com.aikeybridge.ui.theme.TextLabel
import com.aikeybridge.ui.theme.TextMain

/** Bước 2 onboarding: chọn script nền, chạy đầy đủ (Trợ năng) hay chỉ cấu hình một lần. */
@Composable
fun UsageScreen(
    config: ActiveConfig,
    current: UsageMode,
    chosen: Boolean,
    onBack: () -> Unit,
    onConfirm: (UsageMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scriptAvailable = config.shellWatch != null && config.target.shellKeyevent != null
    // Bản thuần Script nền: mặc định luôn chọn Script nền.
    var selected by rememberSaveable {
        mutableStateOf(if (current == UsageMode.ONE_TIME && chosen) UsageMode.ONE_TIME else UsageMode.SCRIPT)
    }
    val assistant = config.profile.assistantName
    val target = config.target.displayName
    val wake = config.profile.wakePhrase?.let { "“$it”" } ?: assistant

    Column(modifier.fillMaxSize()) {
        SubHeader(onBack = onBack) {
            MonoText("BƯỚC 2 / 4", 10.sp, TextDim, letterSpacing = 1.2.sp)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 26.dp, end = 26.dp, top = 14.dp, bottom = 16.dp),
        ) {
            ScreenTitle("Cách dùng")
            Gap(10.dp)
            BodyText(
                "Các lệnh cấu hình chỉ chạy một lần. Khác nhau ở chỗ có cần app chạy nền để thay $wake hay không.",
                13.5.sp,
                Muted,
                lineHeight = 20.sp,
            )
            Gap(22.dp)
            ModeCard(
                title = "Script nền (Bank-Safe)",
                tag = "khuyên dùng",
                selected = selected == UsageMode.SCRIPT,
                points = listOf(
                    "$wake → $target, Micro bật sẵn",
                    "Không dùng Trợ năng · Tương thích 100% mọi app ngân hàng",
                    "Bắt buộc tắt Tuỳ chọn nhà phát triển & Gỡ lỗi USB để dùng app ngân hàng — vẫn chạy",
                ),
                cost = "Cần Shizuku một lần · sau khởi động máy chạm thông báo để cho phép đọc log",
                onClick = { selected = UsageMode.SCRIPT },
            )
            Gap(12.dp)
            ModeCard(
                title = "Cấu hình một lần",
                tag = null,
                selected = selected == UsageMode.ONE_TIME,
                points = listOf(
                    "Gán phím tắt / chạm mặt lưng → $target",
                    "Không cần Trợ năng, không chạy nền",
                    "$wake vẫn là $assistant · bạn tự bấm Micro",
                ),
                cost = "Chỉ cần Shizuku hoặc ADB một lần (tuỳ chọn)",
                onClick = { selected = UsageMode.ONE_TIME },
            )
            Gap(12.dp)
            BodyText(
                "Đổi lại được bất cứ lúc nào trong Chế độ.",
                11.sp,
                TextFaint,
                Modifier.padding(horizontal = 2.dp),
            )
        }
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 26.dp, end = 26.dp, bottom = 12.dp),
        ) {
            PrimaryCta(
                text = "Tiếp tục",
                onClick = { onConfirm(selected) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = R.drawable.lc_arrow_right,
            )
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    tag: String?,
    selected: Boolean,
    points: List<String>,
    cost: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Accent else Hairline, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BodyText(title, 17.sp, TextMain, Modifier.weight(1f), weight = FontWeight.SemiBold)
            if (tag != null) {
                MonoText(
                    tag.uppercase(),
                    7.5.sp,
                    Accent,
                    Modifier
                        .padding(end = 10.dp)
                        .clip(CircleShape)
                        .background(Accent.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    letterSpacing = 0.6.sp,
                )
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
        points.forEach { point ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
                LucideIcon(R.drawable.lc_check, if (selected) Accent else TextLabel, 13.dp, Modifier.padding(top = 3.dp))
                BodyText(point, 13.sp, TextBody, lineHeight = 19.sp)
            }
        }
        MonoText(cost, 9.5.sp, TextFaint, maxLines = 2)
    }
}
