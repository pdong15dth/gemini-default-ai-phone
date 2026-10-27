package com.aikeybridge.data

import android.content.Context
import android.os.Build
import android.provider.Settings

data class DeviceInfo(
    val manufacturer: String,
    val brand: String,
    val model: String,
    /** Tên thương mại nếu ROM có khai báo, ví dụ "iQOO 12" thay vì "V2307A". */
    val marketName: String?,
    /** Phiên bản ROM nếu đọc được, ví dụ "OriginOS 4", "HyperOS 1.0", "ColorOS V14". */
    val romVersion: String?,
    val androidRelease: String,
    val sdk: Int,
    val fingerprint: String,
) {
    val displayName: String get() = marketName ?: "${brand.replaceFirstChar { it.uppercase() }} $model".trim()
}

object DeviceDetector {

    private val marketNameProps = listOf(
        "ro.vivo.market.name",
        "ro.product.marketname",
        "ro.product.vendor.marketname",
        "ro.vendor.oplus.market.name",
        "ro.oppo.market.name",
        "ro.config.marketing_name",
        "ro.product.nubia.marketname",
    )

    private val romProps = listOf(
        "ro.vivo.os.build.display.id" to null,
        "ro.vivo.os.version" to "OriginOS",
        "ro.mi.os.version.name" to "HyperOS",
        "ro.miui.ui.version.name" to "MIUI",
        "ro.build.version.oplusrom" to "ColorOS",
        "ro.build.version.opporom" to "ColorOS",
        "ro.build.version.magic" to null,
        "ro.build.version.emui" to null,
        "ro.build.nubia.rom.name" to null,
    )

    fun read(context: Context? = null): DeviceInfo {
        val manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "Không rõ" }
        val brand = Build.BRAND.orEmpty().ifBlank { "Không rõ" }
        val model = Build.MODEL.orEmpty().ifBlank { "Không rõ" }
        return DeviceInfo(
            manufacturer = manufacturer,
            brand = brand,
            model = model,
            marketName = readMarketName(context, model),
            romVersion = readRomVersion(),
            androidRelease = Build.VERSION.RELEASE.orEmpty().ifBlank { "?" },
            sdk = Build.VERSION.SDK_INT,
            fingerprint = Build.FINGERPRINT.orEmpty(),
        )
    }

    private fun readMarketName(context: Context?, model: String): String? {
        marketNameProps.firstNotNullOfOrNull { prop(it) }?.let { return it }
        val deviceName = context?.let {
            runCatching { Settings.Global.getString(it.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
        }?.trim()
        return deviceName?.takeIf { it.isNotBlank() && !it.equals(model, ignoreCase = true) }
    }

    private fun readRomVersion(): String? {
        for ((key, prefix) in romProps) {
            val value = prop(key) ?: continue
            val clean = value.replace('_', ' ').trim()
            return when {
                prefix == null -> clean
                clean.startsWith(prefix, ignoreCase = true) -> clean
                else -> "$prefix $clean"
            }
        }
        return null
    }

    /** Đọc system property qua reflection; property không tồn tại hoặc bị chặn thì trả null. */
    private fun prop(key: String): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java)
        (get.invoke(null, key) as? String)?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}
