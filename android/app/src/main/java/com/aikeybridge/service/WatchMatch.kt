package com.aikeybridge.service

import com.aikeybridge.model.ShellWatch

/** Khớp một dòng event log với mẫu [ShellWatch], cùng cú pháp regex mà script nền đưa cho `logcat -e`. */
class WatchMatch(val watch: ShellWatch) {

    enum class Kind { VOICE, BUTTON }

    private val panel = compile(watch.panel)
    private val button = watch.button?.let(::compile)

    fun kindOf(line: String): Kind? = when {
        panel?.containsMatchIn(line) == true -> Kind.VOICE
        button?.containsMatchIn(line) == true -> Kind.BUTTON
        else -> null
    }

    private fun compile(pattern: String): Regex? {
        if (pattern.isBlank()) return null
        return runCatching { Regex(pattern) }.getOrElse { Regex(Regex.escape(pattern)) }
    }
}
