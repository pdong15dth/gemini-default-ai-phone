package com.aikeybridge.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RedirectState {
    IDLE,
    DISMISSING,
    LAUNCHING,
    WAITING,
    TAPPING,
    DONE,
    FAILED,
}

object RuntimeStatus {

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _state = MutableStateFlow(RedirectState.IDLE)
    val state: StateFlow<RedirectState> = _state.asStateFlow()

    fun setConnected(value: Boolean) {
        _connected.value = value
    }

    fun setState(value: RedirectState) {
        _state.value = value
    }
}
