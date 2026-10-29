package com.aikeybridge.shell

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.aikeybridge.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import rikka.shizuku.Shizuku

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val ok: Boolean get() = exitCode == 0
    val output: String get() = stdout.ifBlank { stderr }.trim()
}

interface ShellRunner {
    suspend fun exec(command: String): ShellResult
}

/**
 * Bộ thực thi lệnh Shell đa tầng (Multi-Engine):
 * 1. **Engine nội bộ ([EngineBinderProvider] / [EngineDaemonMain])**: Chạy ngầm trong RAM dưới quyền `UID 2000`
 *    và đã tách khỏi nhóm cgroup của `adbd` — hoạt động ngay cả khi đã TẮT Tùy chọn nhà phát triển, TẮT Gỡ lỗi USB/Không dây
 *    và không cài Shizuku.
 * 2. **Engine Gỡ lỗi không dây nội bộ ([BuiltInWirelessAdb])**: Tự kết nối TLS `127.0.0.1` khi bật Gỡ lỗi không dây
 *    và tự khởi chạy [EngineDaemonMain] mà không cần app Shizuku.
 * 3. **Shizuku (Dự phòng)**: Nếu máy có sẵn Shizuku đang chạy thì tận dụng luôn và tự nâng cấp lên [EngineDaemonMain].
 */
class ShizukuShell(private val context: Context) : ShellRunner {

    private val mutex = Mutex()
    private val pending = AtomicReference<kotlinx.coroutines.CancellableContinuation<Boolean>?>(null)
    private val bootstrappedFromShizuku = AtomicBoolean(false)

    @Volatile
    private var service: IShellService? = null

    @Volatile
    private var userServiceBlocked: Boolean = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = if (binder != null && binder.pingBinder()) {
                IShellService.Stub.asInterface(binder)
            } else {
                null
            }
            if (service != null) userServiceBlocked = false
            pending.getAndSet(null)?.let { continuation ->
                if (continuation.isActive) continuation.resume(service != null)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    private val args = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, ShellUserService::class.java.name),
    )
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    override suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        // 1. Ưu tiên cao nhất: EngineDaemonMain (UID 2000 nội bộ của app, sống cả khi đã tắt Gỡ lỗi)
        execViaEngineDaemon(command)?.let { return@withContext it }

        // Thử đánh thức EngineDaemonMain nếu tiến trình app vừa mới mở lại
        EngineBinderProvider.requestDaemonReconnect(context)
        repeat(4) {
            delay(45)
            execViaEngineDaemon(command)?.let { return@withContext it }
        }

        // 2. Nếu chưa có EngineDaemon nhưng có Shizuku đang chạy -> chạy lệnh và tự dựng luôn EngineDaemonMain
        if (isShizukuReady()) {
            val res = execViaShizukuInternal(command)
            if (res != null) {
                if (bootstrappedFromShizuku.compareAndSet(false, true)) {
                    runCatching { execViaShizukuInternal(EngineDaemonMain.bootstrapCommand(context)) }
                }
                return@withContext res
            }
        }

        // 3. Thử tự kết nối qua Built-in Wireless ADB (nếu người dùng đã ghép nối và đang bật Gỡ lỗi không dây)
        if (BuiltInWirelessAdb.connectAndBootstrapDaemon(context)) {
            execViaEngineDaemon(command)?.let { return@withContext it }
        }
        BuiltInWirelessAdb.execViaWirelessAdb(context, command)?.let { return@withContext it }

        ShellResult(-1, "", "Chưa kết nối được Engine nội bộ hoặc Gỡ lỗi không dây.")
    }

    private suspend fun execViaEngineDaemon(command: String): ShellResult? {
        val daemon = EngineBinderProvider.service() ?: return null
        return try {
            val raw = withTimeoutOrNull(COMMAND_TIMEOUT_MS) {
                daemon.exec(command)
            } ?: return ShellResult(-1, "", "Lệnh quá thời gian (${COMMAND_TIMEOUT_MS / 1000}s).")
            parse(raw)
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun execViaShizukuInternal(command: String): ShellResult? {
        if (!userServiceBlocked) {
            try {
                if (ensureBound()) {
                    val raw = withTimeoutOrNull(COMMAND_TIMEOUT_MS) {
                        service?.exec(command)
                    }
                    if (raw != null) return parse(raw)
                    return ShellResult(-1, "", "Lệnh quá thời gian (${COMMAND_TIMEOUT_MS / 1000}s).")
                } else {
                    userServiceBlocked = true
                }
            } catch (_: Throwable) {
                service = null
                userServiceBlocked = true
            }
        }
        return execViaNewProcess(command)
    }

    private fun isShizukuReady(): Boolean = runCatching {
        !Shizuku.isPreV11() &&
            Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    private suspend fun execViaNewProcess(command: String): ShellResult? {
        val method = newProcessMethod ?: return null
        val process = try {
            method.invoke(null, arrayOf("sh", "-c", command), null, null) as? Process
        } catch (_: Throwable) {
            null
        } ?: return null

        return try {
            runCatching { process.outputStream.close() }
            withTimeoutOrNull(COMMAND_TIMEOUT_MS) {
                coroutineScope {
                    val outDeferred = async(Dispatchers.IO) {
                        runCatching { process.inputStream.bufferedReader().use { it.readText() } }.getOrDefault("")
                    }
                    val errDeferred = async(Dispatchers.IO) {
                        runCatching { process.errorStream.bufferedReader().use { it.readText() } }.getOrDefault("")
                    }
                    val exit = process.waitFor()
                    ShellResult(exit, outDeferred.await(), errDeferred.await())
                }
            } ?: run {
                runCatching { process.destroy() }
                ShellResult(-1, "", "Lệnh quá thời gian (${COMMAND_TIMEOUT_MS / 1000}s).")
            }
        } catch (throwable: Throwable) {
            runCatching { process.destroy() }
            ShellResult(-1, "", throwable.message ?: throwable.toString())
        }
    }

    suspend fun ensureBound(): Boolean = mutex.withLock {
        if (service != null) return@withLock true
        withTimeoutOrNull(BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                pending.set(continuation)
                continuation.invokeOnCancellation {
                    pending.compareAndSet(continuation, null)
                }
                try {
                    Shizuku.bindUserService(args, connection)
                } catch (throwable: Throwable) {
                    pending.compareAndSet(continuation, null)
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            }
        } ?: false
    }

    fun close() {
        service = null
        userServiceBlocked = false
        runCatching { Shizuku.unbindUserService(args, connection, true) }
    }

    private fun parse(raw: String): ShellResult = try {
        val json = JSONObject(raw)
        ShellResult(
            exitCode = json.optInt("exit", -1),
            stdout = json.optString("out"),
            stderr = json.optString("err"),
        )
    } catch (throwable: Throwable) {
        ShellResult(-1, raw, "Không đọc được kết quả: ${throwable.message}")
    }

    companion object {
        const val BIND_TIMEOUT_MS = 3_500L
        const val COMMAND_TIMEOUT_MS = 8_000L

        fun isEngineOrShizukuReady(): Boolean {
            if (EngineBinderProvider.isAlive()) return true
            return runCatching {
                !Shizuku.isPreV11() &&
                    Shizuku.pingBinder() &&
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
        }

        val newProcessMethod by lazy {
            runCatching {
                Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java,
                ).apply { isAccessible = true }
            }.getOrNull()
        }
    }
}
