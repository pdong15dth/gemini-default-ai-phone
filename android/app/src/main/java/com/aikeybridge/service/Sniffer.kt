package com.aikeybridge.service

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Dò cửa sổ trợ lý lạ: trong lúc bật, service ghi lại mọi cửa sổ vừa trồi lên
 * (chỉ tên gói + tên lớp) để người dùng chọn làm trigger. Không chuyển hướng khi đang dò.
 */
object Sniffer {

    data class Candidate(
        val packageName: String,
        val className: String,
        val hits: Int,
        val lastAt: Long,
    ) {
        val looksLikeAssistant: Boolean
            get() = ASSISTANT_HINT.containsMatchIn(packageName) || ASSISTANT_HINT.containsMatchIn(className)
    }

    data class State(
        val active: Boolean = false,
        val endsAt: Long = 0,
        val candidates: List<Candidate> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val isActive: Boolean
        get() {
            val current = _state.value
            return current.active && SystemClock.elapsedRealtime() < current.endsAt
        }

    fun start(durationMs: Long) {
        _state.value = State(active = true, endsAt = SystemClock.elapsedRealtime() + durationMs)
    }

    fun stop() {
        _state.update { it.copy(active = false) }
    }

    fun reset() {
        _state.value = State()
    }

    fun observe(packageName: String?, className: String?, ownPackage: String) {
        if (!isActive) return
        if (packageName.isNullOrBlank() || className.isNullOrBlank()) return
        if (packageName == ownPackage || className.startsWith("android.widget.")) return
        val now = System.currentTimeMillis()
        _state.update { state ->
            val existing = state.candidates.firstOrNull {
                it.packageName == packageName && it.className == className
            }
            val updated = if (existing == null) {
                state.candidates + Candidate(packageName, className, 1, now)
            } else {
                state.candidates.map {
                    if (it === existing) it.copy(hits = it.hits + 1, lastAt = now) else it
                }
            }
            state.copy(candidates = updated.takeLast(MAX_CANDIDATES))
        }
    }

    private val ASSISTANT_HINT = Regex("(?i)voice|assist|agent|copilot|speech|breeno|xiaoai|yoyo|jovi")
    private const val MAX_CANDIDATES = 24
}
