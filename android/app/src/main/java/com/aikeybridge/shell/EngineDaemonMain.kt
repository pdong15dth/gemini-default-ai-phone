package com.aikeybridge.shell

import android.content.AttributionSource
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Tiến trình Engine nội bộ chạy dưới quyền `shell` (UID 2000) qua `/system/bin/app_process`.
 *
 * Đặc điểm cốt lõi:
 * 1. Tự chuyển PID sang nhóm cgroup gốc ([WatchScript.ESCAPE_CGROUPS]) ngay khi khởi chạy.
 *    Trên Android 16 (như vivo X200 Ultra), nơi `/sys/fs/cgroup` thuộc quyền `system:system` (`0775`) khiến
 *    tiến trình UID 2000 vẫn nằm trong `0::/system/uid_0/pid_<adbd>`, Engine tự động neo giữ `adb_wifi_enabled=1`
 *    qua `IContentObserver` thời gian thực để khi người dùng tắt Gỡ lỗi USB (`adb_enabled=0`) hoặc tắt
 *    Tùy chọn nhà phát triển (`development_settings_enabled=0`), `AdbService` không bao giờ dừng `adbd`
 *    và `init` không bao giờ giết tiến trình này hay script nền.
 * 2. Không mở bất kỳ cổng mạng TCP nào (tránh mọi trình quét cổng của app ngân hàng / VNeID),
 *    mà truyền thẳng [IShellService] Binder vào [EngineBinderProvider] của app.
 * 3. Khi app đóng hoặc bị buộc dừng, Engine KHÔNG tự gọi hệ thống mở lại app ngầm; chỉ khi người dùng
 *    mở lại app (ghi `.engine-req`), Engine mới đẩy lại Binder trong ~150ms.
 */
object EngineDaemonMain {

    private const val TAG = "AKB_EngineDaemon"

    @Volatile
    private var cachedSettingsProvider: Any? = null
    private val settingsToken = Binder()
    private val guardBurstUntilMs = AtomicLong(0L)
    private val guardLock = Object()

    @JvmStatic
    fun main(args: Array<String>) {
        escapeAddbCgroup()
        if (Looper.getMainLooper() == null) {
            @Suppress("DEPRECATION")
            Looper.prepareMainLooper()
        }

        val extDir = args.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: "/sdcard/Android/data/com.aikeybridge/files"
        val dir = File(extDir)
        runCatching { dir.mkdirs() }

        val myPid = Process.myPid()
        val pidFile = File(dir, EngineBinderProvider.PID_FILE)
        val reqFile = File(dir, EngineBinderProvider.REQ_FILE)
        val aliveFile = File(dir, EngineBinderProvider.ALIVE_FILE)
        val enabledFile = File(dir, WatchScript.ENABLED_FILE)

        // Nếu có phiên bản daemon cũ đang chạy thì dừng nó trước
        runCatching {
            if (pidFile.exists()) {
                val oldPid = pidFile.readText().trim().toIntOrNull()
                if (oldPid != null && oldPid != myPid && oldPid > 100) {
                    Process.killProcess(oldPid)
                }
            }
            pidFile.writeText(myPid.toString())
        }

        // Khởi tạo neo giữ adbd (adb_wifi_enabled=1) để tắt Gỡ lỗi USB / Nhà phát triển trên Android 16 không chết
        startAdbKeepAliveGuard()

        val service = ShellUserService()
        var lastReqToken = readReqToken(reqFile)
        var lastAliveWrite = 0L
        var lastScriptCheck = 0L
        var clientAlive = false
        var pendingPushRetries = 4

        fun pushBinder(): Boolean {
            val reply = callEngineProvider(service.asBinder()) ?: return false
            val token = runCatching {
                reply.classLoader = EngineDaemonMain::class.java.classLoader
                reply.getBinder(EngineBinderProvider.KEY_CLIENT_TOKEN)
            }.getOrNull()
            if (token != null && token.pingBinder()) {
                clientAlive = true
                runCatching {
                    token.linkToDeath({ clientAlive = false }, 0)
                }
                Log.i(TAG, "Delivered IShellService binder to app (pid=$myPid)")
                return true
            }
            return false
        }

        // Đẩy binder ngay khi khởi chạy (lúc này app đang mở sẵn)
        if (pushBinder()) {
            pendingPushRetries = 0
        }

        // Vòng lặp nhẹ theo dõi nhịp sống & tự kết nối lại khi app mở lại (kể cả khi đã tắt Gỡ lỗi)
        while (true) {
            try {
                val currentOwnerPid = runCatching { pidFile.readText().trim().toIntOrNull() }.getOrNull()
                if (currentOwnerPid != null && currentOwnerPid != myPid) {
                    break
                }

                val now = System.currentTimeMillis()
                if (now - lastAliveWrite >= 15_000L) {
                    lastAliveWrite = now
                    runCatching { aliveFile.writeText("$now pid=$myPid") }
                }

                if (now - lastScriptCheck >= 3_000L) {
                    lastScriptCheck = now
                    if (getGlobalSettingFast("adb_wifi_enabled") != "1") {
                        putGlobalSettingFast("adb_wifi_enabled", "1")
                    }
                    ensureWatchScriptAlive(enabledFile)
                }

                val reqToken = readReqToken(reqFile)
                if (reqToken.isNotEmpty() && reqToken != lastReqToken) {
                    lastReqToken = reqToken
                    pendingPushRetries = 6
                }

                if (pendingPushRetries > 0 && !clientAlive) {
                    if (pushBinder()) {
                        pendingPushRetries = 0
                    } else {
                        pendingPushRetries -= 1
                    }
                }

                Thread.sleep(if (pendingPushRetries > 0 && !clientAlive) 150L else 250L)
            } catch (_: InterruptedException) {
                break
            } catch (_: Throwable) {
                runCatching { Thread.sleep(1000L) }
            }
        }
    }

    private fun startAdbKeepAliveGuard() {
        runCatching {
            putGlobalSettingFast("adb_wifi_enabled", "1")
        }

        val guardThread = Thread({
            while (true) {
                try {
                    val now = System.currentTimeMillis()
                    val until = guardBurstUntilMs.get()
                    if (now < until) {
                        if (getGlobalSettingFast("adb_wifi_enabled") != "1") {
                            putGlobalSettingFast("adb_wifi_enabled", "1")
                        }
                        Thread.sleep(1L)
                    } else {
                        synchronized(guardLock) {
                            guardLock.wait(5_000L)
                        }
                    }
                } catch (_: InterruptedException) {
                    break
                } catch (_: Throwable) {
                    runCatching { Thread.sleep(10L) }
                }
            }
        }, "akb_adb_guard").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
            start()
        }

        runCatching {
            val observer = object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    onSettingChanged()
                }

                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    onSettingChanged()
                }

                override fun onChange(selfChange: Boolean, uri: Uri?, flags: Int) {
                    onSettingChanged()
                }

                private fun onSettingChanged() {
                    if (getGlobalSettingFast("adb_wifi_enabled") != "1") {
                        putGlobalSettingFast("adb_wifi_enabled", "1")
                    }
                    val target = System.currentTimeMillis() + 350L
                    guardBurstUntilMs.set(target)
                    synchronized(guardLock) {
                        guardLock.notifyAll()
                    }
                }
            }

            val iObserver = ContentObserver::class.java
                .getDeclaredMethod("getContentObserver")
                .apply { isAccessible = true }
                .invoke(observer) ?: return@runCatching

            val smClass = Class.forName("android.os.ServiceManager")
            val contentBinder = smClass.getDeclaredMethod("getService", String::class.java)
                .invoke(null, "content") as? IBinder ?: return@runCatching
            val stubClass = Class.forName("android.content.IContentService\$Stub")
            val contentService = stubClass.getDeclaredMethod("asInterface", IBinder::class.java)
                .invoke(null, contentBinder) ?: return@runCatching

            val uris = listOf(
                Uri.parse("content://settings/global/adb_wifi_enabled"),
                Uri.parse("content://settings/global/adb_enabled"),
                Uri.parse("content://settings/global/development_settings_enabled"),
            )
            for (uri in uris) {
                registerObserverOnService(contentService, uri, iObserver)
            }
            Log.i(TAG, "Registered real-time adbd keepalive observer (${guardThread.name})")
        }.onFailure { t ->
            Log.w(TAG, "registerContentObserver failed: ${t.message}")
        }
    }

    private fun registerObserverOnService(contentService: Any, uri: Uri, iObserver: Any) {
        val iCsClass = runCatching { Class.forName("android.content.IContentService") }
            .getOrDefault(contentService.javaClass)
        for (m in iCsClass.methods) {
            if (m.name != "registerContentObserver") continue
            m.isAccessible = true
            val p = m.parameterTypes
            runCatching {
                when (p.size) {
                    5 -> {
                        m.invoke(contentService, uri, false, iObserver, 0, Build.VERSION.SDK_INT)
                        return
                    }
                    4 -> {
                        m.invoke(contentService, uri, false, iObserver, 0)
                        return
                    }
                    3 -> {
                        m.invoke(contentService, uri, false, iObserver)
                        return
                    }
                }
            }
        }
    }

    private fun getSettingsProvider(): Any? {
        cachedSettingsProvider?.let { return it }
        synchronized(this) {
            cachedSettingsProvider?.let { return it }
            val am = getActivityManager() ?: return null
            val holder = getContentProviderExternal(am, "settings", settingsToken) ?: return null
            val providerField = holder.javaClass.getDeclaredField("provider").apply { isAccessible = true }
            val provider = providerField.get(holder) ?: return null
            cachedSettingsProvider = provider
            return provider
        }
    }

    private fun putGlobalSettingFast(key: String, value: String): Boolean {
        val provider = getSettingsProvider() ?: return false
        val extras = Bundle().apply {
            putString("value", value)
        }
        return runCatching {
            invokeProviderCall(provider, "settings", "PUT_global", extras, arg = key)
            true
        }.getOrElse {
            cachedSettingsProvider = null
            false
        }
    }

    private fun getGlobalSettingFast(key: String): String? {
        val provider = getSettingsProvider() ?: return null
        return runCatching {
            val reply = invokeProviderCall(provider, "settings", "GET_global", Bundle.EMPTY, arg = key)
            reply?.getString("value")
        }.getOrElse {
            cachedSettingsProvider = null
            null
        }
    }

    private fun ensureWatchScriptAlive(enabledFile: File) {
        if (!enabledFile.exists()) return
        val scriptFile = File(WatchScript.PATH)
        if (!scriptFile.exists()) return
        val running = isWatchScriptRunning()
        if (!running) {
            runCatching {
                ProcessBuilder("sh", "-c", WatchScript.startCommand())
                    .redirectErrorStream(true)
                    .start()
                    .waitFor()
                Log.i(TAG, "Respawned background WatchScript (${WatchScript.PATH})")
            }
        }
    }

    private fun isWatchScriptRunning(): Boolean = runCatching {
        val procDir = File("/proc")
        val entries = procDir.listFiles() ?: return false
        for (entry in entries) {
            val name = entry.name
            if (name.isEmpty() || !name[0].isDigit()) continue
            val cmdline = runCatching {
                File(entry, "cmdline").readBytes().toString(Charsets.UTF_8).replace('\u0000', ' ')
            }.getOrDefault("")
            if (cmdline.contains("akb-gemini.sh")) return true
        }
        false
    }.getOrDefault(false)

    private fun readReqToken(reqFile: File): String =
        runCatching { if (reqFile.exists()) reqFile.readText().trim() else "" }.getOrDefault("")

    private fun escapeAddbCgroup() {
        val pid = Process.myPid().toString()
        val cgroupRoots = listOf("/sys/fs/cgroup", "/acct", "/dev/cg2_bpf")
        for (root in cgroupRoots) {
            runCatching {
                val procs = File(root, "cgroup.procs")
                if (procs.exists() && procs.canWrite()) {
                    procs.writeText(pid)
                }
            }
        }
    }

    private fun callEngineProvider(serviceBinder: IBinder): Bundle? {
        val authority = EngineBinderProvider.AUTHORITY
        val token = Binder()
        val am = getActivityManager() ?: return null
        var holder: Any? = null
        try {
            holder = getContentProviderExternal(am, authority, token) ?: return null
            val providerField = holder.javaClass.getDeclaredField("provider").apply { isAccessible = true }
            val provider = providerField.get(holder) ?: return null
            val extras = Bundle().apply {
                putBinder(EngineBinderProvider.KEY_BINDER, serviceBinder)
            }
            return invokeProviderCall(provider, authority, EngineBinderProvider.METHOD_SEND_BINDER, extras)
        } catch (t: Throwable) {
            Log.w(TAG, "callEngineProvider failed: ${t.javaClass.simpleName}: ${t.message}")
            return null
        } finally {
            if (holder != null) {
                runCatching { removeContentProviderExternal(am, authority, token) }
            }
        }
    }

    private fun getActivityManager(): Any? = runCatching {
        val cls = Class.forName("android.app.ActivityManager")
        cls.getDeclaredMethod("getService").apply { isAccessible = true }.invoke(null)
    }.getOrElse {
        runCatching {
            val cls = Class.forName("android.app.ActivityManagerNative")
            cls.getDeclaredMethod("getDefault").apply { isAccessible = true }.invoke(null)
        }.getOrNull()
    }

    private fun getContentProviderExternal(am: Any, authority: String, token: IBinder): Any? {
        val iAmClass = runCatching { Class.forName("android.app.IActivityManager") }.getOrDefault(am.javaClass)
        for (method in iAmClass.methods) {
            if (method.name != "getContentProviderExternal") continue
            method.isAccessible = true
            val params = method.parameterTypes
            return when (params.size) {
                4 -> method.invoke(am, authority, 0, token, "*cmd*")
                3 -> method.invoke(am, authority, 0, token)
                else -> continue
            }
        }
        return null
    }

    private fun removeContentProviderExternal(am: Any, authority: String, token: IBinder) {
        val iAmClass = runCatching { Class.forName("android.app.IActivityManager") }.getOrDefault(am.javaClass)
        for (method in iAmClass.methods) {
            if (method.name == "removeContentProviderExternalAsUser" && method.parameterTypes.size == 3) {
                method.isAccessible = true
                method.invoke(am, authority, token, 0)
                return
            }
            if (method.name == "removeContentProviderExternal" && method.parameterTypes.size == 2) {
                method.isAccessible = true
                method.invoke(am, authority, token)
                return
            }
        }
    }

    private fun invokeProviderCall(
        provider: Any,
        authority: String,
        methodName: String,
        extras: Bundle,
        arg: String? = null,
    ): Bundle? {
        val iProviderClass = runCatching { Class.forName("android.content.IContentProvider") }
            .getOrDefault(provider.javaClass)
        val callMethods = iProviderClass.methods.filter { it.name == "call" }

        // 1. Android 12+ (API 31+): bắt buộc ưu tiên overload nhận AttributionSource + authority
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (m in callMethods) {
                val p = m.parameterTypes
                if (p.isNotEmpty() && p[0].name == "android.content.AttributionSource") {
                    m.isAccessible = true
                    val attr = AttributionSource.Builder(Process.myUid())
                        .setPackageName("com.android.shell")
                        .build()
                    return when (p.size) {
                        5 -> m.invoke(provider, attr, authority, methodName, arg, extras) as? Bundle
                        6 -> m.invoke(provider, attr, authority, methodName, arg, extras, null) as? Bundle
                        else -> continue
                    }
                }
            }
        }

        // 2. Android 10–11 (API 29–30): ưu tiên overload có tham số authority (5 hoặc 6 tham số)
        for (m in callMethods.sortedByDescending { it.parameterTypes.size }) {
            m.isAccessible = true
            val p = m.parameterTypes
            if (p.isNotEmpty() && p[0].name == "android.content.AttributionSource") continue
            return when (p.size) {
                6 -> m.invoke(provider, "com.android.shell", null, authority, methodName, arg, extras) as? Bundle
                5 -> m.invoke(provider, "com.android.shell", authority, methodName, arg, extras) as? Bundle
                4 -> m.invoke(provider, "com.android.shell", methodName, arg, extras) as? Bundle
                else -> continue
            }
        }
        return null
    }

    /**
     * Lệnh shell khởi chạy [EngineDaemonMain] tách khỏi nhóm cgroup của `adbd` (hoặc neo giữ `adbd`
     * trên Android 16) để sống sót ngay cả khi người dùng tắt Gỡ lỗi USB và tắt Tùy chọn nhà phát triển.
     */
    fun bootstrapCommand(context: Context): String {
        val pkg = context.packageName
        val extDir = context.getExternalFilesDir(null)?.absolutePath
            ?: "/sdcard/Android/data/$pkg/files"
        return buildString {
            append("settings put global adb_wifi_enabled 1 2>/dev/null; ")
            append("APK=\$(pm path $pkg | head -n 1 | cut -d: -f2); ")
            append("if [ -n \"\$APK\" ]; then ")
            append("setsid sh -c '")
            append("trap \"\" HUP INT TERM; ")
            append("for cg in ${WatchScript.ESCAPE_CGROUPS}; do echo \$\$ 2>/dev/null > \"\$cg/cgroup.procs\"; done; ")
            append("CLASSPATH=\"'\"\$APK\"'\" /system/bin/app_process /system/bin --nice-name=akb_engine ")
            append("com.aikeybridge.shell.EngineDaemonMain \"'\"$extDir\"'\" </dev/null >/dev/null 2>&1 & ")
            append("sleep 0.2")
            append("' </dev/null >/dev/null 2>&1; fi")
        }
    }

    /**
     * Lệnh tự động tắt Gỡ lỗi USB (`adb_enabled=0`) và ẩn Tùy chọn nhà phát triển (`development_settings_enabled=0`)
     * cho app ngân hàng / VNeID, đồng thời giữ `adb_wifi_enabled=1` để `AdbService` không dừng `adbd`
     * (ngăn `init` giết nhóm cgroup `0::/system/uid_0/pid_<adbd>` trên Android 16 như vivo X200 Ultra).
     */
    const val LOCK_DEV_MODE_CMD: String =
        "settings put global adb_wifi_enabled 1; " +
            "settings put global adb_enabled 0; " +
            "settings put global development_settings_enabled 0"
}
