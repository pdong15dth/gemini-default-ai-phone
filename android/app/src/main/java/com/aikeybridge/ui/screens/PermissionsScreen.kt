package com.aikeybridge.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikeybridge.R
import com.aikeybridge.data.ActiveConfig
import com.aikeybridge.data.AppSettings
import com.aikeybridge.data.PermissionSnapshot
import com.aikeybridge.data.UsageMode
import com.aikeybridge.model.AssistantTarget
import com.aikeybridge.ui.Intents
import com.aikeybridge.ui.components.BodyText
import com.aikeybridge.ui.components.Gap
import com.aikeybridge.ui.components.IconTile
import com.aikeybridge.ui.components.LucideIcon
import com.aikeybridge.ui.components.MonoText
import com.aikeybridge.ui.components.Panel
import com.aikeybridge.ui.components.PrimaryCta
import com.aikeybridge.ui.components.ScreenTitle
import com.aikeybridge.ui.components.StatusDot
import com.aikeybridge.ui.components.SubHeader
import com.aikeybridge.ui.components.hairlineBottom
import com.aikeybridge.ui.theme.Accent
import com.aikeybridge.ui.theme.Danger
import com.aikeybridge.ui.theme.Hairline
import com.aikeybridge.ui.theme.Muted
import com.aikeybridge.ui.theme.TextDim
import com.aikeybridge.ui.theme.TextGhost
import com.aikeybridge.ui.theme.TextLabel
import com.aikeybridge.ui.theme.TextMain
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable

private enum class PermKey { ACCESSIBILITY, AUTOSTART, BATTERY, TARGET, SHIZUKU }

@Composable
fun PermissionsScreen(
    config: ActiveConfig,
    permissions: PermissionSnapshot,
    settings: AppSettings,
    onboarding: Boolean,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onToggleAutostart: (Boolean) -> Unit,
    onRequestShizuku: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val target = config.target

    fun open(key: PermKey) = when (key) {
        PermKey.ACCESSIBILITY -> Intents.openAccessibility(context)
        PermKey.AUTOSTART -> Intents.openAutostart(context)
        PermKey.BATTERY -> Intents.requestBatteryOptimization(context)
        PermKey.TARGET -> if (target.id == AssistantTarget.GEMINI) {
            Intents.openGemini(context)
        } else {
            Intents.openPlayStore(context, target.packages.first())
        }
        PermKey.SHIZUKU -> if (permissions.shizukuRunning && !permissions.shizukuGranted) {
            onRequestShizuku()
        } else {
            Intents.openShizuku(context)
        }
    }

    val missing = buildList {
        if (!permissions.accessibility && settings.usageMode == UsageMode.FULL) add(PermKey.ACCESSIBILITY)
        if (!settings.manualAutostart) add(PermKey.AUTOSTART)
        if (!permissions.targetInstalled) add(PermKey.TARGET)
    }

    Column(modifier.fillMaxSize()) {
        SubHeader(onBack = onBack) {
            if (onboarding) MonoText("BƯỚC 3 / 4", 10.sp, TextDim, letterSpacing = 1.2.sp)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 26.dp, end = 26.dp, top = 14.dp),
        ) {
            ScreenTitle("Cấp quyền")
            Gap(10.dp)
            BodyText(
                "Ba quyền bắt buộc, hai quyền khuyến nghị. Chạm từng dòng để mở đúng màn hình cài đặt.",
                13.5.sp,
                Muted,
                lineHeight = 20.sp,
            )
            Gap(22.dp)
            Panel {
                PermRow(
                    icon = R.drawable.lc_accessibility,
                    title = "Trợ năng",
                    subtitle = "bắt buộc",
                    granted = permissions.accessibility,
                    onClick = { open(PermKey.ACCESSIBILITY) },
                )
                PermRow(
                    icon = R.drawable.lc_rocket,
                    title = "Tự khởi chạy",
                    subtitle = "bắt buộc trên ${config.profile.os}",
                    granted = settings.manualAutostart,
                    grantedLabel = "đã xác nhận",
                    onClick = { open(PermKey.AUTOSTART) },
                    trailingAction = if (settings.manualAutostart) null else ("đã bật?" to { onToggleAutostart(true) }),
                    onLongClickGranted = { onToggleAutostart(false) },
                )
                PermRow(
                    icon = R.drawable.lc_battery_charging,
                    title = "Pin không hạn chế",
                    subtitle = "khuyến nghị",
                    granted = permissions.batteryUnrestricted,
                    onClick = { open(PermKey.BATTERY) },
                )
                PermRow(
                    icon = if (target.id == AssistantTarget.GEMINI) R.drawable.lc_sparkles else R.drawable.lc_app_window,
                    title = target.displayName,
                    subtitle = "bắt buộc · ứng dụng đích",
                    granted = permissions.targetInstalled,
                    grantedLabel = "đã cài",
                    onClick = { open(PermKey.TARGET) },
                )
                PermRow(
                    icon = R.drawable.lc_terminal,
                    title = "Shizuku",
                    subtitle = when {
                        permissions.shizukuGranted -> "chỉ để cấu hình · đã cấp"
                        permissions.shizukuRunning -> "đang chạy · chạm để cấp quyền"
                        permissions.shizukuInstalled -> "đã cài · chưa chạy"
                        else -> "chỉ để cấu hình · có thể dùng ADB"
                    },
                    granted = permissions.shizukuGranted,
                    grantedLabel = "đã cấp",
                    divider = false,
                    onClick = { open(PermKey.SHIZUKU) },
                )
            }
            Gap(14.dp)
            if (!settings.manualAutostart && config.profile.autostartHint.isNotBlank()) {
                MonoText(
                    "Tự khởi chạy: ${config.profile.autostartHint}",
                    9.5.sp,
                    TextDim,
                    maxLines = 4,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
                Gap(10.dp)
            }
            if (config.profile.oem == "vivo") {
                MonoText(
                    "OriginOS sẽ hỏi “AI Key Bridge muốn mở ${config.target.displayName}” ở lần đầu — chọn Luôn mở, nếu không mỗi lần chuyển đều bị chặn.",
                    9.5.sp,
                    TextDim,
                    maxLines = 4,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
                Gap(10.dp)
            }
            if (!permissions.accessibility) {
                MonoText(
                    "Nút Trợ năng bị mờ? Thông tin ứng dụng → ⋮ → Cho phép cài đặt bị hạn chế, rồi bật lại.",
                    9.5.sp,
                    TextDim,
                    maxLines = 3,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
            Gap(20.dp)
        }

        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 26.dp)
                .padding(bottom = 10.dp),
        ) {
            Row(
                Modifier.padding(start = 2.dp, end = 2.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                LucideIcon(R.drawable.lc_shield_alert, TextDim, 14.dp, Modifier.padding(top = 2.dp))
                BodyText(
                    "Không có Trợ năng, app không thấy được cửa sổ ${config.profile.assistantName} — không có đường dự phòng đáng tin cậy.",
                    11.5.sp,
                    TextDim,
                    lineHeight = 16.sp,
                )
            }
            PrimaryCta(
                text = when {
                    missing.isNotEmpty() -> "Cấp ${missing.size} quyền còn thiếu"
                    onboarding -> "Tiếp tục"
                    else -> "Xong"
                },
                onClick = { if (missing.isNotEmpty()) open(missing.first()) else onContinue() },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = if (missing.isEmpty()) R.drawable.lc_arrow_right else null,
            )
            if (onboarding) {
                BodyText(
                    "Để sau",
                    13.sp,
                    TextDim,
                    Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onSkip)
                        .padding(vertical = 13.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PermRow(
    icon: Int,
    title: String,
    subtitle: String,
    granted: Boolean,
    onClick: () -> Unit,
    divider: Boolean = true,
    grantedLabel: String = "đã bật",
    trailingAction: Pair<String, () -> Unit>? = null,
    onLongClickGranted: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .hairlineBottom(divider)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = if (granted) onLongClickGranted else null,
            )
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BodyText(title, 14.sp, TextMain, weight = FontWeight.Medium, maxLines = 1)
            MonoText(subtitle, 9.5.sp, TextDim)
        }
        when {
            granted -> Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(Accent)
                MonoText(grantedLabel, 9.5.sp, TextLabel)
            }
            trailingAction != null -> MonoText(
                trailingAction.first,
                9.5.sp,
                Accent,
                Modifier
                    .clip(CircleShape)
                    .border(1.dp, Hairline, CircleShape)
                    .clickable(role = Role.Button, onClick = trailingAction.second)
                    .padding(horizontal = 9.dp, vertical = 6.dp),
            )
            subtitle.startsWith("bắt buộc") -> Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(Danger)
                LucideIcon(R.drawable.lc_chevron_right, TextGhost, 16.dp)
            }
            else -> LucideIcon(R.drawable.lc_chevron_right, TextGhost, 16.dp)
        }
    }
}
