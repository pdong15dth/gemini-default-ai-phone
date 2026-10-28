package com.aikeybridge.shell

/**
 * Quyền cấp một lần bằng shell (Shizuku hoặc ADB) để app tự nghe event log thay cho script nền.
 * Tất cả lưu trong hệ thống, còn nguyên khi tắt Gỡ lỗi / Tuỳ chọn nhà phát triển và sau khi khởi động lại.
 */
object WatchGrants {

    fun commands(pkg: String): List<String> = listOf(
        // Thông báo của foreground service; Android 12 trở xuống không có quyền này, lệnh lỗi cũng không sao.
        "pm grant $pkg android.permission.POST_NOTIFICATIONS",
        // Mở trợ lý từ nền (Android chặn mở activity khi app không ở trước).
        "cmd appops set $pkg SYSTEM_ALERT_WINDOW allow",
        "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow",
        "cmd deviceidle whitelist +$pkg",
    )

    /**
     * Đọc event log của tiến trình khác. READ_LOGS gắn nhóm `log` nên Android giết tiến trình app ngay khi cấp
     * để nạp nhóm mới: chạy sau cùng, rồi tự mở lại app (lệnh chạy trong tiến trình shell, không chết theo app).
     */
    fun readLogsCommand(pkg: String): String =
        "pm grant $pkg android.permission.READ_LOGS; (sleep 1; am start -n $pkg/.MainActivity >/dev/null 2>&1) &"

    fun adbCommands(pkg: String): List<String> =
        commands(pkg).map { "adb shell $it" } +
            "adb shell \"${WatchScript.stopCommand().replace("$", "\\$")}\"" +
            "adb shell pm grant $pkg android.permission.READ_LOGS"
}
