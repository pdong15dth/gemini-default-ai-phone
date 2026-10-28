package com.aikeybridge.service

import android.app.SearchManager
import android.content.Context
import android.os.Bundle

/**
 * Mở trợ lý mặc định như khi giữ nút nguồn: SearchManager.launchAssist (API ẩn, thanh hệ thống gọi).
 * Trợ lý mặc định là Google thì hệ thống hiện lớp phủ Gemini (khoanh tròn để hỏi được). Không cần Shizuku: đã thử
 * trên Vivo Android 17, trong khi mọi intent (VOICE_COMMAND, VOICE_ASSIST, ASSIST, deep link) chỉ mở app hoặc bị bỏ qua.
 */
object AssistOverlay {

    fun launch(context: Context): LaunchOutcome =
        runCatching {
            val manager = context.getSystemService(SearchManager::class.java)
            SearchManager::class.java.getMethod("launchAssist", Bundle::class.java).invoke(manager, Bundle())
            LaunchOutcome(true, "launchAssist")
        }.getOrElse {
            val cause = it.cause ?: it
            LaunchOutcome(false, "launchAssist lỗi: ${cause.javaClass.simpleName}: ${cause.message}")
        }
}
