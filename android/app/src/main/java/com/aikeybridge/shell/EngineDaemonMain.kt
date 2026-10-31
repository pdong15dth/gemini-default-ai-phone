package com.aikeybridge.shell

import android.content.Context
import java.io.File
import java.util.Base64
import kotlin.system.exitProcess

/**
 * Engine nội bộ chạy dưới quyền `shell` (UID 2000) thuần `/system/bin/sh` (`/data/local/tmp/akb-engine.sh`).
 *
 * Đặc điểm cốt lõi trên Vivo OriginOS & Android 16:
 * 1. **TUYỆT ĐỐI KHÔNG giữ `/system/bin/app_process` trong RAM**:
 *    Trên Vivo OriginOS (`vivo-services.jar` + `SettingsProvider.apk`), mọi `app_process` chạy dưới UID 2000
 *    đều đăng ký `SHELL_MONITOR` với `VivoCommonService`, đặt cờ `mHasShellAppProc = true` (`HasShellAppProc=true`).
 *    Khi cờ này bật, `SettingsProvider.shouldBlockShell()` chặn toàn bộ lệnh `settings put system/secure/global`
 *    từ UID 2000. Chạy thuần `/system/bin/sh` giúp `HasShellAppProc=false` 100% thời gian, mọi lệnh `settings put`
 *    đều hoạt động bình thường.
 * 2. **Sống sót khi tắt Gỡ lỗi USB (`adb_enabled=0`) và tắt Tùy chọn nhà phát triển (`development_settings_enabled=0`)**:
 *    Tự chuyển sang cgroup gốc ([WatchScript.ESCAPE_CGROUPS]). Tuyệt đối KHÔNG tự ý bật `adb_wifi_enabled=1`
 *    khi đang tắt để tránh kích hoạt hộp thoại hệ thống “Cho phép gỡ lỗi không dây trên mạng này?” lặp vô tận
 *    khi người dùng đổi mạng Wi-Fi, tắt Wi-Fi hoặc bấm Từ chối.
 * 3. **Không mở cổng mạng TCP**: Giao tiếp với app qua thư mục IPC riêng (`getExternalFilesDir`),
 *    an toàn tuyệt đối trước mọi trình quét cổng của ứng dụng ngân hàng và VNeID.
 */
object EngineDaemonMain {

    const val PATH = "/data/local/tmp/akb-engine.sh"

    @JvmStatic
    fun main(args: Array<String>) {
        // Nếu được gọi qua app_process cũ, cài đặt & khởi chạy ngay daemon thuần /system/bin/sh rồi thoát ngay
        // để VivoCommonService trả HasShellAppProc về false tức thì.
        val extDir = args.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: "/sdcard/Android/data/com.aikeybridge/files"
        runCatching {
            val scriptFile = File(PATH)
            scriptFile.writeText(engineScriptContent(extDir), Charsets.UTF_8)
            scriptFile.setExecutable(true, false)
            ProcessBuilder("sh", "-c", "setsid sh $PATH </dev/null >/dev/null 2>&1 &")
                .redirectErrorStream(true)
                .start()
                .waitFor()
        }
        exitProcess(0)
    }

    /**
     * Nội dung daemon thuần `/system/bin/sh` chạy ngầm dưới UID 2000 (`/data/local/tmp/akb-engine.sh`).
     */
    fun engineScriptContent(extDir: String): String = buildString {
        appendLine("#!/system/bin/sh")
        appendLine("# AI Key Bridge — Engine nội bộ thuần /system/bin/sh (UID 2000).")
        appendLine("# Không dùng app_process để giữ VivoCommonService HasShellAppProc=false.")
        appendLine("exec </dev/null >/dev/null 2>&1")
        appendLine("trap \"\" HUP INT TERM")
        appendLine("for cg in ${WatchScript.ESCAPE_CGROUPS}; do echo \$\$ 2>/dev/null > \"\$cg/cgroup.procs\"; done")
        appendLine("MY_PID=\$\$")
        appendLine("ps -A -o PID,ARGS 2>/dev/null | grep -E '[a]kb_engine|[c]om\\.aikeybridge:shell|[i]notifyd - .*com\\.aikeybridge|[c]om\\.android\\.commands\\.content\\.Content' | while read p r; do")
        appendLine("  [ \"\$p\" != \"\$MY_PID\" ] && kill -9 \"\$p\" 2>/dev/null")
        appendLine("done")
        appendLine("pm grant com.aikeybridge android.permission.WRITE_SECURE_SETTINGS 2>/dev/null")
        appendLine("pm grant com.aikeybridge android.permission.READ_LOGS 2>/dev/null")
        appendLine("DIR='$extDir'")
        appendLine("IPC_DIR=\"\$DIR/${EngineBinderProvider.IPC_DIR}\"")
        appendLine("PID_FILE=\"\$DIR/${EngineBinderProvider.PID_FILE}\"")
        appendLine("ALIVE_FILE=\"\$DIR/${EngineBinderProvider.ALIVE_FILE}\"")
        appendLine("REQ_FILE=\"\$DIR/${EngineBinderProvider.REQ_FILE}\"")
        appendLine("ENABLED_FILE=\"\$DIR/${WatchScript.ENABLED_FILE}\"")
        appendLine("[ \"\$(stat -c %u \"\$IPC_DIR\" 2>/dev/null)\" = \"2000\" ] && rm -rf \"\$IPC_DIR\" 2>/dev/null")
        appendLine("for sf in \"\$REQ_FILE\" \"\$ENABLED_FILE\" \"\$DIR/${WatchScript.ALIVE_FILE}\" \"\$DIR/script-events.log\" \"\$DIR/${WatchScript.STATE_FILE}\" \"\$DIR/${WatchScript.TRACE_FILE}\"; do")
        appendLine("  [ \"\$(stat -c %u \"\$sf\" 2>/dev/null)\" = \"2000\" ] && rm -f \"\$sf\" 2>/dev/null")
        appendLine("done")
        appendLine("if [ -f \"\$PID_FILE\" ]; then")
        appendLine("  OLD_PID=\$(cat \"\$PID_FILE\" 2>/dev/null)")
        appendLine("  if [ -n \"\$OLD_PID\" ] && [ \"\$OLD_PID\" != \"\$MY_PID\" ]; then")
        appendLine("    ( sleep 1; kill -9 \"\$OLD_PID\" 2>/dev/null ) </dev/null >/dev/null 2>&1 &")
        appendLine("  fi")
        appendLine("fi")
        appendLine("( sleep 1; ps -A -o PID,ARGS 2>/dev/null | grep '[a]kb-engine\\.sh' | while read p r; do [ \"\$p\" != \"\$MY_PID\" ] && kill -9 \"\$p\" 2>/dev/null; done ) </dev/null >/dev/null 2>&1 &")
        appendLine("if grep -q 'ANCHOR_ADB_WIFI' ${WatchScript.PATH} 2>/dev/null; then")
        appendLine("  sed -i '/adb_wifi_enabled/d; /ANCHOR_ADB_WIFI/d' ${WatchScript.PATH} 2>/dev/null")
        appendLine("  ps -A -o PID,ARGS 2>/dev/null | grep -E '${WatchScript.STOP_PATTERN}' | while read p r; do kill -9 \"\$p\" 2>/dev/null; done")
        appendLine("  [ -s \"\$ENABLED_FILE\" ] && setsid sh ${WatchScript.PATH} </dev/null >/dev/null 2>&1 &")
        appendLine("fi")
        appendLine("echo \"\$MY_PID\" > \"\$PID_FILE\" 2>/dev/null")
        appendLine("rm -f \"\$IPC_DIR\"/.tmp-* 2>/dev/null")
        appendLine("date +%s > \"\$ALIVE_FILE\" 2>/dev/null")
        appendLine("run_ipc() {")
        appendLine("  for req in \"\$IPC_DIR\"/req-*.sh; do")
        appendLine("    [ -s \"\$req\" ] || continue")
        appendLine("    id=\"\${req##*/req-}\"")
        appendLine("    id=\"\${id%.sh}\"")
        appendLine("    [ -f \"\$IPC_DIR/out-\$id\" ] && [ -f \"\$IPC_DIR/err-\$id\" ] && [ -f \"\$IPC_DIR/rc-\$id\" ] || continue")
        appendLine("    work=\"/data/local/tmp/.akb-run-\$id.sh\"")
        appendLine("    cp \"\$req\" \"\$work\" 2>/dev/null || continue")
        appendLine("    : > \"\$req\" 2>/dev/null")
        appendLine("    (")
        appendLine("      (")
        appendLine("        (")
        appendLine("          timeout 12 /system/bin/sh \"\$work\" </dev/null 3>&-")
        appendLine("          echo \$? > \"/data/local/tmp/.akb-rc-\$id\"")
        appendLine("        ) 2>&1 1>&3 | cat > \"\$IPC_DIR/err-\$id\"")
        appendLine("      ) 3>&1 | cat > \"\$IPC_DIR/out-\$id\"")
        appendLine("      cat \"/data/local/tmp/.akb-rc-\$id\" > \"\$IPC_DIR/rc-\$id\" 2>/dev/null")
        appendLine("      rm -f \"\$work\" \"/data/local/tmp/.akb-rc-\$id\" 2>/dev/null")
        appendLine("      ( sleep 5; rm -f \"\$req\" \"\$IPC_DIR/out-\$id\" \"\$IPC_DIR/err-\$id\" \"\$IPC_DIR/rc-\$id\" 2>/dev/null ) </dev/null >/dev/null 2>&1 &")
        appendLine("    ) </dev/null >/dev/null 2>&1 &")
        appendLine("  done")
        appendLine("  if [ -s \"\$REQ_FILE\" ]; then")
        appendLine("    : > \"\$REQ_FILE\" 2>/dev/null")
        appendLine("    date +%s > \"\$ALIVE_FILE\" 2>/dev/null")
        appendLine("  fi")
        appendLine("}")
        appendLine("tick=0")
        appendLine("while true; do")
        appendLine("  run_ipc")
        appendLine("  tick=\$((tick + 1))")
        appendLine("  if [ \$tick -ge 20 ]; then")
        appendLine("    tick=0")
        appendLine("    read -r cur_pid < \"\$PID_FILE\" 2>/dev/null")
        appendLine("    [ \"\$cur_pid\" = \"\$MY_PID\" ] || break")
        appendLine("    date +%s > \"\$ALIVE_FILE\" 2>/dev/null")
        appendLine("    if [ -s \"\$ENABLED_FILE\" ] && [ -f ${WatchScript.PATH} ]; then")
        appendLine("      if ! ps -A -o ARGS 2>/dev/null | grep -q '[a]kb-gemini.sh'; then")
        appendLine("        setsid sh ${WatchScript.PATH} </dev/null >/dev/null 2>&1 &")
        appendLine("      fi")
        appendLine("    fi")
        appendLine("  fi")
        appendLine("  sleep 0.1")
        appendLine("done")
    }

    /**
     * Lệnh shell cài đặt và khởi chạy `/data/local/tmp/akb-engine.sh` tách khỏi cgroup của `adbd`
     * mà không dùng `app_process`.
     */
    fun bootstrapCommand(context: Context): String {
        val pkg = context.packageName
        val extDir = context.getExternalFilesDir(null)?.absolutePath
            ?: "/sdcard/Android/data/$pkg/files"
        val encoded = Base64.getEncoder().encodeToString(
            engineScriptContent(extDir).toByteArray(Charsets.UTF_8),
        )
        return buildString {
            append("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; ")
            append("pm grant $pkg android.permission.READ_LOGS 2>/dev/null; ")
            append("ps -A -o PID,ARGS 2>/dev/null | grep -E '[a]kb_engine|[c]om\\.aikeybridge:shell' | while read p r; do kill -9 \$p 2>/dev/null; done; ")
            append("[ \"\$(stat -c %u '$extDir/${EngineBinderProvider.IPC_DIR}' 2>/dev/null)\" = \"2000\" ] && rm -rf '$extDir/${EngineBinderProvider.IPC_DIR}' 2>/dev/null; ")
            append("for sf in '$extDir/${EngineBinderProvider.REQ_FILE}' '$extDir/${WatchScript.ENABLED_FILE}' '$extDir/${WatchScript.ALIVE_FILE}' '$extDir/script-events.log' '$extDir/${WatchScript.STATE_FILE}' '$extDir/${WatchScript.TRACE_FILE}'; do [ \"\$(stat -c %u \"\$sf\" 2>/dev/null)\" = \"2000\" ] && rm -f \"\$sf\" 2>/dev/null; done; ")
            append("date +%s > '$extDir/${EngineBinderProvider.ALIVE_FILE}' 2>/dev/null; ")
            append("echo $encoded | base64 -d > $PATH.tmp; ")
            append("if ! cmp -s $PATH.tmp $PATH 2>/dev/null || ! ps -A -o ARGS 2>/dev/null | grep -q '[a]kb-engine.sh'; then ")
            append("chmod 755 $PATH.tmp && mv -f $PATH.tmp $PATH; ")
            append("setsid sh $PATH </dev/null >/dev/null 2>&1 & sleep 0.15; ")
            append("else rm -f $PATH.tmp 2>/dev/null; fi")
        }
    }

    /**
     * Lệnh tự động tắt Gỡ lỗi USB (`adb_enabled=0`) và ẩn Tùy chọn nhà phát triển (`development_settings_enabled=0`)
     * cho app ngân hàng / VNeID. Không tự ý bật `adb_wifi_enabled=1` để tránh bung popup Gỡ lỗi không dây.
     */
    const val LOCK_DEV_MODE_CMD: String =
        "pm grant com.aikeybridge android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; " +
            "ps -A -o PID,ARGS 2>/dev/null | grep -E '[a]kb_engine|[c]om\\.aikeybridge:shell|[c]om\\.android\\.commands\\.content\\.Content' | while read p r; do kill -9 \$p 2>/dev/null; done; " +
            "cmd activity broadcast -n com.aikeybridge/.shell.WirelessAdbPairingReceiver -a com.aikeybridge.action.LOCK_DEV_MODE --receiver-include-background >/dev/null 2>&1; " +
            "settings put global adb_enabled 0; " +
            "settings put global development_settings_enabled 0"
}
