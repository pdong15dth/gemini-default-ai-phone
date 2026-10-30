package com.aikeybridge.shell

import android.Manifest
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
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.aikeybridge.MainActivity
import com.aikeybridge.R
import com.aikeybridge.diagnostics.Diagnostics
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.NetworkInterface
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class WirelessAdbUiState(
    val pairedBefore: Boolean = false,
    val pairingActive: Boolean = false,
    val pairingHost: String = "127.0.0.1",
    val pairingPort: Int = -1,
    val connectHost: String = "127.0.0.1",
    val connectPort: Int = -1,
    val connecting: Boolean = false,
    val statusText: String = "",
)

/**
 * Engine Gỡ lỗi không dây (Wireless ADB) tích hợp trực tiếp trong APK — thay thế hoàn toàn Shizuku.
 *
 * Cơ chế:
 * 1. Lần đầu: Người dùng bấm "Ghép nối Gỡ lỗi không dây", app chạy Foreground Service [WirelessAdbPairingService]
 *    (giữ MulticastLock + ngăn ROM đóng băng tiến trình khi người dùng sang Cài đặt), đẩy thông báo có ô nhập 6 số
 *    ngay trên thanh trạng thái và mở màn hình Gỡ lỗi không dây.
 * 2. Người dùng mở "Ghép nối thiết bị bằng mã ghép nối", nhập 6 số vào thông báo.
 * 3. Sau khi ghép nối (hoặc những lần sau khi bật Gỡ lỗi không dây): App tự kết nối vào cổng TLS nội bộ,
 *    khởi chạy [EngineDaemonMain] (`UID 2000` tách khỏi cgroup của `adbd`), rồi có thể tắt ngay Tùy chọn nhà phát triển.
 */
object BuiltInWirelessAdb {

    private const val KEY_FILE = "akb_adb_rsa.pk8"
    private const val CERT_FILE = "akb_adb_cert.der"
    private const val PREFS_NAME = "akb_wireless_adb"
    private const val KEY_PAIRED = "paired_once"

    internal const val CHANNEL_ID = "akb_wireless_adb_pairing"
    internal const val NOTIF_ID = 20001
    const val ACTION_REPLY_PAIRING_CODE = "com.aikeybridge.action.REPLY_PAIRING_CODE"
    const val ACTION_STOP_PAIRING = "com.aikeybridge.action.STOP_PAIRING"
    const val KEY_PAIRING_CODE_TEXT = "pairing_code"

    private const val SERVICE_TYPE_TLS_PAIRING = "_adb-tls-pairing._tcp"
    private const val SERVICE_TYPE_TLS_CONNECT = "_adb-tls-connect._tcp"

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(WirelessAdbUiState())
    val state: StateFlow<WirelessAdbUiState> = _state.asStateFlow()

    @Volatile
    private var managerInstance: AbsAdbConnectionManager? = null

    @Volatile
    private var pairingMdns: LocalAdbMdns? = null

    @Volatile
    private var connectMdns: LocalAdbMdns? = null

    fun init(context: Context) {
        val appContext = context.applicationContext
        val paired = isPaired(appContext)
        _state.update { it.copy(pairedBefore = paired) }
    }

    fun isPaired(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PAIRED, false)

    private fun setPaired(context: Context, paired: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PAIRED, paired)
            .apply()
        _state.update { it.copy(pairedBefore = paired) }
    }

    private fun getManager(context: Context): AbsAdbConnectionManager {
        managerInstance?.let { return it }
        synchronized(this) {
            managerInstance?.let { return it }
            val appContext = context.applicationContext
            val (privKey, cert) = loadOrCreateCredentials(appContext)
            return object : AbsAdbConnectionManager() {
                init {
                    setApi(Build.VERSION.SDK_INT)
                    setTimeout(6, TimeUnit.SECONDS)
                    setThrowOnUnauthorised(true)
                }

                override fun getPrivateKey(): PrivateKey = privKey
                override fun getCertificate(): Certificate = cert
                override fun getDeviceName(): String = "AIKeyBridge"
            }.also { managerInstance = it }
        }
    }

    /**
     * Bắt đầu chế độ ghép nối 6 số: chạy Foreground Service giữ tiến trình sống khi sang Cài đặt,
     * bật quét mDNS cổng `_adb-tls-pairing._tcp` và `_adb-tls-connect._tcp`, đồng thời hiển thị
     * thông báo nhập mã 6 số trên thanh trạng thái.
     */
    fun startPairingFlow(context: Context) {
        val appContext = context.applicationContext
        stopMdns()
        _state.update {
            it.copy(
                pairingActive = true,
                pairingPort = -1,
                statusText = "Đang tìm cổng ghép nối… Hãy mở Gỡ lỗi không dây → Ghép nối thiết bị bằng mã ghép nối.",
            )
        }

        val initialTitle = "Ghép nối 6 số · Bấm ⌄ để mở nút nhập"
        val initialBody = "Mở “Ghép nối thiết bị bằng mã ghép nối”, bấm ⌄ ở góc phải thông báo này rồi chạm “Nhập mã 6 số”."
        startPairingForegroundService(appContext, initialTitle, initialBody)
        showPairingNotification(appContext, initialTitle, initialBody)

        val pMdns = LocalAdbMdns(
            context = appContext,
            serviceType = SERVICE_TYPE_TLS_PAIRING,
            onLost = {
                _state.update { it.copy(pairingPort = -1) }
            },
        ) { host, port ->
            if (port > 0) {
                val prevPort = _state.value.pairingPort
                _state.update {
                    it.copy(
                        pairingHost = host,
                        pairingPort = port,
                        statusText = "Đã thấy cổng ghép nối $port · Nhập 6 số trên thanh thông báo.",
                    )
                }
                if (prevPort != port) {
                    Diagnostics.info("adb", "Phát hiện cổng ghép nối Gỡ lỗi không dây: $host:$port")
                    showPairingNotification(
                        appContext,
                        title = "Đã thấy cổng ($port) · Bấm ⌄ để Nhập 6 số",
                        body = "Bấm mũi tên ⌄ ở góc phải thông báo để hiện nút “Nhập mã 6 số”, rồi gõ 6 số đang hiện trên Cài đặt.",
                    )
                }
            }
        }
        val cMdns = LocalAdbMdns(appContext, SERVICE_TYPE_TLS_CONNECT) { host, port ->
            if (port > 0) {
                val prevPort = _state.value.connectPort
                _state.update { it.copy(connectHost = host, connectPort = port) }
                if (prevPort != port) {
                    Diagnostics.info("adb", "Phát hiện cổng kết nối Gỡ lỗi không dây: $host:$port")
                }
                // Nếu máy đã ghép nối từ trước và người dùng vừa gạt bật Gỡ lỗi không dây, tự kết nối luôn!
                if (_state.value.pairedBefore &&
                    !EngineBinderProvider.isAlive() &&
                    !_state.value.connecting &&
                    _state.value.pairingPort <= 0
                ) {
                    scope.launch {
                        val booted = connectAndBootstrapDaemon(appContext)
                        if (booted && _state.value.pairingActive) {
                            stopMdns()
                            stopPairingForegroundService(appContext)
                            _state.update {
                                it.copy(
                                    pairingActive = false,
                                    connecting = false,
                                    statusText = "Engine nội bộ đã tự động kết nối!",
                                )
                            }
                            showResultNotification(
                                appContext,
                                title = "Đã bật Engine nội bộ thành công!",
                                body = "Chạm vào đây để quay lại AI Key Bridge và bật Script nền (tự tắt Gỡ lỗi).",
                            )
                        }
                    }
                }
            }
        }
        pairingMdns = pMdns
        connectMdns = cMdns
        pMdns.start()
        cMdns.start()
    }

    fun stopPairingFlow(context: Context) {
        val appContext = context.applicationContext
        stopMdns()
        stopPairingForegroundService(appContext)
        _state.update { it.copy(pairingActive = false, pairingPort = -1) }
        val nm = appContext.getSystemService(NotificationManager::class.java)
        nm?.cancel(NOTIF_ID)
    }

    private fun stopMdns() {
        runCatching { pairingMdns?.stop() }
        runCatching { connectMdns?.stop() }
        pairingMdns = null
        connectMdns = null
    }

    /**
     * Ghép nối với mã 6 số người dùng nhập từ thông báo hoặc từ giao diện, sau đó tự kết nối
     * và khởi chạy luôn [EngineDaemonMain] (`UID 2000`).
     */
    fun submitPairingCode(context: Context, rawCode: String, onResult: ((Boolean, String) -> Unit)? = null) {
        val appContext = context.applicationContext
        val digits = rawCode.filter { it.isDigit() }
        val code = if (digits.length > 6) digits.takeLast(6) else digits
        if (code.length != 6) {
            val msg = "Mã ghép nối phải gồm đúng 6 chữ số (bạn nhập: $rawCode)."
            showPairingNotification(appContext, "Mã chưa đúng 6 số", msg)
            onResult?.invoke(false, msg)
            return
        }

        scope.launch {
            _state.update { it.copy(connecting = true, statusText = "Đang ghép nối với mã $code…") }
            showPairingNotification(
                appContext,
                title = "Đang ghép nối với mã $code…",
                body = "Vui lòng giữ nguyên màn hình Gỡ lỗi không dây trong giây lát.",
                ongoing = true,
            )

            val cachedPort = _state.value.pairingPort
            val cachedHost = _state.value.pairingHost
            // Luôn ưu tiên truy vấn NsdService trực tiếp (chỉ mất ~7ms sau khi Broadcast đánh thức tiến trình)
            // phòng trường hợp Vivo OriginOS fast_freezer đóng băng app lúc người dùng đóng/mở lại hộp thoại 6 số.
            val discovered = discoverServiceOnce(appContext, SERVICE_TYPE_TLS_PAIRING, 2_800L)
            var port = discovered?.second?.takeIf { it > 0 } ?: cachedPort
            var host = discovered?.first?.takeIf { it.isNotBlank() } ?: cachedHost
            if (port > 0) {
                _state.update { it.copy(pairingHost = host, pairingPort = port) }
                Diagnostics.info("adb", "Cổng ghép nối hiện tại khi gửi mã: $host:$port")
            }

            if (port <= 0) {
                val msg = "Chưa thấy cổng ghép nối. Hãy giữ hộp thoại “Ghép nối thiết bị bằng mã ghép nối” đang mở trên màn hình rồi gửi mã."
                _state.update { it.copy(connecting = false, statusText = msg) }
                showPairingNotification(appContext, "Ghép nối chưa thành công", msg)
                onResult?.invoke(false, msg)
                return@launch
            }

            val mgr = getManager(appContext)
            val candidatePorts = listOfNotNull(
                port.takeIf { it > 0 },
                cachedPort.takeIf { it > 0 && it != port },
            )
            val candidateHosts = listOfNotNull(
                "127.0.0.1",
                host.takeIf { it.isNotBlank() && it != "127.0.0.1" },
            )
            var pairedOk = false
            var lastError: String? = null
            outer@ for (targetPort in candidatePorts) {
                for (targetHost in candidateHosts) {
                    val ok = runCatching {
                        mgr.pair(targetHost, targetPort, code)
                    }.onFailure { e ->
                        lastError = e.message ?: e.javaClass.simpleName
                        Diagnostics.warn("adb", "Ghép nối thử tại $targetHost:$targetPort thất bại: $lastError")
                    }.getOrDefault(false)
                    if (ok) {
                        port = targetPort
                        pairedOk = true
                        break@outer
                    }
                }
            }

            if (!pairedOk) {
                val msg = "Ghép nối thất bại (cổng $port). Kiểm tra lại 6 số đang hiện trên hộp thoại ghép nối."
                _state.update { it.copy(connecting = false, statusText = msg) }
                showPairingNotification(appContext, "Ghép nối chưa thành công", msg)
                onResult?.invoke(false, msg)
                return@launch
            }

            setPaired(appContext, true)
            _state.update {
                it.copy(
                    statusText = "Đã ghép nối! Đang khởi chạy Engine nội bộ…",
                )
            }
            Diagnostics.success("adb", "Đã ghép nối Gỡ lỗi không dây thành công (cổng $port).")
            showPairingNotification(
                appContext,
                title = "Đã ghép nối! Đang bật Engine nội bộ…",
                body = "Đang khởi chạy Engine UID 2000 trong RAM…",
                ongoing = true,
            )

            // Chờ nhẹ để adbd cập nhật danh sách khóa vừa ghép nối trước khi mở kết nối TLS
            delay(250)
            val booted = connectAndBootstrapDaemon(appContext)
            stopMdns()
            stopPairingForegroundService(appContext)
            _state.update {
                it.copy(
                    pairingActive = false,
                    connecting = false,
                    statusText = if (booted) "Engine nội bộ đã chạy!" else "Đã ghép nối · chạm Kết nối Engine.",
                )
            }
            showResultNotification(
                appContext,
                title = if (booted) "Đã bật Engine nội bộ thành công!" else "Đã ghép nối thành công!",
                body = if (booted) {
                    "Chạm vào đây để quay lại AI Key Bridge và bật Script nền (tự tắt Gỡ lỗi)."
                } else {
                    "Chạm để quay lại AI Key Bridge."
                },
            )
            // Đưa người dùng quay lại thẳng màn hình AI Key Bridge nếu hệ thống cho phép
            runCatching {
                val openApp = Intent(appContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                appContext.startActivity(openApp)
            }
            onResult?.invoke(booted, if (booted) "Đã ghép nối và bật Engine nội bộ!" else "Đã ghép nối Gỡ lỗi không dây.")
        }
    }

    /**
     * Kết nối tới Gỡ lỗi không dây (nếu đang bật) và dựng [EngineDaemonMain] (`UID 2000`) lên RAM.
     * Trả về `true` nếu [EngineBinderProvider.isAlive] đã nhận được Binder.
     */
    suspend fun connectAndBootstrapDaemon(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (EngineBinderProvider.isAlive()) return@withContext true
        val appContext = context.applicationContext

        // Thử đánh thức daemon sẵn có trong RAM trước (trường hợp đã bật trong lần khởi động này)
        EngineBinderProvider.requestDaemonReconnect(appContext)
        repeat(6) {
            if (EngineBinderProvider.isAlive()) return@withContext true
            delay(60)
        }

        _state.update { it.copy(connecting = true) }
        try {
            val mgr = getManager(appContext)
            val connected = ensureAdbConnected(appContext, mgr)
            if (!connected) {
                return@withContext false
            }

            setPaired(appContext, true)

            // Đặt sẵn yêu cầu kết nối mới trước khi khởi chạy EngineDaemonMain
            EngineBinderProvider.requestDaemonReconnect(appContext)

            // Khởi chạy EngineDaemonMain tách khỏi cgroup adbd
            val cmd = EngineDaemonMain.bootstrapCommand(appContext)
            execDirectOnAdb(mgr, cmd)

            // Chờ tối đa 3 giây để EngineDaemonMain đẩy IShellService Binder về EngineBinderProvider
            repeat(35) { step ->
                if (EngineBinderProvider.isAlive()) {
                    Diagnostics.success("engine", "Engine nội bộ (UID 2000) đã kết nối qua Gỡ lỗi không dây.")
                    return@withContext true
                }
                if (step == 10 || step == 22) {
                    EngineBinderProvider.requestDaemonReconnect(appContext)
                }
                delay(85)
            }
            return@withContext EngineBinderProvider.isAlive()
        } finally {
            _state.update { it.copy(connecting = false) }
        }
    }

    /**
     * Thực thi một lệnh trực tiếp qua luồng `AdbStream` khi `EngineDaemonMain` chưa kịp lên.
     */
    suspend fun execViaWirelessAdb(context: Context, command: String): ShellResult? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val mgr = getManager(appContext)
        if (!ensureAdbConnected(appContext, mgr)) return@withContext null
        execDirectOnAdb(mgr, command)
    }

    private suspend fun ensureAdbConnected(context: Context, mgr: AbsAdbConnectionManager): Boolean {
        if (runCatching { mgr.isConnected }.getOrDefault(false)) return true

        repeat(3) { attempt ->
            var port = _state.value.connectPort
            var host = _state.value.connectHost
            if (port <= 0 || attempt > 0) {
                val discovered = discoverServiceOnce(
                    context = context,
                    serviceType = SERVICE_TYPE_TLS_CONNECT,
                    timeoutMs = if (attempt == 0) 3_800L else 2_500L,
                )
                if (discovered != null) {
                    host = discovered.first
                    port = discovered.second
                    _state.update { it.copy(connectHost = host, connectPort = port) }
                }
            }

            if (port > 0) {
                val candidates = listOfNotNull(
                    "127.0.0.1",
                    host.takeIf { it.isNotBlank() && it != "127.0.0.1" },
                )
                for (targetHost in candidates) {
                    val ok = runCatching {
                        if (!mgr.isConnected) {
                            runCatching { mgr.disconnect() }
                        }
                        mgr.connect(targetHost, port)
                    }.onFailure { e ->
                        Diagnostics.warn("adb", "Kết nối ADB $targetHost:$port (lần ${attempt + 1}) lỗi: ${e.message}")
                    }.getOrDefault(false)
                    if (ok && runCatching { mgr.isConnected }.getOrDefault(false)) {
                        return true
                    }
                }
            }

            if (attempt < 2) {
                delay(350)
            }
        }
        return false
    }

    private fun execDirectOnAdb(mgr: AbsAdbConnectionManager, command: String): ShellResult? {
        val marker = "__AKB_EXIT_"
        val wrapped = "{ $command\n}; echo \"\n$marker\$?\""
        return try {
            mgr.openStream("shell:$wrapped").use { stream ->
                val reader = stream.openInputStream().bufferedReader(StandardCharsets.UTF_8)
                val outLines = mutableListOf<String>()
                var exitCode = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    val markerIdx = line.indexOf(marker)
                    if (markerIdx >= 0) {
                        if (markerIdx > 0) {
                            outLines.add(line.substring(0, markerIdx))
                        }
                        val codeStr = line.substring(markerIdx + marker.length).trim()
                        exitCode = codeStr.toIntOrNull() ?: 0
                        break
                    }
                    outLines.add(line)
                }
                val stdout = outLines.joinToString("\n").trimEnd('\r', '\n')
                ShellResult(exitCode, stdout, "")
            }
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Quét mDNS một lần trong khoảng [timeoutMs] để tìm `(host, port)` của dịch vụ ADB nội bộ.
     */
    private suspend fun discoverServiceOnce(
        context: Context,
        serviceType: String,
        timeoutMs: Long,
    ): Pair<String, Int>? = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<Pair<String, Int>>()
        val watcher = LocalAdbMdns(context, serviceType) { host, port ->
            if (port > 0 && !deferred.isCompleted) {
                deferred.complete(host to port)
            }
        }
        watcher.start()
        try {
            withTimeoutOrNull(timeoutMs) {
                deferred.await()
            }
        } finally {
            watcher.stop()
        }
    }

    /**
     * Kiểm tra địa chỉ IP phát hiện qua mDNS có thuộc về chính thiết bị này hay không.
     * Lưu ý: Không bao giờ dùng `ServerSocket.bind("127.0.0.1", port)` như `libadb-android` vì `adbd`
     * lắng nghe trên `[::]:port` (wildcard IPv6), khiến `bind("127.0.0.1", port)` luôn thành công giả!
     */
    internal fun isLocalAddress(context: Context, rawHost: String): Boolean {
        val normalized = rawHost.substringBefore('%').trim()
        if (normalized.isEmpty() ||
            normalized == "127.0.0.1" ||
            normalized == "::1" ||
            normalized.equals("localhost", ignoreCase = true)
        ) {
            return true
        }

        val localAddresses = mutableSetOf<String>()
        runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val linkProps = cm?.getLinkProperties(cm.activeNetwork)
            linkProps?.linkAddresses?.forEach { la ->
                la.address?.hostAddress?.substringBefore('%')?.let { localAddresses.add(it.lowercase()) }
            }
        }
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                for (nif in interfaces) {
                    val addrs = nif.inetAddresses ?: continue
                    for (addr in addrs) {
                        addr.hostAddress?.substringBefore('%')?.let { localAddresses.add(it.lowercase()) }
                    }
                }
            }
        }

        if (localAddresses.isEmpty()) {
            return true
        }
        return localAddresses.contains(normalized.lowercase())
    }

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    internal fun ensureChannel(context: Context, nm: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ghép nối Engine Gỡ lỗi không dây",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Nhập mã 6 số để ghép nối Engine nội bộ ngay trên màn hình Cài đặt"
        }
        nm.createNotificationChannel(channel)
    }

    internal fun buildPairingNotification(
        context: Context,
        title: String,
        body: String,
        ongoing: Boolean = true,
    ): Notification {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm != null) ensureChannel(context, nm)

        val remoteInput = RemoteInput.Builder(KEY_PAIRING_CODE_TEXT)
            .setLabel("Nhập 6 chữ số ghép nối")
            .build()

        val replyIntent = Intent(context, WirelessAdbPairingReceiver::class.java).apply {
            action = ACTION_REPLY_PAIRING_CODE
        }
        val replyFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val replyPending = PendingIntent.getBroadcast(context, 1, replyIntent, replyFlags)

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.lc_terminal,
            "Nhập mã 6 số",
            replyPending,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()

        val stopIntent = Intent(context, WirelessAdbPairingReceiver::class.java).apply {
            action = ACTION_STOP_PAIRING
        }
        val stopPending = PendingIntent.getBroadcast(
            context,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.lc_terminal)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(ongoing)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(replyAction)
            .addAction(0, "Huỷ", stopPending)
            .build()
    }

    private fun showPairingNotification(
        context: Context,
        title: String,
        body: String,
        ongoing: Boolean = true,
    ) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val notif = buildPairingNotification(context, title, body, ongoing)
        runCatching { nm.notify(NOTIF_ID, notif) }
    }

    private fun startPairingForegroundService(context: Context, title: String, body: String) {
        runCatching {
            val intent = Intent(context, WirelessAdbPairingService::class.java).apply {
                putExtra(WirelessAdbPairingService.EXTRA_TITLE, title)
                putExtra(WirelessAdbPairingService.EXTRA_BODY, body)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private fun stopPairingForegroundService(context: Context) {
        runCatching {
            context.stopService(Intent(context, WirelessAdbPairingService::class.java))
        }
    }

    private fun showResultNotification(context: Context, title: String, body: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context, nm)
        val openApp = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            context,
            4,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.lc_check)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { nm.notify(NOTIF_ID, notif) }
    }

    private fun loadOrCreateCredentials(context: Context): Pair<PrivateKey, Certificate> {
        val keyFile = File(context.filesDir, KEY_FILE)
        val certFile = File(context.filesDir, CERT_FILE)
        if (keyFile.exists() && certFile.exists()) {
            runCatching {
                val kf = KeyFactory.getInstance("RSA")
                val priv = kf.generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                val cf = CertificateFactory.getInstance("X.509")
                val cert = cf.generateCertificate(ByteArrayInputStream(certFile.readBytes()))
                return priv to cert
            }
        }

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val kp = kpg.generateKeyPair()
        val certBytes = generateSelfSignedX509Der(kp.private, kp.public)
        val cf = CertificateFactory.getInstance("X.509")
        val cert = cf.generateCertificate(ByteArrayInputStream(certBytes))

        runCatching {
            keyFile.writeBytes(kp.private.encoded)
            certFile.writeBytes(certBytes)
        }
        return kp.private to cert
    }

    /**
     * Tạo chứng chỉ X.509 v3 tự ký (ASN.1 DER) cho cặp khóa RSA 2048 mà không cần thư viện BouncyCastle nặng nề.
     */
    private fun generateSelfSignedX509Der(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        // OID sha256WithRSAEncryption: 1.2.840.113549.1.1.11
        val sigAlgId = derSequence(
            derOid(byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B)),
            derNull(),
        )
        // Subject / Issuer: CN=AIKeyBridge (OID 2.5.4.3 = 55 04 03)
        val cnAttr = derSequence(
            derOid(byteArrayOf(0x55, 0x04, 0x03)),
            derUtf8String("AIKeyBridge"),
        )
        val rdn = derSet(cnAttr)
        val name = derSequence(rdn)

        val validity = derSequence(
            derUtcTime("240101000000Z"),
            derUtcTime("491231235959Z"),
        )

        val version = byteArrayOf(0xA0.toByte(), 0x03, 0x02, 0x01, 0x02) // [0] EXPLICIT v3 (2)
        val serial = derInteger(byteArrayOf(0x01, 0x23, 0x45, 0x67))
        val spki = publicKey.encoded // SubjectPublicKeyInfo đã ở dạng DER Sequence chuẩn

        val tbsCert = derSequence(
            version,
            serial,
            sigAlgId,
            name,
            validity,
            name,
            spki,
        )

        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(privateKey)
        sig.update(tbsCert)
        val sigBytes = sig.sign()

        return derSequence(
            tbsCert,
            sigAlgId,
            derBitString(sigBytes),
        )
    }

    private fun derTag(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val len = content.size
        if (len < 128) {
            out.write(len)
        } else {
            val lenBytes = mutableListOf<Byte>()
            var tmp = len
            while (tmp > 0) {
                lenBytes.add(0, (tmp and 0xFF).toByte())
                tmp = tmp ushr 8
            }
            out.write(0x80 or lenBytes.size)
            for (b in lenBytes) out.write(b.toInt())
        }
        out.write(content)
        return out.toByteArray()
    }

    private fun derSequence(vararg items: ByteArray): ByteArray {
        val buf = ByteArrayOutputStream()
        for (item in items) buf.write(item)
        return derTag(0x30, buf.toByteArray())
    }

    private fun derSet(vararg items: ByteArray): ByteArray {
        val buf = ByteArrayOutputStream()
        for (item in items) buf.write(item)
        return derTag(0x31, buf.toByteArray())
    }

    private fun derOid(oidBytes: ByteArray): ByteArray = derTag(0x06, oidBytes)
    private fun derNull(): ByteArray = byteArrayOf(0x05, 0x00)
    private fun derInteger(bytes: ByteArray): ByteArray = derTag(0x02, bytes)
    private fun derUtf8String(text: String): ByteArray = derTag(0x0C, text.toByteArray(StandardCharsets.UTF_8))
    private fun derUtcTime(ascii: String): ByteArray = derTag(0x17, ascii.toByteArray(StandardCharsets.US_ASCII))
    private fun derBitString(bytes: ByteArray): ByteArray {
        val prefixed = ByteArray(bytes.size + 1)
        prefixed[0] = 0x00
        System.arraycopy(bytes, 0, prefixed, 1, bytes.size)
        return derTag(0x03, prefixed)
    }
}

/**
 * Bộ quét mDNS nội bộ thay thế hoàn toàn `io.github.muntashirakon.adb.android.AdbMdns`
 * (vốn bị lỗi `ServerSocket.bind("127.0.0.1", port)` trên Android 14–17 khi `adbd` bind `[::]:port`).
 */
internal class LocalAdbMdns(
    private val context: Context,
    private val serviceType: String,
    private val onLost: (() -> Unit)? = null,
    private val onResolved: (host: String, port: Int) -> Unit,
) {
    private val nsdManager: NsdManager? = context.getSystemService(NsdManager::class.java)
    private val running = AtomicBoolean(false)

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            running.set(false)
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            running.set(false)
        }

        override fun onDiscoveryStarted(serviceType: String?) {
            running.set(true)
        }

        override fun onDiscoveryStopped(serviceType: String?) {
            running.set(false)
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
            if (serviceInfo != null) {
                resolveServiceWithRetry(serviceInfo, 0)
            }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
            onLost?.invoke()
        }
    }

    fun start() {
        val mgr = nsdManager ?: return
        if (running.getAndSet(true)) return
        runCatching {
            mgr.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }.onFailure {
            running.set(false)
        }
    }

    fun stop() {
        val mgr = nsdManager ?: return
        if (!running.getAndSet(false)) return
        runCatching {
            mgr.stopServiceDiscovery(discoveryListener)
        }
    }

    private fun resolveServiceWithRetry(serviceInfo: NsdServiceInfo, attempt: Int) {
        val mgr = nsdManager ?: return
        if (!running.get()) return
        runCatching {
            mgr.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(failedInfo: NsdServiceInfo?, errorCode: Int) {
                        if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE && attempt < 6 && running.get()) {
                            BuiltInWirelessAdb.scope.launch {
                                delay(110L * (attempt + 1))
                                resolveServiceWithRetry(serviceInfo, attempt + 1)
                            }
                        }
                    }

                    override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                        val info = resolvedInfo ?: return
                        val port = info.port
                        if (port <= 0) return
                        val rawHost = info.host?.hostAddress?.substringBefore('%').orEmpty()
                        if (BuiltInWirelessAdb.isLocalAddress(context, rawHost)) {
                            onResolved(rawHost.ifBlank { "127.0.0.1" }, port)
                        }
                    }
                },
            )
        }
    }
}

/**
 * Foreground Service giữ cho tiến trình `com.aikeybridge` không bị Vivo OriginOS / Android 14+
 * đóng băng (`Cached App Freezer`) trong lúc người dùng chuyển sang ứng dụng Cài đặt để lấy mã 6 số.
 */
class WirelessAdbPairingService : Service() {

    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onCreate() {
        super.onCreate()
        runCatching {
            val wifi = applicationContext.getSystemService(WifiManager::class.java)
            multicastLock = wifi?.createMulticastLock("akb_wireless_adb_mdns")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Đang chờ mở hộp thoại Ghép nối…"
        val body = intent?.getStringExtra(EXTRA_BODY)
            ?: "Vào Gỡ lỗi không dây → Ghép nối thiết bị bằng mã ghép nối, rồi bấm Nhập mã 6 số."
        val notif = BuiltInWirelessAdb.buildPairingNotification(this, title, body, ongoing = true)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    BuiltInWirelessAdb.NOTIF_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(BuiltInWirelessAdb.NOTIF_ID, notif)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
        }
        multicastLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BODY = "extra_body"
    }
}

class WirelessAdbPairingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            BuiltInWirelessAdb.ACTION_STOP_PAIRING -> {
                BuiltInWirelessAdb.stopPairingFlow(context)
            }
            BuiltInWirelessAdb.ACTION_REPLY_PAIRING_CODE -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val code = results?.getCharSequence(BuiltInWirelessAdb.KEY_PAIRING_CODE_TEXT)?.toString().orEmpty()
                val pendingResult = goAsync()
                BuiltInWirelessAdb.submitPairingCode(context, code) { _, _ ->
                    runCatching { pendingResult.finish() }
                }
            }
        }
    }
}
