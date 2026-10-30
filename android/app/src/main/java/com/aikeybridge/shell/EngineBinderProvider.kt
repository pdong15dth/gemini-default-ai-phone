package com.aikeybridge.shell

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Cầu nối giao tiếp với Engine nội bộ chạy dưới UID 2000 (`shell`).
 *
 * Trên Vivo OriginOS, mọi tiến trình `/system/bin/app_process` chạy dưới UID 2000 đều bị
 * `VivoCommonService` gắn cờ `HasShellAppProc=true`, khiến `SettingsProvider.shouldBlockShell()`
 * chặn toàn bộ lệnh `settings put`. Vì vậy Engine nội bộ chạy thuần `/system/bin/sh`
 * (`/data/local/tmp/akb-engine.sh`), giao tiếp với app qua thư mục IPC riêng và giữ `HasShellAppProc=false`.
 */
class EngineBinderProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        context?.let {
            appContext = it.applicationContext
            requestDaemonReconnect(it)
        }
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == METHOD_SEND_BINDER && extras != null) {
            extras.classLoader = EngineBinderProvider::class.java.classLoader
            val incoming = extras.getBinder(KEY_BINDER)
            if (incoming != null && incoming.pingBinder()) {
                acceptBinder(incoming)
                return Bundle().apply {
                    putBinder(KEY_CLIENT_TOKEN, clientToken)
                }
            }
        }
        return null
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        const val AUTHORITY = "com.aikeybridge.engine"
        const val METHOD_SEND_BINDER = "sendBinder"
        const val KEY_BINDER = "binder"
        const val KEY_CLIENT_TOKEN = "client"
        const val REQ_FILE = ".engine-req"
        const val ALIVE_FILE = "engine-alive"
        const val PID_FILE = "engine-daemon.pid"
        const val IPC_DIR = "ipc"
        private const val ALIVE_MAX_AGE_MS = 12_000L

        private val clientToken = Binder()
        private val listeners = CopyOnWriteArraySet<() -> Unit>()
        private val reqCounter = AtomicLong(0L)

        @Volatile
        private var appContext: Context? = null

        @Volatile
        private var currentBinder: IBinder? = null

        @Volatile
        private var currentService: IShellService? = null

        private val deathRecipient = IBinder.DeathRecipient {
            currentBinder = null
            currentService = null
            notifyListeners()
        }

        fun isAlive(context: Context? = null): Boolean {
            if (context != null) {
                appContext = context.applicationContext
            }
            val b = currentBinder
            if (b != null) {
                if (b.pingBinder()) return true
                currentBinder = null
                currentService = null
            }
            return isShellDaemonAlive(context ?: appContext)
        }

        private fun isShellDaemonAlive(context: Context?): Boolean {
            val ctx = context ?: return false
            val dir = runCatching { ctx.getExternalFilesDir(null) }.getOrNull() ?: return false
            val aliveFile = File(dir, ALIVE_FILE)
            if (!aliveFile.exists()) return false
            val now = System.currentTimeMillis()
            val bootAt = now - SystemClock.elapsedRealtime()
            val modified = aliveFile.lastModified()
            return modified >= bootAt && (now - modified) <= ALIVE_MAX_AGE_MS
        }

        fun service(): IShellService? {
            val b = currentBinder ?: return null
            if (!b.pingBinder()) {
                currentBinder = null
                currentService = null
                return null
            }
            return currentService
        }

        /**
         * Thực thi lệnh shell UID 2000 qua Engine nội bộ (`/data/local/tmp/akb-engine.sh`).
         */
        suspend fun execCommand(
            context: Context,
            command: String,
            timeoutMs: Long = ShizukuShell.COMMAND_TIMEOUT_MS,
        ): ShellResult? = withContext(Dispatchers.IO) {
            appContext = context.applicationContext
            service()?.let { daemon ->
                runCatching {
                    val raw = withTimeoutOrNull(timeoutMs) { daemon.exec(command) }
                        ?: return@withContext ShellResult(-1, "", "Lệnh quá thời gian (${timeoutMs / 1000}s).")
                    val json = JSONObject(raw)
                    return@withContext ShellResult(
                        exitCode = json.optInt("exit", -1),
                        stdout = json.optString("out"),
                        stderr = json.optString("err"),
                    )
                }
            }

            if (!isAlive(context)) return@withContext null
            val dir = runCatching { context.getExternalFilesDir(null) }.getOrNull() ?: return@withContext null
            val ipcDir = File(dir, IPC_DIR).apply { mkdirs() }
            val id = "${System.currentTimeMillis()}_${reqCounter.incrementAndGet()}"
            val tmpFile = File(ipcDir, ".tmp-$id")
            val reqFile = File(ipcDir, "req-$id.sh")
            val outFile = File(ipcDir, "out-$id")
            val errFile = File(ipcDir, "err-$id")
            val rcFile = File(ipcDir, "rc-$id")

            try {
                tmpFile.writeText(command, Charsets.UTF_8)
                if (!tmpFile.renameTo(reqFile)) {
                    reqFile.writeText(command, Charsets.UTF_8)
                    tmpFile.delete()
                }
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    if (rcFile.exists()) {
                        val code = runCatching { rcFile.readText().trim().toIntOrNull() }.getOrNull() ?: -1
                        val out = runCatching { if (outFile.exists()) outFile.readText() else "" }.getOrDefault("")
                        val err = runCatching { if (errFile.exists()) errFile.readText() else "" }.getOrDefault("")
                        return@withContext ShellResult(code, out, err)
                    }
                    delay(25)
                }
                ShellResult(-1, "", "Lệnh quá thời gian (${timeoutMs / 1000}s).")
            } catch (_: Throwable) {
                null
            } finally {
                runCatching { tmpFile.delete() }
                runCatching { reqFile.delete() }
                runCatching { outFile.delete() }
                runCatching { errFile.delete() }
                runCatching { rcFile.delete() }
            }
        }

        internal fun acceptBinder(binder: IBinder) {
            val old = currentBinder
            if (old === binder && binder.pingBinder()) return
            runCatching { old?.unlinkToDeath(deathRecipient, 0) }
            currentBinder = binder
            currentService = IShellService.Stub.asInterface(binder)
            runCatching { binder.linkToDeath(deathRecipient, 0) }
            notifyListeners()
        }

        fun addListener(listener: () -> Unit) {
            listeners.add(listener)
            if (isAlive()) listener()
        }

        fun removeListener(listener: () -> Unit) {
            listeners.remove(listener)
        }

        private fun notifyListeners() {
            for (listener in listeners) {
                runCatching { listener() }
            }
        }

        fun notifyStateChanged() {
            notifyListeners()
        }

        /**
         * Đánh thức Engine nội bộ (`/data/local/tmp/akb-engine.sh`) cập nhật nhịp sống tức thì
         * ngay cả khi người dùng đã tắt hoàn toàn Tùy chọn nhà phát triển và Gỡ lỗi USB/Không dây.
         */
        fun requestDaemonReconnect(context: Context) {
            appContext = context.applicationContext
            if (isAlive(context)) return
            runCatching {
                val dir = context.getExternalFilesDir(null) ?: return
                dir.mkdirs()
                File(dir, REQ_FILE).writeText("${System.currentTimeMillis()}-${System.nanoTime()}")
            }
        }
    }
}
