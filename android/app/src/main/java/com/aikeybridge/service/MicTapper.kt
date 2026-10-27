package com.aikeybridge.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.aikeybridge.model.VoiceUiSpec

enum class TapResult(val label: String) {
    NODE_CLICK("node click"),
    NODE_GESTURE("chạm theo node"),
    FALLBACK_GESTURE("chạm theo vị trí"),
    NONE("không thấy nút"),
    ;

    val ok: Boolean get() = this != NONE
}

object MicTapper {

    fun findTargetWindow(
        service: AccessibilityService,
        packages: List<String>,
    ): AccessibilityWindowInfo? {
        val windows = runCatching { service.windows }.getOrDefault(emptyList())
        return windows
            .filter { window ->
                val root = runCatching { window.root }.getOrNull() ?: return@filter false
                val pkg = runCatching { root.packageName?.toString() }.getOrNull()
                pkg in packages
            }
            .maxByOrNull { it.layer }
    }

    fun tap(service: AccessibilityService, window: AccessibilityWindowInfo, voice: VoiceUiSpec): TapResult {
        val root = runCatching { window.root }.getOrNull() ?: return TapResult.NONE
        val node = findMicNode(root, voice)
        if (node != null) {
            if (clickNode(node)) return TapResult.NODE_CLICK
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (!rect.isEmpty && tapAt(service, rect.centerX(), rect.centerY())) return TapResult.NODE_GESTURE
        }

        val fallbackX = voice.fallbackX ?: return TapResult.NONE
        val fallbackY = voice.fallbackY ?: return TapResult.NONE

        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        if (bounds.isEmpty) {
            val metrics = service.resources.displayMetrics
            bounds.set(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        val x = bounds.left + (bounds.width() * fallbackX).toInt()
        val y = bounds.top + (bounds.height() * fallbackY).toInt()
        return if (tapAt(service, x, y)) TapResult.FALLBACK_GESTURE else TapResult.NONE
    }

    private fun findMicNode(root: AccessibilityNodeInfo, voice: VoiceUiSpec): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++

            val viewId = runCatching { node.viewIdResourceName }.getOrNull()
                ?.substringAfterLast('/')
                .orEmpty()
            val description = runCatching { node.contentDescription?.toString() }.getOrNull().orEmpty()
            if (
                (viewId.isNotEmpty() && voice.micViewIdRegex.containsMatchIn(viewId)) ||
                (description.isNotEmpty() && voice.micContentDescRegex.containsMatchIn(description))
            ) {
                return node
            }

            for (index in 0 until runCatching { node.childCount }.getOrDefault(0)) {
                runCatching { node.getChild(index) }.getOrNull()?.let(queue::add)
            }
        }
        return null
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < MAX_PARENT_DEPTH) {
            val clickable = runCatching { current?.isClickable }.getOrDefault(false) == true
            if (clickable) {
                val clicked = runCatching {
                    current?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }.getOrDefault(false) == true
                if (clicked) return true
            }
            current = runCatching { current?.parent }.getOrNull()
            depth++
        }
        return false
    }

    private fun tapAt(service: AccessibilityService, x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return runCatching { service.dispatchGesture(gesture, null, null) }.getOrDefault(false)
    }

    private const val MAX_NODES = 600
    private const val MAX_PARENT_DEPTH = 6
}
