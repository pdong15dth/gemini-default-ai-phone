package com.aikeybridge.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikeybridge.R
import com.aikeybridge.data.ActiveConfig
import com.aikeybridge.data.AppSettings
import com.aikeybridge.data.PermissionSnapshot
import com.aikeybridge.data.UsageMode
import com.aikeybridge.diagnostics.AttemptSummary
import com.aikeybridge.service.RedirectState
import com.aikeybridge.ui.Format
import com.aikeybridge.ui.Intents
import com.aikeybridge.ui.ScriptStatus
import com.aikeybridge.ui.components.PrimaryCta
import com.aikeybridge.ui.components.StatusDot
import com.aikeybridge.ui.components.BodyText
import com.aikeybridge.ui.components.CircleButton
import com.aikeybridge.ui.components.Gap
import com.aikeybridge.ui.components.LucideIcon
import com.aikeybridge.ui.components.MonoText
import com.aikeybridge.ui.components.OutlineCta
import com.aikeybridge.ui.components.SectionLabel
import com.aikeybridge.ui.components.TabHeader
import com.aikeybridge.ui.components.ToggleRow
import com.aikeybridge.ui.theme.Accent
import com.aikeybridge.ui.theme.Danger
import com.aikeybridge.ui.theme.Hairline
import com.aikeybridge.ui.theme.Ink
import com.aikeybridge.ui.theme.RingBase
import com.aikeybridge.ui.theme.Surface
import com.aikeybridge.ui.theme.TextFaint
import com.aikeybridge.ui.theme.TextGhost
import com.aikeybridge.ui.theme.TextLabel
import com.aikeybridge.ui.theme.TextMain
import com.aikeybridge.ui.theme.Warn

private enum class Health { OFF, READY, NEEDS_PERMISSION, NEEDS_SETUP, ROM_CHANGED, NO_TRIGGER, SCRIPT_STOPPED }

@Composable
fun OverviewScreen(
    config: ActiveConfig,
    permissions: PermissionSnapshot,
    settings: AppSettings,
    summary: AttemptSummary,
    redirectState: RedirectState,
    romChanged: Boolean,
    now: Long,
    onOpenLogs: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenModes: () -> Unit,
    onOpenTriggers: () -> Unit,
    onOpenScript: () -> Unit,
    scriptStatus: ScriptStatus,
    scriptStartedThisBoot: Boolean,
    onCheckScript: () -> Unit,
    onStartScript: () -> Unit,
    onStopScript: () -> Unit,
    onRequestShizuku: () -> Unit = {},
    onConnectShizuku: () -> Unit = onRequestShizuku,
    onToggleRedirect: (Boolean) -> Unit,
    onToggleAutoMic: (Boolean) -> Unit,
    onToggleLockScreen: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val oneTime = settings.usageMode == UsageMode.ONE_TIME
    val script = !oneTime
    val scriptOn = scriptStatus.running ?: scriptStartedThisBoot
    if (config.shellWatch != null) {
        LaunchedEffect(permissions.shizukuGranted) { onCheckScript() }
    }
    val health = when {
        script -> if (scriptOn) Health.READY else Health.SCRIPT_STOPPED
        oneTime && !permissions.targetInstalled -> Health.NEEDS_PERMISSION
        else -> if (romChanged) Health.ROM_CHANGED else Health.READY
    }
    val (stateText, stateColor) = when (health) {
        Health.OFF -> "Đang tắt" to TextFaint
        Health.READY -> "Sẵn sàng" to Accent
        Health.NEEDS_PERMISSION -> "Thiếu quyền" to Danger
        Health.NEEDS_SETUP -> "Cần kích hoạt" to Warn
        Health.ROM_CHANGED -> "ROM đã đổi" to Warn
        Health.NO_TRIGGER -> "Chưa có trigger" to Warn
        Health.SCRIPT_STOPPED -> "Script đã dừng" to Warn
    }
    val fix: (() -> Unit)? = when (health) {
        Health.OFF -> { { onToggleRedirect(true) } }
        Health.NEEDS_PERMISSION -> onOpenPermissions
        Health.NEEDS_SETUP, Health.ROM_CHANGED -> onOpenSetup
        Health.NO_TRIGGER -> onOpenTriggers
        Health.SCRIPT_STOPPED -> onOpenScript
        Health.READY -> null
    }

    Column(modifier.fillMaxSize()) {
        TabHeader("AI Key Bridge", "Tổng quan") {
            CircleButton(R.drawable.lc_history, "Nhật ký", onOpenLogs)
            CircleButton(R.drawable.lc_shield_check, "Quyền", onOpenPermissions)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            StatusRing(
                stateText = stateText,
                stateColor = stateColor,
                flow = when {
                    oneTime -> "Lối tắt → ${config.target.displayName}"
                    script -> "Script → ${config.target.displayName}"
                    else -> config.flowLabel
                },
                busy = redirectState !in setOf(RedirectState.IDLE, RedirectState.DONE, RedirectState.FAILED),
                actionHint = when (health) {
                    Health.READY -> null
                    Health.OFF -> "chạm để bật"
                    else -> "chạm để sửa"
                },
                onClick = fix,
            )
            if (oneTime && !scriptOn) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Warn.copy(alpha = 0.08f))
                        .clickable(role = Role.Button, onClick = onOpenModes)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BodyText(
                        "${config.profile.wakePhrase?.let { "“$it”" } ?: config.profile.assistantName} vẫn mở ${config.profile.assistantName} " +
                            "ở chế độ một lần. Chuyển sang Script nền để thay bằng ${config.target.displayName}.",
                        12.sp,
                        Warn,
                        Modifier.weight(1f),
                        lineHeight = 17.sp,
                    )
                    LucideIcon(R.drawable.lc_chevron_right, Warn, 14.dp)
                }
            }
            MonoText(
                summary.last?.let { "lần cuối ${Format.relative(it.at, now)} · ${summary.today} lần hôm nay" }
                    ?: if (oneTime) {
                        "chưa có lượt nào · dùng lối tắt Gọi AI để thử"
                    } else {
                        "chưa có lượt nào · gọi ${config.profile.assistantName} để thử"
                    },
                9.5.sp,
                TextFaint,
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Stat(summary.rate?.let { "$it%" } ?: "—", "thành công", Modifier.weight(1f))
                StatRule()
                Stat(Format.seconds(summary.avgMs), "trung bình", Modifier.weight(1f).padding(start = 16.dp))
                StatRule()
                Stat(
                    summary.failedToday.toString(),
                    "lỗi hôm nay",
                    Modifier.weight(1f).padding(start = 16.dp),
                    valueColor = if (summary.failedToday > 0) Danger else TextMain,
                )
            }

            if (config.shellWatch != null) {
                ScriptCard(
                    status = scriptStatus,
                    startedThisBoot = scriptStartedThisBoot,
                    permissions = permissions,
                    activeMode = script,
                    onOpen = onOpenScript,
                    onStart = onStartScript,
                    onStop = onStopScript,
                    onRequestShizuku = onRequestShizuku,
                    onConnectShizuku = onConnectShizuku,
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 18.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .border(
                        1.dp,
                        if (permissions.ready) Hairline else Warn.copy(alpha = 0.45f),
                        RoundedCornerShape(16.dp),
                    )
                    .clickable(role = Role.Button, onClick = onOpenPermissions)
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(if (permissions.ready) Accent else Warn, 7.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BodyText(
                        "Quyền & Hướng dẫn bằng ảnh",
                        13.5.sp,
                        TextMain,
                        weight = FontWeight.Medium,
                    )
                    MonoText(
                        if (permissions.ready) {
                            "đạt ${permissions.coreGrantedCount}/${permissions.coreTotalCount} mục cốt lõi · chạm để mở cài đặt hoặc xem ảnh"
                        } else {
                            "đạt ${permissions.coreGrantedCount}/${permissions.coreTotalCount} mục · chạm để mở thẳng cài đặt & xem ảnh hướng dẫn"
                        },
                        9.sp,
                        if (permissions.ready) TextFaint else Warn,
                        maxLines = 2,
                    )
                }
                MonoText("ảnh HD", 9.5.sp, Accent)
                LucideIcon(R.drawable.lc_chevron_right, TextGhost, 14.dp)
            }

            SectionLabel(
                "Chế độ",
                Modifier.padding(horizontal = 24.dp),
                action = "Quản lý ›",
                onAction = onOpenModes,
            )
            ToggleRow(
                title = "Chuyển hướng",
                subtitle = when {
                    oneTime -> "chế độ một lần · bật Đầy đủ trong Chế độ"
                    script -> "do script nền đảm nhận"
                    else -> config.flowLabel
                },
                checked = settings.redirectEnabled && !oneTime && !script,
                onCheckedChange = onToggleRedirect,
                enabled = !oneTime && !script,
                icon = R.drawable.lc_radio,
                horizontalPadding = 24.dp,
            )
            ToggleRow(
                title = "Tự bật Micro",
                subtitle = if (config.target.voiceUi != null) {
                    "chạm Mic sau khi ${config.target.displayName} mở"
                } else {
                    "${config.target.displayName} không hỗ trợ"
                },
                checked = settings.autoMic && config.target.voiceUi != null,
                onCheckedChange = onToggleAutoMic,
                enabled = config.target.voiceUi != null,
                icon = R.drawable.lc_mic,
                horizontalPadding = 24.dp,
            )
            ToggleRow(
                title = "Màn hình khóa",
                subtitle = "TriggerActivity · cần 3 quyền",
                checked = settings.lockScreen,
                onCheckedChange = onToggleLockScreen,
                icon = R.drawable.lc_lock_keyhole,
                horizontalPadding = 24.dp,
            )
            Gap(24.dp)
        }
        OutlineCta(
            text = if (romChanged) "ROM đã đổi · chạy lại kích hoạt" else "Kiểm tra lại cấu hình",
            onClick = onOpenSetup,
            icon = R.drawable.lc_refresh_cw,
            iconTint = if (romChanged) Warn else TextLabel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 14.dp),
        )
    }
}

@Composable
private fun StatusRing(
    stateText: String,
    stateColor: Color,
    flow: String,
    busy: Boolean,
    actionHint: String?,
    onClick: (() -> Unit)?,
) {
    val angle = if (busy) {
        val spin = rememberInfiniteTransition(label = "orbit")
        spin.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
            label = "angle",
        ).value
    } else {
        0f
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(262.dp)
                .clip(CircleShape)
                .then(
                    if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier,
                )
                .semantics { contentDescription = "Trạng thái: $stateText" },
            contentAlignment = Alignment.Center,
        ) {
            val ringBase = RingBase
            val ink = Ink
            Canvas(Modifier.size(262.dp)) {
                drawCircle(
                    Brush.radialGradient(
                        0f to stateColor.copy(alpha = 0.09f),
                        0.72f to stateColor.copy(alpha = 0f),
                        center = center,
                        radius = size.minDimension / 2,
                    ),
                )
                val ringRadius = 116.dp.toPx()
                drawCircle(ringBase, radius = ringRadius, style = Stroke(width = 1.5.dp.toPx()))
                rotate(angle) {
                    val dot = Offset(center.x, center.y - ringRadius)
                    drawCircle(ink, radius = 13.dp.toPx(), center = dot)
                    drawCircle(stateColor, radius = 5.dp.toPx(), center = dot)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                MonoText("TRẠNG THÁI", 9.sp, TextGhost, letterSpacing = 1.8.sp)
                BodyText(stateText, 28.sp, TextMain, weight = FontWeight.SemiBold, letterSpacing = (-0.9).sp, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val parts = flow.split(" → ")
                    MonoText(parts.first(), 10.sp, TextLabel)
                    LucideIcon(R.drawable.lc_arrow_right, TextGhost, 10.dp)
                    MonoText(parts.last(), 10.sp, TextLabel)
                }
                if (actionHint != null) MonoText(actionHint, 9.sp, stateColor)
            }
        }
    }
}

@Composable
private fun ScriptCard(
    status: ScriptStatus,
    startedThisBoot: Boolean,
    permissions: PermissionSnapshot,
    activeMode: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRequestShizuku: () -> Unit,
    onConnectShizuku: () -> Unit,
) {
    val context = LocalContext.current
    val state = scriptState(status, startedThisBoot)
    val runningOrBoot = status.running == true || startedThisBoot
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 18.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .border(1.dp, if (runningOrBoot) Accent.copy(alpha = 0.35f) else Hairline, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(state.color, 8.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BodyText("Script nền (Bank-Safe)", 14.sp, TextMain, weight = FontWeight.Medium)
                    MonoText(state.label, 9.5.sp, state.color)
                }
                MonoText(
                    when {
                        activeMode -> state.detail
                        status.running == true -> "đang chạy ngầm trong RAM · ${state.detail}"
                        else -> "không cần Trợ năng · tương thích mọi app ngân hàng"
                    },
                    9.sp,
                    TextFaint,
                    maxLines = 2,
                )
            }
            LucideIcon(R.drawable.lc_chevron_right, TextGhost, 14.dp)
        }
        if (permissions.shizukuGranted) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryCta(
                    text = if (status.running == true) "Kích hoạt & Bật lại" else "Kích hoạt & Bật script",
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                    leadingIcon = R.drawable.lc_terminal,
                    loading = status.busy,
                    height = 42.dp,
                    radius = 12.dp,
                )
                if (status.running == true) {
                    OutlineCta(
                        text = "Tắt",
                        onClick = onStop,
                        modifier = Modifier.weight(0.5f),
                        height = 42.dp,
                        radius = 12.dp,
                    )
                }
            }
        } else if (!runningOrBoot) {
            val canAutoConnectWireless = permissions.adbWifiEnabled && permissions.wirelessAdbPaired
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryCta(
                    text = when {
                        permissions.shizukuBinderAlive -> "Cấp quyền Shizuku"
                        permissions.shizukuRunning || canAutoConnectWireless -> "Kết nối Engine"
                        else -> "Ghép nối 6 số"
                    },
                    onClick = {
                        when {
                            permissions.shizukuBinderAlive -> onConnectShizuku()
                            permissions.shizukuRunning || canAutoConnectWireless -> onRequestShizuku()
                            else -> onOpen()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    leadingIcon = R.drawable.lc_terminal,
                    height = 42.dp,
                    radius = 12.dp,
                )
                OutlineCta(
                    text = "Shizuku",
                    onClick = onConnectShizuku,
                    modifier = Modifier.weight(0.65f),
                    height = 42.dp,
                    radius = 12.dp,
                )
            }
        }
        if (runningOrBoot) {
            MonoText(
                "Script chạy riêng, không phụ thuộc app. Tắt Tuỳ chọn nhà phát triển & Gỡ lỗi USB để dùng app ngân hàng vẫn chạy.",
                9.sp,
                TextFaint,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color = TextMain) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        BodyText(value, 20.sp, valueColor, weight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, maxLines = 1)
        MonoText(label, 9.sp, TextFaint)
    }
}

@Composable
private fun StatRule() {
    Box(
        Modifier
            .width(1.dp)
            .height(32.dp)
            .background(Hairline),
    )
}
