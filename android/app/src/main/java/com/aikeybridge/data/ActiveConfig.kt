package com.aikeybridge.data

import com.aikeybridge.model.AssistantTarget
import com.aikeybridge.model.LaunchType
import com.aikeybridge.model.LogTrigger
import com.aikeybridge.model.OemProfile
import com.aikeybridge.model.ProvisionStep
import com.aikeybridge.model.RestoreStep
import com.aikeybridge.model.ShellWatch
import com.aikeybridge.model.TriggerSpec

/** Cấu hình đang chạy: profile hãng + đích đến + trigger tự dò + tinh chỉnh của người dùng. */
data class ActiveConfig(
    val profile: OemProfile,
    val detectedProfileId: String?,
    val manualProfile: Boolean,
    val target: AssistantTarget,
    val triggers: List<TriggerSpec>,
    val hardwareMode: HardwareMode,
    val logTriggers: List<LogTrigger>,
    val dismissDelayMs: Long,
    val micTimeoutMs: Long,
    val cooldownMs: Long,
    val dedupe: Boolean,
    val shellWatch: ShellWatch? = profile.shellWatch,
) {
    val provisioning: List<ProvisionStep> get() = target.provisioning + profile.provisioning
    val restore: List<RestoreStep> get() = target.restore + profile.restore
    val flowLabel: String get() = "${profile.assistantName} → ${target.displayName}"
}

object ProfileMatcher {

    fun detect(profiles: List<OemProfile>, manufacturer: String, brand: String): OemProfile? {
        val key = "$manufacturer $brand".lowercase()
        return profiles.firstOrNull { profile -> profile.match.any { it.isNotBlank() && it in key } }
    }
}

object ConfigResolver {

    const val MIN_DISMISS_DELAY = 200L
    const val MAX_DISMISS_DELAY = 1500L
    const val MIN_MIC_TIMEOUT = 1000L
    const val MAX_MIC_TIMEOUT = 5000L
    private const val DEFAULT_WAIT_MS = 2200L

    fun resolve(
        catalog: Catalog,
        settings: AppSettings,
        manufacturer: String,
        brand: String,
    ): ActiveConfig {
        val detected = ProfileMatcher.detect(catalog.profiles, manufacturer, brand)
        val manual = when (settings.profileOverride) {
            null -> null
            OemProfile.GENERIC_ID -> generic()
            else -> catalog.profile(settings.profileOverride)
        }
        val profile = manual ?: detected ?: generic()
        val target = resolveTarget(catalog, settings)

        val custom = settings.customTriggers.map { trigger ->
            TriggerSpec(
                id = "custom:${trigger.packageName}/${trigger.className}",
                label = "Tự dò · ${trigger.className.substringAfterLast('.')}",
                packageName = trigger.packageName,
                classRegex = Regex(Regex.escape(trigger.className)),
                custom = true,
            )
        }

        val customPanels = settings.customTriggers.map {
            it.className.substringAfterLast('.').ifBlank { it.packageName }
        }
        val mergedPanels = (listOfNotNull(profile.shellWatch?.panel) + customPanels)
            .flatMap { it.split('|') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val mergedButtons = (listOfNotNull(profile.shellWatch?.button) + settings.customLogPatterns)
            .flatMap { it.split('|') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val resolvedShellWatch = if (mergedPanels.isNotEmpty()) {
            ShellWatch(
                panel = mergedPanels.joinToString("|"),
                button = mergedButtons.takeIf { it.isNotEmpty() }?.joinToString("|"),
            )
        } else {
            profile.shellWatch
        }

        return ActiveConfig(
            profile = profile,
            detectedProfileId = detected?.id,
            manualProfile = manual != null,
            target = target,
            triggers = profile.triggers + custom,
            hardwareMode = settings.hardwareMode,
            logTriggers = profile.logTriggers + settings.customLogPatterns.mapNotNull { pattern ->
                runCatching { Regex(pattern) }.getOrNull()?.let {
                    LogTrigger(id = "custom-log:$pattern", label = "Tự dò log", pattern = it, custom = true)
                }
            },
            dismissDelayMs = (settings.dismissDelayMs ?: profile.dismissDelayMs)
                .coerceIn(MIN_DISMISS_DELAY, MAX_DISMISS_DELAY),
            micTimeoutMs = (settings.micTimeoutMs ?: target.voiceUi?.windowWaitMs ?: DEFAULT_WAIT_MS)
                .coerceIn(MIN_MIC_TIMEOUT, MAX_MIC_TIMEOUT),
            cooldownMs = profile.cooldownMs,
            dedupe = settings.dedupe,
            shellWatch = resolvedShellWatch,
        )
    }

    private fun resolveTarget(catalog: Catalog, settings: AppSettings): AssistantTarget {
        val pkg = settings.customTargetPackage
        if (settings.targetId == AssistantTarget.CUSTOM && !pkg.isNullOrBlank()) {
            return customTarget(pkg, settings.customTargetLabel ?: pkg)
        }
        return catalog.target(settings.targetId)
            ?: catalog.target(AssistantTarget.GEMINI)
            ?: catalog.targets.first()
    }

    fun customTarget(pkg: String, label: String) = AssistantTarget(
        id = AssistantTarget.CUSTOM,
        displayName = label,
        packages = listOf(pkg),
        launchType = LaunchType.PACKAGE,
        assistAction = null,
        launchLabel = pkg,
        beta = false,
        overlayNeedsDefaultAssistant = false,
        shellKeyevent = null,
        voiceUi = null,
        provisioning = emptyList(),
        restore = emptyList(),
    )

    /** Máy chưa có profile: chỉ chạy bằng trigger người dùng tự dò. */
    fun generic() = OemProfile(
        schema = 3,
        id = OemProfile.GENERIC_ID,
        oem = "generic",
        os = "Android",
        displayName = "Chưa có profile",
        assistantName = "trợ lý gốc",
        wakePhrase = null,
        verified = false,
        match = emptyList(),
        assistantPackages = emptyList(),
        triggers = emptyList(),
        logTriggers = emptyList(),
        shellWatch = null,
        cooldownMs = 1600,
        dismissDelayMs = 500,
        autostartHint = "Cài đặt → Ứng dụng → AI Key Bridge → bật Tự khởi chạy và Pin không hạn chế.",
        provisioning = emptyList(),
        restore = emptyList(),
    )
}
