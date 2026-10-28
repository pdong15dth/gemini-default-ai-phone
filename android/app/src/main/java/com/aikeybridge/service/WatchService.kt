package com.aikeybridge.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import com.aikeybridge.MainActivity
import com.aikeybridge.R
import com.aikeybridge.TriggerActivity
import com.aikeybridge.data.ActiveConfig
import com.aikeybridge.data.ConfigSource
import com.aikeybridge.data.PermissionsReader
import com.aikeybridge.data.SettingsRepository
import com.aikeybridge.data.UsageMode
import com.aikeybridge.diagnostics.Attempt
import com.aikeybridge.diagnostics.AttemptLog
import com.aikeybridge.diagnostics.Diagnostics
import com.aikeybridge.shell.ShizukuShell
import com.aikeybridge.shell.WatchScript
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/** Service nghe nền có đang chạy không; UI đọc để hiện trạng thái mà không cần Shizuku. */
object WatchStatus {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun setRunning(value: Boolean) {
        _running.value = value
    }
}

/**
 * Chế độ Script nền chạy ngay trong tiến trình app: đọc event log, thấy cửa sổ trợ lý gốc hoặc nút AI
 * thì gọi trợ lý đã chọn.
 *
 * Script shell chạy bằng quyền shell nên luôn nằm trong nhóm tiến trình của adbd; tắt Gỡ lỗi USB là init
 * dừng adbd và giết cả nhóm, không có root thì không thoát ra được. Tiến trình app không dính adbd, chỉ cần
 * READ_LOGS cấp một lần bằng `pm grant` — còn nguyên khi tắt Gỡ lỗi và sau khi khởi động lại máy.
 *
 * Còn Shizuku thì làm y như script (Back đóng trợ lý gốc, phím trợ lý mở lớp phủ); hết Shizuku thì
 * mở thẳng chế độ nghe của trợ lý bằng intent.
 */
class WatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val busy = AtomicBoolean(false)
    private lateinit var source: ConfigSource
    private lateinit var shell: ShizukuShell
    private lateinit var watcher: LogcatWatcher

    @Volatile
    private var quietUntil = 0L

    @Volatile
    private var match: WatchMatch? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Diagnostics.init(this)
        AttemptLog.init(this)
        source = ConfigSource(this)
        shell = ShizukuShell(this)
        watcher = LogcatWatcher(listOf("events"), OWN_ONLY_AFTER_MS, ::onLine)
        createChannel()
        val initial = notification(LogStatus.access.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, initial)
        }
        WatchStatus.setRunning(true)
        scope.launch {
            LogStatus.access.collect { access ->
                getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(access))
                if (access == LogAccess.SYSTEM) Diagnostics.success("watch", "Đọc được event log hệ thống · đang nghe.")
                if (access == LogAccess.OWN_ONLY) {
                    Diagnostics.warn("watch", "Android chưa cho đọc log hệ thống — mở app và chọn Cho phép khi được hỏi.")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when {
            !LogStatus.hasReadLogs(this) -> {
                watcher.stop()
                LogStatus.set(LogAccess.NO_PERMISSION)
                Diagnostics.warn("watch", "Thiếu READ_LOGS — bấm Kích hoạt (cần Shizuku) hoặc cấp bằng ADB.")
            }
            // Android 13+ từ chối đọc log khi app không ở trước. Mở lại logcat lúc người dùng đang mở app
            // để hệ thống hỏi lại; đang đọc được thì giữ nguyên tiến trình logcat cũ.
            !watcher.running || LogStatus.access.value == LogAccess.OWN_ONLY -> {
                watcher.stop()
                watcher.start(scope)
                Diagnostics.info("watch", "Mở event log trong app.")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        watcher.stop()
        scope.cancel()
        shell.close()
        WatchStatus.setRunning(false)
        Diagnostics.warn("watch", "Đã dừng nghe nền.")
        super.onDestroy()
    }

    /** Chạy trên luồng đọc logcat. */
    private fun onLine(line: String) {
        if (SystemClock.elapsedRealtime() < quietUntil || busy.get()) return
        val config = source.current()
        val watch = config.shellWatch ?: return
        val current = match?.takeIf { it.watch == watch } ?: WatchMatch(watch).also { match = it }
        val kind = current.kindOf(line) ?: return
        if (!busy.compareAndSet(false, true)) return
        scope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            val outcome = try {
                fire(config, kind)
            } catch (throwable: Throwable) {
                Diagnostics.error("watch", throwable.message ?: throwable.toString())
                false to "lỗi: ${throwable.javaClass.simpleName}"
            } finally {
                // Cửa sổ trợ lý gốc đóng/mở còn sinh thêm vài dòng log; bỏ qua như script ngủ 1.5s rồi xoá log.
                quietUntil = SystemClock.elapsedRealtime() + COOLDOWN_MS
                busy.set(false)
            }
            val voice = kind == WatchMatch.Kind.VOICE
            AttemptLog.record(
                Attempt(
                    at = System.currentTimeMillis(),
                    flow = if (voice) config.flowLabel else "Nút → ${config.target.displayName}",
                    ok = outcome.first,
                    durationMs = SystemClock.elapsedRealtime() - startedAt,
                    detail = "nghe nền · ${if (voice) "khẩu lệnh" else "nút"} · ${outcome.second}",
                ),
            )
        }
    }

    private suspend fun fire(config: ActiveConfig, kind: WatchMatch.Kind): Pair<Boolean, String> {
        val voice = kind == WatchMatch.Kind.VOICE
        val target = config.target
        val keyevent = target.shellKeyevent
        val overlayAllowed = !target.overlayNeedsDefaultAssistant || PermissionsReader.googleAssistantDefault(this)
        Diagnostics.info("watch", if (voice) "Bắt được ${config.profile.assistantName}." else "Bắt được nút AI.")

        if (keyevent != null && overlayAllowed && shizukuReady()) {
            val result = shell.exec(WatchScript.fireCommand(voice, keyevent, config.dismissDelayMs))
            if (result.ok) {
                Diagnostics.success("watch", "Đã gọi ${target.displayName} bằng phím trợ lý (Shizuku).")
                return true to "Shizuku · phím $keyevent"
            }
            Diagnostics.warn("watch", "Gửi phím qua Shizuku lỗi: ${result.output} — mở bằng intent.")
        }

        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val asleep = getSystemService(PowerManager::class.java)?.isInteractive == false
        val outcome = if (locked || asleep) {
            val started = runCatching {
                startActivity(
                    Intent(this, TriggerActivity::class.java)
                        .putExtra(TriggerActivity.EXTRA_VOICE, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            }
            LaunchOutcome(started.isSuccess, started.exceptionOrNull()?.message ?: "TriggerActivity")
        } else {
            AssistantLauncher.launchVoice(this, target)
        }
        Diagnostics.log(
            if (outcome.ok) Diagnostics.Level.SUCCESS else Diagnostics.Level.ERROR,
            "watch",
            "Không Shizuku · ${outcome.detail}" +
                if (voice) " · không đóng được ${config.profile.assistantName}" else "",
        )
        return outcome.ok to "intent · ${outcome.detail}"
    }

    private fun shizukuReady(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Nghe nền", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Giữ AI Key Bridge nghe trợ lý gốc khi chạy nền."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun notification(access: LogAccess): Notification {
        val config = source.current()
        val wake = config.profile.wakePhrase?.let { "“$it”" } ?: config.profile.assistantName
        val (title, text) = when (access) {
            LogAccess.SYSTEM -> "Đang nghe $wake" to
                "→ ${config.target.displayName} · dùng app ngân hàng thì tắt Tuỳ chọn nhà phát triển & Gỡ lỗi USB, vẫn chạy"
            LogAccess.OWN_ONLY -> "Chạm để cho phép đọc log" to
                "Android chưa cho app đọc log hệ thống. Mở app và chọn Cho phép."
            LogAccess.NO_PERMISSION -> "Thiếu quyền đọc log" to "Mở app và bấm Kích hoạt (cần Shizuku một lần)."
            LogAccess.WAITING, LogAccess.OFF -> "Đang mở event log…" to "Nếu Android hỏi quyền truy cập log, chọn Cho phép."
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "watch"
        private const val NOTIFICATION_ID = 7

        /** Kênh events có thể im vài giây; đợi lâu hơn trước khi kết luận Android chỉ cho đọc log của chính app. */
        private const val OWN_ONLY_AFTER_MS = 12_000L
        private const val COOLDOWN_MS = 1_500L

        /** Bật hoặc mở lại logcat nếu Android vừa từ chối. Gọi nhiều lần không sao. */
        fun start(context: Context): Boolean = runCatching {
            context.startForegroundService(Intent(context, WatchService::class.java))
        }.onFailure { Diagnostics.error("watch", "Không bật được service nghe nền: ${it.message}") }.isSuccess

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchService::class.java))
        }

        /** Có nên chạy không: đang chọn Script nền và người dùng đã bật. */
        fun wanted(settings: SettingsRepository): Boolean {
            val state = settings.state.value
            return state.watchEnabled && state.usageMode == UsageMode.SCRIPT
        }
    }
}

/** Khởi động lại máy hoặc cập nhật app: bật lại nghe nền nếu người dùng đã bật trước đó. */
class WatchBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!WatchService.wanted(SettingsRepository(context))) return
        Diagnostics.init(context)
        Diagnostics.info("watch", "Tự bật nghe nền sau ${if (intent.action == Intent.ACTION_BOOT_COMPLETED) "khởi động máy" else "cập nhật app"}.")
        WatchService.start(context)
    }
}
