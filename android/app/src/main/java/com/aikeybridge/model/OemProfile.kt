package com.aikeybridge.model

/**
 * Profile của một hãng/ROM: trợ lý gốc trông như thế nào và cần vá gì để chuyển hướng.
 * Đích đến (Gemini, ChatGPT...) tách riêng trong [AssistantTarget] để ghép tự do với hãng.
 */
data class OemProfile(
    val schema: Int,
    val id: String,
    val oem: String,
    val os: String,
    val displayName: String,
    val assistantName: String,
    val wakePhrase: String?,
    val verified: Boolean,
    val match: List<String>,
    val assistantPackages: List<String>,
    val triggers: List<TriggerSpec>,
    val logTriggers: List<LogTrigger>,
    val shellWatch: ShellWatch?,
    val cooldownMs: Long,
    val dismissDelayMs: Long,
    val autostartHint: String,
    val provisioning: List<ProvisionStep>,
    val restore: List<RestoreStep>,
) {
    val isGeneric: Boolean get() = id == GENERIC_ID

    companion object {
        const val GENERIC_ID = "generic"
    }
}

data class TriggerSpec(
    val id: String,
    val label: String,
    val packageName: String,
    val classRegex: Regex,
    val custom: Boolean = false,
) {
    fun matches(pkg: String?, cls: String?): Boolean =
        pkg == packageName && cls != null && classRegex.matches(cls)
}

/**
 * Cách dùng "Script nền": script shell chạy bằng quyền shell (Shizuku/ADB) đọc event log,
 * thấy [panel] thì bấm Back rồi gọi trợ lý; thấy [button] thì gọi trợ lý luôn.
 */
data class ShellWatch(
    val panel: String,
    val button: String?,
)

/** Chế độ nút cứng B: một dòng logcat khớp [pattern] nghĩa là người dùng vừa bấm nút. */
data class LogTrigger(
    val id: String,
    val label: String,
    val pattern: Regex,
    val custom: Boolean = false,
)

enum class LaunchType { ASSIST, PACKAGE }

data class AssistantTarget(
    val id: String,
    val displayName: String,
    val packages: List<String>,
    val launchType: LaunchType,
    val assistAction: String?,
    val launchLabel: String,
    val beta: Boolean,
    val overlayNeedsDefaultAssistant: Boolean,
    val shellKeyevent: Int?,
    val voiceUi: VoiceUiSpec?,
    val provisioning: List<ProvisionStep>,
    val restore: List<RestoreStep>,
) {
    companion object {
        const val GEMINI = "gemini"
        const val CUSTOM = "custom"
    }
}

data class VoiceUiSpec(
    val windowWaitMs: Long,
    val retryIntervalMs: Long,
    val micViewIdRegex: Regex,
    val micContentDescRegex: Regex,
    val fallbackX: Float?,
    val fallbackY: Float?,
)

data class ProvisionStep(
    val id: String,
    val title: String,
    val detail: String,
    val command: String,
    val verify: String,
    val expect: String,
    val hint: String,
)

data class RestoreStep(
    val id: String,
    val title: String,
    val command: String,
    val hint: String,
)
