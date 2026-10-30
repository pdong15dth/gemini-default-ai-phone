package com.aikeybridge.shell

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Nhận [IBinder] của [IShellService] từ [EngineDaemonMain] (chạy dưới UID 2000 `shell`).
 * Chỉ tiến trình hệ thống / shell có quyền `INTERACT_ACROSS_USERS_FULL` mới gọi được provider này.
 */
class EngineBinderProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        context?.let { requestDaemonReconnect(it) }
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

        private val clientToken = Binder()
        private val listeners = CopyOnWriteArraySet<() -> Unit>()

        @Volatile
        private var currentBinder: IBinder? = null

        @Volatile
        private var currentService: IShellService? = null

        private val deathRecipient = IBinder.DeathRecipient {
            currentBinder = null
            currentService = null
            notifyListeners()
        }

        fun isAlive(): Boolean {
            val b = currentBinder ?: return false
            return if (b.pingBinder()) {
                true
            } else {
                currentBinder = null
                currentService = null
                false
            }
        }

        fun service(): IShellService? {
            if (!isAlive()) return null
            return currentService
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

        /**
         * Báo cho [EngineDaemonMain] đang chạy ngầm trong RAM gửi lại [IBinder] vào tiến trình app mới mở.
         * Hoạt động ngay cả khi người dùng đã tắt hoàn toàn Tùy chọn nhà phát triển và Gỡ lỗi USB/Không dây.
         */
        fun requestDaemonReconnect(context: Context) {
            if (isAlive()) return
            runCatching {
                val dir = context.getExternalFilesDir(null) ?: return
                dir.mkdirs()
                File(dir, REQ_FILE).writeText("${System.currentTimeMillis()}-${System.nanoTime()}")
            }
        }
    }
}
