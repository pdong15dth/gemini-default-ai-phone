package com.aikeybridge.shell

import com.aikeybridge.data.ActionMode
import com.aikeybridge.model.ShellWatch
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchScriptTest {

    private val watch = ShellWatch("CopilotPanelWindow", "ACTION_BUTTON_TO_START_FUNCTIONS")
    private val log = "/storage/emulated/0/Android/data/com.aikeybridge/files/script-events.log"

    @Test
    fun scriptMatchesReadmeFlowAndLogsEachRedirect() {
        val script = WatchScript.content(watch, 231, log)

        assertTrue(script.contains("trap \"\" HUP INT TERM"))
        assertTrue(script.contains("logcat -b events -e 'CopilotPanelWindow|ACTION_BUTTON_TO_START_FUNCTIONS' -m 1"))
        // Trợ lý gốc tự đóng cũng sinh dòng có tên cửa sổ: không được coi là vừa gọi.
        assertTrue(script.indexOf("*\"Focus leaving\"*) continue ;;") in 0 until script.indexOf("*CopilotPanelWindow*)"))
        assertTrue(script.contains("*CopilotPanelWindow*) input keyevent 4; kind=voice; sleep 0.5 ;;"))
        assertTrue(script.contains("*) kind=button ;;"))
        assertTrue(script.contains("grep -q 'mWakefulness=Awake'"))
        assertTrue(script.contains("input keyevent 224"))
        assertTrue(script.contains("input keyevent 231"))
        assertTrue(script.contains("LOG='$log'"))
        assertTrue(script.contains("echo \"\$(date +%s) \$kind\" >> \"\$LOG\""))
    }

    @Test
    fun buttonPathHasNoFixedDelayBeforeAssistant() {
        val script = WatchScript.content(watch, 231, log, dismissDelayMs = 700)
        val lines = script.lines().map { it.trim() }

        // Nút: không còn "sleep 0.5" chung trước khi gọi trợ lý như bản cũ.
        val esac = lines.indexOf("esac")
        assertEquals("if dumpsys power 2>/dev/null | grep -q 'mWakefulness=Awake'; then", lines[esac + 1])
        assertEquals("input keyevent 231", lines[esac + 2])
        // Khẩu lệnh dùng độ trễ của cấu hình.
        assertTrue(script.contains("kind=voice; sleep 0.7 ;;"))
    }

    @Test
    fun micTapOnlyWhenOverlayHasFocus() {
        val mic = com.aikeybridge.model.OverlayMic("FloatyActivity", 0.686f, 0.940f)
        val script = WatchScript.content(watch, 219, log, mic = mic)

        assertTrue(script.contains("set -- \$(wm size | tail -1 | sed 's/.*: //; s/x/ /')"))
        assertTrue(script.contains("MX=\$((\$1 * 686 / 1000)); MY=\$((\$2 * 940 / 1000))"))
        assertTrue(script.contains("MX=\$((\$1 - 113 * D / 160))"))
        assertTrue(script.contains("MY=\$((\$2 - 80 * D / 160))"))
        assertTrue(script.contains("MY=\$((\$2 - 48 * D / 160))"))
        // Chạm khi lớp phủ chưa hiện là chạm vào app bên dưới (có thể là app ngân hàng); chạm lúc nó còn trượt lên
        // là trúng ô nhập chữ. Mọi lệnh có timeout để script không kẹt rồi bỏ lỡ lần gọi sau.
        val focused = "timeout 2 dumpsys window displays 2>/dev/null | grep -q 'mCurrentFocus=.*FloatyActivity'"
        assertTrue(script.contains("    if $focused; then\n      sleep 0.6\n"))
        assertTrue(script.contains("      $focused && timeout 2 input tap \$MX \$MY"))
        assertTrue(script.contains("dumpsys input_method 2>/dev/null | grep -q 'mInputShown=true'"))
        // Dọn đa nhiệm giết dịch vụ trợ lý của Google: chưa thấy lớp phủ thì gửi lại phím một lần.
        assertTrue(script.contains("[ \$i -eq 7 ] && timeout 2 input keyevent 219"))
        // Không khai báo gói trợ lý thì không dừng app nào.
        assertFalse(script.contains("am force-stop"))

        // App Google kẹt ("Invocation source … is not enabled"): gửi lại vẫn không lên thì khởi động lại nó.
        val owned = WatchScript.content(watch, 219, log, mic = mic.copy(owner = "com.google.android.googlequicksearchbox"))
        assertTrue(
            owned.contains(
                "[ \$i -eq 14 ] && timeout 3 am force-stop com.google.android.googlequicksearchbox && sleep 1 && timeout 2 input keyevent 219",
            ),
        )
        assertTrue(script.indexOf("input tap") > script.indexOf("    input keyevent 219\n  fi"))
        assertFalse(WatchScript.content(watch, 219, log).contains("input tap"))
    }

    @Test
    fun secondsFormatting() {
        assertEquals("0.5", WatchScript.seconds(500))
        assertEquals("1.2", WatchScript.seconds(1150))
        assertEquals("0.0", WatchScript.seconds(-20))
    }

    @Test
    fun installCommandRoundTripsThroughBase64() {
        val script = WatchScript.content(watch, 231, log)
        val command = WatchScript.installCommand(script)
        val encoded = command.removePrefix("echo ").substringBefore(" | base64 -d")

        assertEquals(script, String(Base64.getDecoder().decode(encoded), Charsets.UTF_8))
        assertTrue(command.endsWith("> ${WatchScript.PATH} && chmod 755 ${WatchScript.PATH}"))
    }

    @Test
    fun stopPatternHitsScriptsButNotItsOwnCommandLine() {
        val pattern = Regex(WatchScript.STOP_PATTERN)
        val stop = WatchScript.stopCommand()

        assertTrue(pattern.containsMatchIn(" 2031 sh /data/local/tmp/akb-gemini.sh"))
        assertTrue(pattern.containsMatchIn("29961 sh /data/local/tmp/gemini.sh"))
        assertTrue(pattern.containsMatchIn(" 1322 logcat -b events -e CopilotPanelWindow|ACTION_BUTTON_TO_START_FUNCTIONS -m 1"))
        assertTrue(pattern.containsMatchIn(" 1450 logcat -b events -e com.miui.voiceassist -m 1"))
        // Dòng ps của chính "sh -c <stop>" và của grep không được khớp, nếu không lệnh sẽ tự giết mình.
        assertFalse(pattern.containsMatchIn("sh -c $stop"))
        assertFalse(pattern.containsMatchIn("grep -E ${WatchScript.STOP_PATTERN}"))
        assertFalse(stop.contains("pkill"))
    }

    @Test
    fun startCommandOnlyStarts() {
        val start = WatchScript.startCommand()
        assertFalse(start.contains("kill"))
        assertTrue(start.startsWith("setsid sh ${WatchScript.PATH}"))
    }

    @Test
    fun scriptLeavesAdbdProcessGroupBeforeSpawningChildren() {
        val lines = WatchScript.content(watch, 231, log).lines().map { it.trim() }
        val escape = lines.indexOf(
            "for cg in /sys/fs/cgroup /acct /dev/cg2_bpf; do echo \$\$ 2>/dev/null > \"\$cg/cgroup.procs\"; done",
        )

        assertTrue(escape > 0)
        assertTrue(escape < lines.indexOf("while true; do"))
    }

    @Test
    fun scriptRecordsStateAndHeartbeatNextToEventLog() {
        val script = WatchScript.content(watch, 231, log)

        assertTrue(script.contains("DIR=\"\${LOG%/*}\""))
        assertTrue(script.contains("settings put global adb_wifi_enabled 1 2>/dev/null"))
        assertTrue(script.contains("echo 1 > \"\$DIR/script-enabled\" 2>/dev/null"))
        assertTrue(script.contains("cat /proc/\$\$/cgroup; } > \"\$DIR/script-state.log\""))
        assertTrue(script.contains("date '+%F %T' > \"\$DIR/script-alive\" 2>/dev/null; sleep 30; done ) </dev/null >/dev/null 2>&1 &"))
    }

    @Test
    fun detectsProcessGroupOfAdbd() {
        assertEquals(listOf("0::/uid_0/pid_1834"), WatchScript.processGroups("0::/uid_0/pid_1834\n"))
        assertEquals(
            listOf("1:cpuacct:/uid_2000/pid_771"),
            WatchScript.processGroups("1:cpuacct:/uid_2000/pid_771\n0::/\n"),
        )
        assertTrue(WatchScript.processGroups("0::/\n").isEmpty())
        assertTrue(WatchScript.processGroups("0::/system\n").isEmpty())
    }

    @Test
    fun parsesEventLines() {
        assertEquals(1_793_120_047_000L to "voice", WatchScript.parseEvent("1793120047 voice"))
        assertEquals(1_793_120_050_000L to "button", WatchScript.parseEvent(" 1793120050 button \n"))
        assertNull(WatchScript.parseEvent("garbage"))
    }

    @Test
    fun multiPatternPanelFormatsValidShellCaseBranches() {
        val multi = ShellWatch("com.heytap.speechassist|com.coloros.speechassist", null)
        val script = WatchScript.content(multi, 231, log)

        assertTrue(script.contains("*com.heytap.speechassist*|*com.coloros.speechassist*) input keyevent 4; kind=voice; sleep 0.5 ;;"))
    }

    @Test
    fun perActionCustomModesGenerateIndependentVoiceAndButtonBranches() {
        val mic = com.aikeybridge.model.OverlayMic("FloatyActivity", 0.642f, 0.939f)
        val script = WatchScript.content(
            watch = watch,
            keyevent = 219,
            eventLog = log,
            mic = mic,
            voiceAction = ActionMode.GEMINI_VOICE,
            buttonAction = ActionMode.GOOGLE_CTS,
        )

        assertTrue(script.contains("if [ \"\$kind\" = \"voice\" ]; then"))
        assertTrue(script.contains("input keyevent 219"))
        assertTrue(script.contains("am start --user 0 -a com.aikeybridge.action.CTS -n com.aikeybridge/.LaunchActivity --es action_mode GOOGLE_CTS"))
        assertTrue(script.contains("input tap \$MX \$MY"))

        val screenOnly = WatchScript.content(
            watch = watch,
            keyevent = 219,
            eventLog = log,
            mic = mic,
            voiceAction = ActionMode.GEMINI_SCREEN,
            buttonAction = ActionMode.GEMINI_SCREEN,
        )
        assertFalse(screenOnly.contains("input tap"))
    }

    @Test
    fun shellLoopsAndBranchesAreBalanced() {
        val mic = com.aikeybridge.model.OverlayMic("FloatyActivity", 0.642f, 0.939f, "com.google.android.googlequicksearchbox")
        val scripts = listOf(
            WatchScript.content(watch, 231, log),
            WatchScript.content(watch, 219, log, mic = mic),
            WatchScript.content(watch, 219, log, mic = mic, voiceAction = ActionMode.GEMINI_VOICE, buttonAction = ActionMode.GOOGLE_CTS),
            WatchScript.content(watch, 219, log, mic = mic, voiceAction = ActionMode.GEMINI_VOICE, buttonAction = ActionMode.GEMINI_SCREEN),
            WatchScript.content(watch, 219, log, mic = mic, voiceAction = ActionMode.GOOGLE_CTS, buttonAction = ActionMode.GOOGLE_CTS),
            EngineDaemonMain.engineScriptContent("/storage/emulated/0/Android/data/com.aikeybridge/files"),
        )
        val tokenRegex = Regex("\\b(do|done|then|fi|case|esac)\\b")
        for (script in scripts) {
            val codeLines = script.lines().filterNot { it.trimStart().startsWith("#") }
            val tokens = codeLines.flatMap { line -> tokenRegex.findAll(line).map { it.value } }
            assertEquals(tokens.count { it == "do" }, tokens.count { it == "done" })
            assertEquals(tokens.count { it == "then" }, tokens.count { it == "fi" })
            assertEquals(tokens.count { it == "case" }, tokens.count { it == "esac" })
        }
    }

    @Test
    fun engineDaemonPreservesInFlightIpcAndAvoidsSelfOverwrite() {
        val daemon = EngineDaemonMain.engineScriptContent("/storage/emulated/0/Android/data/com.aikeybridge/files")
        // Daemon khởi động lại tuyệt đối không được xoá .run-* hay req-* đang chạy dở.
        assertFalse(daemon.contains("rm -f \"\$IPC_DIR\"/req-*"))
        assertFalse(daemon.contains("rm -f \"\$IPC_DIR\"/.run-*"))
        assertTrue(daemon.contains("rm -f \"\$IPC_DIR\"/.tmp-* 2>/dev/null"))
        // Không được redirect trực tiếp fd 1/fd 2 của /system/bin/sh "$work" vào file FUSE vì SELinux
        // chặn system_server nhận fuse:file descriptor qua Binder (Failed transaction 2147483646).
        assertFalse(daemon.contains("/system/bin/sh \"\$work\" > \"\$IPC_DIR/out-\$id\""))
        assertTrue(daemon.contains("timeout 12 /system/bin/sh \"\$work\" </dev/null 3>&-"))
        assertTrue(daemon.contains(") 2>&1 1>&3 | cat > \"\$IPC_DIR/err-\$id\""))
        assertTrue(daemon.contains(") 3>&1 | cat > \"\$IPC_DIR/out-\$id\""))
        // Neo giữ adb_wifi_enabled=1 định kỳ (kèm broadcast sang app UID có WRITE_SECURE_SETTINGS)
        // để tắt Gỡ lỗi USB trên Android 16 (kể cả khi shizuku_server đang chạy làm HasShellAppProc=true) không làm adbd dừng.
        assertTrue(daemon.contains("[ \"\$(settings get global adb_wifi_enabled 2>/dev/null)\" != \"1\" ] && settings put global adb_wifi_enabled 1 2>/dev/null"))
        assertTrue(daemon.contains("com.aikeybridge.action.ANCHOR_ADB_WIFI"))
        assertTrue(daemon.contains("pm grant com.aikeybridge android.permission.WRITE_SECURE_SETTINGS"))
        assertTrue(EngineDaemonMain.LOCK_DEV_MODE_CMD.contains("[c]om\\.android\\.commands\\.content\\.Content"))
        assertTrue(EngineDaemonMain.LOCK_DEV_MODE_CMD.contains("com.aikeybridge.action.LOCK_DEV_MODE"))

        val watchScript = WatchScript.content(watch, 219, log)
        assertTrue(watchScript.contains("if ! ps -A -o ARGS 2>/dev/null | grep -q '[a]kb-engine.sh'; then"))
        assertTrue(watchScript.contains("mv -f ${EngineDaemonMain.PATH}.tmp ${EngineDaemonMain.PATH}"))
        assertTrue(watchScript.contains("com.aikeybridge.action.ANCHOR_ADB_WIFI"))
    }
}
