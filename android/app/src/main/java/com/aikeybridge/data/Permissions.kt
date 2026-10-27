package com.aikeybridge.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import com.aikeybridge.service.AssistantAccessibilityService
import rikka.shizuku.Shizuku

data class PermissionSnapshot(
    val accessibility: Boolean = false,
    val assistantDefault: Boolean = false,
    val voiceInteraction: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val autostartConfirmed: Boolean = false,
    val geminiInstalled: Boolean = false,
    val shizukuInstalled: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuGranted: Boolean = false,
    val targetInstalled: Boolean = false,
    val assistantRequired: Boolean = true,
    val nativeAssistantInstalled: Boolean = false,
    val readLogs: Boolean = false,
) {
    val assistantOk: Boolean get() = assistantDefault || !assistantRequired
    val ready: Boolean get() = accessibility && assistantOk && targetInstalled
}

object PermissionsReader {

    fun read(
        context: Context,
        settings: AppSettings,
        targetPackages: List<String>,
        assistantRequired: Boolean,
        nativeAssistantPackages: List<String>,
    ): PermissionSnapshot {
        val resolver = context.contentResolver
        val voice = Settings.Secure.getString(resolver, "voice_interaction_service").orEmpty()

        return PermissionSnapshot(
            accessibility = accessibilityEnabled(context),
            assistantDefault = googleAssistantDefault(context),
            voiceInteraction = voice.startsWith(GOOGLE_PACKAGE),
            batteryUnrestricted = batteryUnrestricted(context),
            autostartConfirmed = settings.manualAutostart,
            geminiInstalled = isInstalled(context, GEMINI_PACKAGE),
            shizukuInstalled = isInstalled(context, SHIZUKU_PACKAGE),
            shizukuRunning = shizukuRunning(),
            shizukuGranted = shizukuGranted(),
            targetInstalled = targetPackages.any { isInstalled(context, it) },
            assistantRequired = assistantRequired,
            nativeAssistantInstalled = nativeAssistantPackages.any { isInstalled(context, it) },
            readLogs = context.checkSelfPermission(android.Manifest.permission.READ_LOGS) ==
                PackageManager.PERMISSION_GRANTED,
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

    private fun batteryUnrestricted(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
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
