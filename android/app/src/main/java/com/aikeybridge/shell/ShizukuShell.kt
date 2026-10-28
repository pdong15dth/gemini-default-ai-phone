package com.aikeybridge.shell

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.aikeybridge.BuildConfig
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

class ShizukuShell(private val context: Context) : ShellRunner {

    private val mutex = Mutex()
    private val pending = AtomicReference<kotlinx.coroutines.CancellableContinuation<Boolean>?>(null)

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
        if (!isShizukuReady()) {
            return@withContext ShellResult(-1, "", "Chưa kết nối được Shizuku.")
        }
        // Trên Vivo OriginOS: dùng UserService như cũ.
        // Trên Xiaomi HyperOS (hoặc ROM chặn app_process callback của UserService): khi bind thất bại
        // sẽ tự chuyển sang IShizukuService.newProcess và nhớ trạng thái này cho các lệnh kế tiếp.
        if (!userServiceBlocked) {
            try {
                if (ensureBound()) {
                    val raw = withTimeoutOrNull(COMMAND_TIMEOUT_MS) {
                        service?.exec(command)
                    }
                    if (raw != null) return@withContext parse(raw)
                    return@withContext ShellResult(-1, "", "Lệnh quá thời gian (${COMMAND_TIMEOUT_MS / 1000}s).")
                } else {
                    userServiceBlocked = true
                }
            } catch (_: Throwable) {
                service = null
                userServiceBlocked = true
            }
        }

        execViaNewProcess(command)
            ?: ShellResult(-1, "", "Chưa kết nối được Shizuku.")
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

    private companion object {
        const val BIND_TIMEOUT_MS = 3_500L
        const val COMMAND_TIMEOUT_MS = 8_000L

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
