package com.aikeybridge

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.aikeybridge.data.ConfigSource
import com.aikeybridge.diagnostics.Diagnostics
import com.aikeybridge.service.AssistantLauncher

class TriggerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val config = runCatching { ConfigSource(this).current() }.getOrNull()
        if (config == null) {
            Diagnostics.error("lock", "TriggerActivity không tải được profile.")
        } else {
            val outcome = AssistantLauncher.launch(this, config.target)
            Diagnostics.log(
                if (outcome.ok) Diagnostics.Level.SUCCESS else Diagnostics.Level.ERROR,
                "lock",
                "TriggerActivity: ${outcome.detail}",
            )
        }

        window.decorView.postDelayed({ finish() }, FINISH_DELAY_MS)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        finish()
    }

    private companion object {
        const val FINISH_DELAY_MS = 250L
    }
}
