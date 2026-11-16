package com.jarvis.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat

fun hasPerm(c: Context, p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

class PermItem(val label: String, val perm: String?, val ok: (Context) -> Boolean)

val PERMS = listOf(
    PermItem("Microphone (voice)", Manifest.permission.RECORD_AUDIO) { hasPerm(it, Manifest.permission.RECORD_AUDIO) },
    PermItem("Contacts", Manifest.permission.READ_CONTACTS) { hasPerm(it, Manifest.permission.READ_CONTACTS) },
    PermItem("Phone calls", Manifest.permission.CALL_PHONE) { hasPerm(it, Manifest.permission.CALL_PHONE) },
    PermItem("SMS", Manifest.permission.SEND_SMS) { hasPerm(it, Manifest.permission.SEND_SMS) },
    PermItem("Modify system settings (brightness)", null) { Settings.System.canWrite(it) },
    PermItem("Accessibility service (Hybrid)", null) { JarvisAccessibilityService.enabled(it) }
)
