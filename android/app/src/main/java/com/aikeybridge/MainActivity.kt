package com.aikeybridge

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import com.aikeybridge.ui.AppViewModel
import com.aikeybridge.ui.BridgeApp
import com.aikeybridge.ui.theme.BridgeTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == AppViewModel.SHIZUKU_REQUEST_CODE) {
            viewModel.onShizukuPermissionResult(grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        runCatching { Shizuku.addRequestPermissionResultListener(permissionListener) }
        setContent {
            BridgeTheme {
                BridgeApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Kiểm tra quyền và mở lại event log khi app ở trước — lúc Android cho phép hỏi quyền đọc log.
        viewModel.checkScript()
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(permissionListener) }
        super.onDestroy()
    }
}

internal fun shareLog(context: Context, file: java.io.File): Boolean =
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(share, "Chia sẻ log chẩn đoán")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }.isSuccess

/**
 * Khởi động lại tiến trình app. Shizuku chỉ gửi binder cho tiến trình khởi chạy sau khi server đã chạy,
 * nên lần đầu bật Shizuku khi app đang mở phải khởi động lại app mới nhận được.
 */
internal fun restartApp(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(launch)
    android.os.Process.killProcess(android.os.Process.myPid())
}
