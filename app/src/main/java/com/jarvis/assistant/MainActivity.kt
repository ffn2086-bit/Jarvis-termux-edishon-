package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class MainActivity : ComponentActivity() {
    private val vm: JarvisVM by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val pal = animPal(vm.hybrid)
            CompositionLocalProvider(LocalPal provides pal) { App(vm) }
        }
    }
}

@Composable
fun App(vm: JarvisVM) {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf("home") }
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick++
        if (!Settings.System.canWrite(ctx)) openSpecial(ctx, PERMS[4])
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        tick++; if (ok) vm.listen()
    }

    BackHandler(screen == "settings") { screen = "home" }

    if (screen == "home") {
        Home(vm, onSettings = { screen = "settings" }, onMic = {
            if (hasPerm(ctx, android.Manifest.permission.RECORD_AUDIO)) vm.listen()
            else micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        })
    } else {
        SettingsScreen(vm, tick, onBack = { screen = "home" },
            onGrant = { p -> if (p.perm != null) launcher.launch(arrayOf(p.perm)) else openSpecial(ctx, p) },
            onAllowAll = {
                launcher.launch(PERMS.mapNotNull { it.perm }.toTypedArray())
            })
    }
}

fun openSpecial(ctx: Context, p: PermItem) {
    val i = if (p.label.startsWith("Accessibility")) Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    else Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))
    ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
