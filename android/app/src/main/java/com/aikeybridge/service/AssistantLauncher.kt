package com.aikeybridge.service

import android.content.Context
import android.content.Intent
import com.aikeybridge.model.AssistantTarget
import com.aikeybridge.model.LaunchType

data class LaunchOutcome(
    val ok: Boolean,
    val detail: String,
)

/**
 * Mở đích bằng intent tường minh. Không bao giờ bắn ACTION_ASSIST ngầm định: trên OriginOS
 * Tiểu V cũng nhận ACTION_ASSIST nên intent ngầm định sẽ hiện hộp chọn hoặc mở lại Tiểu V.
 */
object AssistantLauncher {

    fun launch(context: Context, target: AssistantTarget): LaunchOutcome {
        if (target.launchType == LaunchType.ASSIST) {
            val action = target.assistAction ?: Intent.ACTION_ASSIST
            for (pkg in target.packages) {
                val explicit = Intent(action)
                    .setPackage(pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (explicit.resolveActivity(context.packageManager) == null) continue
                val started = runCatching { context.startActivity(explicit) }.isSuccess
                if (started) return LaunchOutcome(true, "${action.substringAfterLast('.')} → $pkg")
            }
        }
        return launchPackage(context, target)
    }

    /**
     * Mở thẳng chế độ nghe (VOICE_COMMAND) của đích nếu app đó nhận, không thì như [launch].
     * Dùng khi không có Shizuku để gửi phím trợ lý.
     */
    fun launchVoice(context: Context, target: AssistantTarget): LaunchOutcome {
        for (pkg in target.packages) {
            val voice = Intent(Intent.ACTION_VOICE_COMMAND)
                .setPackage(pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (voice.resolveActivity(context.packageManager) == null) continue
            val started = runCatching { context.startActivity(voice) }.isSuccess
            if (started) return LaunchOutcome(true, "VOICE_COMMAND → $pkg")
        }
        return launch(context, target)
    }

    private fun launchPackage(context: Context, target: AssistantTarget): LaunchOutcome {
        for (pkg in target.packages) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: continue
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            val started = runCatching { context.startActivity(intent) }.isSuccess
            if (started) return LaunchOutcome(true, "Mở app → $pkg")
        }
        return LaunchOutcome(false, "Chưa cài ${target.displayName} (${target.packages.joinToString()})")
    }
}
