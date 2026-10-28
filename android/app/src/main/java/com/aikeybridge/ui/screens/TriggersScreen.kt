package com.aikeybridge.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikeybridge.R
import com.aikeybridge.data.ActiveConfig
import com.aikeybridge.data.CustomTrigger
import com.aikeybridge.service.Sniffer
import com.aikeybridge.ui.AppViewModel
import com.aikeybridge.ui.components.BodyText
import com.aikeybridge.ui.components.CircleButton
import com.aikeybridge.ui.components.Gap
import com.aikeybridge.ui.components.LucideIcon
import com.aikeybridge.ui.components.MonoText
import com.aikeybridge.ui.components.Panel
import com.aikeybridge.ui.components.PrimaryCta
import com.aikeybridge.ui.components.ScreenTitle
import com.aikeybridge.ui.components.SectionLabel
import com.aikeybridge.ui.components.SubHeader
import com.aikeybridge.ui.components.TinyTag
import com.aikeybridge.ui.components.hairlineBottom
import com.aikeybridge.ui.theme.Accent
import com.aikeybridge.ui.theme.Muted
import com.aikeybridge.ui.theme.TextBody
import com.aikeybridge.ui.theme.TextDim
import com.aikeybridge.ui.theme.TextFaint
import com.aikeybridge.ui.theme.TextLabel
import com.aikeybridge.ui.theme.TextMain
import com.aikeybridge.ui.theme.Track
import kotlinx.coroutines.delay

@Composable
fun TriggersScreen(
    config: ActiveConfig,
    customTriggers: List<CustomTrigger>,
    sniffer: Sniffer.State,
    accessibilityOn: Boolean,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSave: (Sniffer.Candidate) -> Unit,
    onRemove: (CustomTrigger) -> Unit,
    modifier: Modifier = Modifier,
) {
    var remainingMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sniffer.active, sniffer.endsAt) {
        while (sniffer.active) {
            remainingMs = (sniffer.endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            if (remainingMs == 0L) break
            delay(100)
        }
        if (!sniffer.active) remainingMs = 0
    }
    val saved = customTriggers.toSet()
    val candidates = sniffer.candidates.sortedWith(
        compareByDescending<Sniffer.Candidate> { it.looksLikeAssistant }.thenByDescending { it.lastAt },
    )

    Column(modifier.fillMaxSize()) {
        SubHeader(onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 16.dp),
        ) {
            ScreenTitle("Dò trợ lý", size = 26.sp)
            Gap(10.dp)
            BodyText(
                "Bấm Dò, rồi gọi trợ lý gốc hoặc bấm nút AI trong ${AppViewModel.SNIFF_WINDOW_MS / 1000} giây. " +
                    "Chọn cửa sổ vừa hiện để lưu làm trigger. App chỉ ghi tên gói và tên lớp cửa sổ.",
                13.5.sp,
                Muted,
                lineHeight = 20.sp,
            )

            SectionLabel("Đang dùng · ${config.profile.displayName}", Modifier.padding(top = 10.dp))
            Panel {
                val builtIn = config.triggers.filterNot { it.custom }
                if (builtIn.isEmpty() && customTriggers.isEmpty()) {
                    BodyText("Chưa có trigger nào. Dò để thêm.", 12.sp, TextFaint, Modifier.padding(vertical = 14.dp))
                }
                builtIn.forEachIndexed { index, trigger ->
                    TriggerRow(
                        title = trigger.label,
                        detail = "${trigger.packageName} / ${trigger.classRegex.pattern}",
                        divider = index != builtIn.lastIndex || customTriggers.isNotEmpty(),
                    )
                }
                customTriggers.forEachIndexed { index, trigger ->
                    TriggerRow(
                        title = trigger.className.substringAfterLast('.'),
                        detail = "${trigger.packageName} · tự dò",
                        divider = index != customTriggers.lastIndex,
                        onRemove = { onRemove(trigger) },
                    )
                }
            }

            if (sniffer.active || candidates.isNotEmpty()) {
                SectionLabel(
                    if (sniffer.active) "Đang dò · ${(remainingMs + 999) / 1000}s" else "Kết quả dò · ${candidates.size} cửa sổ",
                    Modifier.padding(top = 10.dp),
                )
                if (sniffer.active) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Track),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(remainingMs / AppViewModel.SNIFF_WINDOW_MS.toFloat())
                                .background(Accent),
                        )
                    }
                }
                Panel {
                    if (candidates.isEmpty()) {
                        BodyText(
                            "Chưa thấy cửa sổ nào. Gọi trợ lý gốc ngay bây giờ…",
                            12.sp,
                            TextFaint,
                            Modifier.padding(vertical = 14.dp),
                        )
                    }
                    candidates.forEachIndexed { index, candidate ->
                        val isSaved = CustomTrigger(candidate.packageName, candidate.className) in saved
                        CandidateRow(
                            candidate = candidate,
                            saved = isSaved,
                            divider = index != candidates.lastIndex,
                            onClick = { if (!isSaved) onSave(candidate) },
                        )
                    }
                }
            }
        }
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
        ) {
            if (!accessibilityOn) {
                MonoText(
                    "Cần cấp quyền Shizuku để dò cửa sổ trợ lý.",
                    9.5.sp,
                    TextDim,
                    Modifier.padding(start = 2.dp, bottom = 10.dp),
                )
            }
            PrimaryCta(
                text = if (sniffer.active) "Dừng dò" else "Dò trong ${AppViewModel.SNIFF_WINDOW_MS / 1000} giây",
                onClick = if (sniffer.active) onStop else onStart,
                leadingIcon = if (sniffer.active) R.drawable.lc_x else R.drawable.lc_scan_search,
                enabled = accessibilityOn || sniffer.active,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TriggerRow(title: String, detail: String, divider: Boolean, onRemove: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .hairlineBottom(divider)
            .padding(vertical = if (onRemove == null) 13.dp else 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BodyText(title, 13.5.sp, TextMain, maxLines = 1)
            MonoText(detail, 9.sp, TextFaint, maxLines = 2)
        }
        if (onRemove != null) {
            CircleButton(R.drawable.lc_trash_2, "Xoá trigger", onRemove, tint = TextDim, iconSize = 14.dp)
        }
    }
}

@Composable
private fun CandidateRow(candidate: Sniffer.Candidate, saved: Boolean, divider: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .hairlineBottom(divider)
            .clickable(enabled = !saved, role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                BodyText(
                    candidate.className.substringAfterLast('.'),
                    13.5.sp,
                    if (candidate.looksLikeAssistant) TextMain else TextBody,
                    Modifier.weight(1f, fill = false),
                    maxLines = 1,
                )
                if (candidate.looksLikeAssistant) TinyTag("giống trợ lý", Accent)
            }
            MonoText("${candidate.packageName} · ${candidate.hits} lần", 9.sp, TextFaint)
        }
        if (saved) {
            LucideIcon(R.drawable.lc_check, Accent, 14.dp)
        } else {
            MonoText("lưu", 9.5.sp, TextLabel)
        }
    }
}
