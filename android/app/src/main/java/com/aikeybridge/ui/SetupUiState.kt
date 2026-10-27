package com.aikeybridge.ui

import com.aikeybridge.model.ProvisionStep
import com.aikeybridge.model.RestoreStep
import com.aikeybridge.shell.ProvisionReport

enum class StepStatus { PENDING, RUNNING, OK, FAILED }

data class SetupStepUi(
    val id: String,
    val title: String,
    val detail: String,
    val command: String,
    val hint: String,
    val status: StepStatus = StepStatus.PENDING,
    val message: String = "",
)

data class RestoreStepUi(
    val id: String,
    val title: String,
    val command: String,
    val hint: String,
    val status: StepStatus = StepStatus.PENDING,
    val message: String = "",
)

data class SetupUiState(
    val steps: List<SetupStepUi> = emptyList(),
    val restoreSteps: List<RestoreStepUi> = emptyList(),
    val running: Boolean = false,
    val restoring: Boolean = false,
    val provisioningReport: ProvisionReport? = null,
    val restoreReport: ProvisionReport? = null,
) {
    val passed: Int get() = steps.count { it.status == StepStatus.OK }
    val failed: List<SetupStepUi> get() = steps.filter { it.status == StepStatus.FAILED }
    val adbScript: String get() = steps.joinToString("\n") { "adb shell ${it.command}" }

    companion object {
        fun from(provisioning: List<ProvisionStep>, restore: List<RestoreStep>): SetupUiState = SetupUiState(
            steps = provisioning.map {
                SetupStepUi(
                    id = it.id,
                    title = it.title,
                    detail = it.detail,
                    command = it.command,
                    hint = it.hint,
                )
            },
            restoreSteps = restore.map {
                RestoreStepUi(
                    id = it.id,
                    title = it.title,
                    command = it.command,
                    hint = it.hint,
                )
            },
        )
    }
}
