package com.tuapp.macro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build

class TapAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: TapAccessibilityService? = null

        fun performTap(x: Float, y: Float) {
            instance?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val path = Path().apply { moveTo(x, y) }
                    val stroke = GestureDescription.StrokeDescription(path, 0, 1)
                    val gesture = GestureDescription.Builder().addStroke(stroke).build()
                    it.dispatchGesture(gesture, null, null)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; super.onDestroy() }
}