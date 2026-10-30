package com.aikeybridge.service

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock

/**
 * Mở trợ lý mặc định như khi giữ nút nguồn: SearchManager.launchAssist (API ẩn, thanh hệ thống gọi).
 * Trợ lý mặc định là Google thì hệ thống hiện lớp phủ Gemini (khoanh tròn để hỏi được). Không cần Shizuku: đã thử
 * trên Vivo Android 17, trong khi mọi intent (VOICE_COMMAND, VOICE_ASSIST, ASSIST, deep link) chỉ mở app hoặc bị bỏ qua.
 */
object AssistOverlay {

    /**
     * Mở lớp phủ trợ lý mặc định (Gemini FloatyActivity khi Google là trợ lý mặc định).
     */
    fun launch(context: Context): LaunchOutcome =
        launchWithBundle(context, Bundle(), "launchAssist")

    /**
     * Mở trực tiếp giao diện Google Circle to Search (OmnientActivity - khoanh tròn tìm kiếm / dịch màn hình).
     * Truyền `omni.entry_point = 1` và `invocation_time_ms` để GsaVoiceInteractionService của Google mở
     * Circle to Search thay vì lớp phủ thoại của Gemini.
     */
    fun launchCircleToSearch(context: Context): LaunchOutcome {
        val bundle = createCtsBundle()
        val assist = launchWithBundle(context, bundle, "CircleToSearch (launchAssist)")
        if (assist.ok) return assist

        val vis = tryVoiceInteractionService(context, bundle)
        if (vis.ok) return vis

        val contextual = tryContextualSearch()
        if (contextual.ok) return contextual

        return assist
    }

    fun createCtsBundle(elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()): Bundle =
        Bundle().apply {
            putLong("invocation_time_ms", elapsedRealtimeMs)
            putInt("omni.entry_point", 1)
            putBoolean("cts_trigger", true)
            putBoolean("micts_trigger", true)
        }

    private fun launchWithBundle(context: Context, bundle: Bundle, label: String): LaunchOutcome =
        runCatching {
            val manager = context.getSystemService(SearchManager::class.java)
                ?: return LaunchOutcome(false, "$label lỗi: SearchManager null")
            SearchManager::class.java.getMethod("launchAssist", Bundle::class.java).invoke(manager, bundle)
            LaunchOutcome(true, label)
        }.getOrElse {
            val cause = it.cause ?: it
            LaunchOutcome(false, "$label lỗi: ${cause.javaClass.simpleName}: ${cause.message}")
        }

    @SuppressLint("PrivateApi")
    private fun tryVoiceInteractionService(context: Context, bundle: Bundle): LaunchOutcome =
        runCatching {
            val sm = Class.forName("android.os.ServiceManager")
            val binder = sm.getMethod("getService", String::class.java)
                .invoke(null, "voiceinteraction") as? IBinder
                ?: return LaunchOutcome(false, "voiceinteraction service null")
            val stub = Class.forName("com.android.internal.app.IVoiceInteractionManagerService\$Stub")
            val service = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            val serviceClass = Class.forName("com.android.internal.app.IVoiceInteractionManagerService")
            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val method = serviceClass.getMethod(
                    "showSessionFromSession",
                    IBinder::class.java,
                    Bundle::class.java,
                    Int::class.javaPrimitiveType,
                    String::class.java,
                )
                (method.invoke(service, null, bundle, 7, context.packageName) as? Boolean) == true
            } else {
                val method = serviceClass.getMethod(
                    "showSessionFromSession",
                    IBinder::class.java,
                    Bundle::class.java,
                    Int::class.javaPrimitiveType,
                )
                (method.invoke(service, null, bundle, 7) as? Boolean) == true
            }
            if (ok) {
                LaunchOutcome(true, "CircleToSearch (VoiceInteraction)")
            } else {
                LaunchOutcome(false, "showSessionFromSession trả về false")
            }
        }.getOrElse {
            val cause = it.cause ?: it
            LaunchOutcome(false, "VoiceInteraction lỗi: ${cause.javaClass.simpleName}: ${cause.message}")
        }

    @SuppressLint("PrivateApi")
    private fun tryContextualSearch(): LaunchOutcome =
        runCatching {
            val sm = Class.forName("android.os.ServiceManager")
            val binder = sm.getMethod("getService", String::class.java)
                .invoke(null, "contextual_search") as? IBinder
                ?: return LaunchOutcome(false, "contextual_search service null")
            val stub = Class.forName("android.app.contextualsearch.IContextualSearchManager\$Stub")
            val service = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            val serviceClass = Class.forName("android.app.contextualsearch.IContextualSearchManager")
            serviceClass.getMethod("startContextualSearch", Int::class.javaPrimitiveType)
                .invoke(service, 1)
            LaunchOutcome(true, "CircleToSearch (ContextualSearch)")
        }.getOrElse {
            val cause = it.cause ?: it
            LaunchOutcome(false, "ContextualSearch lỗi: ${cause.javaClass.simpleName}: ${cause.message}")
        }
}
