package com.aikeybridge.shell

import com.aikeybridge.model.ProvisionStep
import com.aikeybridge.model.RestoreStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisionerTest {

    @Test
    fun verifyOutputMatchesExpectedValueCaseInsensitively() {
        assertTrue(Provisioner.verifyOutput("googlequicksearchbox", "com.google.android.googlequicksearchbox/foo"))
        assertTrue(Provisioner.verifyOutput("en-US", "Locales for com.vivo.ai.copilot for user 0 are [en-US]"))
        assertTrue(!Provisioner.verifyOutput("allow", "deny"))
    }

    @Test
    fun provisioningRunsVerifyAndCountsPassedSteps() = kotlinx.coroutines.runBlocking {
        val shell = FakeShell(
            mapOf(
                "set one" to ShellResult(0, "", ""),
                "get one" to ShellResult(0, "value-one", ""),
                "set two" to ShellResult(0, "", ""),
                "get two" to ShellResult(0, "", ""),
            ),
        )
        val steps = listOf(
            step("one", "set one", "get one", "value-one"),
            step("two", "set two", "get two", "value-two"),
        )

        val phases = mutableListOf<StepPhase>()
        val report = Provisioner(shell, steps, emptyList()).runProvisioning { _, phase, _ -> phases += phase }

        assertEquals(2, report.total)
        assertEquals(1, report.passed)
        assertTrue(!report.ok)
        assertEquals(listOf(StepPhase.RUNNING, StepPhase.OK, StepPhase.RUNNING, StepPhase.FAILED), phases)
    }

    @Test
    fun missingPackageDuringRestoreCountsAsHandled() = kotlinx.coroutines.runBlocking {
        val shell = FakeShell(
            mapOf("reset missing" to ShellResult(1, "", "Unknown package: com.example")),
        )
        val restore = listOf(RestoreStep("missing", "Bỏ appops", "reset missing", ""))

        val report = Provisioner(shell, emptyList(), restore).runRestore { _, _, _ -> }

        assertTrue(report.ok)
    }

    private fun step(id: String, command: String, verify: String, expect: String) = ProvisionStep(
        id = id,
        title = id,
        detail = "",
        command = command,
        verify = verify,
        expect = expect,
        hint = "",
    )

    private class FakeShell(private val results: Map<String, ShellResult>) : ShellRunner {
        override suspend fun exec(command: String): ShellResult =
            results[command] ?: ShellResult(-1, "", "unexpected command: $command")
    }
}
