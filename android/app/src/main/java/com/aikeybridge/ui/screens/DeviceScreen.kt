package com.aikeybridge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikeybridge.R
import com.aikeybridge.data.ActiveConfig
import com.aikeybridge.data.DeviceInfo
import com.aikeybridge.model.OemProfile
import com.aikeybridge.ui.components.BodyText
import com.aikeybridge.ui.components.Gap
import com.aikeybridge.ui.components.LucideIcon
import com.aikeybridge.ui.components.MonoText
import com.aikeybridge.ui.components.Panel
import com.aikeybridge.ui.components.PrimaryCta
import com.aikeybridge.ui.components.ScreenTitle
import com.aikeybridge.ui.components.SectionLabel
import com.aikeybridge.ui.components.StatusDot
import com.aikeybridge.ui.components.SubHeader
import com.aikeybridge.ui.theme.Accent
import com.aikeybridge.ui.theme.Hairline
import com.aikeybridge.ui.theme.Muted
import com.aikeybridge.ui.theme.Surface
import com.aikeybridge.ui.theme.Surface2
import com.aikeybridge.ui.theme.TextDim
import com.aikeybridge.ui.theme.TextFaint
import com.aikeybridge.ui.theme.TextIcon
import com.aikeybridge.ui.theme.TextLabel
import com.aikeybridge.ui.theme.TextMain
import com.aikeybridge.ui.theme.Warn

/** Bước 1 onboarding: nhận diện máy và cho người dùng xác nhận/chọn lại hãng. */
@Composable
fun DeviceScreen(
    device: DeviceInfo,
    config: ActiveConfig,
    profiles: List<OemProfile>,
    onBack: () -> Unit,
    onConfirm: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detected = profiles.firstOrNull { it.id == config.detectedProfileId }
    var selected by rememberSaveable {
        mutableStateOf(if (config.manualProfile) config.profile.id else detected?.id ?: OemProfile.GENERIC_ID)
    }
    val chosen = profiles.firstOrNull { it.id == selected }

    Column(modifier.fillMaxSize()) {
        SubHeader(onBack = onBack) {
            MonoText("BƯỚC 1 / 4", 10.sp, TextDim, letterSpacing = 1.2.sp)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 26.dp, end = 26.dp, top = 14.dp, bottom = 16.dp),
        ) {
            ScreenTitle("Máy của bạn")
            Gap(10.dp)
            BodyText(
                "App tự nhận diện hãng và ROM để biết trợ lý gốc trông như thế nào. Sai thì chọn lại bên dưới.",
                13.5.sp,
                Muted,
                lineHeight = 20.sp,
            )
            Gap(22.dp)

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Surface)
                    .border(1.dp, Hairline, RoundedCornerShape(18.dp))
                    .padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface2)
                        .border(1.dp, Hairline, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    LucideIcon(R.drawable.lc_smartphone, TextIcon, 22.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BodyText(device.displayName, 19.sp, TextMain, weight = FontWeight.SemiBold, maxLines = 1)
                    MonoText(
                        listOfNotNull(
                            device.brand.lowercase(),
                            device.model.takeIf { device.marketName != null },
                            device.romVersion,
                            "Android ${device.androidRelease}",
                        ).joinToString(" · "),
                        9.5.sp,
                        TextFaint,
                        maxLines = 2,
                    )
                    Row(
                        Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(if (detected != null) Accent else Warn, 6.dp)
                        MonoText(
                            when {
                                detected == null -> "chưa có profile cho ${device.manufacturer.lowercase()}"
                                detected.verified -> "nhận diện: ${detected.displayName}"
                                else -> "nhận diện: ${detected.displayName} · beta"
                            },
                            9.5.sp,
                            if (detected != null) TextLabel else Warn,
                        )
                    }
                }
            }

            SectionLabel("Hãng điện thoại", Modifier.padding(top = 14.dp))
            Panel {
                profiles.forEach { profile ->
                    ProfileRow(
                        name = profile.displayName,
                        subtitle = "${profile.assistantName} · ${profile.os}",
                        selected = selected == profile.id,
                        tag = if (profile.verified) "đã kiểm chứng" to Accent else "beta" to Warn,
                        matched = profile.id == detected?.id,
                        onClick = { selected = profile.id },
                    )
                }
                ProfileRow(
                    name = "Hãng khác",
                    subtitle = "dò cửa sổ trợ lý sau khi cấp quyền",
                    selected = selected == OemProfile.GENERIC_ID,
                    tag = null,
                    divider = false,
                    onClick = { selected = OemProfile.GENERIC_ID },
                )
            }
            Gap(12.dp)
            BodyText(
                when {
                    chosen == null ->
                        "Không sao — sau bước Cấp quyền, vào Cài đặt → Trợ lý gốc & trigger, bấm Dò rồi gọi trợ lý của máy để app học cửa sổ đó."
                    !chosen.verified ->
                        "Profile ${chosen.displayName} đang ở mức beta, chưa thử trên máy thật. Nếu gọi ${chosen.assistantName} mà app không phản ứng, dùng Dò trợ lý."
                    else ->
                        "${chosen.displayName} đã được kiểm chứng trên máy thật với ${chosen.assistantName}."
                },
                11.sp,
                TextFaint,
                Modifier.padding(horizontal = 2.dp),
                lineHeight = 16.sp,
            )
        }

        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 26.dp, end = 26.dp, bottom = 12.dp),
        ) {
            val keepsAuto = selected == (detected?.id ?: OemProfile.GENERIC_ID)
            PrimaryCta(
                text = when {
                    !keepsAuto -> "Dùng lựa chọn này · Tiếp tục"
                    detected != null -> "Đúng máy này · Tiếp tục"
                    else -> "Tiếp tục"
                },
                onClick = { onConfirm(if (keepsAuto) null else selected) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = R.drawable.lc_arrow_right,
            )
        }
    }
}
