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

    private fun launchPackage(context: Context, target: AssistantTarget): LaunchOutcome {
        for (pkg in target.packages) {
            val outcome = launchPackage(context, pkg, target.displayName)
            if (outcome.ok) return outcome
        }
        return LaunchOutcome(false, "Chưa cài ${target.displayName} (${target.packages.joinToString()})")
    }

    fun launchPackage(context: Context, pkg: String, displayName: String = pkg): LaunchOutcome {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return LaunchOutcome(false, "Chưa cài $displayName ($pkg)")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val started = runCatching { context.startActivity(intent) }.isSuccess
        return if (started) {
            LaunchOutcome(true, "Mở app → $pkg")
        } else {
            LaunchOutcome(false, "Không mở được $displayName ($pkg)")
        }
    }

    const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"
}
