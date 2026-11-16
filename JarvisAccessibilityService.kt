package com.jarvis.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: JarvisAccessibilityService? = null
        fun enabled(ctx: Context): Boolean {
            val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return s.contains(ctx.packageName + "/")
        }
    }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun clickText(t: String): Boolean {
        val root = rootInActiveWindow ?: return false
        for (n in root.findAccessibilityNodeInfosByText(t)) {
            var x: AccessibilityNodeInfo? = n
            while (x != null && !x.isClickable) x = x.parent
            if (x != null && x.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        return false
    }

    fun clickDesc(d: String): Boolean {
        val root = rootInActiveWindow ?: return false
        fun walk(n: AccessibilityNodeInfo?): Boolean {
            if (n == null) return false
            if (n.contentDescription?.toString().equals(d, true) && n.isClickable) {
                return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            for (i in 0 until n.childCount) if (walk(n.getChild(i))) return true
            return false
        }
        return walk(root)
    }

    fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        fun walk(n: AccessibilityNodeInfo?): Boolean {
            if (n == null) return false
            if (n.isScrollable) {
                return n.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            }
            for (i in 0 until n.childCount) if (walk(n.getChild(i))) return true
            return false
        }
        return walk(root)
    }

    fun global(op: String): Boolean = when (op) {
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        else -> false
    }
}
