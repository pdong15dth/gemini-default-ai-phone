package com.aikeybridge.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.aikeybridge.service.SettingsGuideHelper

object Intents {

    fun openAccessibility(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(SettingsGuideHelper.EXTRA_FRAGMENT_ARG_KEY, " downloaded_services")
        if (start(context, intent)) {
            SettingsGuideHelper.guideDynamicSetting(
                context = context,
                texts = listOf(
                    "Các dịch vụ đã tải xuống",
                    "Ứng dụng đã cài đặt",
                    "AI Key Bridge",
                    "Downloaded services",
                    "Installed apps",
                ),
            )
        }
    }

    /** Mở Tuỳ chọn nhà phát triển để người dùng tắt Gỡ lỗi không dây / Developer Mode trước khi vào app ngân hàng. */
    fun openDeveloperSettings(context: Context) {
        val devIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .putExtra(SettingsGuideHelper.EXTRA_FRAGMENT_ARG_KEY, "enable_development")
        if (start(context, devIntent)) {
            SettingsGuideHelper.guideDynamicSetting(
                context = context,
                texts = listOf("Tùy chọn nhà phát triển", "Developer options"),
            )
        } else {
            start(context, Intent(Settings.ACTION_SETTINGS))
        }
    }

    /** Mở thẳng tới mục Gỡ lỗi không dây trong Tuỳ chọn nhà phát triển để ghép nối mã 6 số. */
    fun openWirelessDebuggingSettings(context: Context) {
        val devIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .putExtra(SettingsGuideHelper.EXTRA_FRAGMENT_ARG_KEY, "toggle_adb_wireless")
        if (start(context, devIntent)) {
            SettingsGuideHelper.guideDynamicSetting(
                context = context,
                texts = listOf("Gỡ lỗi không dây", "Ghép nối thiết bị bằng mã ghép nối", "Wireless debugging"),
                scrollDownFirst = true,
            )
        } else {
            start(context, Intent(Settings.ACTION_SETTINGS))
        }
    }

    fun openAppDetails(context: Context, packageName: String = context.packageName) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName"),
        ).putExtra(SettingsGuideHelper.EXTRA_FRAGMENT_ARG_KEY, "permission_settings")
        if (start(context, intent)) {
            SettingsGuideHelper.guideDynamicSetting(
                context = context,
                texts = listOf("Quyền", "Permissions"),
            )
        }
    }

    /**
     * Mở thẳng màn hình Quyền quản lý thiết bị của một ứng dụng cụ thể (Google, Gemini, AI Key Bridge):
     * - Vivo OriginOS: SoftPermissionDetailActivity kèm extra `perId` (10002=Tự khởi động, 10003=Khởi động liên kết,
     *   10004=Cửa sổ nổi, 10006=Màn hình khóa, 10007=Bật lên dưới nền) để tự động cuộn xuống cuối trang và
     *   nháy sáng đúng công tắc đang cần bật!
     * - Xiaomi HyperOS / MIUI: PermissionsEditorActivity (Mở cửa sổ mới khi chạy trong nền, Hiển thị trên màn hình khóa).
     * - Hãng khác: mở Thông tin ứng dụng của gói đó.
     */
    fun openAppPermissions(context: Context, packageName: String, perId: Int = 10004) {
        val vivoIntent = Intent("permission.intent.action.softPermissionDetail")
            .putExtra("packagename", packageName)
            .putExtra("perId", perId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (start(context, vivoIntent)) return

        val miuiEditors = listOf(
            "com.miui.permcenter.permissions.PermissionsEditorActivity",
            "com.miui.permcenter.permissions.AppPermissionsEditorActivity",
        )
        for (cls in miuiEditors) {
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName("com.miui.securitycenter", cls)
                .putExtra("extra_pkgname", packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            if (start(context, intent)) {
                SettingsGuideHelper.guideDynamicSetting(
                    context = context,
                    texts = listOf(
                        "Hiển thị cửa sổ pop-up khi chạy trong nền",
                        "Hiển thị trên màn hình khóa",
                        "Cửa sổ nổi",
                    ),
                )
                return
            }
        }

        openAppDetails(context, packageName)
    }

    /**
     * Mở thẳng màn Kiểm soát pin dưới nền:
     * - Vivo OriginOS (khi truyền `packageName`): nhảy thẳng vào trang "Chi tiết mức sử dụng pin của ứng dụng"
     *   của đúng app đó (Google hoặc Gemini) qua `PowerUsageSummaryActivity` (không phải tìm trong danh sách 200 app).
     * - Vivo OriginOS (không truyền `packageName`): mở danh sách Kiểm soát pin dưới nền.
     * - Hãng khác: mở danh sách tối ưu hóa pin hoặc Thông tin ứng dụng.
     */
    fun openBackgroundBatterySettings(context: Context, packageName: String? = null) {
        if (packageName != null) {
            val vivoAppBattery = Intent()
                .setComponent(
                    ComponentName(
                        "com.iqoo.powersaving",
                        "com.iqoo.powersaving.fuelgauge.PowerUsageSummaryActivity",
                    ),
                )
                .putExtra("package_name", packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            if (start(context, vivoAppBattery)) {
                SettingsGuideHelper.guideBatterySettings(context, packageName)
                return
            }
        }

        val vivoBattery = Intent("com.iqoo.powersaving.battery.high.power.jump")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (start(context, vivoBattery)) {
            SettingsGuideHelper.guideDynamicSetting(
                context = context,
                texts = listOf("Google", "Gemini", "AI Key Bridge"),
            )
            return
        }

        if (packageName != null) {
            openAppDetails(context, packageName)
            return
        }
        if (!start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
            openAppDetails(context)
        }
    }

    /**
     * Mở thẳng màn hình Đánh thức bằng giọng nói ("Hi Jovi" / Tiểu V) trên Vivo OriginOS.
     */
    fun openVoiceWakeSettings(context: Context) {
        val candidates = listOf(
            Intent("vivo.intent.action.wakeup.WAKEUP_SETTINGS")
                .putExtra(SettingsGuideHelper.EXTRA_FRAGMENT_ARG_KEY, "voice_wakeup"),
            Intent(Intent.ACTION_VIEW, Uri.parse("voicewakeup://settings"))
                .putExtra(SettingsGuideHelper.EXTRA_FRAGMENT_ARG_KEY, "voice_wakeup"),
            Intent().setComponent(
                ComponentName("com.vivo.voicewakeup", "com.vivo.voicewakeup.activities.WakeupSettingsActivity"),
            ).putExtra(SettingsGuideHelper.EXTRA_FRAGMENT_ARG_KEY, "voice_wakeup"),
            Intent("com.vivo.settings.action.JOVI_SETTINGS")
                .putExtra(SettingsGuideHelper.EXTRA_FRAGMENT_ARG_KEY, "voice_wakeup"),
        )
        for (intent in candidates) {
            if (start(context, intent)) {
                SettingsGuideHelper.guideDynamicSetting(
                    context = context,
                    texts = listOf("Đánh thức bằng giọng nói", "Ghi lại cụm từ đánh thức", "Voice wake"),
                )
                return
            }
        }
        start(context, Intent(Settings.ACTION_SETTINGS))
    }

    fun openAssistantSettings(context: Context, assistantAlreadySelected: Boolean = false) {
        val roleIntent = Intent("android.intent.action.MANAGE_DEFAULT_APP")
            .putExtra("android.intent.extra.ROLE_NAME", "android.app.role.ASSISTANT")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (start(context, roleIntent)) {
            SettingsGuideHelper.guideAssistantSettings(context, assistantAlreadySelected)
            return
        }

        val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        if (start(context, intent)) {
            SettingsGuideHelper.guideAssistantSettings(context, assistantAlreadySelected)
        } else if (start(context, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) {
            SettingsGuideHelper.guideDynamicSetting(
                context = context,
                texts = listOf("Ứng dụng trợ lý kỹ thuật số", "Trợ lý kỹ thuật số", "Digital assistant app"),
            )
        }
    }

    fun requestBatteryOptimization(context: Context) {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
        if (!start(context, direct)) {
            start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    fun openAutostart(context: Context) {
        val components = listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        )
        for (component in components) {
            if (start(context, Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
                SettingsGuideHelper.guideDynamicSetting(
                    context = context,
                    texts = listOf("AI Key Bridge", "Google", "Gemini"),
                )
                return
            }
        }
        openAppDetails(context)
    }

    /** MIUI / HyperOS: mở màn "Quyền khác" của AI Key Bridge (Mở cửa sổ mới khi chạy trong nền, Hiển thị trên màn hình khóa). */
    fun openMiuiOtherPermissions(context: Context) {
        openAppPermissions(context, context.packageName)
    }

    /** Vivo: Cài đặt → Nút thông minh (nhấn / nhấn đúp / nhấn giữ). */
    fun openSmartKey(context: Context): Boolean {
        val smartKeyIntent = Intent("com.vivo.settings.acticon.JOVI_KEY")
            .putExtra(SettingsGuideHelper.EXTRA_FRAGMENT_ARG_KEY, "jovi_key_long_press_voice_assist")
        if (start(context, smartKeyIntent)) {
            SettingsGuideHelper.guideDynamicSetting(
                context = context,
                texts = listOf("Trợ lý giọng nói", "Voice assistant"),
                scrollDownFirst = true,
            )
            return true
        }
        return start(context, Intent(Settings.ACTION_SETTINGS))
    }

    /** Vivo: Chạm mặt lưng không mở thẳng được (activity không export) → mở Cài đặt và dẫn tới Lối tắt và trợ năng. */
    fun openBackTap(context: Context): Boolean {
        val settingsIntent = Intent(Settings.ACTION_SETTINGS)
            .putExtra(SettingsGuideHelper.EXTRA_FRAGMENT_ARG_KEY, "shortcut_accessibility")
        if (start(context, settingsIntent)) {
            SettingsGuideHelper.guideDynamicSetting(
                context = context,
                texts = listOf("Lối tắt và trợ năng", "Shortcuts & accessibility"),
                scrollDownFirst = true,
            )
            return true
        }
        return false
    }

    /** Vivo: Khởi chạy nhanh (giữ Giảm âm lượng khi tắt màn hình). */
    fun openQuickLaunch(context: Context): Boolean {
        if (start(context, Intent("com.vivo.SmartKey.MainActivity"))) {
            SettingsGuideHelper.guideDynamicSetting(
                context = context,
                texts = listOf("Khởi chạy nhanh", "Mở ứng dụng", "Gọi AI", "Quick action"),
            )
            return true
        }
        return openBackTap(context)
    }

    fun isLaunchAliasEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(launchAlias(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    /** Bật/tắt icon "Gọi AI" trong danh sách app để ROM liệt kê được khi chọn "mở ứng dụng". */
    fun setLaunchAlias(context: Context, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            launchAlias(context),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun launchAlias(context: Context) = ComponentName(context.packageName, "com.aikeybridge.LaunchAlias")

    fun openShizuku(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (launch != null && start(context, launch)) {
            SettingsGuideHelper.guideDynamicSetting(
                context = context,
                texts = listOf("Bắt đầu", "Start", "Ghép nối"),
            )
            return
        }
        openUrl(context, "https://shizuku.rikka.app/download/")
    }

    fun openPlayStore(context: Context, packageName: String) {
        if (!start(context, Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))) {
            openUrl(context, "https://play.google.com/store/apps/details?id=$packageName")
        }
    }

    fun openGemini(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(GEMINI_PACKAGE)
        if (launch != null && start(context, launch)) {
            SettingsGuideHelper.guideGeminiLockscreen(context)
            return
        }
        openPlayStore(context, GEMINI_PACKAGE)
    }

    fun openGoogleApp(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(GOOGLE_PACKAGE)
        if (launch != null && start(context, launch)) return
        openPlayStore(context, GOOGLE_PACKAGE)
    }

    fun openUrl(context: Context, url: String) {
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Throwable) {
        false
    }

    const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"
    const val GEMINI_PACKAGE = "com.google.android.apps.bard"
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
}
