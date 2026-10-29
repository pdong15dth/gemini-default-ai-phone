package com.aikeybridge.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.provider.Settings
import android.util.Xml
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.PathInterpolator
import com.aikeybridge.shell.ShizukuShell
import java.io.StringReader
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import rikka.shizuku.Shizuku

/**
 * Tự động cuộn tới mục cần cài đặt và phát hiệu ứng sáng dòng mặc định của hệ điều hành Vivo OriginOS
 * (`vivo:color/color_list_item_background_highlight` — giống hệt mục 1 và 2).
 * Hoàn toàn tìm dòng động theo `resource-id` / `text` trên cây giao diện thực tế, KHÔNG dùng tọa độ cứng.
 */
object SettingsGuideHelper {

    /** Khóa chuẩn của Android / Vivo OriginOS để hệ điều hành tự cuộn và highlight dòng cài đặt. */
    const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeJob: Job? = null
    private var activeOverlay: OverlayHolder? = null

    @Volatile
    internal var activeActivity: SettingsSpotlightActivity? = null

    @Volatile
    internal var currentTarget: RowTarget? = null

    @Volatile
    private var shellRunner: ShizukuShell? = null

    private fun shell(context: Context): ShizukuShell =
        shellRunner ?: synchronized(this) {
            shellRunner ?: ShizukuShell(context.applicationContext).also { shellRunner = it }
        }

    private fun isShizukuReady(): Boolean = runCatching {
        !Shizuku.isPreV11() &&
            Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    internal data class RowTarget(
        val rect: RectF,
        val roundTop: Boolean = true,
        val roundBottom: Boolean = true,
        val isChecked: Boolean = false,
    )

    private data class OverlayHolder(
        val windowManager: WindowManager,
        val view: VivoOsRowHighlightView,
    )

    fun dismiss() {
        activeJob?.cancel()
        activeJob = null
        removeOverlayNow()
    }

    private fun removeOverlayNow() {
        val act = activeActivity
        if (act != null) {
            activeActivity = null
            runCatching { act.dismissSilently() }
        }
        val holder = activeOverlay ?: return
        activeOverlay = null
        runCatching { holder.view.stopAnimation() }
        runCatching { holder.windowManager.removeViewImmediate(holder.view) }
    }

    internal fun forwardTouchTap(context: Context, x: Int, y: Int) {
        if (!isShizukuReady()) return
        val appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            delay(55)
            shell(appContext).exec("input tap $x $y")
        }
    }

    /**
     * Hướng dẫn Mục 3 (Pin dưới nền):
     * 1. Tự động cuộn trang `PowerUsageSummaryActivity` xuống dưới cùng.
     * 2. Tìm động dòng "Kiểm soát pin dưới nền" (`background_power_manage_content`) và nháy sáng kiểu mặc định OS.
     * 3. Khi người dùng bấm sang `ExcessivePowerDescriptionActivity`, tìm động dòng "Cho phép sử dụng pin ngầm" (`all_opt`)
     *    và nháy sáng kiểu mặc định OS.
     */
    fun guideBatterySettings(context: Context, targetPackage: String? = null) {
        val appContext = context.applicationContext
        dismiss()
        activeJob = scope.launch {
            val shizuku = shell(appContext)
            ensureOverlayPermission(appContext, shizuku)
            val defaultMinY = (appContext.resources.displayMetrics.heightPixels * 0.11f).toInt()

            // 1. Chờ trang Chi tiết mức sử dụng pin mở lên rồi cuộn xuống cuối trang
            delay(320)
            scrollDown(appContext, shizuku)
            delay(180)

            // 2. Tìm động dòng "Kiểm soát pin dưới nền" trên màn hình hiện tại (không dùng tọa độ cứng)
            val row1 = findRowBoundsDynamic(
                shizuku = shizuku,
                resourceIds = listOf("background_power_manage_content"),
                texts = listOf("Kiểm soát pin dưới nền", "Background power"),
                minY = defaultMinY,
                preferFastActivityDump = true,
                scrolledToBottom = true,
            )
            if (row1 != null) {
                playOsNativeHighlight(appContext, row1)
            }

            // 3. Chờ người dùng chạm vào "Kiểm soát pin dưới nền" để mở màn 2 (ExcessivePowerDescriptionActivity)
            val waitStart = System.currentTimeMillis()
            var enteredScreen2 = false
            while (isActive && System.currentTimeMillis() - waitStart < 8000L) {
                if (isShizukuReady()) {
                    val focus = withContext(Dispatchers.IO) {
                        shizuku.exec("dumpsys window | grep mCurrentFocus").stdout
                    }
                    if (focus.contains("ExcessivePowerDescriptionActivity")) {
                        enteredScreen2 = true
                        break
                    } else if (focus.contains("mCurrentFocus=") &&
                        !focus.contains("com.iqoo.powersaving") &&
                        !focus.contains("aikeybridge") &&
                        !focus.contains("null")
                    ) {
                        removeOverlayNow()
                        return@launch
                    }
                } else {
                    val row2Check = findRowViaAccessibility(
                        resourceIds = listOf("id/all_opt", "vos_control_opt"),
                        texts = listOf("Cho phép sử dụng pin ngầm", "Allow background power"),
                        minY = defaultMinY,
                    )
                    if (row2Check != null) {
                        enteredScreen2 = true
                        break
                    }
                }
                delay(140)
            }

            if (!enteredScreen2) return@launch

            // 4. Màn 2 mở lên: tìm động dòng "Cho phép sử dụng pin ngầm" (`all_opt`) và nháy sáng kiểu mặc định OS
            removeOverlayNow()
            delay(180)
            val row2 = findRowBoundsDynamic(
                shizuku = shizuku,
                resourceIds = listOf("id/all_opt", "vos_control_opt"),
                texts = listOf("Cho phép sử dụng pin ngầm", "Allow background power", "High background power"),
                minY = defaultMinY,
                preferFastActivityDump = true,
            )
            if (row2 != null) {
                playOsNativeHighlight(appContext, row2)
            }

            // Đồng bộ trạng thái khi người dùng ở màn 2
            val step2Start = System.currentTimeMillis()
            while (isActive && System.currentTimeMillis() - step2Start < 6000L) {
                if (isShizukuReady()) {
                    val focus = withContext(Dispatchers.IO) {
                        shizuku.exec("dumpsys window | grep mCurrentFocus").stdout
                    }
                    if (focus.contains("mCurrentFocus=") &&
                        !focus.contains("ExcessivePowerDescriptionActivity") &&
                        !focus.contains("aikeybridge") &&
                        !focus.contains("null")
                    ) {
                        if (!targetPackage.isNullOrBlank()) {
                            withContext(Dispatchers.IO) {
                                shizuku.exec("cmd deviceidle whitelist +$targetPackage")
                            }
                        }
                        break
                    }
                }
                delay(200)
            }
        }
    }

    /**
     * Hướng dẫn Mục 4 (Trợ lý kỹ thuật số mặc định — `DefaultAppActivity`):
     * - Nếu đã chọn Google (hoặc người dùng mở riêng mục "Cho Gemini xem màn hình"), nháy sáng dòng
     *   "Dùng dữ liệu của màn hình và ứng dụng".
     * - Nếu chưa chọn Google, nháy sáng dòng "Google" trước; ngay khi người dùng chọn "Google" xong thì
     *   tự động nháy sáng tiếp công tắc "Dùng dữ liệu của màn hình và ứng dụng".
     */
    fun guideAssistantSettings(context: Context, assistantAlreadySelected: Boolean = false) {
        val appContext = context.applicationContext
        dismiss()
        activeJob = scope.launch {
            val shizuku = shell(appContext)
            ensureOverlayPermission(appContext, shizuku)
            val defaultMinY = (appContext.resources.displayMetrics.heightPixels * 0.11f).toInt()

            delay(420)
            if (assistantAlreadySelected) {
                val contextRow = findRowBoundsDynamic(
                    shizuku = shizuku,
                    resourceIds = listOf("switchWidget"),
                    texts = listOf("Dùng dữ liệu của màn hình và ứng dụng", "Dùng dữ liệu của màn hình", "Use text from screen"),
                    minY = defaultMinY,
                )
                if (contextRow != null) {
                    playOsNativeHighlight(appContext, contextRow)
                }
                return@launch
            }

            val googleRow = findRowBoundsDynamic(
                shizuku = shizuku,
                resourceIds = emptyList(),
                texts = listOf("Google"),
                minY = defaultMinY,
            )
            if (googleRow != null) {
                playOsNativeHighlight(appContext, googleRow)
                if (googleRow.isChecked) {
                    delay(1550)
                    val contextRow = findRowBoundsDynamic(
                        shizuku = shizuku,
                        resourceIds = listOf("switchWidget"),
                        texts = listOf("Dùng dữ liệu của màn hình và ứng dụng", "Dùng dữ liệu của màn hình", "Use text from screen"),
                        minY = defaultMinY,
                    )
                    if (contextRow != null) {
                        playOsNativeHighlight(appContext, contextRow)
                    }
                } else {
                    // Chờ người dùng chấm chọn "Google", sau đó nháy sáng tiếp công tắc "Dùng dữ liệu của màn hình"
                    val waitStart = System.currentTimeMillis()
                    while (isActive && System.currentTimeMillis() - waitStart < 8000L) {
                        delay(350)
                        val updatedGoogle = findRowBoundsDynamic(
                            shizuku = shizuku,
                            resourceIds = emptyList(),
                            texts = listOf("Google"),
                            minY = defaultMinY,
                        ) ?: break
                        if (updatedGoogle.isChecked) {
                            delay(180)
                            val contextRow = findRowBoundsDynamic(
                                shizuku = shizuku,
                                resourceIds = listOf("switchWidget"),
                                texts = listOf("Dùng dữ liệu của màn hình và ứng dụng", "Dùng dữ liệu của màn hình", "Use text from screen"),
                                minY = defaultMinY,
                            )
                            if (contextRow != null) {
                                playOsNativeHighlight(appContext, contextRow)
                            }
                            break
                        }
                    }
                }
            }
        }
    }

    /**
     * Hướng dẫn Mục 5 (Gemini trên màn hình khóa):
     * Theo dõi động từng bước khi người dùng thao tác trong Gemini:
     * 1. Màn hình chính Gemini -> nháy sáng Avatar góc trên bên phải.
     * 2. Menu tài khoản (Avatar) -> nháy sáng dòng "Cài đặt".
     * 3. Trang "Cài đặt" của Gemini -> tự động cuộn xuống và nháy sáng dòng "Điều khiển thiết bị".
     * 4. Trang "Điều khiển thiết bị" -> nháy sáng dòng "Gemini trên màn hình khoá".
     * 5. Trang "Gemini trên màn hình khoá" -> nháy sáng dòng "Dùng Gemini mà không cần mở khoá?".
     */
    fun guideGeminiLockscreen(context: Context) {
        val appContext = context.applicationContext
        dismiss()
        activeJob = scope.launch {
            val shizuku = shell(appContext)
            ensureOverlayPermission(appContext, shizuku)
            val dm = appContext.resources.displayMetrics
            val defaultMinY = (dm.heightPixels * 0.11f).toInt()

            delay(480)
            var lastStage = -1
            val startTime = System.currentTimeMillis()
            while (isActive && System.currentTimeMillis() - startTime < 22000L) {
                if (isShizukuReady()) {
                    val focus = withContext(Dispatchers.IO) {
                        shizuku.exec("dumpsys window | grep mCurrentFocus").stdout
                    }
                    if (focus.contains("mCurrentFocus=") &&
                        !focus.contains("googlequicksearchbox") &&
                        !focus.contains("apps.bard") &&
                        !focus.contains("aikeybridge") &&
                        !focus.contains("null")
                    ) {
                        removeOverlayNow()
                        return@launch
                    }
                }

                // Kiểm tra từ bước sâu nhất ngược về bước đầu
                val lockToggle = findRowBoundsDynamic(
                    shizuku = shizuku,
                    resourceIds = emptyList(),
                    texts = listOf("Dùng Gemini mà không cần mở khoá", "Dùng Gemini mà không cần mở khóa", "Use Gemini without unlocking"),
                    minY = defaultMinY,
                )
                if (lockToggle != null) {
                    if (lastStage != 4) {
                        playOsNativeHighlight(appContext, lockToggle)
                        lastStage = 4
                    }
                    return@launch
                }

                val lockMenuRow = findRowBoundsDynamic(
                    shizuku = shizuku,
                    resourceIds = emptyList(),
                    texts = listOf("Gemini trên màn hình khoá", "Gemini trên màn hình khóa", "Gemini on lock screen"),
                    minY = defaultMinY,
                )
                if (lockMenuRow != null) {
                    if (lastStage != 3) {
                        playOsNativeHighlight(appContext, lockMenuRow)
                        lastStage = 3
                    }
                    delay(350)
                    continue
                }

                val settingsHeader = findRowBoundsDynamic(
                    shizuku = shizuku,
                    resourceIds = listOf("assistant_robin_settings_page"),
                    texts = listOf("Quản lý tài khoản", "Lựa chọn ưu tiên", "Điều khiển thiết bị", "Device control"),
                    minY = defaultMinY,
                )
                if (settingsHeader != null) {
                    if (lastStage != 2) {
                        removeOverlayNow()
                        scrollDown(appContext, shizuku)
                        delay(280)
                        val deviceControlRow = findRowBoundsDynamic(
                            shizuku = shizuku,
                            resourceIds = emptyList(),
                            texts = listOf("Điều khiển thiết bị", "Device control"),
                            minY = defaultMinY,
                        )
                        if (deviceControlRow != null) {
                            playOsNativeHighlight(appContext, deviceControlRow)
                            lastStage = 2
                        }
                    }
                    delay(350)
                    continue
                }

                val avatarMenu = findRowBoundsDynamic(
                    shizuku = shizuku,
                    resourceIds = listOf("og_bento_menu_content_container"),
                    texts = listOf("Quản lý Tài khoản Google", "Manage your Google Account"),
                    minY = defaultMinY,
                )
                if (avatarMenu != null) {
                    if (lastStage != 1) {
                        val settingsRow = findRowBoundsDynamic(
                            shizuku = shizuku,
                            resourceIds = emptyList(),
                            texts = listOf("Cài đặt", "Settings"),
                            minY = defaultMinY,
                        )
                        if (settingsRow != null) {
                            playOsNativeHighlight(appContext, settingsRow)
                            lastStage = 1
                        }
                    }
                    delay(350)
                    continue
                }

                if (lastStage == -1) {
                    val avatarBtn = findRowBoundsDynamic(
                        shizuku = shizuku,
                        resourceIds = listOf("og_apd_internal_image_view", "og_apd_ring_view"),
                        texts = listOf("Tài khoản Google", "Google Account", "Đã đăng nhập"),
                        minY = (dm.heightPixels * 0.04f).toInt(),
                        allowToolbarArea = true,
                    )
                    if (avatarBtn != null) {
                        playOsNativeHighlight(appContext, avatarBtn)
                    }
                    lastStage = 0
                }

                delay(350)
            }
        }
    }

    /**
     * Tự động cuộn (nếu cần) và nháy sáng kiểu mặc định của OS (`color_list_item_background_highlight`)
     * cho bất kỳ trang Cài đặt nào bằng cách tìm động dòng theo `resourceIds` / `texts` (không dùng tọa độ cứng).
     */
    fun guideDynamicSetting(
        context: Context,
        resourceIds: List<String> = emptyList(),
        texts: List<String> = emptyList(),
        scrollDownFirst: Boolean = false,
        minTopRatio: Float = 0.11f,
    ) {
        val appContext = context.applicationContext
        dismiss()
        activeJob = scope.launch {
            val shizuku = shell(appContext)
            ensureOverlayPermission(appContext, shizuku)

            delay(420)
            if (scrollDownFirst) {
                scrollDown(appContext, shizuku)
                delay(260)
            }

            val dm = appContext.resources.displayMetrics
            val minY = (dm.heightPixels * minTopRatio.coerceAtLeast(0.11f)).toInt()
            var target = findRowBoundsDynamic(
                shizuku = shizuku,
                resourceIds = resourceIds,
                texts = texts,
                minY = minY,
            )
            if (!scrollDownFirst && (target == null || target.rect.bottom > dm.heightPixels * 0.99f)) {
                // Nếu mục nằm khuất dưới màn hình, tự cuộn xuống rồi tìm lại
                scrollDown(appContext, shizuku)
                delay(260)
                target = findRowBoundsDynamic(
                    shizuku = shizuku,
                    resourceIds = resourceIds,
                    texts = texts,
                    minY = minY,
                ) ?: target
            }

            if (target != null) {
                playOsNativeHighlight(appContext, target)
            }
        }
    }

    private suspend fun ensureOverlayPermission(appContext: Context, shizuku: ShizukuShell) {
        if (!Settings.canDrawOverlays(appContext) && isShizukuReady()) {
            withContext(Dispatchers.IO) {
                shizuku.exec("appops set ${appContext.packageName} SYSTEM_ALERT_WINDOW allow")
            }
        }
    }

    private suspend fun scrollDown(appContext: Context, shizuku: ShizukuShell) {
        val dm = appContext.resources.displayMetrics
        val cx = dm.widthPixels / 2
        val startY = (dm.heightPixels * 0.77f).toInt()
        val endY = (dm.heightPixels * 0.18f).toInt()
        val scrolledViaShell = if (isShizukuReady()) {
            withContext(Dispatchers.IO) {
                shizuku.exec("input swipe $cx $startY $cx $endY 320").ok
            }
        } else {
            false
        }
        if (!scrolledViaShell) {
            scrollViaAccessibility()
        }
    }

    /**
     * Tìm động khung bao (`RowTarget`) của dòng cài đặt trên màn hình hiện tại thông qua Accessibility,
     * `dumpsys activity top` (siêu nhanh ~120ms) hoặc `uiautomator dump`, hoàn toàn không phụ thuộc tọa độ cứng.
     */
    private suspend fun findRowBoundsDynamic(
        shizuku: ShizukuShell,
        resourceIds: List<String>,
        texts: List<String>,
        minY: Int = 0,
        allowToolbarArea: Boolean = false,
        preferFastActivityDump: Boolean = false,
        scrolledToBottom: Boolean = false,
    ): RowTarget? {
        // 1. Thử tìm nhanh qua AccessibilityService nếu đang bật
        findRowViaAccessibility(resourceIds, texts, minY, allowToolbarArea)?.let { return it }

        if (!isShizukuReady()) return null

        // 2. Nếu có resource-id đặc trưng, thử đọc cây View từ `dumpsys activity top` (~120ms, nhanh gấp 18 lần uiautomator)
        if (preferFastActivityDump && resourceIds.isNotEmpty()) {
            val topDump = withContext(Dispatchers.IO) {
                shizuku.exec("dumpsys activity top").stdout
            }
            parseRowBoundsFromActivityTop(
                dump = topDump,
                resourceIds = resourceIds,
                minY = minY,
                allowToolbarArea = allowToolbarArea,
                scrolledToBottom = scrolledToBottom,
            )?.let { return it }
        }

        // 3. Tìm qua uiautomator dump từ Shizuku
        val xml = withContext(Dispatchers.IO) {
            shizuku.exec("uiautomator dump /sdcard/akb_guide_ui.xml >/dev/null 2>&1 && cat /sdcard/akb_guide_ui.xml && rm -f /sdcard/akb_guide_ui.xml").stdout
        }
        if (!xml.contains("<hierarchy")) return null
        return parseRowBoundsFromXml(xml, resourceIds, texts, minY, allowToolbarArea)
    }

    private data class HierarchyEntry(
        val indent: Int,
        val node: UiNode,
        val visible: Boolean,
        val isScrollView: Boolean,
        val height: Int,
    )

    private val VIEW_LINE_REGEX = Regex(
        """^(\s+)([\w.$]+)\{[0-9a-fA-F]+\s+([VIG]\S{8})\s+\S+\s+(-?\d+),(-?\d+)-(-?\d+),(-?\d+)(?:\s+#[0-9a-fA-F]+\s+([\w.:/]+))?.*""",
    )

    private fun parseRowBoundsFromActivityTop(
        dump: String,
        resourceIds: List<String>,
        minY: Int,
        allowToolbarArea: Boolean,
        scrolledToBottom: Boolean,
    ): RowTarget? = runCatching {
        val blocks = dump.split("View Hierarchy:")
        for (i in 1 until blocks.size) {
            val block = blocks[i]
            val lines = block.lineSequence().take(450).toList()
            val decorLine = lines.firstOrNull { it.contains("DecorView{") } ?: continue
            if (!decorLine.contains(" V.") ||
                decorLine.contains("SettingsSpotlightActivity") ||
                decorLine.contains("MainActivity")
            ) {
                continue
            }
            val nodes = ArrayList<UiNode>(128)
            val stack = ArrayDeque<HierarchyEntry>()
            for (line in lines) {
                if (line.contains("Looper (") || line.contains("Local FragmentActivity")) break
                val m = VIEW_LINE_REGEX.matchEntire(line) ?: continue
                val indent = m.groupValues[1].length
                val className = m.groupValues[2]
                val flags = m.groupValues[3]
                val l = m.groupValues[4].toIntOrNull() ?: continue
                val t = m.groupValues[5].toIntOrNull() ?: continue
                val r = m.groupValues[6].toIntOrNull() ?: continue
                val b = m.groupValues[7].toIntOrNull() ?: continue
                val resId = m.groupValues[8]

                while (stack.isNotEmpty() && stack.last().indent >= indent) {
                    stack.removeLast()
                }
                val parent = stack.lastOrNull()
                val visible = flags.startsWith("V") && (parent?.visible ?: true)
                val clickable = flags.length >= 7 && flags[6] == 'C'
                val width = r - l
                val height = b - t

                val scrollOffsetY = if (parent != null && parent.isScrollView && scrolledToBottom && height > parent.height) {
                    height - parent.height
                } else {
                    0
                }

                val screenLeft = (parent?.node?.bounds?.left ?: 0) + l
                val screenTop = (parent?.node?.bounds?.top ?: 0) + t - scrollOffsetY
                val rect = Rect(screenLeft, screenTop, screenLeft + width, screenTop + height)
                val uiNode = UiNode(
                    resourceId = resId,
                    text = "",
                    contentDesc = "",
                    clickable = clickable,
                    checkable = false,
                    checked = false,
                    bounds = rect,
                    parent = parent?.node,
                )
                if (visible && width > 0 && height > 0) {
                    nodes.add(uiNode)
                }
                val isScroll = className.contains("ScrollView", ignoreCase = true)
                stack.addLast(HierarchyEntry(indent, uiNode, visible, isScroll, height))
            }
            selectTargetFromNodes(nodes, resourceIds, emptyList(), minY, allowToolbarArea)?.let { return it }
        }
        null
    }.getOrNull()

    private fun findRowViaAccessibility(
        resourceIds: List<String>,
        texts: List<String>,
        minY: Int,
        allowToolbarArea: Boolean = false,
    ): RowTarget? {
        val service = AssistantAccessibilityService.activeService ?: return null
        val root = runCatching { service.rootInActiveWindow }.getOrNull() ?: return null
        val nodes = ArrayList<UiNode>(128)
        collectAccessibilityNodes(root, null, nodes)
        return selectTargetFromNodes(nodes, resourceIds, texts, minY, allowToolbarArea)
    }

    private fun collectAccessibilityNodes(
        node: AccessibilityNodeInfo?,
        parent: UiNode?,
        out: MutableList<UiNode>,
    ) {
        node ?: return
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val uiNode = UiNode(
            resourceId = node.viewIdResourceName.orEmpty(),
            text = normalizeText(node.text?.toString().orEmpty()),
            contentDesc = normalizeText(node.contentDescription?.toString().orEmpty()),
            clickable = node.isClickable,
            checkable = node.isCheckable,
            checked = node.isChecked,
            bounds = rect,
            parent = parent,
        )
        out.add(uiNode)
        for (i in 0 until node.childCount) {
            collectAccessibilityNodes(node.getChild(i), uiNode, out)
        }
    }

    private data class UiNode(
        val resourceId: String,
        val text: String,
        val contentDesc: String,
        val clickable: Boolean,
        val checkable: Boolean,
        val checked: Boolean,
        val bounds: Rect,
        val parent: UiNode?,
    )

    private fun parseRowBoundsFromXml(
        xml: String,
        resourceIds: List<String>,
        texts: List<String>,
        minY: Int,
        allowToolbarArea: Boolean = false,
    ): RowTarget? = runCatching {
        val nodes = ArrayList<UiNode>(128)
        val stack = ArrayDeque<UiNode>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "node") {
                val rid = parser.getAttributeValue(null, "resource-id").orEmpty()
                val rawText = parser.getAttributeValue(null, "text").orEmpty()
                val rawDesc = parser.getAttributeValue(null, "content-desc").orEmpty()
                val clickable = parser.getAttributeValue(null, "clickable") == "true"
                val checkable = parser.getAttributeValue(null, "checkable") == "true"
                val checked = parser.getAttributeValue(null, "checked") == "true"
                val boundsStr = parser.getAttributeValue(null, "bounds").orEmpty()
                val rect = parseBoundsString(boundsStr) ?: Rect()
                val node = UiNode(
                    resourceId = rid,
                    text = normalizeText(rawText),
                    contentDesc = normalizeText(rawDesc),
                    clickable = clickable,
                    checkable = checkable,
                    checked = checked,
                    bounds = rect,
                    parent = stack.lastOrNull(),
                )
                nodes.add(node)
                stack.addLast(node)
            } else if (event == XmlPullParser.END_TAG && parser.name == "node") {
                if (stack.isNotEmpty()) stack.removeLast()
            }
            event = parser.next()
        }
        selectTargetFromNodes(nodes, resourceIds, texts, minY, allowToolbarArea)
    }.getOrNull()

    private fun selectTargetFromNodes(
        nodes: List<UiNode>,
        resourceIds: List<String>,
        texts: List<String>,
        minY: Int,
        allowToolbarArea: Boolean,
    ): RowTarget? {
        val normTexts = texts.map { normalizeText(it) }.filter { it.isNotEmpty() }

        // 1. Ưu tiên khớp theo resource-id
        if (resourceIds.isNotEmpty()) {
            for (n in nodes) {
                if (!allowToolbarArea && isToolbarOrAncestor(n)) continue
                if (n.bounds.top >= minY &&
                    n.bounds.width() > 0 &&
                    resourceIds.any { id -> n.resourceId.contains(id, ignoreCase = true) }
                ) {
                    return buildRowTarget(nodes, climbToRowBounds(n))
                }
            }
        }

        // 2. Ưu tiên khớp chính xác toàn bộ text (exact match) theo thứ tự ưu tiên của texts
        for (t in normTexts) {
            for (n in nodes) {
                if (!allowToolbarArea && isToolbarOrAncestor(n)) continue
                if (n.bounds.top >= minY &&
                    n.bounds.width() > 0 &&
                    n.text == t
                ) {
                    return buildRowTarget(nodes, climbToRowBounds(n))
                }
            }
        }

        // 3. Khớp chuỗi con (substring match) theo thứ tự ưu tiên của texts
        for (t in normTexts) {
            for (n in nodes) {
                if (!allowToolbarArea && isToolbarOrAncestor(n)) continue
                if (n.bounds.top >= minY &&
                    n.bounds.width() > 0 &&
                    n.text.isNotEmpty() &&
                    n.text.contains(t)
                ) {
                    return buildRowTarget(nodes, climbToRowBounds(n))
                }
            }
        }

        // 4. Dự phòng khớp theo content-desc cho các dòng/nút không đặt text trực tiếp
        for (t in normTexts) {
            for (n in nodes) {
                if (!allowToolbarArea && isToolbarOrAncestor(n)) continue
                if (n.bounds.top >= minY &&
                    n.bounds.width() > 0 &&
                    n.bounds.height() in 56..450 &&
                    n.contentDesc.isNotEmpty() &&
                    n.contentDesc.contains(t)
                ) {
                    return buildRowTarget(nodes, climbToRowBounds(n))
                }
            }
        }

        return null
    }

    private fun isToolbarOrAncestor(node: UiNode): Boolean {
        var cur: UiNode? = node
        while (cur != null) {
            val rid = cur.resourceId
            if (rid.isNotEmpty() &&
                (rid.contains("vtoolbar", ignoreCase = true) ||
                    rid.contains("bbk_title", ignoreCase = true) ||
                    rid.contains("toolbar_base", ignoreCase = true) ||
                    rid.contains("navigation_view", ignoreCase = true) ||
                    rid.contains("title_view", ignoreCase = true) ||
                    rid.contains("og_bento_toolbar", ignoreCase = true))
            ) {
                return true
            }
            cur = cur.parent
        }
        return false
    }

    private fun climbToRowBounds(node: UiNode): Rect {
        var cur: UiNode? = node
        var bestRow: Rect? = null
        while (cur != null) {
            val b = cur.bounds
            if (b.width() >= 500 && b.height() in 65..450) {
                bestRow = b
                if (cur.clickable) break
            }
            cur = cur.parent
        }
        if (bestRow != null) return bestRow

        // Hỗ trợ nút dạng viên thuốc (pill button) như nút "Bắt đầu" / "Ghép nối" trong Shizuku hoặc nút icon
        cur = node
        while (cur != null) {
            val b = cur.bounds
            if (cur.clickable && b.width() >= 120 && b.height() in 56..320) {
                return b
            }
            cur = cur.parent
        }
        return node.bounds
    }

    private fun buildRowTarget(nodes: List<UiNode>, rect: Rect): RowTarget {
        var roundTop = true
        var roundBottom = true
        var isChecked = false

        for (n in nodes) {
            val b = n.bounds
            if ((n.checkable || n.checked) && n.checked && rect.contains(b)) {
                isChecked = true
            }
            if (rect.width() >= 500 && n.clickable && b != rect) {
                if (b.width() >= 500 && b.height() in 65..450 && abs(b.left - rect.left) <= 24) {
                    if (abs(b.bottom - rect.top) <= 8) roundTop = false
                    if (abs(b.top - rect.bottom) <= 8) roundBottom = false
                }
            }
        }

        return RowTarget(
            rect = RectF(rect),
            roundTop = roundTop,
            roundBottom = roundBottom,
            isChecked = isChecked,
        )
    }

    private fun parseBoundsString(bounds: String): Rect? {
        // Định dạng uiautomator: [left,top][right,bottom]
        val m = BOUNDS_REGEX.matchEntire(bounds) ?: return null
        val l = m.groupValues[1].toIntOrNull() ?: return null
        val t = m.groupValues[2].toIntOrNull() ?: return null
        val r = m.groupValues[3].toIntOrNull() ?: return null
        val b = m.groupValues[4].toIntOrNull() ?: return null
        return Rect(l, t, r, b)
    }

    private fun normalizeText(input: String): String =
        Normalizer.normalize(input, Normalizer.Form.NFC).lowercase().trim()

    private val BOUNDS_REGEX = Regex("""\[(\d+),(\d+)]\[(\d+),(\d+)]""")

    private fun scrollViaAccessibility(): Boolean {
        val service = AssistantAccessibilityService.activeService ?: return false
        val root = runCatching { service.rootInActiveWindow }.getOrNull() ?: return false
        val scrollable = findScrollableNode(root)
        var scrolled = false
        if (scrollable != null) {
            scrolled = scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }
        if (!scrolled) {
            scrolled = dispatchSwipeUp(service)
        }
        return scrolled
    }

    private fun dispatchSwipeUp(service: AccessibilityService): Boolean = runCatching {
        val dm = service.resources.displayMetrics
        val cx = dm.widthPixels / 2f
        val startY = dm.heightPixels * 0.77f
        val endY = dm.heightPixels * 0.18f
        val path = Path().apply {
            moveTo(cx, startY)
            lineTo(cx, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 240L))
            .build()
        service.dispatchGesture(gesture, null, null)
    }.getOrDefault(false)

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        node ?: return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val found = findScrollableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    /**
     * Phát hiệu ứng sáng dòng y hệt `com.vivo.permissionmanager.utils.l1.a` / `VRecyclerView` của Vivo OriginOS
     * (chỉ nháy nền sáng dịu ngay trên dòng cài đặt rồi tự tắt, không làm tối màn hình, không viền xanh).
     */
    private fun playOsNativeHighlight(context: Context, target: RowTarget) {
        removeOverlayNow()
        currentTarget = target

        val service = AssistantAccessibilityService.activeService
        if (service != null) {
            val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = VivoOsRowHighlightView(service, target) {
                removeOverlayNow()
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            if (runCatching {
                    wm.addView(view, lp)
                    activeOverlay = OverlayHolder(wm, view)
                    view.startAnimation()
                }.isSuccess
            ) {
                return
            }
        }

        val appContext = context.applicationContext
        runCatching {
            val intent = Intent(appContext, SettingsSpotlightActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            appContext.startActivity(intent)
        }
        scope.launch {
            delay(90)
            if (activeActivity == null && currentTarget === target && isShizukuReady()) {
                withContext(Dispatchers.IO) {
                    shell(appContext).exec(
                        "am start -n ${appContext.packageName}/${SettingsSpotlightActivity::class.java.name} --activity-no-animation >/dev/null 2>&1",
                    )
                }
            }
        }
    }
}

/**
 * Activity trong suốt hiển thị hiệu ứng sáng dòng mặc định của Vivo OriginOS (`VivoOsRowHighlightView`)
 * rồi tự đóng; nếu người dùng chạm ngay trong lúc dòng đang sáng thì chuyển tiếp thao tác chạm xuống màn hình dưới.
 */
class SettingsSpotlightActivity : Activity() {

    private var highlightView: VivoOsRowHighlightView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        SettingsGuideHelper.activeActivity = this
        bindTarget()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        overridePendingTransition(0, 0)
        SettingsGuideHelper.activeActivity = this
        bindTarget()
    }

    private fun bindTarget() {
        highlightView?.stopAnimation()
        val target = SettingsGuideHelper.currentTarget
        if (target == null) {
            dismissSilently()
            return
        }
        val view = VivoOsRowHighlightView(this, target) {
            dismissSilently()
        }
        highlightView = view
        setContentView(view)
        view.startAnimation()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.actionMasked == MotionEvent.ACTION_DOWN) {
            val x = ev.rawX.toInt()
            val y = ev.rawY.toInt()
            dismissSilently()
            SettingsGuideHelper.forwardTouchTap(applicationContext, x, y)
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    fun dismissSilently() {
        runCatching { highlightView?.stopAnimation() }
        if (!isFinishing) {
            finish()
            overridePendingTransition(0, 0)
        }
    }

    override fun onDestroy() {
        runCatching { highlightView?.stopAnimation() }
        if (SettingsGuideHelper.activeActivity === this) {
            SettingsGuideHelper.activeActivity = null
        }
        super.onDestroy()
    }
}

/**
 * Tái tạo chính xác hiệu ứng highlight dòng mặc định của Vivo OriginOS (`com.vivo.permissionmanager.utils.l1` / `VRecyclerView`):
 * - Màu nền chuẩn `originui_vrecyclerview_item_high_light_background_rom15_0` (`#0F77FF` ở nền sáng, `#4E8CDF` ở nền tối)
 * - Độ đậm cực đại: alpha `42` (~16%, tương đương hằng số `0x42190000` = `38.25f` của Vivo OriginOS)
 * - Nội suy: `PathInterpolator(0.25f, 0.1f, 0.25f, 0.1f)` (350ms sáng lên) -> `PathInterpolator(0.33f, 0f, 0.67f, 1f)` (350ms dịu xuống)
 * - Bo góc theo đúng vị trí dòng trong thẻ nhóm (`VRoundedCornerDrawable`: bo 18dp ở mép ngoài của thẻ, phẳng 0dp ở mép tiếp giáp dòng liền kề).
 */
internal class VivoOsRowHighlightView(
    context: Context,
    private val target: SettingsGuideHelper.RowTarget,
    private val onFinished: () -> Unit,
) : View(context) {

    private val density = resources.displayMetrics.density
    private val screenWidth = resources.displayMetrics.widthPixels
    private val locOnScreen = IntArray(2)
    private val baseRgb: Int = resolveVivoHighlightBaseRgb(context)
    private val maxAlpha: Int = 54 // Chuẩn OriginOS (originui_vrecyclerview_item_high_light_background_rom15_0)

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(0, Color.red(baseRgb), Color.green(baseRgb), Color.blue(baseRgb))
    }

    private val rowPath = Path()
    private var animatorSet: AnimatorSet? = null

    private fun resolveVivoHighlightBaseRgb(ctx: Context): Int {
        val secureNight = runCatching {
            Settings.Secure.getInt(ctx.contentResolver, "ui_night_mode", -1) == 2
        }.getOrDefault(false)
        val configNight = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val isNightMode = secureNight || configNight
        return if (isNightMode) 0x004E8CDF else 0x000F77FF
    }

    private fun buildPulseAnimator(): AnimatorSet {
        val inInterp = PathInterpolator(0.25f, 0.1f, 0.25f, 0.1f)
        val outInterp = PathInterpolator(0.33f, 0f, 0.67f, 1f)
        val update = ValueAnimator.AnimatorUpdateListener { anim ->
            val a = (anim.animatedValue as Int).coerceIn(0, 255)
            highlightPaint.color = Color.argb(a, Color.red(baseRgb), Color.green(baseRgb), Color.blue(baseRgb))
            postInvalidateOnAnimation()
        }

        val fadeIn1 = ValueAnimator.ofInt(0, maxAlpha).apply {
            duration = 350L
            interpolator = inInterp
            addUpdateListener(update)
        }
        val fadeOut1 = ValueAnimator.ofInt(maxAlpha, 0).apply {
            duration = 350L
            interpolator = outInterp
            addUpdateListener(update)
        }
        val fadeIn2 = ValueAnimator.ofInt(0, maxAlpha).apply {
            duration = 350L
            interpolator = inInterp
            addUpdateListener(update)
        }
        val fadeOut2 = ValueAnimator.ofInt(maxAlpha, 0).apply {
            duration = 420L
            interpolator = outInterp
            addUpdateListener(update)
        }

        return AnimatorSet().apply {
            playSequentially(fadeIn1, fadeOut1, fadeIn2, fadeOut2)
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        onFinished()
                    }
                },
            )
        }
    }

    fun startAnimation() {
        stopAnimation()
        animatorSet = buildPulseAnimator().also { it.start() }
    }

    fun stopAnimation() {
        animatorSet?.removeAllListeners()
        animatorSet?.cancel()
        animatorSet = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rect = target.rect
        if (rect.isEmpty) return

        getLocationOnScreen(locOnScreen)
        canvas.save()
        canvas.translate(-locOnScreen[0].toFloat(), -locOnScreen[1].toFloat())

        rowPath.reset()
        if (rect.width() < screenWidth * 0.5f) {
            // Nút dạng viên thuốc (pill button) hoặc nút icon tròn
            val pillRadius = min(rect.width(), rect.height()) / 2f
            rowPath.addRoundRect(rect, pillRadius, pillRadius, Path.Direction.CW)
        } else {
            val topR = if (target.roundTop) 18f * density else 0f
            val botR = if (target.roundBottom) 18f * density else 0f
            val radii = floatArrayOf(topR, topR, topR, topR, botR, botR, botR, botR)
            rowPath.addRoundRect(rect, radii, Path.Direction.CW)
        }
        canvas.drawPath(rowPath, highlightPaint)

        canvas.restore()
    }
}
