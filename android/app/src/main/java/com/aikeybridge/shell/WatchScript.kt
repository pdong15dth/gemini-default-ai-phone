package com.aikeybridge.shell

import com.aikeybridge.data.ActionMode
import com.aikeybridge.model.OverlayMic
import com.aikeybridge.model.ShellWatch
import java.util.Base64
import kotlin.math.roundToInt

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
     * @param mic có thì chạm nút Micro sau khi lớp phủ hiện, để vừa khoanh tròn được (phím 219) vừa nghe ngay.
     */
    fun content(
        watch: ShellWatch,
        keyevent: Int,
        eventLog: String,
        dismissDelayMs: Long = 500,
        mic: OverlayMic? = null,
        voiceAction: ActionMode = ActionMode.DEFAULT,
        buttonAction: ActionMode = ActionMode.DEFAULT,
    ): String {
        val pattern = listOfNotNull(watch.panel, watch.button).joinToString("|")
        val panelCase = watch.panel.split('|').filter { it.isNotBlank() }.joinToString("|") { "*$it*" }
        val dismiss = seconds(dismissDelayMs)
        val voiceTapMic = mic != null && (voiceAction == ActionMode.DEFAULT || voiceAction == ActionMode.GEMINI_VOICE)
        val buttonTapMic = mic != null && (buttonAction == ActionMode.DEFAULT || buttonAction == ActionMode.GEMINI_VOICE)
        val activeMic = mic?.takeIf { voiceTapMic || buttonTapMic }
        val voiceCmd = launchCommandFor(voiceAction, keyevent)
        val buttonCmd = launchCommandFor(buttonAction, keyevent)
        val defaultKeyeventCmd = "input keyevent $keyevent"
        return buildString {
            appendLine("#!/system/bin/sh")
            appendLine("# AI Key Bridge — script nền (Shizuku/ADB). Bỏ qua HUP/INT/TERM để rút cáp không chết.")
            appendLine("exec </dev/null >/dev/null 2>&1")
            appendLine("trap \"\" HUP INT TERM")
            appendLine("# Tắt Gỡ lỗi / Tuỳ chọn nhà phát triển thì init dừng adbd và giết cả nhóm tiến trình (cgroup) của nó.")
            appendLine("# Chuyển sang nhóm gốc như Shizuku, logcat/input sinh ra sau đó cũng nằm ngoài nhóm của adbd.")
            appendLine("for cg in $ESCAPE_CGROUPS; do echo \$\$ 2>/dev/null > \"\$cg/cgroup.procs\"; done")
            appendLine("# Trên Android 16 (như vivo X200 Ultra), /sys/fs/cgroup thuộc system:system (0775) nên UID 2000")
            appendLine("# vẫn nằm trong 0::/system/uid_0/pid_<adbd>. Dừng mọi app_process akb_engine cũ để VivoCommonService")
            appendLine("# giữ HasShellAppProc=false, rồi neo giữ adb_wifi_enabled=1 để khi tắt Gỡ lỗi USB (adb_enabled=0)")
            appendLine("# hoặc tắt Tùy chọn nhà phát triển, AdbService không dừng adbd và init không giết script.")
            appendLine("ps -A -o PID,ARGS 2>/dev/null | grep '[a]kb_engine' | while read p r; do kill -9 \$p 2>/dev/null; done")
            appendLine("settings put global adb_wifi_enabled 1 2>/dev/null")
            appendLine("LOG='$eventLog'")
            appendLine("DIR=\"\${LOG%/*}\"")
            appendLine("echo 1 > \"\$DIR/$ENABLED_FILE\" 2>/dev/null")
            val extDir = eventLog.substringBeforeLast('/', "/sdcard/Android/data/com.aikeybridge/files")
            val engineB64 = Base64.getEncoder().encodeToString(
                EngineDaemonMain.engineScriptContent(extDir).toByteArray(Charsets.UTF_8),
            )
            appendLine("if ! ps -A -o ARGS 2>/dev/null | grep -q '[a]kb-engine.sh'; then")
            appendLine("  echo $engineB64 | base64 -d > ${EngineDaemonMain.PATH}.tmp 2>/dev/null && chmod 755 ${EngineDaemonMain.PATH}.tmp 2>/dev/null && mv -f ${EngineDaemonMain.PATH}.tmp ${EngineDaemonMain.PATH} 2>/dev/null")
            appendLine("  [ -f ${EngineDaemonMain.PATH} ] && setsid sh ${EngineDaemonMain.PATH} </dev/null >/dev/null 2>&1 &")
            appendLine("fi")
            appendLine("# Chẩn đoán đọc được cả khi Shizuku đã chết: trạng thái lúc khởi động + nhịp còn sống mỗi ${ALIVE_INTERVAL_S}s.")
            appendLine(
                "{ echo \"start \$(date '+%F %T') pid=\$\$ ppid=\$PPID uid=\$(id -u) ctx=\$(cat /proc/self/attr/current 2>/dev/null)\"; " +
                    "cat /proc/\$\$/cgroup; } > \"\$DIR/$STATE_FILE\" 2>/dev/null",
            )
            appendLine("( while true; do [ \"\$(settings get global adb_wifi_enabled 2>/dev/null)\" != \"1\" ] && settings put global adb_wifi_enabled 1 2>/dev/null; date '+%F %T' > \"\$DIR/$ALIVE_FILE\" 2>/dev/null; sleep $ALIVE_INTERVAL_S; done ) &")
            if (activeMic != null) {
                appendLine("# Toạ độ nút Micro của lớp phủ: ưu tiên tính theo dp (wm density + navigation_mode) từ mép phải/dưới")
                appendLine("# vì thanh nhập FloatyActivity neo 2 nút phải cố định theo dp (Mic cách mép phải ~113dp),")
                appendLine("# tránh chạm nhầm vào ô 'Hỏi Gemini' làm bật bàn phím trên màn rộng dp (X200 Ultra / 411dp) hoặc điều hướng 3 nút.")
                appendLine("set -- \$(wm size | tail -1 | sed 's/.*: //; s/x/ /')")
                appendLine("MX=\$((\$1 * ${permille(activeMic.x)} / 1000)); MY=\$((\$2 * ${permille(activeMic.y)} / 1000))")
                appendLine("D=\$(wm density 2>/dev/null | tail -1 | sed 's/.*: //')")
                appendLine("if [ -n \"\$D\" ] && [ \"\$D\" -gt 0 ] 2>/dev/null; then")
                appendLine("  MX=\$((\$1 - 113 * D / 160))")
                appendLine("  if [ \"\$(settings get secure navigation_mode 2>/dev/null)\" = \"0\" ]; then")
                appendLine("    MY=\$((\$2 - 80 * D / 160))")
                appendLine("  else")
                appendLine("    MY=\$((\$2 - 48 * D / 160))")
                appendLine("  fi")
                appendLine("fi")
            }
            appendLine("while true; do")
            appendLine("  logcat -b events -c")
            appendLine("  line=\$(logcat -b events -e '$pattern' -m 1)")
            appendLine("  case \"\$line\" in")
            appendLine("    # Cửa sổ trợ lý gốc tự đóng cũng sinh dòng có tên nó: bỏ qua, không mở trợ lý ngoài ý muốn.")
            appendLine("    *\"Focus leaving\"*) continue ;;")
            appendLine("    $panelCase) input keyevent 4; kind=voice; sleep $dismiss ;;")
            appendLine("    *) kind=button ;;")
            appendLine("  esac")
            if (voiceCmd == defaultKeyeventCmd && buttonCmd == defaultKeyeventCmd) {
                appendLine("  if dumpsys power 2>/dev/null | grep -q 'mWakefulness=Awake'; then")
                appendLine("    input keyevent $keyevent")
                appendLine("  else")
                appendLine("    input keyevent 224")
                appendLine("    sleep 0.5")
                appendLine("    input keyevent $keyevent")
                appendLine("  fi")
                appendLine("  echo \"\$(date +%T) \$kind · đã gửi phím $keyevent\" > \"\$DIR/$TRACE_FILE\" 2>/dev/null")
            } else {
                appendLine("  if ! dumpsys power 2>/dev/null | grep -q 'mWakefulness=Awake'; then")
                appendLine("    input keyevent 224")
                appendLine("    sleep 0.5")
                appendLine("  fi")
                if (voiceCmd == buttonCmd) {
                    appendLine("  $voiceCmd")
                } else {
                    appendLine("  if [ \"\$kind\" = \"voice\" ]; then")
                    appendLine("    $voiceCmd")
                    appendLine("  else")
                    appendLine("    $buttonCmd")
                    appendLine("  fi")
                }
                appendLine("  echo \"\$(date +%T) \$kind · đã gọi hành động\" > \"\$DIR/$TRACE_FILE\" 2>/dev/null")
            }
            if (activeMic != null) {
                val onlyKind = when {
                    voiceTapMic && !buttonTapMic -> "voice"
                    !voiceTapMic && buttonTapMic -> "button"
                    else -> null
                }
                val indent = if (onlyKind != null) "  " else ""
                if (onlyKind != null) {
                    appendLine("  if [ \"\$kind\" = \"$onlyKind\" ]; then")
                }
                val focused = "timeout 2 dumpsys window displays 2>/dev/null | grep -q 'mCurrentFocus=.*${activeMic.window}'"
                appendLine("$indent  # Chỉ chạm khi lớp phủ đã giữ focus và đứng yên: chạm lúc lớp phủ còn trượt lên là trúng ô nhập chữ,")
                appendLine("$indent  # chạm khi chưa hiện là trúng app bên dưới. timeout để script không kẹt và bỏ lỡ lần gọi sau.")
                appendLine("$indent  i=0")
                appendLine("$indent  while [ \$i -lt $MIC_TRIES ]; do")
                appendLine("$indent    if $focused; then")
                appendLine("$indent      sleep ${seconds(MIC_SETTLE_MS)}")
                appendLine("$indent      if timeout 2 dumpsys input_method 2>/dev/null | grep -q 'mInputShown=true'; then")
                appendLine("$indent        input keyevent 4; sleep 0.4")
                appendLine("$indent      fi")
                appendLine("$indent      $focused && timeout 2 input tap \$MX \$MY && echo \"\$(date +%T) chạm Micro\" >> \"\$DIR/$TRACE_FILE\" 2>/dev/null")
                appendLine("$indent      sleep 0.35")
                appendLine("$indent      if timeout 2 dumpsys input_method 2>/dev/null | grep -q 'mInputShown=true'; then")
                appendLine("$indent        input keyevent 4; sleep 0.4")
                appendLine("$indent        $focused && timeout 2 input tap \$MX \$MY && echo \"\$(date +%T) đóng bàn phím & chạm lại Micro\" >> \"\$DIR/$TRACE_FILE\" 2>/dev/null")
                appendLine("$indent        sleep 0.35; timeout 2 dumpsys input_method 2>/dev/null | grep -q 'mInputShown=true' && input keyevent 4")
                appendLine("$indent      fi")
                appendLine("$indent      break")
                appendLine("$indent    fi")
                appendLine("$indent    # Dọn đa nhiệm giết cả dịch vụ trợ lý của Google; phím đầu tới lúc nó đang chạy lại thì bị bỏ qua.")
                appendLine("$indent    [ \$i -eq $MIC_RESEND_AT ] && timeout 2 input keyevent $keyevent && echo \"\$(date +%T) gửi lại phím $keyevent\" >> \"\$DIR/$TRACE_FILE\" 2>/dev/null")
                if (activeMic.owner != null) {
                    appendLine("$indent    # App trợ lý có lúc kẹt, từ chối mọi lời gọi (\"Invocation source … is not enabled\") tới khi khởi động lại.")
                    appendLine(
                        "$indent    [ \$i -eq 14 ] && timeout 3 am force-stop ${activeMic.owner} && sleep 1 && timeout 2 input keyevent $keyevent && " +
                            "echo \"\$(date +%T) khởi động lại ${activeMic.owner}, gửi lại phím\" >> \"\$DIR/$TRACE_FILE\" 2>/dev/null",
                    )
                }
                appendLine("$indent    sleep 0.2; i=\$((i + 1))")
                appendLine("$indent  done")
                if (onlyKind != null) {
                    appendLine("  fi")
                }
            }
            appendLine("  echo \"\$(date +%s) \$kind\" >> \"\$LOG\" 2>/dev/null")
            appendLine("  sleep 0.5")
            appendLine("done")
        }
    }

    private fun launchCommandFor(mode: ActionMode, keyevent: Int): String = when (mode) {
        ActionMode.GOOGLE_CTS ->
            "am start --user 0 -a com.aikeybridge.action.CTS -n com.aikeybridge/.LaunchActivity --es action_mode GOOGLE_CTS --ez from_script true >/dev/null 2>&1"
        ActionMode.GOOGLE_APP ->
            "monkey -p com.google.android.googlequicksearchbox -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1"
        ActionMode.DEFAULT, ActionMode.GEMINI_VOICE, ActionMode.GEMINI_SCREEN ->
            "input keyevent $keyevent"
    }

    /** 0.642 → 642: sh chỉ tính số nguyên. */
    fun permille(fraction: Float): Int = (fraction * 1000).roundToInt().coerceIn(0, 1000)

    /** Chờ lớp phủ có focus tối đa khoảng 7 giây (gồm cả lần khởi động lại app trợ lý) trước khi bỏ chạm Micro. */
    private const val MIC_TRIES = 28

    /** Sau khoảng 1,4 giây chưa thấy lớp phủ thì gửi lại phím trợ lý một lần. */
    private const val MIC_RESEND_AT = 7

    /** Gửi lại rồi mà khoảng 3 giây vẫn chưa thấy lớp phủ thì dừng hẳn app trợ lý cho nó khởi động lại. */
    private const val MIC_RESET_AT = 14

    /** Lớp phủ trượt lên mất khoảng nửa giây sau khi có focus; chạm sớm hơn là trúng ô nhập chữ. */
    private const val MIC_SETTLE_MS = 600L

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
    fun startCommand(): String = "setsid sh $PATH </dev/null >/dev/null 2>&1 & sleep 0.1"

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
    const val ENABLED_FILE = "script-enabled"

    /** Các bước của lần gọi gần nhất kèm giờ, để biết script kẹt ở đâu. */
    const val TRACE_FILE = "script-trace.log"
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
