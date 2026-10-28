package com.aikeybridge.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager

/**
 * Cửa sổ nổi 1px trong suốt, không nhận chạm, chỉ gắn trong lúc mở trợ lý từ nền.
 * Android 15+ chỉ miễn chặn mở activity từ nền cho app có quyền "Hiển thị trên ứng dụng khác" khi
 * cửa sổ nổi của app đang hiện. Không để thường trực: app ngân hàng coi cửa sổ nổi là đè màn hình.
 */
object OverlayGate {

    class Handle internal constructor(val manager: WindowManager, val view: View)

    /** Gắn cửa sổ; null nếu chưa có quyền hoặc hệ thống từ chối. Gọi trên luồng chính. */
    fun open(context: Context): Handle? {
        if (!Settings.canDrawOverlays(context)) return null
        val manager = context.getSystemService(WindowManager::class.java) ?: return null
        val view = View(context)
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        )
        return runCatching { manager.addView(view, params) }.map { Handle(manager, view) }.getOrNull()
    }

    fun closeLater(handle: Handle) {
        Handler(Looper.getMainLooper()).postDelayed(
            { runCatching { handle.manager.removeView(handle.view) } },
            CLOSE_AFTER_MS,
        )
    }

    private const val CLOSE_AFTER_MS = 1_500L
}
