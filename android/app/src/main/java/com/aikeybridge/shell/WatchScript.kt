package com.aikeybridge.shell

import com.aikeybridge.model.ShellWatch
import java.util.Base64

/**
 * Script nền chạy bằng quyền shell, cùng cơ chế với scripts/gemini.sh trong README:
 * đọc kênh events của logcat, thấy cửa sổ trợ lý gốc thì Back, rồi đánh thức màn hình và gọi trợ lý.
 * Mỗi lần chuyển hướng ghi một dòng "<epoch giây> voice|button" vào [eventLog] để app đưa vào Nhật ký.
 */
object WatchScript {

    const val PATH = "/data/local/tmp/akb-gemini.sh"

    /**
     * @param dismissDelayMs chờ sau Back để trợ lý gốc nhả Micro; chỉ áp dụng cho khẩu lệnh.
     * Nút lối tắt không có panel nào cần đóng nên gọi trợ lý ngay. Màn hình đang sáng thì bỏ
     * bước đánh thức (224) và khoảng chờ sau nó.
     */
    fun content(watch: ShellWatch, keyevent: Int, eventLog: String, dismissDelayMs: Long = 500): String {
        val pattern = listOfNotNull(watch.panel, watch.button).joinToString("|")
        val panelCase = watch.panel.split('|').filter { it.isNotBlank() }.joinToString("|") { "*$it*" }
        val dismiss = seconds(dismissDelayMs)
        return buildString {
            appendLine("#!/system/bin/sh")
            appendLine("# AI Key Bridge — script nền (Shizuku/ADB). Bỏ qua HUP/INT/TERM để rút cáp không chết.")
            appendLine("trap \"\" HUP INT TERM")
            appendLine("# Tắt Gỡ lỗi / Tuỳ chọn nhà phát triển thì init dừng adbd và giết cả nhóm tiến trình (cgroup) của nó.")
            appendLine("# Chuyển sang nhóm gốc như Shizuku, logcat/input sinh ra sau đó cũng nằm ngoài nhóm của adbd.")
            appendLine("for cg in $ESCAPE_CGROUPS; do echo \$\$ 2>/dev/null > \"\$cg/cgroup.procs\"; done")
            appendLine("LOG='$eventLog'")
            appendLine("DIR=\"\${LOG%/*}\"")
            appendLine("# Chẩn đoán đọc được cả khi Shizuku đã chết: trạng thái lúc khởi động + nhịp còn sống mỗi ${ALIVE_INTERVAL_S}s.")
            appendLine(
                "{ echo \"start \$(date '+%F %T') pid=\$\$ ppid=\$PPID uid=\$(id -u) ctx=\$(cat /proc/self/attr/current 2>/dev/null)\"; " +
                    "cat /proc/\$\$/cgroup; } > \"\$DIR/$STATE_FILE\" 2>/dev/null",
            )
            appendLine("( while true; do date '+%F %T' > \"\$DIR/$ALIVE_FILE\" 2>/dev/null; sleep $ALIVE_INTERVAL_S; done ) &")
            appendLine("while true; do")
            appendLine("  logcat -b events -c")
            appendLine("  line=\$(logcat -b events -e '$pattern' -m 1)")
            appendLine("  case \"\$line\" in")
            appendLine("    $panelCase) input keyevent 4; kind=voice; sleep $dismiss ;;")
            appendLine("    *) kind=button ;;")
            appendLine("  esac")
            appendLine("  ${assistCommand(keyevent)}")
            appendLine("  echo \"\$(date +%s) \$kind\" >> \"\$LOG\" 2>/dev/null")
            appendLine("  sleep 1.5")
            appendLine("done")
        }
    }

    /** Đánh thức màn hình nếu đang tắt (224) rồi gửi phím trợ lý; giống hệt thân vòng lặp của script. */
    fun assistCommand(keyevent: Int): String =
        "if dumpsys power 2>/dev/null | grep -q 'mWakefulness=Awake'; then input keyevent $keyevent; " +
            "else input keyevent 224; sleep 0.5; input keyevent $keyevent; fi"

    /** Một lượt của script, để app gọi qua Shizuku khi tự nghe event log: khẩu lệnh thì Back đóng trợ lý gốc trước. */
    fun fireCommand(voice: Boolean, keyevent: Int, dismissDelayMs: Long): String {
        val dismiss = if (voice) "input keyevent 4; sleep ${seconds(dismissDelayMs)}; " else ""
        return dismiss + assistCommand(keyevent)
    }

    /** 500 → "0.5" cho lệnh sleep của toybox. */
    fun seconds(ms: Long): String {
        val tenths = (ms.coerceAtLeast(0) + 50) / 100
        return "${tenths / 10}.${tenths % 10}"
    }

    /** Ghi script qua base64 để không phải escape dấu nháy trong sh -c. */
    fun installCommand(script: String): String {
        val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_8))
        return "echo $encoded | base64 -d > $PATH && chmod 755 $PATH"
    }

    /** Chạy script bằng setsid. Gọi [stopCommand] ở một lệnh riêng trước đó. */
    fun startCommand(): String = "setsid sh $PATH </dev/null >/dev/null 2>&1 &"

    /**
     * Dừng mọi bản đang chạy (cả gemini.sh của README) và logcat con của chúng.
     * Không dùng pkill -f: trên toybox của OriginOS nó giết luôn sh -c gọi nó.
     * Mẫu "[s]h", "[l]ogcat" để grep không khớp vào chính dòng lệnh này.
     */
    fun stopCommand(): String =
        "ps -A -o PID,ARGS | grep -E '$STOP_PATTERN' | while read pid rest; do kill -9 \$pid; done"

    /** In ra "pid args" nếu script đang chạy; rỗng nếu không. */
    fun statusCommand(): String = "ps -A -o PID,ARGS | grep '[a]kb-gemini.sh'"

    /** Hai file script ghi cạnh file sự kiện (thư mục riêng của app, app tự đọc được). */
    const val STATE_FILE = "script-state.log"
    const val ALIVE_FILE = "script-alive"
    const val ALIVE_INTERVAL_S = 30

    /** Cây cgroup gốc mà script thử chuyển vào, cùng danh sách với starter của Shizuku. */
    const val ESCAPE_CGROUPS = "/sys/fs/cgroup /acct /dev/cg2_bpf"

    fun cgroupCommand(pid: String): String = "cat /proc/$pid/cgroup"

    /**
     * Các dòng /proc/<pid>/cgroup còn nằm trong nhóm tiến trình "uid_X/pid_Y" — nhóm init giết cả cụm
     * khi dừng adbd. Rỗng nghĩa là đã ra nhóm gốc, tắt Gỡ lỗi không kéo script chết theo.
     */
    fun processGroups(cgroup: String): List<String> =
        cgroup.lines().map { it.trim() }.filter { PROCESS_GROUP.containsMatchIn(it) }

    private val PROCESS_GROUP = Regex("/uid_\\d+/pid_\\d+")

    fun adbCommands(script: String): List<String> = listOf(
        "adb shell \"${installCommand(script)}\"",
        "adb shell \"${stopCommand().replace("$", "\\$")}\"",
        "adb shell \"${startCommand()}\"",
    )

    const val STOP_PATTERN = "[s]h /data/local/tmp/(akb-)?gemini\\.sh|[l]ogcat -b events -e .*-m 1"

    /** Dòng "1793120047 voice" → (epoch ms, kind). */
    fun parseEvent(line: String): Pair<Long, String>? {
        val parts = line.trim().split(' ')
        if (parts.size != 2) return null
        val seconds = parts[0].toLongOrNull() ?: return null
        return seconds * 1000 to parts[1]
    }
}
