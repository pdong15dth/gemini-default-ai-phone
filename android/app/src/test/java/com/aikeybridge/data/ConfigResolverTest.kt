package com.aikeybridge.data

import com.aikeybridge.model.AssistantTarget
import com.aikeybridge.model.LaunchType
import com.aikeybridge.model.LogTrigger
import com.aikeybridge.model.OemProfile
import com.aikeybridge.model.TriggerSpec
import com.aikeybridge.model.VoiceUiSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigResolverTest {

    private val vivo = profile("vivo_originos", listOf("vivo", "iqoo"), verified = true)
    private val xiaomi = profile("xiaomi_hyperos", listOf("xiaomi", "redmi", "poco"))
    private val oppo = profile("oppo_coloros", listOf("oppo", "oneplus", "realme"))
    private val catalog = Catalog(
        profiles = listOf(vivo, xiaomi, oppo),
        targets = listOf(gemini(), chatgpt()),
        profileAssets = emptyMap(),
    )

    @Test
    fun detectsProfileFromManufacturerOrBrand() {
        assertEquals("vivo_originos", ProfileMatcher.detect(catalog.profiles, "vivo", "iQOO")?.id)
        assertEquals("xiaomi_hyperos", ProfileMatcher.detect(catalog.profiles, "Xiaomi", "Redmi")?.id)
        assertEquals("oppo_coloros", ProfileMatcher.detect(catalog.profiles, "OnePlus", "OnePlus")?.id)
        assertNull(ProfileMatcher.detect(catalog.profiles, "samsung", "samsung"))
    }

    @Test
    fun unknownDeviceFallsBackToGenericWithoutTriggers() {
        val config = ConfigResolver.resolve(catalog, AppSettings(), "samsung", "samsung")

        assertTrue(config.profile.isGeneric)
        assertNull(config.detectedProfileId)
        assertTrue(config.triggers.isEmpty())
    }

    @Test
    fun manualOverrideWinsButKeepsDetectedId() {
        val config = ConfigResolver.resolve(catalog, AppSettings(profileOverride = "oppo_coloros"), "vivo", "vivo")

        assertEquals("oppo_coloros", config.profile.id)
        assertEquals("vivo_originos", config.detectedProfileId)
        assertTrue(config.manualProfile)
    }

    @Test
    fun choosingOtherBrandForcesGenericEvenWhenDetected() {
        val config = ConfigResolver.resolve(
            catalog,
            AppSettings(profileOverride = OemProfile.GENERIC_ID),
            "Xiaomi",
            "Redmi",
        )

        assertTrue(config.profile.isGeneric)
        assertTrue(config.manualProfile)
        assertEquals("xiaomi_hyperos", config.detectedProfileId)
        assertTrue(config.triggers.isEmpty())
    }

    @Test
    fun customTriggersMatchExactClassName() {
        val settings = AppSettings(
            customTriggers = listOf(CustomTrigger("com.example.ai", "com.example.ai.Panel\$Window")),
        )
        val config = ConfigResolver.resolve(catalog, settings, "vivo", "vivo")
        val custom = config.triggers.single { it.custom }

        assertTrue(custom.matches("com.example.ai", "com.example.ai.Panel\$Window"))
        assertFalse(custom.matches("com.example.ai", "com.example.ai.PanelXWindow"))
        assertFalse(custom.matches("com.other", "com.example.ai.Panel\$Window"))
    }

    @Test
    fun customTargetLaunchesPackageWithoutAssistantRequirement() {
        val settings = AppSettings(targetId = AssistantTarget.CUSTOM, customTargetPackage = "com.app", customTargetLabel = "App")
        val config = ConfigResolver.resolve(catalog, settings, "vivo", "vivo")

        assertEquals(LaunchType.PACKAGE, config.target.launchType)
        assertEquals(listOf("com.app"), config.target.packages)
        assertFalse(config.target.overlayNeedsDefaultAssistant)
        assertNull(config.target.voiceUi)
    }

    @Test
    fun provisioningCombinesTargetThenProfileSteps() {
        val config = ConfigResolver.resolve(catalog, AppSettings(), "vivo", "vivo")

        assertEquals(listOf("target_step", "vivo_originos_step"), config.provisioning.map { it.id })
        val chat = ConfigResolver.resolve(catalog, AppSettings(targetId = "chatgpt"), "vivo", "vivo")
        assertEquals(listOf("vivo_originos_step"), chat.provisioning.map { it.id })
    }

    @Test
    fun tuningIsClampedAndFallsBackToDefaults() {
        val defaults = ConfigResolver.resolve(catalog, AppSettings(), "vivo", "vivo")
        assertEquals(500L, defaults.dismissDelayMs)
        assertEquals(2200L, defaults.micTimeoutMs)

        val clamped = ConfigResolver.resolve(
            catalog,
            AppSettings(dismissDelayMs = 10, micTimeoutMs = 99_000),
            "vivo",
            "vivo",
        )
        assertEquals(ConfigResolver.MIN_DISMISS_DELAY, clamped.dismissDelayMs)
        assertEquals(ConfigResolver.MAX_MIC_TIMEOUT, clamped.micTimeoutMs)
    }

    @Test
    fun logTriggersMergeProfileAndValidCustomPatterns() {
        val settings = AppSettings(
            hardwareMode = HardwareMode.LOGCAT,
            customLogPatterns = listOf("SideKey.*down", "[broken"),
        )
        val config = ConfigResolver.resolve(catalog, settings, "vivo", "vivo")

        assertEquals(HardwareMode.LOGCAT, config.hardwareMode)
        assertEquals(listOf("ACTION_BUTTON", "SideKey.*down"), config.logTriggers.map { it.pattern.pattern })
    }

    @Test
    fun unknownTargetIdFallsBackToGemini() {
        val config = ConfigResolver.resolve(catalog, AppSettings(targetId = "missing"), "vivo", "vivo")
        assertEquals(AssistantTarget.GEMINI, config.target.id)
    }

    @Test
    fun defaultThemeModeFollowsSystem() {
        assertEquals(ThemeMode.SYSTEM, AppSettings().themeMode)
    }

    private fun profile(id: String, match: List<String>, verified: Boolean = false) = OemProfile(
        schema = 3,
        id = id,
        oem = id.substringBefore('_'),
        os = "OS",
        displayName = id,
        assistantName = "Assistant",
        wakePhrase = null,
        verified = verified,
        match = match,
        assistantPackages = listOf("com.$id"),
        triggers = listOf(TriggerSpec("t", "t", "com.$id", Regex(".*Panel.*"))),
        logTriggers = listOf(LogTrigger("log", "log", Regex("ACTION_BUTTON"))),
        shellWatch = null,
        cooldownMs = 1600,
        dismissDelayMs = 500,
        autostartHint = "",
        provisioning = listOf(com.aikeybridge.model.ProvisionStep("${id}_step", "", "", "", "", "", "")),
        restore = emptyList(),
    )

    private fun gemini() = AssistantTarget(
        id = AssistantTarget.GEMINI,
        displayName = "Gemini",
        packages = listOf("com.google.android.apps.bard"),
        launchType = LaunchType.ASSIST,
        assistAction = "android.intent.action.ASSIST",
        launchLabel = "",
        beta = false,
        overlayNeedsDefaultAssistant = true,
        shellKeyevent = 231,
        voiceUi = VoiceUiSpec(2200, 120, Regex(".*mic.*"), Regex("(?i)voice"), 0.86f, 0.92f),
        provisioning = listOf(com.aikeybridge.model.ProvisionStep("target_step", "", "", "", "", "", "")),
        restore = emptyList(),
    )

    private fun chatgpt() = gemini().copy(
        id = "chatgpt",
        displayName = "ChatGPT",
        packages = listOf("com.openai.chatgpt"),
        launchType = LaunchType.PACKAGE,
        overlayNeedsDefaultAssistant = false,
        shellKeyevent = null,
        provisioning = emptyList(),
    )
}
