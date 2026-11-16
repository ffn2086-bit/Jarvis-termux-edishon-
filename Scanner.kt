package com.jarvis.assistant

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

/** Heuristic scan of installed user apps (sideloaded + risky capability combinations). */
object Scanner {
    private val stores = setOf(
        "com.android.vending", "com.sec.android.app.samsungapps", "com.amazon.venezia",
        "com.huawei.appmarket", "com.xiaomi.mipicks", "org.fdroid.fdroid"
    )

    fun scan(ctx: Context): String {
        val pm = ctx.packageManager
        val pkgs = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES)
        var checked = 0
        val flagged = mutableListOf<String>()
        for (p in pkgs) {
            val ai = p.applicationInfo ?: continue
            if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
            if (p.packageName == ctx.packageName) continue
            checked++
            val perms = p.requestedPermissions?.toSet() ?: emptySet()
            var score = 0
            val installer = try {
                if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(p.packageName).installingPackageName
                else @Suppress("DEPRECATION") pm.getInstallerPackageName(p.packageName)
            } catch (e: Exception) { null }
            if (installer == null || installer !in stores) score += 1
            if (p.services?.any { it.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE" } == true) score += 2
            if ("android.permission.SYSTEM_ALERT_WINDOW" in perms) score += 1
            if ("android.permission.REQUEST_INSTALL_PACKAGES" in perms) score += 1
            if (("android.permission.READ_SMS" in perms || "android.permission.RECEIVE_SMS" in perms) &&
                "android.permission.INTERNET" in perms) score += 1
            if (score >= 3) flagged.add(ai.loadLabel(pm).toString())
        }
        return if (flagged.isEmpty())
            "സ്കാൻ പൂർത്തിയായി സർ. $checked ആപ്പുകൾ പരിശോധിച്ചു, എല്ലാം സുരക്ഷിതമായി കാണുന്നു."
        else
            "സ്കാൻ പൂർത്തിയായി സർ. $checked ആപ്പുകളിൽ ${flagged.size} എണ്ണം ശ്രദ്ധിക്കേണ്ടതുണ്ട്: ${flagged.take(6).joinToString(", ")}."
    }
}
