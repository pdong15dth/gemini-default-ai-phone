package com.aikeybridge.shell

/**
 * Quyền cấp một lần bằng shell (Shizuku hoặc ADB) để app tự nghe event log thay cho script nền.
 * Tất cả lưu trong hệ thống, còn nguyên khi tắt Gỡ lỗi / Tuỳ chọn nhà phát triển và sau khi khởi động lại.
 */
object WatchGrants {

    fun commands(pkg: String): List<String> = listOf(
        // Đọc event log của tiến trình khác — thứ duy nhất cần quyền shell.
        "pm grant $pkg android.permission.READ_LOGS",
        // Thông báo của foreground service; Android 12 trở xuống không có quyền này, lệnh lỗi cũng không sao.
        "pm grant $pkg android.permission.POST_NOTIFICATIONS",
        // Mở trợ lý từ nền (Android chặn mở activity khi app không ở trước).
        "cmd appops set $pkg SYSTEM_ALERT_WINDOW allow",
        "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow",
        "cmd deviceidle whitelist +$pkg",
    )

    fun adbCommands(pkg: String): List<String> =
        commands(pkg).map { "adb shell $it" } + "adb shell \"${WatchScript.stopCommand().replace("$", "\\$")}\""
}
