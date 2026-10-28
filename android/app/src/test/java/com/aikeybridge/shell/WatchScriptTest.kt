package com.aikeybridge.shell

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
        val mic = com.aikeybridge.model.OverlayMic("FloatyActivity", 0.642f, 0.939f)
        val script = WatchScript.content(watch, 219, log, mic = mic)

        assertTrue(script.contains("set -- \$(wm size | tail -1 | sed 's/.*: //; s/x/ /')"))
        assertTrue(script.contains("MX=\$((\$1 * 642 / 1000)); MY=\$((\$2 * 939 / 1000))"))
        // Chạm khi lớp phủ chưa hiện là chạm vào app bên dưới (có thể là app ngân hàng); chạm lúc nó còn trượt lên
        // là trúng ô nhập chữ. Mọi lệnh có timeout để script không kẹt rồi bỏ lỡ lần gọi sau.
        val focused = "timeout 2 dumpsys window displays 2>/dev/null | grep -q 'mCurrentFocus=.*FloatyActivity'"
        assertTrue(script.contains("    if $focused; then\n      sleep 0.6\n      $focused && timeout 2 input tap \$MX \$MY"))
        // Dọn đa nhiệm giết dịch vụ trợ lý của Google: chưa thấy lớp phủ thì gửi lại phím một lần.
        assertTrue(script.contains("[ \$i -eq 7 ] && timeout 2 input keyevent 219"))
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
        assertTrue(script.contains("cat /proc/\$\$/cgroup; } > \"\$DIR/script-state.log\""))
        assertTrue(script.contains("date '+%F %T' > \"\$DIR/script-alive\" 2>/dev/null; sleep 30; done ) &"))
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
}
