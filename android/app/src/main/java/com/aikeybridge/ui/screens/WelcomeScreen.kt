package com.aikeybridge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikeybridge.R
import com.aikeybridge.data.ActiveConfig
import com.aikeybridge.data.DeviceInfo
import com.aikeybridge.ui.components.BodyText
import com.aikeybridge.ui.components.Eyebrow
import com.aikeybridge.ui.components.Gap
import com.aikeybridge.ui.components.LucideIcon
import com.aikeybridge.ui.components.MonoText
import com.aikeybridge.ui.components.Panel
import com.aikeybridge.ui.components.PrimaryCta
import com.aikeybridge.ui.components.hairlineBottom
import com.aikeybridge.ui.theme.Accent
import com.aikeybridge.ui.theme.DotOff
import com.aikeybridge.ui.theme.Ink
import com.aikeybridge.ui.theme.Muted
import com.aikeybridge.ui.theme.TextBody
import com.aikeybridge.ui.theme.TextDim
import com.aikeybridge.ui.theme.TextGhost
import com.aikeybridge.ui.theme.TextLabel
import com.aikeybridge.ui.theme.TextMain
import com.aikeybridge.ui.theme.Warn

@Composable
fun WelcomeScreen(
    device: DeviceInfo,
    config: ActiveConfig,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = config.profile
    val target = config.target
    val headline = profile.wakePhrase?.let { "Hô “$it”." } ?: "Gọi ${profile.assistantName}."

    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 26.dp, end = 26.dp, top = 42.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        LucideIcon(R.drawable.lc_audio_lines, Ink, 16.dp)
                    }
                    Box(Modifier.width(10.dp))
                    MonoText("AI KEY BRIDGE", 10.5.sp, TextLabel, letterSpacing = 2.sp)
                }
                Gap(34.dp)
                BodyText(headline, 42.sp, TextMain, weight = FontWeight.Bold, letterSpacing = (-1.6).sp, lineHeight = 46.sp)
                BodyText(
                    "${target.displayName} trả lời.",
                    42.sp,
                    Accent,
                    weight = FontWeight.Bold,
                    letterSpacing = (-1.6).sp,
                    lineHeight = 46.sp,
                )
                Gap(14.dp)
                BodyText(
                    "Thay ${profile.assistantName} của ROM bằng ${target.displayName}" +
                        (if (target.voiceUi != null) ", tự bấm Micro." else ".") +
                        " Không root, không cần máy tính.",
                    14.5.sp,
                    Muted,
                    lineHeight = 21.sp,
                )
            }

            Column {
                Gap(28.dp)
                Eyebrow("Luồng chạy")
                Gap(4.dp)
                val steps = buildList {
                    add("Bắt cửa sổ ${profile.assistantName}" to "50 ms")
                    add("Đóng panel, nhả audio" to "${config.dismissDelayMs} ms")
                    add("Mở ${target.displayName}" to "400 ms")
                    if (target.voiceUi != null) add("Tự bật Micro" to "250 ms")
                }
                steps.forEachIndexed { index, (text, time) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .hairlineBottom()
                            .padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MonoText("%02d".format(index + 1), 10.sp, TextGhost)
                        BodyText(text, 13.sp, TextBody, Modifier.weight(1f), maxLines = 1)
                        MonoText(time, 9.5.sp, TextDim)
                    }
                }

                Gap(22.dp)
                Panel {
                    DetectRow(R.drawable.lc_smartphone, "Thiết bị", device.displayName, TextDim)
                    DetectRow(
                        R.drawable.lc_package,
                        "ROM",
                        listOfNotNull(
                            device.romVersion ?: profile.os.takeUnless { profile.isGeneric },
                            "Android ${device.androidRelease}",
                        ).joinToString(" · "),
                        TextDim,
                    )
                    DetectRow(
                        R.drawable.lc_badge_check,
                        "Profile",
                        when {
                            profile.isGeneric -> "chưa có · tự dò được"
                            profile.verified -> "${profile.oem} · có sẵn"
                            else -> "${profile.oem} · beta"
                        },
                        if (profile.isGeneric || !profile.verified) Warn else Accent,
                        divider = false,
                    )
                }

                Gap(24.dp)
                PrimaryCta(
                    text = "Bắt đầu · 4 bước",
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = R.drawable.lc_arrow_right,
                )
                StepDots(current = 0, Modifier.padding(top = 16.dp, bottom = 6.dp), total = 4)
            }
        }
    }
}

@Composable
private fun DetectRow(icon: Int, key: String, value: String, iconTint: Color, divider: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .hairlineBottom(divider)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucideIcon(icon, iconTint, 15.dp)
        BodyText(key, 13.sp, TextLabel, Modifier.weight(1f))
        MonoText(value, 11.5.sp, TextMain)
    }
}

@Composable
fun StepDots(current: Int, modifier: Modifier = Modifier, total: Int = 3) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        repeat(total) { index ->
            Box(
                Modifier
                    .size(width = if (index == current) 18.dp else 6.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(if (index == current) Accent else DotOff),
            )
        }
    }
}
