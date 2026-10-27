package com.aikeybridge.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {

    /** 1100 → "1,1s" theo kiểu số Việt Nam. */
    fun seconds(ms: Long?): String {
        if (ms == null) return "—"
        val tenths = (ms + 50) / 100
        return "${tenths / 10},${tenths % 10}s"
    }

    fun relative(at: Long, now: Long): String {
        val diff = (now - at).coerceAtLeast(0)
        val minutes = diff / 60_000
        return when {
            minutes < 1 -> "vừa xong"
            minutes < 60 -> "$minutes phút trước"
            minutes < 24 * 60 -> "${minutes / 60} giờ trước"
            else -> "${minutes / (24 * 60)} ngày trước"
        }
    }

    fun clock(at: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))

    fun clockSeconds(at: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(at))

    /**
     * Rút gọn lệnh shell để hiện trong log:
     * "cmd appops set com.vivo.agent RUN_IN_BACKGROUND allow" → ("appops vivo.agent", "RUN_IN_BACKGROUND · allow").
     */
    fun shortCommand(command: String): Pair<String, String> {
        // Lệnh ghép "a; b" (ví dụ gỡ rồi thêm role): hiện lệnh cuối, là lệnh quyết định kết quả.
        val parts = command.split(';').last { it.isNotBlank() }.trim().split(Regex("\\s+"))
        fun shortPkg(pkg: String): String {
            val segments = pkg.split('.')
            return if (segments.last().length > 8) segments.last() else segments.takeLast(2).joinToString(".")
        }
        fun friendly(pkg: String) = when {
            "googlequicksearchbox" in pkg -> "Google"
            "apps.bard" in pkg -> "Gemini"
            else -> shortPkg(pkg)
        }
        return when {
            parts.size >= 4 && parts[0] == "settings" && parts[1] == "put" ->
                "${parts[2]} ${parts[3]}" to "→ ${parts.getOrNull(4)?.substringBefore('/')?.let(::friendly) ?: ""}"
            parts.size >= 4 && parts[0] == "settings" && parts[1] == "delete" ->
                "${parts[2]} ${parts[3]}" to "xoá giá trị"
            parts.size >= 5 && parts[0] == "cmd" && parts[1] == "appops" ->
                "appops ${shortPkg(parts[3])}" to listOfNotNull(parts.getOrNull(4), parts.getOrNull(5)).joinToString(" · ")
            parts.size >= 5 && parts[0] == "cmd" && parts[1] == "role" ->
                "role ${parts[3].substringAfterLast('.').lowercase()}" to
                    (if (parts[2].startsWith("remove")) "bỏ ${friendly(parts[4])}" else "→ ${friendly(parts[4])}")
            parts.size >= 4 && parts[0] == "cmd" && parts[1] == "locale" ->
                "locale ${shortPkg(parts[3])}" to
                    (parts.getOrNull(5)?.takeUnless { it == "\"\"" } ?: "xoá locale riêng")
            else -> command.take(40) to ""
        }
    }
}
