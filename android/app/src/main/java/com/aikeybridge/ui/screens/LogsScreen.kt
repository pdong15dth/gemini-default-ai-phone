package com.aikeybridge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikeybridge.R
import com.aikeybridge.diagnostics.Attempt
import com.aikeybridge.diagnostics.AttemptSummary
import com.aikeybridge.diagnostics.Diagnostics
import com.aikeybridge.ui.Format
import com.aikeybridge.ui.components.BodyText
import com.aikeybridge.ui.components.Chip
import com.aikeybridge.ui.components.MonoText
import com.aikeybridge.ui.components.OutlineCta
import com.aikeybridge.ui.components.SectionLabel
import com.aikeybridge.ui.components.TabHeader
import com.aikeybridge.ui.components.hairlineBottom
import com.aikeybridge.ui.theme.Accent
import com.aikeybridge.ui.theme.Bar
import com.aikeybridge.ui.theme.Danger
import com.aikeybridge.ui.theme.DangerTitle
import com.aikeybridge.ui.theme.TextFaint
import com.aikeybridge.ui.theme.TextGhost
import com.aikeybridge.ui.theme.TextIcon
import com.aikeybridge.ui.theme.TextLabel
import com.aikeybridge.ui.theme.TextMain
import com.aikeybridge.ui.theme.TextSoft
import com.aikeybridge.ui.theme.Warn

@Composable
fun LogsScreen(
    summary: AttemptSummary,
    attempts: List<Attempt>,
    entries: List<Diagnostics.Entry>,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var raw by rememberSaveable { mutableStateOf(false) }
    val recentAttempts = remember(attempts) { attempts.asReversed().take(MAX_ROWS) }
    val recentEntries = remember(entries) { entries.asReversed().take(MAX_ROWS) }

    Column(modifier.fillMaxSize()) {
        TabHeader("Chẩn đoán", "Nhật ký") {
            Chip("7 ngày", textColor = TextIcon)
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 16.dp),
        ) {
            item(key = "rate") { RateRow(summary) }
            item(key = "chart") { WeekChart(summary) }
            item(key = "head") {
                SectionLabel(
                    if (raw) "Log chi tiết" else "Sự kiện gần nhất",
                    Modifier
                        .hairlineBottom()
                        .padding(top = 4.dp),
                    action = if (raw) "lượt chạy" else "tất cả",
                    onAction = { raw = !raw },
                )
            }
            if (raw) {
                if (recentEntries.isEmpty()) item(key = "empty") { EmptyRow("Chưa có dòng log nào.") }
                itemsIndexed(recentEntries, key = { index, it -> "e_${index}_${it.id}" }) { _, it -> EntryRow(it) }
            } else {
                if (recentAttempts.isEmpty()) {
                    item(key = "empty") { EmptyRow("Chưa có lượt chuyển hướng nào. Gọi trợ lý gốc để thử.") }
                }
                itemsIndexed(recentAttempts, key = { index, it -> "a_${index}_${it.at}" }) { _, it -> AttemptRow(it) }
            }
        }
        OutlineCta(
            text = "Xuất log chẩn đoán · .zip",
            onClick = onExport,
            icon = R.drawable.lc_file_down,
            iconTint = Accent,
            textColor = TextSoft,
            height = 50.dp,
            radius = 15.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun RateRow(summary: AttemptSummary) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Top) {
                BodyText(
                    summary.rate?.toString() ?: "—",
                    42.sp,
                    TextMain,
                    weight = FontWeight.Bold,
                    letterSpacing = (-2).sp,
                    lineHeight = 48.sp,
                )
                if (summary.rate != null) {
                    BodyText("%", 18.sp, Accent, Modifier.padding(top = 8.dp), weight = FontWeight.SemiBold)
                }
            }
            MonoText("tỉ lệ chuyển hướng thành công", 9.sp, TextFaint)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            MonoText("${summary.total} lần · ${Format.seconds(summary.avgMs)} tb", 10.sp, TextIcon)
            when {
                summary.failed == 0 -> MonoText("không có lỗi", 10.sp, TextFaint)
                summary.micFailed > 0 -> MonoText("${summary.failed} lỗi · ${summary.micFailed} mic không bật", 10.sp, Danger)
                else -> MonoText("${summary.failed} lỗi", 10.sp, Danger)
            }
        }
    }
}

@Composable
private fun WeekChart(summary: AttemptSummary) {
    val max = summary.days.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 4.dp)
            .semantics {
                contentDescription = summary.days.joinToString { "${it.label}: ${it.total} lượt" }
            },
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(78.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            summary.days.forEach { day ->
                val fraction = if (day.total == 0) 0.05f else 0.12f + 0.88f * day.total / max
                Box(
                    Modifier
                        .weight(1f)
                        .height(78.dp * fraction)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            when {
                                day.today -> Accent
                                day.failed > 0 && day.failed * 2 >= day.total -> Warn.copy(alpha = 0.5f)
                                else -> Bar
                            },
                        ),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            summary.days.forEach { day ->
                MonoText(day.label, 8.5.sp, if (day.today) Accent else TextGhost, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AttemptRow(attempt: Attempt) {
    Row(
        Modifier
            .fillMaxWidth()
            .hairlineBottom()
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoText(Format.clock(attempt.at), 10.sp, TextFaint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BodyText(attempt.flow, 12.5.sp, if (attempt.ok) TextSoft else DangerTitle, maxLines = 1)
            MonoText(attempt.detail, 8.5.sp, TextGhost)
        }
        MonoText(Format.seconds(attempt.durationMs), 10.sp, if (attempt.ok) TextLabel else Danger)
    }
}

@Composable
private fun EntryRow(entry: Diagnostics.Entry) {
    val color = when (entry.level) {
        Diagnostics.Level.SUCCESS -> Accent
        Diagnostics.Level.WARN -> Warn
        Diagnostics.Level.ERROR -> Danger
        Diagnostics.Level.INFO -> TextLabel
    }
    Row(
        Modifier
            .fillMaxWidth()
            .hairlineBottom()
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MonoText(Format.clockSeconds(entry.at), 9.sp, TextFaint)
        MonoText(entry.tag, 9.sp, color, Modifier.padding(end = 2.dp))
        BodyText(entry.message, 11.5.sp, TextSoft, Modifier.weight(1f), lineHeight = 16.sp, maxLines = 4)
    }
}

@Composable
private fun EmptyRow(text: String) {
    BodyText(text, 12.sp, TextFaint, Modifier.padding(vertical = 18.dp))
}

private const val MAX_ROWS = 100
