package com.aikeybridge.shell

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.aikeybridge.BuildConfig
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
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

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = if (binder != null && binder.pingBinder()) {
                IShellService.Stub.asInterface(binder)
            } else {
                null
            }
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
        try {
            if (!ensureBound()) {
                return@withContext ShellResult(-1, "", "Chưa kết nối được Shizuku.")
            }
            val raw = service?.exec(command)
                ?: return@withContext ShellResult(-1, "", "UserService đã ngắt kết nối.")
            parse(raw)
        } catch (throwable: Throwable) {
            service = null
            ShellResult(-1, "", throwable.message ?: throwable.toString())
        }
    }

    suspend fun ensureBound(): Boolean = mutex.withLock {
        if (service != null) return@withLock true
        withTimeoutOrNull(TIMEOUT_MS) {
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
        const val TIMEOUT_MS = 6_000L
    }
}
