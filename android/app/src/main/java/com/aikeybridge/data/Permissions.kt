package com.aikeybridge.data

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import com.aikeybridge.service.AssistantAccessibilityService
import com.aikeybridge.shell.BuiltInWirelessAdb
import com.aikeybridge.shell.EngineBinderProvider
import rikka.shizuku.Shizuku

data class VivoAppPerms(
    val autostart: Boolean? = null,
    val floating: Boolean? = null,
    val lockScreen: Boolean? = null,
    val bgPopup: Boolean? = null,
) {
    val readable: Boolean get() = autostart != null || lockScreen != null || bgPopup != null
    val googleAllGranted: Boolean?
        get() = when {
            autostart != null && floating != null && lockScreen != null ->
                autostart && floating && lockScreen
            floating == false -> false
            else -> null
        }
    val geminiAllGranted: Boolean?
        get() = when {
            autostart != null && floating != null && lockScreen != null && bgPopup != null ->
                autostart && floating && lockScreen && bgPopup
            floating == false -> false
            else -> null
        }

    fun missingGoogleSummary(): String? {
        val missing = buildList {
            if (autostart == false) add("Tự động khởi động")
            if (floating == false) add("Cửa sổ nổi")
            if (lockScreen == false) add("Màn hình khóa")
        }
        return if (missing.isEmpty()) null else "Đang tắt: ${missing.joinToString(", ")}"
    }

    fun missingGeminiSummary(): String? {
        val missing = buildList {
            if (autostart == false) add("Tự động khởi động")
            if (floating == false) add("Cửa sổ nổi")
            if (lockScreen == false) add("Màn hình khóa")
            if (bgPopup == false) add("Cửa sổ bật lên dưới nền")
        }
        return if (missing.isEmpty()) null else "Đang tắt: ${missing.joinToString(", ")}"
    }

    /**
     * Mã perId của Vivo SoftPermissionDetailActivity để tự cuộn & nháy sáng đúng công tắc đang thiếu:
     * 10002 = Tự động khởi động, 10003 = Khởi động liên kết, 10004 = Cửa sổ nổi,
     * 10006 = Hiển thị trên màn hình khóa, 10007 = Cửa sổ bật lên dưới nền.
     */
    fun firstMissingGooglePerId(): Int = when {
        autostart == false -> 10002
        floating == false -> 10004
        lockScreen == false -> 10006
        else -> 10004
    }

    fun firstMissingGeminiPerId(): Int = when {
        autostart == false -> 10002
        floating == false -> 10004
        lockScreen == false -> 10006
        bgPopup == false -> 10007
        else -> 10004
    }
}

data class PermissionSnapshot(
    val accessibility: Boolean = false,
    val assistantDefault: Boolean = false,
    val voiceInteraction: Boolean = false,
    val screenContextEnabled: Boolean = true,
    val batteryUnrestricted: Boolean = false,
    val googleBatteryUnrestricted: Boolean = false,
    val geminiBatteryUnrestricted: Boolean = false,
    val googleVivoDetail: VivoAppPerms = VivoAppPerms(),
    val geminiVivoDetail: VivoAppPerms = VivoAppPerms(),
    val googleVivoGranted: Boolean = false,
    val geminiVivoGranted: Boolean = false,
    val autostartConfirmed: Boolean = false,
    val googleInstalled: Boolean = false,
    val geminiInstalled: Boolean = false,
    val shizukuInstalled: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuGranted: Boolean = false,
    val shizukuAppInstalled: Boolean = false,
    val shizukuBinderAlive: Boolean = false,
    val shizukuPermissionGranted: Boolean = false,
    val engineDaemonRunning: Boolean = false,
    val targetInstalled: Boolean = false,
    val assistantRequired: Boolean = true,
    val nativeAssistantInstalled: Boolean = false,
    val readLogs: Boolean = false,
    val devOptionsEnabled: Boolean = false,
    val adbEnabled: Boolean = false,
    val adbWifiEnabled: Boolean = false,
    val wirelessAdbPaired: Boolean = false,
) {
    val assistantOk: Boolean get() = assistantDefault || !assistantRequired
    val bothBatteryUnrestricted: Boolean get() = googleBatteryUnrestricted && geminiBatteryUnrestricted
    val coreItems: List<Boolean>
        get() = listOf(
            googleVivoGranted && geminiVivoGranted,
            bothBatteryUnrestricted,
            assistantDefault && screenContextEnabled,
            googleInstalled && targetInstalled,
        )
    val coreGrantedCount: Int get() = coreItems.count { it }
    val coreTotalCount: Int get() = coreItems.size
    val ready: Boolean get() = coreGrantedCount == coreTotalCount
}

object PermissionsReader {

    @Volatile
    private var cachedGoogleVivo: VivoAppPerms? = null

    @Volatile
    private var cachedGeminiVivo: VivoAppPerms? = null

    const val VIVO_QUERY_CMD: String =
        "content query --uri content://com.vivo.permissionmanager.provider.permission/bg_start_up_apps --projection pkgname:currentstate 2>/dev/null | grep -E 'googlequicksearchbox|apps\\.bard' | sed 's/^/AUTO:/'; " +
            "content query --uri content://com.vivo.permissionmanager.provider.permission/float_window_apps --projection pkgname:currentmode 2>/dev/null | grep -E 'googlequicksearchbox|apps\\.bard' | sed 's/^/FLOAT:/'; " +
            "content query --uri content://com.vivo.permissionmanager.provider.permission/control_locked_screen_action --projection pkgname:currentstate 2>/dev/null | grep -E 'googlequicksearchbox|apps\\.bard' | sed 's/^/LOCK:/'; " +
            "content query --uri content://com.vivo.permissionmanager.provider.permission/start_bg_activity --projection pkgname:currentstate 2>/dev/null | grep -E 'googlequicksearchbox|apps\\.bard' | sed 's/^/BG:/'"

    fun updateFromVivoShell(context: Context, output: String): Boolean {
        if (output.isBlank()) return false
        var gAuto: Boolean? = null
        var gFloat: Boolean? = null
        var gLock: Boolean? = null
        var bAuto: Boolean? = null
        var bFloat: Boolean? = null
        var bLock: Boolean? = null
        var bBg: Boolean? = null

        output.lineSequence().forEach { line ->
            val isGoogle = line.contains(GOOGLE_PACKAGE)
            val isGemini = line.contains(GEMINI_PACKAGE)
            val granted = line.contains("currentstate=0") || line.contains("currentmode=0")
            when {
                line.startsWith("AUTO:") -> {
                    if (isGoogle) gAuto = granted
                    if (isGemini) bAuto = granted
                }
                line.startsWith("FLOAT:") -> {
                    if (isGoogle) gFloat = granted
                    if (isGemini) bFloat = granted
                }
                line.startsWith("LOCK:") -> {
                    if (isGoogle) gLock = granted
                    if (isGemini) bLock = granted
                }
                line.startsWith("BG:") -> {
                    if (isGemini) bBg = granted
                }
            }
        }
        val newG = VivoAppPerms(autostart = gAuto, floating = gFloat, lockScreen = gLock, bgPopup = null)
        val newB = VivoAppPerms(autostart = bAuto, floating = bFloat, lockScreen = bLock, bgPopup = bBg)
        val changed = newG != cachedGoogleVivo || newB != cachedGeminiVivo
        if (newG.readable) cachedGoogleVivo = newG
        if (newB.readable) cachedGeminiVivo = newB
        if (newG.readable || newB.readable) {
            saveVivoCache(context, newG, newB)
        }
        return changed
    }

    private fun loadVivoCache(context: Context) {
        if (cachedGoogleVivo != null || cachedGeminiVivo != null) return
        val prefs = context.getSharedPreferences("vivo_perm_cache", Context.MODE_PRIVATE)
        if (!prefs.contains("g_auto")) return
        fun getBoolOrNull(key: String): Boolean? =
            if (prefs.contains(key)) prefs.getBoolean(key, false) else null
        cachedGoogleVivo = VivoAppPerms(
            autostart = getBoolOrNull("g_auto"),
            floating = getBoolOrNull("g_float"),
            lockScreen = getBoolOrNull("g_lock"),
        )
        cachedGeminiVivo = VivoAppPerms(
            autostart = getBoolOrNull("b_auto"),
            floating = getBoolOrNull("b_float"),
            lockScreen = getBoolOrNull("b_lock"),
            bgPopup = getBoolOrNull("b_bg"),
        )
    }

    private fun saveVivoCache(context: Context, g: VivoAppPerms, b: VivoAppPerms) {
        val prefs = context.getSharedPreferences("vivo_perm_cache", Context.MODE_PRIVATE)
        prefs.edit().apply {
            g.autostart?.let { putBoolean("g_auto", it) }
            g.floating?.let { putBoolean("g_float", it) }
            g.lockScreen?.let { putBoolean("g_lock", it) }
            b.autostart?.let { putBoolean("b_auto", it) }
            b.floating?.let { putBoolean("b_float", it) }
            b.lockScreen?.let { putBoolean("b_lock", it) }
            b.bgPopup?.let { putBoolean("b_bg", it) }
            apply()
        }
    }

    fun read(
        context: Context,
        settings: AppSettings,
        targetPackages: List<String>,
        assistantRequired: Boolean,
        nativeAssistantPackages: List<String>,
    ): PermissionSnapshot {
        val resolver = context.contentResolver
        val voice = Settings.Secure.getString(resolver, "voice_interaction_service").orEmpty()
        val structure = Settings.Secure.getString(resolver, "assist_structure_enabled")
        val screenshot = Settings.Secure.getString(resolver, "assist_screenshot_enabled")
        val screenContext = structure != "0" && screenshot != "0"

        val googleBatt = batteryUnrestrictedFor(context, GOOGLE_PACKAGE)
        val geminiBatt = batteryUnrestrictedFor(context, GEMINI_PACKAGE)
        val selfBatt = batteryUnrestrictedFor(context, context.packageName)

        loadVivoCache(context)
        val googleOverlay = overlayAllowedFor(context, GOOGLE_PACKAGE)
        val geminiOverlay = overlayAllowedFor(context, GEMINI_PACKAGE)

        val directG = queryVivoDetail(context, GOOGLE_PACKAGE, includeBgPopup = false)
        val directB = queryVivoDetail(context, GEMINI_PACKAGE, includeBgPopup = true)

        val baseG = if (directG.readable) directG else (cachedGoogleVivo ?: VivoAppPerms())
        val baseB = if (directB.readable) directB else (cachedGeminiVivo ?: VivoAppPerms())

        // FloatWindowUtil.b của Vivo dùng AppOps OP_SYSTEM_ALERT_WINDOW == MODE_ALLOWED làm nguồn chuẩn
        // và tự đồng bộ sang bảng float_window_apps mỗi khi mở SoftPermissionDetailActivity.
        val googleVivoDetail = baseG.copy(floating = googleOverlay ?: baseG.floating)
        val geminiVivoDetail = baseB.copy(floating = geminiOverlay ?: baseB.floating)

        // Nếu đọc được từ Vivo (hoặc AppOps báo Cửa sổ nổi đang tắt), lấy trạng thái thật;
        // nếu không có Shizuku thì kết hợp AppOps SYSTEM_ALERT_WINDOW với manualAutostart
        val googleVivoOk = googleVivoDetail.googleAllGranted
            ?: (settings.manualAutostart || googleOverlay == true)
        val geminiVivoOk = geminiVivoDetail.geminiAllGranted
            ?: (settings.manualAutostart || geminiOverlay == true)
        val autostartOk = googleVivoOk && geminiVivoOk

        val devOpts = runCatching {
            Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        }.getOrDefault(false)
        val adb = runCatching {
            Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED, 0) == 1
        }.getOrDefault(false)
        val adbWifi = runCatching {
            Settings.Global.getInt(resolver, "adb_wifi_enabled", 0) == 1
        }.getOrDefault(false)

        val engineAlive = EngineBinderProvider.isAlive(context)
        if (!engineAlive) {
            EngineBinderProvider.requestDaemonReconnect(context)
        }
        val rawShizukuInstalled = isInstalled(context, SHIZUKU_PACKAGE)
        val rawShizukuRunning = shizukuRunning()
        val rawShizukuGranted = shizukuGranted()

        val effectiveGranted = when (settings.shellBackend) {
            ShellBackend.SHIZUKU -> rawShizukuGranted
            ShellBackend.BUILTIN -> engineAlive
            ShellBackend.AUTO -> rawShizukuGranted || engineAlive
        }
        val effectiveRunning = when (settings.shellBackend) {
            ShellBackend.SHIZUKU -> rawShizukuRunning
            ShellBackend.BUILTIN -> engineAlive
            ShellBackend.AUTO -> rawShizukuRunning || engineAlive
        }
        val effectiveInstalled = when (settings.shellBackend) {
            ShellBackend.SHIZUKU -> rawShizukuInstalled
            ShellBackend.BUILTIN -> true
            ShellBackend.AUTO -> engineAlive || rawShizukuInstalled
        }

        return PermissionSnapshot(
            accessibility = accessibilityEnabled(context),
            assistantDefault = googleAssistantDefault(context),
            voiceInteraction = voice.startsWith(GOOGLE_PACKAGE),
            screenContextEnabled = screenContext,
            batteryUnrestricted = selfBatt || (googleBatt && geminiBatt),
            googleBatteryUnrestricted = googleBatt,
            geminiBatteryUnrestricted = geminiBatt,
            googleVivoDetail = googleVivoDetail,
            geminiVivoDetail = geminiVivoDetail,
            googleVivoGranted = googleVivoOk,
            geminiVivoGranted = geminiVivoOk,
            autostartConfirmed = autostartOk,
            googleInstalled = isInstalled(context, GOOGLE_PACKAGE),
            geminiInstalled = isInstalled(context, GEMINI_PACKAGE),
            shizukuInstalled = effectiveInstalled,
            shizukuRunning = effectiveRunning,
            shizukuGranted = effectiveGranted,
            shizukuAppInstalled = rawShizukuInstalled,
            shizukuBinderAlive = rawShizukuRunning,
            shizukuPermissionGranted = rawShizukuGranted,
            engineDaemonRunning = engineAlive,
            targetInstalled = targetPackages.any { isInstalled(context, it) },
            assistantRequired = assistantRequired,
            nativeAssistantInstalled = nativeAssistantPackages.any { isInstalled(context, it) },
            readLogs = context.checkSelfPermission(android.Manifest.permission.READ_LOGS) ==
                PackageManager.PERMISSION_GRANTED,
            devOptionsEnabled = devOpts,
            adbEnabled = adb,
            adbWifiEnabled = adbWifi,
            wirelessAdbPaired = BuiltInWirelessAdb.isPaired(context),
        )
    }

    fun googleAssistantDefault(context: Context): Boolean {
        val resolver = context.contentResolver
        val assistant = Settings.Secure.getString(resolver, "assistant").orEmpty()
        val voice = Settings.Secure.getString(resolver, "voice_interaction_service").orEmpty()
        return assistant.startsWith(GOOGLE_PACKAGE) || voice.startsWith(GOOGLE_PACKAGE)
    }

    fun accessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1
        if (!enabled) return false
        val component = ComponentName(context, AssistantAccessibilityService::class.java)
        val flat = component.flattenToString()
        val short = component.flattenToShortString()
        val services = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return services.split(':').any { it.equals(flat, true) || it.equals(short, true) }
    }

    private fun batteryUnrestrictedFor(context: Context, packageName: String): Boolean = runCatching {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        power.isIgnoringBatteryOptimizations(packageName)
    }.getOrDefault(false)

    private fun overlayAllowedFor(context: Context, packageName: String): Boolean? = runCatching {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return null
        val uid = context.packageManager.getApplicationInfo(packageName, 0).uid
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, packageName) ==
            AppOpsManager.MODE_ALLOWED
    }.getOrNull()

    private fun queryVivoDetail(context: Context, pkg: String, includeBgPopup: Boolean): VivoAppPerms = runCatching {
        val auto = queryVivoTable(context, "bg_start_up_apps", "currentstate", pkg)?.let { it == 0 }
        val float = queryVivoTable(context, "float_window_apps", "currentmode", pkg)?.let { it == 0 }
        val lock = queryVivoTable(context, "control_locked_screen_action", "currentstate", pkg)?.let { it == 0 }
        val bg = if (includeBgPopup) {
            queryVivoTable(context, "start_bg_activity", "currentstate", pkg)?.let { it == 0 }
        } else {
            null
        }
        VivoAppPerms(autostart = auto, floating = float, lockScreen = lock, bgPopup = bg)
    }.getOrDefault(VivoAppPerms())

    private fun queryVivoTable(context: Context, table: String, col: String, pkg: String): Int? {
        val uri = Uri.parse("content://com.vivo.permissionmanager.provider.permission/$table")
        context.contentResolver.query(uri, arrayOf(col), "pkgname=?", arrayOf(pkg), null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(col)
                if (idx >= 0) return cursor.getInt(idx)
            }
        }
        return null
    }

    private fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun shizukuRunning(): Boolean = try {
        !Shizuku.isPreV11() && Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    private fun shizukuGranted(): Boolean = try {
        shizukuRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"
    const val GEMINI_PACKAGE = "com.google.android.apps.bard"
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
}
