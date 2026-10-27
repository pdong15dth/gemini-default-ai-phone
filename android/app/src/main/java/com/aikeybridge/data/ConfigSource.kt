package com.aikeybridge.data

import android.content.Context

/** Ghép catalog + cài đặt + máy thành [ActiveConfig]; chỉ tính lại khi cài đặt đổi. */
class ConfigSource(context: Context, val settings: SettingsRepository = SettingsRepository(context)) {

    val catalog: Catalog = ProfileRepository.catalog(context)
    val device: DeviceInfo = DeviceDetector.read(context)

    @Volatile
    private var cache: Pair<AppSettings, ActiveConfig>? = null

    fun current(): ActiveConfig = resolve(settings.state.value)

    fun resolve(value: AppSettings): ActiveConfig {
        cache?.let { (key, config) -> if (key === value) return config }
        return ConfigResolver.resolve(catalog, value, device.manufacturer, device.brand)
            .also { cache = value to it }
    }
}
