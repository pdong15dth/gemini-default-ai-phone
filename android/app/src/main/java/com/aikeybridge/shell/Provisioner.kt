package com.aikeybridge.shell

import com.aikeybridge.model.ProvisionStep
import com.aikeybridge.model.RestoreStep

enum class StepPhase { RUNNING, OK, FAILED }

data class ProvisionReport(
    val total: Int,
    val passed: Int,
    val ok: Boolean,
)

class Provisioner(
    private val shell: ShellRunner,
    private val provisioning: List<ProvisionStep>,
    private val restore: List<RestoreStep>,
) {

    suspend fun runProvisioning(
        onStep: suspend (ProvisionStep, StepPhase, String) -> Unit,
    ): ProvisionReport {
        var passed = 0
        for (step in provisioning) {
            onStep(step, StepPhase.RUNNING, "Đang chạy…")
            val run = shell.exec(step.command)
            if (!run.ok) {
                val detail = run.output.ifBlank { "Lệnh trả về ${run.exitCode}" }
                onStep(step, StepPhase.FAILED, detail)
                continue
            }

            val verify = shell.exec(step.verify)
            val verified = verify.ok && verifyOutput(step.expect, verify.stdout)
            val detail = when {
                verified -> "Đã đọc lại: ${verify.stdout.trim().ifBlank { step.expect }}"
                verify.stdout.isNotBlank() -> "Chưa khớp: ${verify.stdout.trim()}"
                else -> verify.output.ifBlank { "Không đọc lại được giá trị." }
            }
            if (verified) passed++
            onStep(step, if (verified) StepPhase.OK else StepPhase.FAILED, detail)
        }

        return ProvisionReport(
            total = provisioning.size,
            passed = passed,
            ok = passed == provisioning.size,
        )
    }

    suspend fun runRestore(
        onStep: suspend (RestoreStep, StepPhase, String) -> Unit,
    ): ProvisionReport {
        var passed = 0
        for (step in restore) {
            onStep(step, StepPhase.RUNNING, "Đang chạy…")
            val result = shell.exec(step.command)
            val ok = result.ok || isMissingPackage(result)
            if (ok) passed++
            onStep(
                step,
                if (ok) StepPhase.OK else StepPhase.FAILED,
                when {
                    result.ok -> "Đã trả lại giá trị mặc định."
                    else -> result.output.ifBlank { "Lệnh trả về ${result.exitCode}" }
                },
            )
        }
        return ProvisionReport(
            total = restore.size,
            passed = passed,
            ok = passed == restore.size,
        )
    }

    private fun isMissingPackage(result: ShellResult): Boolean {
        val text = "${result.stdout}\n${result.stderr}".lowercase()
        return "not found" in text || "unknown package" in text || "no such" in text
    }

    companion object {
        fun verifyOutput(expect: String, output: String): Boolean =
            output.contains(expect, ignoreCase = true)
    }
}
