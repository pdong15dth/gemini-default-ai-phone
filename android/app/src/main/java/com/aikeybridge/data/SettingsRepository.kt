package com.aikeybridge.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Cửa sổ trợ lý do người dùng tự dò được, khớp đúng tên lớp. */
data class CustomTrigger(
    val packageName: String,
    val className: String,
)

/** Cách bắt nút cứng: A = bắt cửa sổ trợ lý gốc, B = đọc event log. */
enum class HardwareMode { WINDOW, LOGCAT }

/**
 * FULL: Trợ năng chạy nền để thay "Hi Jovi".
 * SCRIPT: script shell nền qua Shizuku (như README), không cần Trợ năng, mất sau khi khởi động lại máy.
 * ONE_TIME: chỉ cấu hình một lần + gán phím/lối tắt.
 */
enum class UsageMode { FULL, SCRIPT, ONE_TIME }

data class AppSettings(
    val redirectEnabled: Boolean = true,
    val autoMic: Boolean = true,
    val lockScreen: Boolean = true,
    val onboardingDone: Boolean = false,
    val onboardingStep: Int = 0,
    val manualAutostart: Boolean = false,
    /** null = tự nhận diện theo hãng máy. */
    val profileOverride: String? = null,
    val targetId: String = "gemini",
    val customTargetPackage: String? = null,
    val customTargetLabel: String? = null,
    /** null = dùng giá trị của profile/đích đến. */
    val dismissDelayMs: Long? = null,
    val micTimeoutMs: Long? = null,
    val dedupe: Boolean = true,
    val customTriggers: List<CustomTrigger> = emptyList(),
    val hardwareMode: HardwareMode = HardwareMode.WINDOW,
    val usageMode: UsageMode = UsageMode.FULL,
    val scriptStartedAt: Long = 0,
    val customLogPatterns: List<String> = emptyList(),
    val provisionedFingerprint: String? = null,
    val lastRomCheckAt: Long = 0,
)

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("ai_key_bridge_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private val changeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            _state.value = read()
        }

    init {
        prefs.registerOnSharedPreferenceChangeListener(changeListener)
    }

    fun setRedirectEnabled(value: Boolean) = edit { putBoolean(KEY_REDIRECT, value) }

    fun setAutoMic(value: Boolean) = edit { putBoolean(KEY_AUTO_MIC, value) }

    fun setLockScreen(value: Boolean) = edit { putBoolean(KEY_LOCK_SCREEN, value) }

    fun setOnboardingDone(value: Boolean) = edit { putBoolean(KEY_ONBOARDING, value) }

    fun setOnboardingStep(value: Int) = edit { putInt(KEY_ONBOARDING_STEP, value) }

    fun setManualAutostart(value: Boolean) = edit { putBoolean(KEY_MANUAL_AUTOSTART, value) }

    fun setProfileOverride(id: String?) = edit { putOrRemove(KEY_PROFILE, id) }

    fun setTarget(id: String, customPackage: String? = null, customLabel: String? = null) = edit {
        putString(KEY_TARGET, id)
        if (customPackage != null) {
            putString(KEY_CUSTOM_TARGET_PKG, customPackage)
            putOrRemove(KEY_CUSTOM_TARGET_LABEL, customLabel)
        }
    }

    fun setDismissDelay(value: Long?) = edit { putOrRemove(KEY_DISMISS_DELAY, value) }

    fun setMicTimeout(value: Long?) = edit { putOrRemove(KEY_MIC_TIMEOUT, value) }

    fun setDedupe(value: Boolean) = edit { putBoolean(KEY_DEDUPE, value) }

    fun addCustomTrigger(trigger: CustomTrigger) {
        val current = _state.value.customTriggers
        if (trigger in current) return
        writeTriggers((current + trigger).takeLast(MAX_CUSTOM_TRIGGERS))
    }

    fun removeCustomTrigger(trigger: CustomTrigger) {
        writeTriggers(_state.value.customTriggers - trigger)
    }

    fun setHardwareMode(mode: HardwareMode) = edit { putString(KEY_HARDWARE_MODE, mode.name) }

    fun setUsageMode(mode: UsageMode) = edit { putString(KEY_USAGE_MODE, mode.name) }

    fun setScriptStartedAt(at: Long) = edit { putLong(KEY_SCRIPT_STARTED, at) }

    fun addLogPattern(pattern: String) {
        val current = _state.value.customLogPatterns
        if (pattern in current) return
        edit { putString(KEY_LOG_PATTERNS, (current + pattern).takeLast(MAX_CUSTOM_TRIGGERS).joinToString("\n")) }
    }

    fun removeLogPattern(pattern: String) {
        edit { putString(KEY_LOG_PATTERNS, (_state.value.customLogPatterns - pattern).joinToString("\n")) }
    }

    fun setProvisionedFingerprint(value: String?) = edit { putOrRemove(KEY_FINGERPRINT, value) }

    fun setLastRomCheck(at: Long) = edit { putLong(KEY_ROM_CHECK, at) }

    private fun writeTriggers(list: List<CustomTrigger>) = edit {
        putString(KEY_CUSTOM_TRIGGERS, list.joinToString("\n") { "${it.packageName}\t${it.className}" })
    }

    private fun read(): AppSettings = AppSettings(
        redirectEnabled = prefs.getBoolean(KEY_REDIRECT, true),
        autoMic = prefs.getBoolean(KEY_AUTO_MIC, true),
        lockScreen = prefs.getBoolean(KEY_LOCK_SCREEN, true),
        onboardingDone = prefs.getBoolean(KEY_ONBOARDING, false),
        onboardingStep = prefs.getInt(KEY_ONBOARDING_STEP, 0),
        manualAutostart = prefs.getBoolean(KEY_MANUAL_AUTOSTART, false),
        profileOverride = prefs.getString(KEY_PROFILE, null),
        targetId = prefs.getString(KEY_TARGET, null) ?: "gemini",
        customTargetPackage = prefs.getString(KEY_CUSTOM_TARGET_PKG, null),
        customTargetLabel = prefs.getString(KEY_CUSTOM_TARGET_LABEL, null),
        dismissDelayMs = prefs.getLong(KEY_DISMISS_DELAY, -1).takeIf { it >= 0 },
        micTimeoutMs = prefs.getLong(KEY_MIC_TIMEOUT, -1).takeIf { it >= 0 },
        dedupe = prefs.getBoolean(KEY_DEDUPE, true),
        customTriggers = prefs.getString(KEY_CUSTOM_TRIGGERS, null).orEmpty()
            .lineSequence()
            .mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    CustomTrigger(parts[0], parts[1])
                } else {
                    null
                }
            }
            .toList(),
        hardwareMode = prefs.getString(KEY_HARDWARE_MODE, null)
            ?.let { name -> HardwareMode.entries.firstOrNull { it.name == name } }
            ?: HardwareMode.WINDOW,
        usageMode = prefs.getString(KEY_USAGE_MODE, null)
            ?.let { name -> UsageMode.entries.firstOrNull { it.name == name } }
            ?: UsageMode.FULL,
        scriptStartedAt = prefs.getLong(KEY_SCRIPT_STARTED, 0),
        customLogPatterns = prefs.getString(KEY_LOG_PATTERNS, null).orEmpty()
            .lines()
            .filter { it.isNotBlank() },
        provisionedFingerprint = prefs.getString(KEY_FINGERPRINT, null),
        lastRomCheckAt = prefs.getLong(KEY_ROM_CHECK, 0),
    )

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        val editor = prefs.edit()
        editor.block()
        editor.apply()
    }

    private fun SharedPreferences.Editor.putOrRemove(key: String, value: String?) {
        if (value == null) remove(key) else putString(key, value)
    }

    private fun SharedPreferences.Editor.putOrRemove(key: String, value: Long?) {
        if (value == null) remove(key) else putLong(key, value)
    }

    private companion object {
        const val KEY_REDIRECT = "redirect_enabled"
        const val KEY_AUTO_MIC = "auto_mic"
        const val KEY_LOCK_SCREEN = "lock_screen"
        const val KEY_ONBOARDING = "onboarding_done"
        const val KEY_ONBOARDING_STEP = "onboarding_step"
        const val KEY_MANUAL_AUTOSTART = "manual_autostart"
        const val KEY_PROFILE = "profile_override"
        const val KEY_TARGET = "target_id"
        const val KEY_CUSTOM_TARGET_PKG = "custom_target_package"
        const val KEY_CUSTOM_TARGET_LABEL = "custom_target_label"
        const val KEY_DISMISS_DELAY = "dismiss_delay_ms"
        const val KEY_MIC_TIMEOUT = "mic_timeout_ms"
        const val KEY_DEDUPE = "dedupe"
        const val KEY_CUSTOM_TRIGGERS = "custom_triggers"
        const val KEY_HARDWARE_MODE = "hardware_mode"
        const val KEY_USAGE_MODE = "usage_mode"
        const val KEY_SCRIPT_STARTED = "script_started_at"
        const val KEY_LOG_PATTERNS = "custom_log_patterns"
        const val KEY_FINGERPRINT = "provisioned_fingerprint"
        const val KEY_ROM_CHECK = "last_rom_check"
        const val MAX_CUSTOM_TRIGGERS = 10
    }
}
