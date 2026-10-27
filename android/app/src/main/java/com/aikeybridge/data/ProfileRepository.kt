package com.aikeybridge.data

import android.content.Context
import com.aikeybridge.diagnostics.Diagnostics
import com.aikeybridge.model.AssistantTarget
import com.aikeybridge.model.LaunchType
import com.aikeybridge.model.LogTrigger
import com.aikeybridge.model.OemProfile
import com.aikeybridge.model.ProvisionStep
import com.aikeybridge.model.RestoreStep
import com.aikeybridge.model.ShellWatch
import com.aikeybridge.model.TriggerSpec
import com.aikeybridge.model.VoiceUiSpec
import org.json.JSONArray
import org.json.JSONObject

data class Catalog(
    val profiles: List<OemProfile>,
    val targets: List<AssistantTarget>,
    val profileAssets: Map<String, String>,
) {
    fun profile(id: String?): OemProfile? = profiles.firstOrNull { it.id == id }

    fun target(id: String?): AssistantTarget? = targets.firstOrNull { it.id == id }
}

/** Đọc profile hãng và đích đến đóng gói trong assets. Kết quả được cache theo process. */
object ProfileRepository {

    private const val PROFILE_DIR = "profiles"
    private const val TARGETS_FILE = "targets.json"

    @Volatile
    private var cached: Catalog? = null

    fun catalog(context: Context): Catalog =
        cached ?: synchronized(this) {
            cached ?: load(context.applicationContext).also { cached = it }
        }

    fun rawProfile(context: Context, profileId: String): String? {
        val asset = catalog(context).profileAssets[profileId] ?: return null
        return runCatching {
            context.assets.open(asset).bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    private fun load(context: Context): Catalog {
        val assets = mutableMapOf<String, String>()
        val profiles = context.assets.list(PROFILE_DIR).orEmpty()
            .filter { it.endsWith(".json") }
            .sorted()
            .mapNotNull { file ->
                val path = "$PROFILE_DIR/$file"
                runCatching {
                    parseProfile(context.assets.open(path).bufferedReader().use { it.readText() })
                }.onFailure {
                    Diagnostics.error("profile", "Bỏ qua $file: ${it.message}")
                }.getOrNull()?.also { assets[it.id] = path }
            }
            .sortedWith(compareByDescending<OemProfile> { it.verified }.thenBy { it.displayName })

        val targets = runCatching {
            val root = JSONObject(context.assets.open(TARGETS_FILE).bufferedReader().use { it.readText() })
            root.getJSONArray("targets").objects().map(::parseTarget)
        }.onFailure {
            Diagnostics.error("profile", "Không đọc được $TARGETS_FILE: ${it.message}")
        }.getOrDefault(emptyList())

        return Catalog(
            profiles = profiles,
            targets = targets.ifEmpty { listOf(fallbackGemini()) },
            profileAssets = assets,
        )
    }

    fun parseProfile(raw: String): OemProfile {
        val root = JSONObject(raw)
        val id = root.getString("id")
        return OemProfile(
            schema = root.getInt("schema"),
            id = id,
            oem = root.getString("oem"),
            os = root.getString("os"),
            displayName = root.getString("display_name"),
            assistantName = root.getString("assistant_name"),
            wakePhrase = root.optString("wake_phrase").ifBlank { null },
            verified = root.optBoolean("verified", false),
            match = root.getJSONArray("match").strings().map { it.lowercase() },
            assistantPackages = root.getJSONArray("assistant_packages").strings(),
            triggers = root.getJSONArray("triggers").objects().take(MAX_TRIGGERS).map { item ->
                TriggerSpec(
                    id = item.getString("id"),
                    label = item.getString("label"),
                    packageName = item.getString("package"),
                    classRegex = Regex(item.getString("class_regex")),
                )
            },
            logTriggers = root.optJSONArray("log_triggers")?.objects().orEmpty().take(MAX_TRIGGERS).map { item ->
                LogTrigger(
                    id = item.getString("id"),
                    label = item.getString("label"),
                    pattern = Regex(item.getString("pattern")),
                )
            },
            shellWatch = root.optJSONObject("shell_watch")?.let {
                ShellWatch(panel = it.getString("panel"), button = it.optString("button").ifBlank { null })
            },
            cooldownMs = root.optLong("cooldown_ms", 1600),
            dismissDelayMs = root.optLong("dismiss_delay_ms", 500),
            autostartHint = root.optString("autostart_hint"),
            provisioning = root.optJSONArray("provisioning").provisionSteps(),
            restore = root.optJSONArray("restore").restoreSteps(),
        )
    }

    private fun parseTarget(item: JSONObject): AssistantTarget {
        val launch = item.getJSONObject("launch")
        val type = when (launch.getString("type")) {
            "assist" -> LaunchType.ASSIST
            "package" -> LaunchType.PACKAGE
            else -> error("launch.type không hợp lệ")
        }
        return AssistantTarget(
            id = item.getString("id"),
            displayName = item.getString("display_name"),
            packages = item.getJSONArray("packages").strings(),
            launchType = type,
            assistAction = launch.optString("action").ifBlank { null },
            launchLabel = item.optString("launch_label"),
            beta = item.optBoolean("beta", false),
            overlayNeedsDefaultAssistant = item.optBoolean("overlay_needs_default_assistant", false),
            shellKeyevent = if (item.has("shell_keyevent")) item.getInt("shell_keyevent") else null,
            voiceUi = item.optJSONObject("voice_ui")?.let(::parseVoiceUi),
            provisioning = item.optJSONArray("provisioning").provisionSteps(),
            restore = item.optJSONArray("restore").restoreSteps(),
        )
    }

    private fun parseVoiceUi(voice: JSONObject) = VoiceUiSpec(
        windowWaitMs = voice.getLong("window_wait_ms"),
        retryIntervalMs = voice.getLong("retry_interval_ms"),
        micViewIdRegex = Regex(voice.getString("mic_view_id_regex")),
        micContentDescRegex = Regex(voice.getString("mic_content_desc_regex")),
        fallbackX = if (voice.has("fallback_x")) voice.getDouble("fallback_x").toFloat() else null,
        fallbackY = if (voice.has("fallback_y")) voice.getDouble("fallback_y").toFloat() else null,
    )

    private fun fallbackGemini() = AssistantTarget(
        id = AssistantTarget.GEMINI,
        displayName = "Gemini",
        packages = listOf("com.google.android.apps.bard", "com.google.android.googlequicksearchbox"),
        launchType = LaunchType.PACKAGE,
        assistAction = null,
        launchLabel = "mở app",
        beta = false,
        overlayNeedsDefaultAssistant = true,
        shellKeyevent = 231,
        voiceUi = VoiceUiSpec(
            windowWaitMs = 2200,
            retryIntervalMs = 120,
            micViewIdRegex = Regex(".*(mic|voice).*"),
            micContentDescRegex = Regex("(?i)micro|voice|speak|assistant"),
            fallbackX = 0.86f,
            fallbackY = 0.92f,
        ),
        provisioning = emptyList(),
        restore = emptyList(),
    )

    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

    private fun JSONArray?.provisionSteps(): List<ProvisionStep> =
        this?.objects()?.map { item ->
            ProvisionStep(
                id = item.getString("id"),
                title = item.getString("title"),
                detail = item.getString("detail"),
                command = item.getString("command"),
                verify = item.getString("verify"),
                expect = item.getString("expect"),
                hint = item.getString("hint"),
            )
        }.orEmpty()

    private fun JSONArray?.restoreSteps(): List<RestoreStep> =
        this?.objects()?.map { item ->
            RestoreStep(
                id = item.getString("id"),
                title = item.getString("title"),
                command = item.getString("command"),
                hint = item.getString("hint"),
            )
        }.orEmpty()

    private const val MAX_TRIGGERS = 20
}
