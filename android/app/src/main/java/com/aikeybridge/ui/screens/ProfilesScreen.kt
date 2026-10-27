package com.aikeybridge.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikeybridge.R
import com.aikeybridge.data.ActiveConfig
import com.aikeybridge.data.DeviceInfo
import com.aikeybridge.model.OemProfile
import com.aikeybridge.ui.components.BodyText
import com.aikeybridge.ui.components.Gap
import com.aikeybridge.ui.components.MonoText
import com.aikeybridge.ui.components.OutlineCta
import com.aikeybridge.ui.components.Panel
import com.aikeybridge.ui.components.ScreenTitle
import com.aikeybridge.ui.components.SectionLabel
import com.aikeybridge.ui.components.StatusDot
import com.aikeybridge.ui.components.SubHeader
import com.aikeybridge.ui.components.TinyTag
import com.aikeybridge.ui.components.hairlineBottom
import com.aikeybridge.ui.theme.Accent
import com.aikeybridge.ui.theme.Hairline
import com.aikeybridge.ui.theme.Muted
import com.aikeybridge.ui.theme.TextFaint
import com.aikeybridge.ui.theme.TextLabel
import com.aikeybridge.ui.theme.TextMain
import com.aikeybridge.ui.theme.Warn

@Composable
fun ProfilesScreen(
    config: ActiveConfig,
    device: DeviceInfo,
    profiles: List<OemProfile>,
    onBack: () -> Unit,
    onSelect: (String?) -> Unit,
    onOpenTriggers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detected = profiles.firstOrNull { it.id == config.detectedProfileId }
    Column(modifier.fillMaxSize()) {
        SubHeader(onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 16.dp),
        ) {
            ScreenTitle("Profile ROM", size = 26.sp)
            Gap(10.dp)
            BodyText(
                "Máy: ${device.displayName}. " +
                    (detected?.let { "Tự nhận diện ra ${it.displayName}." } ?: "Chưa có profile khớp hãng này."),
                13.5.sp,
                Muted,
                lineHeight = 20.sp,
            )

            SectionLabel("Chọn hãng", Modifier.padding(top = 10.dp))
            Panel {
                ProfileRow(
                    name = "Tự nhận diện",
                    subtitle = "→ ${detected?.displayName ?: "chưa có · chỉ dùng trigger tự dò"}",
                    selected = !config.manualProfile,
                    tag = null,
                    onClick = { onSelect(null) },
                )
                profiles.forEach { profile ->
                    ProfileRow(
                        name = profile.displayName,
                        subtitle = "${profile.assistantName} · ${profile.os} · ${profile.triggers.size} trigger",
                        selected = config.manualProfile && config.profile.id == profile.id,
                        tag = if (profile.verified) "đã kiểm chứng" to Accent else "beta" to Warn,
                        matched = profile.id == config.detectedProfileId,
                        onClick = { onSelect(profile.id) },
                    )
                }
                ProfileRow(
                    name = "Hãng khác",
                    subtitle = "chỉ dùng trigger tự dò",
                    selected = config.manualProfile && config.profile.isGeneric,
                    tag = null,
                    divider = false,
                    onClick = { onSelect(OemProfile.GENERIC_ID) },
                )
            }
            Gap(12.dp)
            BodyText(
                "Profile beta lấy tên gói và tên cửa sổ trợ lý từ thông tin công khai, chưa thử trên máy thật. " +
                    "Nếu bấm nút AI mà app không phản ứng, dùng Dò trợ lý để lưu đúng cửa sổ của máy bạn.",
                11.sp,
                TextFaint,
                Modifier.padding(horizontal = 2.dp),
                lineHeight = 16.sp,
            )
        }
        OutlineCta(
            text = "Dò cửa sổ trợ lý",
            onClick = onOpenTriggers,
            icon = R.drawable.lc_scan_search,
            iconTint = Accent,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
        )
    }
}

@Composable
internal fun ProfileRow(
    name: String,
    subtitle: String,
    selected: Boolean,
    tag: Pair<String, androidx.compose.ui.graphics.Color>?,
    onClick: () -> Unit,
    matched: Boolean = false,
    divider: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .hairlineBottom(divider)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                BodyText(name, 14.sp, if (selected) TextMain else TextLabel, weight = FontWeight.Medium, maxLines = 1)
                if (tag != null) TinyTag(tag.first, tag.second)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (matched) StatusDot(Accent, 5.dp)
                MonoText(if (matched) "khớp máy này · $subtitle" else subtitle, 9.sp, TextFaint)
            }
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
}
