package com.aikeybridge.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

object Intents {

    fun openAccessibility(context: Context) {
        start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** Mở Tuỳ chọn nhà phát triển để người dùng tắt Gỡ lỗi không dây / Developer Mode trước khi vào app ngân hàng. */
    fun openDeveloperSettings(context: Context) {
        if (!start(context, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))) {
            start(context, Intent(Settings.ACTION_SETTINGS))
        }
    }

    fun openAppDetails(context: Context) {
        start(
            context,
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }

    fun openAssistantSettings(context: Context) {
        val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        if (!start(context, intent)) {
            start(context, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
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
                return
            }
        }
        openAppDetails(context)
    }

    /** MIUI / HyperOS: mở màn "Quyền khác" của AI Key Bridge (Mở cửa sổ mới khi chạy trong nền, Hiển thị trên màn hình khóa). */
    fun openMiuiOtherPermissions(context: Context) {
        val editors = listOf(
            "com.miui.permcenter.permissions.PermissionsEditorActivity",
            "com.miui.permcenter.permissions.AppPermissionsEditorActivity",
        )
        for (cls in editors) {
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName("com.miui.securitycenter", cls)
                .putExtra("extra_pkgname", context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (start(context, intent)) return
        }
        openAppDetails(context)
    }

    /** Vivo: Cài đặt → Nút thông minh (nhấn / nhấn đúp / nhấn giữ). */
    fun openSmartKey(context: Context): Boolean =
        start(context, Intent("com.vivo.settings.acticon.JOVI_KEY")) ||
            start(context, Intent(Settings.ACTION_SETTINGS))

    /** Vivo: Chạm mặt lưng không mở thẳng được (activity không export) → mở Cài đặt để tìm. */
    fun openBackTap(context: Context): Boolean = start(context, Intent(Settings.ACTION_SETTINGS))

    /** Vivo: Khởi chạy nhanh (giữ Giảm âm lượng khi tắt màn hình). */
    fun openQuickLaunch(context: Context): Boolean =
        start(context, Intent("com.vivo.SmartKey.MainActivity")) ||
            start(context, Intent(Settings.ACTION_SETTINGS))

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
        if (launch != null && start(context, launch)) return
        openUrl(context, "https://shizuku.rikka.app/download/")
    }

    fun openPlayStore(context: Context, packageName: String) {
        if (!start(context, Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))) {
            openUrl(context, "https://play.google.com/store/apps/details?id=$packageName")
        }
    }

    fun openGemini(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(GEMINI_PACKAGE)
        if (launch != null && start(context, launch)) return
        openPlayStore(context, GEMINI_PACKAGE)
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

    const val GEMINI_PACKAGE = "com.google.android.apps.bard"
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
}
