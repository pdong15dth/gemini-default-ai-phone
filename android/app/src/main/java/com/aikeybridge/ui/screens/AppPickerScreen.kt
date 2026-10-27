package com.aikeybridge.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikeybridge.R
import com.aikeybridge.ui.AppEntry
import com.aikeybridge.ui.components.BodyText
import com.aikeybridge.ui.components.Gap
import com.aikeybridge.ui.components.LucideIcon
import com.aikeybridge.ui.components.MonoText
import com.aikeybridge.ui.components.ScreenTitle
import com.aikeybridge.ui.components.SubHeader
import com.aikeybridge.ui.components.hairlineBottom
import com.aikeybridge.ui.theme.Accent
import com.aikeybridge.ui.theme.BodyFont
import com.aikeybridge.ui.theme.Hairline
import com.aikeybridge.ui.theme.Muted
import com.aikeybridge.ui.theme.TextDim
import com.aikeybridge.ui.theme.TextFaint
import com.aikeybridge.ui.theme.TextMain

@Composable
fun AppPickerScreen(
    apps: List<AppEntry>?,
    selectedPackage: String?,
    onLoad: () -> Unit,
    onBack: () -> Unit,
    onPick: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onLoad() }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val q = query.trim().lowercase()
        apps.orEmpty().filter { q.isEmpty() || q in it.label.lowercase() || q in it.packageName }
    }

    Column(modifier.fillMaxSize()) {
        SubHeader(onBack = onBack)
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp)) {
            ScreenTitle("Chọn ứng dụng", size = 26.sp)
            Gap(10.dp)
            BodyText(
                "App sẽ được mở thay trợ lý gốc. App ngoài danh sách đích có sẵn không được tự bật Micro.",
                13.5.sp,
                Muted,
                lineHeight = 20.sp,
            )
            Gap(16.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Hairline, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LucideIcon(R.drawable.lc_scan_search, TextDim, 15.dp)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) BodyText("Tìm theo tên hoặc gói", 13.5.sp, TextFaint)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = BodyFont, fontSize = 13.5.sp, color = TextMain),
                        cursorBrush = SolidColor(Accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (apps == null) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    val selected = app.packageName == selectedPackage
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .hairlineBottom()
                            .clickable(role = Role.Button) { onPick(app) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            BodyText(app.label, 14.sp, TextMain, maxLines = 1)
                            MonoText(app.packageName, 9.sp, TextFaint)
                        }
                        if (selected) LucideIcon(R.drawable.lc_check, Accent, 15.dp)
                    }
                }
                if (filtered.isEmpty()) {
                    item { BodyText("Không có app nào khớp.", 12.sp, TextFaint, Modifier.padding(vertical = 18.dp)) }
                }
            }
        }
        Box(Modifier.navigationBarsPadding())
    }
}
