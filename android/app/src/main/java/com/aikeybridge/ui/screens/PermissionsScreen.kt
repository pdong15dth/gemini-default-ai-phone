package com.aikeybridge.ui.screens

import android.graphics.BlurMaskFilter
import android.graphics.Paint as AndroidPaint
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aikeybridge.R
import com.aikeybridge.data.ActiveConfig
import com.aikeybridge.data.AppSettings
import com.aikeybridge.data.PermissionSnapshot
import com.aikeybridge.ui.Intents
import com.aikeybridge.ui.components.BodyText
import com.aikeybridge.ui.components.Gap
import com.aikeybridge.ui.components.LucideIcon
import com.aikeybridge.ui.components.MonoText
import com.aikeybridge.ui.components.OutlineCta
import com.aikeybridge.ui.components.Panel
import com.aikeybridge.ui.components.PrimaryCta
import com.aikeybridge.ui.components.ScreenTitle
import com.aikeybridge.ui.components.SectionLabel
import com.aikeybridge.ui.components.SubHeader
import com.aikeybridge.ui.components.hairlineBottom
import com.aikeybridge.ui.theme.Accent
import com.aikeybridge.ui.theme.CommunityBg
import com.aikeybridge.ui.theme.CommunityLine
import com.aikeybridge.ui.theme.CommunityTitle
import com.aikeybridge.ui.theme.Danger
import com.aikeybridge.ui.theme.DangerBg
import com.aikeybridge.ui.theme.DangerLine
import com.aikeybridge.ui.theme.Hairline
import com.aikeybridge.ui.theme.Ink
import com.aikeybridge.ui.theme.Surface
import com.aikeybridge.ui.theme.Surface2
import com.aikeybridge.ui.theme.TextBody
import com.aikeybridge.ui.theme.TextDim
import com.aikeybridge.ui.theme.TextFaint
import com.aikeybridge.ui.theme.TextMain
import com.aikeybridge.ui.theme.Warn

/** Tọa độ vùng cần chiếu sáng (spotlight) trên khung ảnh gốc 1260 x 2800. */
private data class SpotlightRect(
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int,
)

private data class GuideImage(
    @DrawableRes val resId: Int,
    val label: String,
    val spotlights: List<SpotlightRect> = emptyList(),
    val warmGlow: Boolean = false,
)

private data class PermissionGuideItem(
    val id: String,
    val section: String,
    val title: String,
    val subtitle: String,
    val granted: Boolean,
    val required: Boolean,
    val pathSummary: String,
    val steps: List<String>,
    val images: List<GuideImage>,
    val openButtonLabel: String,
    val onOpenSettings: () -> Unit,
)

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
    val wakePhrase = config.profile.wakePhrase ?: "Hi Jovi"
    var activeGuideIndex by rememberSaveable { mutableIntStateOf(-1) }

    val items = remember(config, permissions, settings) {
        val gDetail = permissions.googleVivoDetail
        val bDetail = permissions.geminiVivoDetail
        listOf(
            PermissionGuideItem(
                id = "google_perms",
                section = "1. Quyền bắt buộc (Kiểm tra tự động)",
                title = "Quyền của Google",
                subtitle = if (permissions.googleVivoGranted) {
                    "Đã bật đủ: Tự khởi động · Cửa sổ nổi · Màn hình khóa"
                } else {
                    gDetail.missingGoogleSummary() ?: "Cần bật: Tự khởi động, Cửa sổ nổi, Màn hình khóa"
                },
                granted = permissions.googleVivoGranted && permissions.googleInstalled,
                required = true,
                pathSummary = "Cài đặt → Ứng dụng → Google → Quyền → Tất cả quyền (tự cuộn tới mục cần bật)",
                steps = listOf(
                    "Bấm nút “Mở quyền của Google” — máy sẽ tự cuộn xuống cuối trang và nháy sáng đúng công tắc đang tắt.",
                    "Bật đủ “Tự động khởi động”, “Cửa sổ nổi” và “Hiển thị trên màn hình khóa”.",
                ),
                images = listOf(
                    GuideImage(
                        resId = R.drawable.guide_vivo_perm_google,
                        label = "Quyền của Google",
                        spotlights = listOf(
                            SpotlightRect(65, 505, 1195, 735),
                            SpotlightRect(65, 785, 1195, 938),
                            SpotlightRect(65, 1160, 1195, 1315),
                        ),
                    ),
                ),
                openButtonLabel = "Mở quyền của Google",
                onOpenSettings = {
                    Intents.openAppPermissions(
                        context,
                        Intents.GOOGLE_PACKAGE,
                        gDetail.firstMissingGooglePerId(),
                    )
                },
            ),
            PermissionGuideItem(
                id = "gemini_perms",
                section = "1. Quyền bắt buộc (Kiểm tra tự động)",
                title = "Quyền của Gemini",
                subtitle = if (!permissions.targetInstalled) {
                    "Chưa cài ứng dụng Gemini"
                } else if (permissions.geminiVivoGranted) {
                    "Đã bật đủ 5 quyền Quản lý thiết bị"
                } else {
                    bDetail.missingGeminiSummary() ?: "Cần bật đủ 5 quyền Quản lý thiết bị"
                },
                granted = permissions.geminiVivoGranted && permissions.targetInstalled,
                required = true,
                pathSummary = "Cài đặt → Ứng dụng → Gemini → Quyền → Tất cả quyền (tự cuộn tới mục cần bật)",
                steps = listOf(
                    "Bấm nút “Mở quyền của Gemini” — máy sẽ tự cuộn thẳng xuống 5 quyền Quản lý thiết bị ở cuối trang.",
                    "Bật đủ 5 quyền: Tự động khởi động, Khởi động liên kết, Cửa sổ nổi, Hiển thị trên màn hình khóa, Cửa sổ bật lên dưới nền.",
                ),
                images = listOf(
                    GuideImage(
                        resId = R.drawable.guide_vivo_perm_gemini,
                        label = "5 quyền của Gemini",
                        spotlights = listOf(
                            SpotlightRect(65, 500, 1195, 1110),
                            SpotlightRect(65, 1315, 1195, 1645),
                        ),
                    ),
                ),
                openButtonLabel = if (permissions.targetInstalled) "Mở quyền của Gemini" else "Cài đặt Gemini",
                onOpenSettings = {
                    if (permissions.targetInstalled) {
                        Intents.openAppPermissions(
                            context,
                            Intents.GEMINI_PACKAGE,
                            bDetail.firstMissingGeminiPerId(),
                        )
                    } else {
                        Intents.openPlayStore(context, Intents.GEMINI_PACKAGE)
                    }
                },
            ),
            PermissionGuideItem(
                id = "battery_bg",
                section = "1. Quyền bắt buộc (Kiểm tra tự động)",
                title = "Pin dưới nền (Google & Gemini)",
                subtitle = when {
                    permissions.bothBatteryUnrestricted -> "Đã bật Cho phép sử dụng pin ngầm cho cả hai"
                    !permissions.googleBatteryUnrestricted && !permissions.geminiBatteryUnrestricted ->
                        "Đang tắt: Bấm Bật ↗ để mở thẳng trang Pin của Google rồi Gemini"
                    !permissions.googleBatteryUnrestricted ->
                        "Đang tắt: Google chưa chọn Cho phép sử dụng pin ngầm"
                    else ->
                        "Đang tắt: Gemini chưa chọn Cho phép sử dụng pin ngầm"
                },
                granted = permissions.bothBatteryUnrestricted,
                required = true,
                pathSummary = "Chi tiết mức sử dụng pin của ứng dụng → Kiểm soát pin dưới nền",
                steps = listOf(
                    "Bấm nút bên dưới để mở thẳng trang Pin của ứng dụng đang thiếu (Google hoặc Gemini) — máy sẽ tự cuộn xuống và nháy sáng mục “Kiểm soát pin dưới nền”.",
                    "Chạm mục “Kiểm soát pin dưới nền” → chọn dòng thứ 2 “Cho phép sử dụng pin ngầm” (được nháy sáng tự động).",
                ),
                images = listOf(
                    GuideImage(
                        resId = R.drawable.guide_battery_usage,
                        label = "1. Kiểm soát pin nền",
                        spotlights = listOf(
                            SpotlightRect(65, 2015, 1195, 2245),
                        ),
                    ),
                    GuideImage(
                        resId = R.drawable.guide_battery_detail,
                        label = "2. Cho phép pin ngầm",
                        spotlights = listOf(
                            SpotlightRect(65, 1170, 1195, 1420),
                        ),
                    ),
                ),
                openButtonLabel = when {
                    !permissions.googleBatteryUnrestricted -> "Mở Pin dưới nền của Google"
                    !permissions.geminiBatteryUnrestricted -> "Mở Pin dưới nền của Gemini"
                    else -> "Mở Kiểm soát pin dưới nền"
                },
                onOpenSettings = {
                    val targetPkg = when {
                        !permissions.googleBatteryUnrestricted -> Intents.GOOGLE_PACKAGE
                        !permissions.geminiBatteryUnrestricted -> Intents.GEMINI_PACKAGE
                        else -> Intents.GOOGLE_PACKAGE
                    }
                    Intents.openBackgroundBatterySettings(context, targetPkg)
                },
            ),
            PermissionGuideItem(
                id = "default_assistant",
                section = "1. Quyền bắt buộc (Kiểm tra tự động)",
                title = "Trợ lý kỹ thuật số mặc định",
                subtitle = when {
                    permissions.assistantDefault && permissions.screenContextEnabled ->
                        "Đã chọn Google & bật Dùng dữ liệu của màn hình"
                    !permissions.assistantDefault ->
                        "Đang thiếu: Chưa chọn Google làm trợ lý mặc định"
                    else ->
                        "Đang tắt: Công tắc “Dùng dữ liệu của màn hình và ứng dụng”"
                },
                granted = permissions.assistantDefault && permissions.screenContextEnabled,
                required = true,
                pathSummary = "Cài đặt → Ứng dụng → Ứng dụng mặc định → Trợ lý kỹ thuật số",
                steps = listOf(
                    "Bấm “Mở cài đặt Trợ lý mặc định” bên dưới — máy sẽ tự động nháy sáng mục cần bật.",
                    "Chọn “Google” làm trợ lý mặc định, và bật công tắc “Dùng dữ liệu của màn hình và ứng dụng” ở ngay bên dưới.",
                ),
                images = listOf(
                    GuideImage(
                        resId = R.drawable.guide_default_assistant,
                        label = "Trợ lý số mặc định",
                        spotlights = listOf(
                            SpotlightRect(70, 950, 1190, 1140),
                            SpotlightRect(65, 1400, 1195, 1795),
                        ),
                    ),
                ),
                openButtonLabel = "Mở cài đặt Trợ lý mặc định",
                onOpenSettings = {
                    Intents.openAssistantSettings(
                        context = context,
                        assistantAlreadySelected = permissions.assistantDefault && !permissions.screenContextEnabled,
                    )
                },
            ),
            PermissionGuideItem(
                id = "gemini_lockscreen",
                section = "2. Cài đặt trong Gemini & Phím gọi nhanh",
                title = "Gemini trên màn hình khóa",
                subtitle = "App Gemini → Avatar → Cài đặt → Điều khiển thiết bị",
                granted = true,
                required = false,
                pathSummary = "App Gemini → Avatar góc phải → Cài đặt → Điều khiển thiết bị → Gemini trên màn hình khoá",
                steps = listOf(
                    "Mở ứng dụng Gemini → chạm vào ảnh đại diện (Avatar) ở góc trên bên phải → chọn “Cài đặt”.",
                    "Chọn “Điều khiển thiết bị” → “Gemini trên màn hình khoá” → bật “Dùng Gemini mà không cần mở khoá?”.",
                ),
                images = listOf(
                    GuideImage(
                        resId = R.drawable.guide_gemini_device_control,
                        label = "Bước 1: Điều khiển thiết bị",
                        spotlights = listOf(
                            SpotlightRect(65, 735, 1195, 1065),
                        ),
                    ),
                    GuideImage(
                        resId = R.drawable.guide_gemini_lockscreen,
                        label = "Bước 2: Bật trên màn khóa",
                        spotlights = listOf(
                            SpotlightRect(50, 730, 1210, 1035),
                        ),
                    ),
                ),
                openButtonLabel = "Mở ứng dụng Gemini",
                onOpenSettings = { Intents.openGemini(context) },
            ),
            PermissionGuideItem(
                id = "voice_wakeup",
                section = "2. Cài đặt trong Gemini & Phím gọi nhanh",
                title = "Đánh thức giọng nói (“$wakePhrase”)",
                subtitle = "Ghi mẫu giọng nói để gọi Gemini rảnh tay",
                granted = true,
                required = false,
                pathSummary = "Cài đặt → BlueAI → Trợ lý ảo BlueLM → Đánh thức bằng giọng nói",
                steps = listOf(
                    "Bấm “Mở Đánh thức bằng giọng nói” bên dưới.",
                    "Bật công tắc và đọc mẫu khẩu lệnh “$wakePhrase” 4–5 lần.",
                ),
                images = listOf(
                    GuideImage(
                        resId = R.drawable.guide_voice_wakeup,
                        label = "Đánh thức bằng giọng nói",
                        spotlights = listOf(
                            SpotlightRect(65, 1655, 1195, 2175),
                        ),
                    ),
                ),
                openButtonLabel = "Mở Đánh thức bằng giọng nói",
                onOpenSettings = { Intents.openVoiceWakeSettings(context) },
            ),
            PermissionGuideItem(
                id = "smart_key",
                section = "2. Cài đặt trong Gemini & Phím gọi nhanh",
                title = "Nút thông minh / Phím cạnh AI",
                subtitle = "Gán Nhấn và giữ → Trợ lý giọng nói",
                granted = true,
                required = false,
                pathSummary = "Cài đặt → Lối tắt và trợ năng → Nút thông minh",
                steps = listOf(
                    "Bấm “Mở cài đặt Nút thông minh” bên dưới.",
                    "Ở mục “Chức năng sẽ bật khi nhấn và giữ nút Thông minh”, chọn “Trợ lý giọng nói”.",
                ),
                images = listOf(
                    GuideImage(
                        resId = R.drawable.guide_smart_key,
                        label = "Nút thông minh",
                        spotlights = listOf(
                            SpotlightRect(50, 2315, 1210, 2565),
                        ),
                    ),
                ),
                openButtonLabel = "Mở cài đặt Nút thông minh",
                onOpenSettings = { Intents.openSmartKey(context) },
            ),
            PermissionGuideItem(
                id = "shizuku",
                section = "3. Công cụ kích hoạt & Bảo mật ngân hàng",
                title = "Shizuku (Gỡ lỗi không dây)",
                subtitle = when {
                    permissions.shizukuGranted -> "Đã kết nối ✓"
                    permissions.shizukuRunning -> "Đang chạy — chạm để cấp quyền"
                    else -> "Chỉ cần bật 1 lần sau khi khởi động lại máy"
                },
                granted = permissions.shizukuGranted,
                required = false,
                pathSummary = "App Shizuku → Bắt đầu thông qua Gỡ lỗi không dây",
                steps = listOf(
                    "Mở Shizuku → bấm “Ghép nối” (lần đầu) rồi bấm “Bắt đầu”.",
                    "Quay lại AI Key Bridge để chạy lệnh và bật Script nền.",
                ),
                images = listOf(
                    GuideImage(
                        resId = R.drawable.guide_shizuku,
                        label = "Shizuku Gỡ lỗi không dây",
                        spotlights = listOf(
                            SpotlightRect(85, 2440, 585, 2790),
                        ),
                    ),
                ),
                openButtonLabel = if (permissions.shizukuRunning && !permissions.shizukuGranted) "Cấp quyền Shizuku" else "Mở Shizuku",
                onOpenSettings = {
                    if (permissions.shizukuRunning && !permissions.shizukuGranted) {
                        onRequestShizuku()
                    } else {
                        Intents.openShizuku(context)
                    }
                },
            ),
            PermissionGuideItem(
                id = "dev_options",
                section = "3. Công cụ kích hoạt & Bảo mật ngân hàng",
                title = "Tắt Tùy chọn nhà phát triển",
                subtitle = if (!permissions.devOptionsEnabled && !permissions.adbEnabled) {
                    "Đã tắt — an toàn 100% cho app ngân hàng & VNeID ✓"
                } else {
                    "Đang bật — tắt sau khi đã bật Script nền để dùng ngân hàng"
                },
                granted = !permissions.devOptionsEnabled && !permissions.adbEnabled,
                required = false,
                pathSummary = "Cài đặt → Quản lý hệ thống → Tùy chọn nhà phát triển",
                steps = listOf(
                    "Sau khi Script nền đã báo “Đang chạy”, bấm mở Tùy chọn nhà phát triển.",
                    "Gạt TẮT công tắc tổng ở đầu trang để mọi app ngân hàng & VNeID hoạt động bình thường.",
                ),
                images = listOf(
                    GuideImage(
                        resId = R.drawable.guide_dev_options,
                        label = "Tắt Tùy chọn nhà phát triển",
                        spotlights = listOf(
                            SpotlightRect(65, 375, 1195, 590),
                        ),
                        warmGlow = true,
                    ),
                ),
                openButtonLabel = "Mở Tùy chọn nhà phát triển",
                onOpenSettings = { Intents.openDeveloperSettings(context) },
            ),
        )
    }

    val missingRequired = items.filter { it.required && !it.granted }
    val requiredTotal = items.count { it.required }
    val requiredGranted = requiredTotal - missingRequired.size

    Column(modifier.fillMaxSize()) {
        SubHeader(onBack = onBack) {
            MonoText(
                "$requiredGranted / $requiredTotal ĐẠT",
                10.sp,
                if (missingRequired.isEmpty()) Accent else Warn,
                letterSpacing = 1.sp,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 8.dp),
        ) {
            ScreenTitle("Quyền & Cài đặt", size = 25.sp)
            Gap(8.dp)

            // Hộp tóm tắt trạng thái cực kỳ gọn gàng
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (missingRequired.isEmpty()) CommunityBg else DangerBg)
                    .border(
                        1.dp,
                        if (missingRequired.isEmpty()) CommunityLine else DangerLine,
                        RoundedCornerShape(15.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background((if (missingRequired.isEmpty()) Accent else Warn).copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    LucideIcon(
                        if (missingRequired.isEmpty()) R.drawable.lc_check else R.drawable.lc_shield_alert,
                        if (missingRequired.isEmpty()) Accent else Warn,
                        16.dp,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BodyText(
                        if (missingRequired.isEmpty()) {
                            "Đã bật đủ $requiredTotal/$requiredTotal nhóm quyền bắt buộc!"
                        } else {
                            "Còn ${missingRequired.size} mục màu cam cần bật"
                        },
                        13.5.sp,
                        if (missingRequired.isEmpty()) CommunityTitle else TextMain,
                        weight = FontWeight.SemiBold,
                    )
                    BodyText(
                        if (missingRequired.isEmpty()) {
                            "Bấm “Ảnh” để xem hình chụp hướng dẫn, hoặc bấm “Mở ↗” để vào thẳng cài đặt."
                        } else {
                            "Bấm nút “Bật ↗” ở dòng màu cam để nhảy thẳng tới cài đặt đó, hoặc bấm “Ảnh” để xem hình."
                        },
                        11.5.sp,
                        if (missingRequired.isEmpty()) TextDim else Warn,
                        lineHeight = 16.sp,
                    )
                }
            }

            val sections = remember(items) { items.groupBy { it.section } }
            sections.entries.forEach { (sectionTitle, sectionItems) ->
                SectionLabel(sectionTitle, Modifier.padding(top = 16.dp, bottom = 4.dp))
                Panel {
                    sectionItems.forEachIndexed { idx, item ->
                        val globalIndex = items.indexOf(item)
                        CleanPermRow(
                            item = item,
                            divider = idx != sectionItems.lastIndex,
                            onOpenSettings = item.onOpenSettings,
                            onOpenGuide = { activeGuideIndex = globalIndex },
                        )
                    }
                }
            }

            Gap(20.dp)
        }

        // Thanh dưới đáy chỉ có đúng 1 nút chính rõ ràng
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 12.dp),
        ) {
            val nextMissing = missingRequired.firstOrNull()
            PrimaryCta(
                text = when {
                    nextMissing != null -> "Bật ngay: ${nextMissing.title}"
                    onboarding -> "Tiếp tục"
                    else -> "Hoàn tất"
                },
                onClick = {
                    if (nextMissing != null) {
                        nextMissing.onOpenSettings()
                    } else {
                        onContinue()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = if (nextMissing != null) R.drawable.lc_arrow_up_right else R.drawable.lc_arrow_right,
            )
            if (onboarding) {
                BodyText(
                    if (nextMissing != null) "Bỏ qua, sang bước tiếp theo →" else "Để sau, vào thẳng app",
                    12.5.sp,
                    TextDim,
                    Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .clickable(
                            role = Role.Button,
                            onClick = if (nextMissing != null) onContinue else onSkip,
                        )
                        .padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (activeGuideIndex in items.indices) {
        val currentItem = items[activeGuideIndex]
        PermissionGuideDialog(
            item = currentItem,
            stepIndex = activeGuideIndex,
            totalSteps = items.size,
            onDismiss = { activeGuideIndex = -1 },
            onPrev = if (activeGuideIndex > 0) {
                { activeGuideIndex-- }
            } else {
                null
            },
            onNext = if (activeGuideIndex < items.lastIndex) {
                { activeGuideIndex++ }
            } else {
                null
            },
        )
    }
}

@Composable
private fun CleanPermRow(
    item: PermissionGuideItem,
    divider: Boolean,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    val missing = item.required && !item.granted
    Row(
        Modifier
            .fillMaxWidth()
            .hairlineBottom(divider)
            .clickable(role = Role.Button, onClick = onOpenSettings)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Biểu tượng trạng thái: Xanh ✓ nếu đã bật, Cam ! nếu đang thiếu
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(
                    when {
                        missing -> Warn.copy(alpha = 0.16f)
                        item.granted -> Accent.copy(alpha = 0.14f)
                        else -> Surface2
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            LucideIcon(
                when {
                    missing -> R.drawable.lc_shield_alert
                    item.granted -> R.drawable.lc_check
                    else -> R.drawable.lc_sparkles
                },
                when {
                    missing -> Warn
                    item.granted -> Accent
                    else -> TextDim
                },
                14.dp,
            )
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BodyText(
                item.title,
                13.5.sp,
                if (missing) Warn else TextMain,
                weight = if (missing) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 2,
                lineHeight = 18.sp,
            )
            MonoText(
                item.subtitle,
                9.5.sp,
                when {
                    missing -> Warn
                    item.granted && item.required -> Accent
                    else -> TextDim
                },
                maxLines = 2,
            )
        }

        // Nút mở Ảnh hướng dẫn
        MonoText(
            "Ảnh",
            9.5.sp,
            TextBody,
            Modifier
                .clip(CircleShape)
                .background(Surface2)
                .border(1.dp, Hairline, CircleShape)
                .clickable(role = Role.Button, onClick = onOpenGuide)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )

        // Nút mở thẳng Cài đặt
        MonoText(
            if (missing) "Bật ↗" else "Mở ↗",
            9.5.sp,
            if (missing) Ink else Accent,
            Modifier
                .clip(CircleShape)
                .background(if (missing) Warn else Accent.copy(alpha = 0.12f))
                .border(
                    1.dp,
                    if (missing) Warn else Accent.copy(alpha = 0.35f),
                    CircleShape,
                )
                .clickable(role = Role.Button, onClick = onOpenSettings)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun PermissionGuideDialog(
    item: PermissionGuideItem,
    stepIndex: Int,
    totalSteps: Int,
    onDismiss: () -> Unit,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
) {
    var selectedImageIdx by remember(item.id) { mutableIntStateOf(0) }
    val currentImage = item.images.getOrElse(selectedImageIdx) { item.images.first() }
    val scrollState = rememberScrollState(0)

    LaunchedEffect(item.id, selectedImageIdx, scrollState.maxValue) {
        if (scrollState.maxValue <= 0 || scrollState.maxValue == Int.MAX_VALUE) return@LaunchedEffect
        val firstSpotY = currentImage.spotlights.firstOrNull()?.y1 ?: 0
        when {
            firstSpotY >= 1450 -> scrollState.animateScrollTo((scrollState.maxValue * 0.72f).toInt())
            firstSpotY >= 900 -> scrollState.animateScrollTo((scrollState.maxValue * 0.35f).toInt())
            else -> scrollState.scrollTo(0)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Surface)
                    .border(1.dp, Hairline, RoundedCornerShape(22.dp)),
            ) {
                // 1. Header cố định ở trên cùng
                Column(
                    Modifier
                        .fillMaxWidth()
                        .hairlineBottom(true)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MonoText(
                                "MỤC ${stepIndex + 1} / $totalSteps",
                                10.sp,
                                Accent,
                                Modifier
                                    .clip(CircleShape)
                                    .background(Accent.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                letterSpacing = 1.sp,
                            )
                            if (item.required) {
                                MonoText(
                                    if (item.granted) "ĐÃ BẬT ✓" else "ĐANG THIẾU",
                                    9.5.sp,
                                    if (item.granted) Accent else Warn,
                                )
                            }
                        }
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Surface2)
                                .clickable(role = Role.Button, onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            LucideIcon(R.drawable.lc_x, TextMain, 14.dp, contentDescription = "Đóng")
                        }
                    }

                    BodyText(item.title, 16.5.sp, TextMain, weight = FontWeight.SemiBold)

                    if (item.images.size > 1) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item.images.forEachIndexed { idx, img ->
                                val selected = idx == selectedImageIdx
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) Accent.copy(alpha = 0.14f) else Surface2)
                                        .border(1.dp, if (selected) Accent else Hairline, RoundedCornerShape(10.dp))
                                        .clickable(role = Role.Button) { selectedImageIdx = idx }
                                        .padding(vertical = 7.dp, horizontal = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    MonoText(
                                        img.label,
                                        9.5.sp,
                                        if (selected) Accent else TextDim,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Nội dung ở giữa cho phép cuộn mượt từ đầu đến cuối ảnh & hướng dẫn
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Surface2)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MonoText(item.pathSummary, 9.5.sp, Accent, maxLines = 2)
                        item.steps.forEachIndexed { i, stepText ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                MonoText("${i + 1}.", 10.5.sp, Accent, Modifier.padding(top = 1.dp))
                                BodyText(stepText, 12.sp, TextBody, Modifier.weight(1f), lineHeight = 17.sp)
                            }
                        }
                    }

                    SpotlightScreenshot(
                        image = currentImage,
                        contentDescription = item.title,
                    )
                }

                // 3. Cụm nút Action cố định ở đáy popup (không bao giờ bị trôi hay khuất)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PrimaryCta(
                        text = item.openButtonLabel,
                        onClick = item.onOpenSettings,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = R.drawable.lc_arrow_up_right,
                        height = 46.dp,
                        radius = 13.dp,
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlineCta(
                            text = "‹ Trước",
                            onClick = { onPrev?.invoke() },
                            modifier = Modifier.weight(1f),
                            height = 40.dp,
                            radius = 11.dp,
                        )
                        OutlineCta(
                            text = if (onNext != null) "Tiếp theo ›" else "Đóng",
                            onClick = { if (onNext != null) onNext() else onDismiss() },
                            modifier = Modifier.weight(1f),
                            height = 40.dp,
                            radius = 11.dp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Hiển thị ảnh chụp màn hình sạch kết hợp hiệu ứng Spotlight chiếu sáng + hào quang phát sáng mềm (Breathing Light Bloom)
 * thay vì vẽ khung viền xanh thô cứng.
 */
@Composable
private fun SpotlightScreenshot(
    image: GuideImage,
    contentDescription: String,
) {
    val transition = rememberInfiniteTransition(label = "spotlightGlow")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    val outerGlowColor = if (image.warmGlow) Color(0xFFFFB547) else Color(0xFFC6FF3D)
    val innerBloomColor = if (image.warmGlow) Color(0xFFFFE6BE) else Color(0xFFF0FFCC)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ink)
            .border(1.dp, Hairline, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(image.resId),
            contentDescription = contentDescription,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(540f / 1200f)
                .drawWithContent {
                    drawContent()
                    if (image.spotlights.isEmpty()) return@drawWithContent

                    val sx = size.width / 1260f
                    val sy = size.height / 2800f
                    val corner = 30f * sx

                    val fullPath = Path().apply {
                        addRect(Rect(0f, 0f, size.width, size.height))
                    }
                    val cutoutPath = Path()
                    val rects = image.spotlights.map { s ->
                        val rect = Rect(
                            left = s.x1 * sx,
                            top = s.y1 * sy,
                            right = s.x2 * sx,
                            bottom = s.y2 * sy,
                        )
                        cutoutPath.addRoundRect(
                            RoundRect(
                                rect = rect,
                                cornerRadius = CornerRadius(corner, corner),
                            ),
                        )
                        rect
                    }

                    // 1. Làm tối dịu vùng ngoài (Spotlight Backdrop) để vùng cần bật nổi bật sáng rõ
                    val dimPath = Path.combine(PathOperation.Difference, fullPath, cutoutPath)
                    drawPath(dimPath, Color.Black.copy(alpha = 0.62f))

                    // 2. Vẽ quầng sáng lan tỏa đa lớp (Gaussian Light Bloom) bằng BlurMaskFilter
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        val widePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                            style = AndroidPaint.Style.STROKE
                            strokeWidth = 18f * sx
                            color = outerGlowColor.copy(alpha = 0.42f * pulse).toArgb()
                            maskFilter = BlurMaskFilter((28f * sx).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
                        }
                        val tightPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                            style = AndroidPaint.Style.STROKE
                            strokeWidth = 7f * sx
                            color = innerBloomColor.copy(alpha = 0.65f * pulse).toArgb()
                            maskFilter = BlurMaskFilter((10f * sx).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
                        }
                        for (r in rects) {
                            native.drawRoundRect(r.left, r.top, r.right, r.bottom, corner, corner, widePaint)
                            native.drawRoundRect(r.left, r.top, r.right, r.bottom, corner, corner, tightPaint)
                        }
                    }

                    // 3. Ánh sáng viền thủy tinh siêu mảnh (1.2dp) ôm sát vùng chiếu sáng
                    val rimStroke = Stroke(width = 1.2.dp.toPx())
                    val rimColor = innerBloomColor.copy(alpha = 0.35f + 0.35f * pulse)
                    for (r in rects) {
                        drawRoundRect(
                            color = rimColor,
                            topLeft = r.topLeft,
                            size = r.size,
                            cornerRadius = CornerRadius(corner, corner),
                            style = rimStroke,
                        )
                    }
                },
        )
    }
}
