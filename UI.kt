package com.jarvis.assistant

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Orb(active: Boolean, modifier: Modifier) {
    val pal = LocalPal.current
    val inf = rememberInfiniteTransition(label = "orb")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "rot")
    val pulse by inf.animateFloat(0.92f, 1.12f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse")
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2
        drawCircle(pal.accent.copy(alpha = 0.07f), r * 0.95f)
        drawCircle(pal.accent.copy(alpha = 0.45f), r * 0.92f, style = Stroke(2f))
        rotate(rot, c) {
            for (i in 0 until 24) {
                rotate(i * 15f, c) {
                    drawLine(pal.accent.copy(alpha = 0.7f), Offset(c.x, c.y - r * 0.95f), Offset(c.x, c.y - r * 0.85f), 5f)
                }
            }
        }
        drawCircle(Color.Black.copy(alpha = 0.6f), r * 0.62f)
        drawCircle(pal.accent.copy(alpha = 0.5f), r * 0.62f, style = Stroke(3f))
        for (k in 0 until 4) {
            val a = Math.toRadians((rot * 2 + k * 90).toDouble())
            drawCircle(pal.accent, 9f, Offset(c.x + (r * 0.62f * cos(a)).toFloat(), c.y + (r * 0.62f * sin(a)).toFloat()))
        }
        drawCircle(pal.accent, r * 0.3f * (if (active) pulse else 1f))
    }
}

@Composable
fun ModeToggle(hybrid: Boolean, onChange: (Boolean) -> Unit) {
    val pal = LocalPal.current
    Row(
        Modifier.clip(RoundedCornerShape(50)).border(1.dp, pal.line, RoundedCornerShape(50)).background(pal.card)
    ) {
        listOf("NORMAL" to false, "HYBRID" to true).forEach { (label, h) ->
            val sel = hybrid == h
            Text(
                label, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp,
                color = if (sel) Color.Black else pal.dim,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (sel) pal.accent else Color.Transparent)
                    .clickable { onChange(h) }.padding(horizontal = 22.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
fun Bubble(m: Msg) {
    val pal = LocalPal.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.user) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp))
                .background(if (m.user) pal.accent.copy(alpha = 0.18f) else pal.card)
                .border(1.dp, pal.line, RoundedCornerShape(16.dp)).padding(12.dp)
        ) {
            Text(m.text, color = Color.White, fontSize = 16.sp)
            Text(m.time, color = pal.dim, fontSize = 11.sp)
        }
    }
}

@Composable
fun Home(vm: JarvisVM, onSettings: () -> Unit, onMic: () -> Unit) {
    val pal = LocalPal.current
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    var clock by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            kotlinx.coroutines.delay(10_000)
        }
    }
    LaunchedEffect(vm.messages.size) { if (vm.messages.isNotEmpty()) list.animateScrollToItem(vm.messages.size - 1) }
    val doSend = { if (input.isNotBlank()) { vm.send(input); input = "" } }

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(pal.bg, pal.card))).padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).border(2.dp, pal.accent, CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Jarvis", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("● ${vm.status}", color = pal.accent, fontSize = 12.sp, maxLines = 1)
            }
            Text(clock, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onSettings) { Text("⚙", color = pal.accent, fontSize = 24.sp) }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ModeToggle(vm.hybrid) { vm.setHybrid(it) } }
        Orb(vm.listening || vm.speaking || vm.busy, Modifier.fillMaxWidth().height(190.dp).padding(vertical = 6.dp))
        Text("CONVERSATION", color = pal.dim, fontSize = 13.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.messages) { Bubble(it) }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("കമാൻഡ് ടൈപ്പ് ചെയ്യൂ…", color = pal.dim) },
                singleLine = true, shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { doSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = pal.accent, unfocusedBorderColor = pal.line, cursorColor = pal.accent
                )
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { doSend() }, colors = ButtonDefaults.buttonColors(containerColor = pal.accent, contentColor = Color.Black)) {
                Text("SEND", fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { vm.stopAll() }, border = BorderStroke(1.dp, pal.line)) { Text("STOP", color = pal.accent) }
            Spacer(Modifier.width(20.dp))
            Box(
                Modifier.size(68.dp).clip(CircleShape).background(if (vm.listening) Color.White else pal.accent).clickable { onMic() },
                contentAlignment = Alignment.Center
            ) { Text("🎙", fontSize = 28.sp) }
        }
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPal.current
    Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(pal.card)
            .border(1.dp, pal.line, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content
    )
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, hint: String, secret: Boolean = false) {
    val pal = LocalPal.current
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text(hint, color = pal.dim) }, shape = RoundedCornerShape(16.dp),
        visualTransformation = if (secret && !show) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (secret) ({ TextButton(onClick = { show = !show }) { Text("👁") } }) else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            focusedBorderColor = pal.accent, unfocusedBorderColor = pal.line, cursorColor = pal.accent
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(options: List<String>, selected: String, onPick: (String) -> Unit) {
    val pal = LocalPal.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach {
            val sel = it == selected
            Text(
                it, color = if (sel) Color.Black else pal.dim, fontSize = 15.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) pal.accent else Color.Transparent)
                    .border(1.dp, pal.line, RoundedCornerShape(50)).clickable { onPick(it) }.padding(horizontal = 18.dp, vertical = 9.dp)
            )
        }
    }
}

@Composable
fun SettingsScreen(vm: JarvisVM, tick: Int, onBack: () -> Unit, onGrant: (PermItem) -> Unit, onAllowAll: () -> Unit) {
    val pal = LocalPal.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val st = vm.store
    val scope = rememberCoroutineScope()
    var gKey by remember { mutableStateOf(st.geminiKey) }
    var yKey by remember { mutableStateOf(st.ytKey) }
    var yOn by remember { mutableStateOf(st.ytEnabled) }
    var model by remember { mutableStateOf(st.model) }
    var voice by remember { mutableStateOf(st.voice) }
    var uName by remember { mutableStateOf(st.userName) }
    var lang by remember { mutableStateOf(st.lang) }
    var ytStatus by remember { mutableStateOf(if (st.ytKey.isBlank()) "Key is empty" else "Not validated") }
    var permOpen by remember { mutableStateOf(false) }
    var showMem by remember { mutableStateOf(false) }
    val granted = remember(tick) { PERMS.count { it.ok(ctx) } }

    Column(Modifier.fillMaxSize().background(pal.bg)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ BACK", color = pal.accent) }
            Text("Settings", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                st.geminiKey = gKey.trim(); st.ytKey = yKey.trim(); st.ytEnabled = yOn
                st.model = model.trim().ifBlank { "gemini-2.5-flash" }; st.voice = voice; st.userName = uName.trim(); st.lang = lang
                onBack()
            }) { Text("SAVE", color = pal.accent, fontWeight = FontWeight.Bold) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Section("Mobile Control") {
                Text("WHAT JARVIS CAN DO ON YOUR PHONE", color = pal.dim, fontSize = 12.sp, letterSpacing = 2.sp)
                listOf(
                    "Turn on / off flashlight", "Set brightness to 50%", "Volume up / down, set volume to 80%",
                    "Open Instagram / WhatsApp / Spotify", "Send WhatsApp to Rahul saying I will be late",
                    "Call Rahul on WhatsApp, video call Mom", "Remember my gym starts at 6 PM / Forget routine",
                    "Play a song on YouTube", "Web search", "Hybrid: malware scan, Termux + GitHub tools, screen control"
                ).forEach { Text("▪ $it", color = Color.White, fontSize = 15.sp) }
                Text("● Confirmation Policy: Active — messages are sent only after your verbal confirmation.", color = Color(0xFF3DE0A0), fontSize = 13.sp)
            }
            Section("API Key") {
                Text("GEMINI API KEY", color = pal.dim, fontSize = 12.sp, letterSpacing = 2.sp)
                Field(gKey, { gKey = it }, "Paste Gemini API Key (AI Studio)", secret = true)
                Text(if (gKey.isBlank()) "● Not Set" else "● Set", color = if (gKey.isBlank()) Color(0xFFFFB020) else Color(0xFF3DE0A0))
            }
            Section("AI Model") {
                Field(model, { model = it }, "Model id")
                Chips(listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro", "gemini-2.0-flash"), model) { model = it }
            }
            Section("Voice") {
                Chips(listOf("Puck", "Charon", "Kore", "Fenrir", "Aoede", "Leda", "Orus", "Zephyr"), voice) { voice = it }
                Text("INPUT LANGUAGE", color = pal.dim, fontSize = 12.sp, letterSpacing = 2.sp)
                Chips(listOf("ml-IN", "en-IN", "hi-IN"), lang) { lang = it }
            }
            Section("Personality") {
                Chips(listOf("Friendly", "Helpful", "Malayalam only", "Always polite"), "") {}
                Text("USER NAME", color = pal.dim, fontSize = 12.sp, letterSpacing = 2.sp)
                Field(uName, { uName = it }, "e.g. Tony Stark, Sir, Boss (optional)")
            }
            Section("Permissions") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$granted/${PERMS.size} granted", color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Button(onClick = onAllowAll, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3DE0A0), contentColor = Color.Black)) { Text("ALLOW ALL", fontWeight = FontWeight.Bold) }
                }
                Text(if (permOpen) "Manage Permissions ▲" else "Manage Permissions ▼", color = pal.accent, modifier = Modifier.clickable { permOpen = !permOpen })
                if (permOpen) PERMS.forEach { p ->
                    val ok = p.ok(ctx)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text((if (ok) "✔ " else "○ ") + p.label, color = Color.White, modifier = Modifier.weight(1f), fontSize = 14.sp)
                        if (!ok) TextButton(onClick = { onGrant(p) }) { Text("GRANT", color = pal.accent) }
                    }
                }
            }
            Section("Memory Core") {
                Text("Short-term conversation buffer + long-term knowledge (Routines, Preferences, Notes, Facts).", color = pal.dim)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { st.clearMemories(); showMem = false }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color(0xFFFF5577))) { Text("CLEAR", color = Color(0xFFFF5577)) }
                    OutlinedButton(onClick = { showMem = true }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, pal.accent)) { Text("VIEW MEMORIES", color = pal.accent) }
                }
            }
            Section("YouTube Automation") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable YouTube Automation", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = yOn, onCheckedChange = { yOn = it }, colors = SwitchDefaults.colors(checkedTrackColor = pal.accent, checkedThumbColor = Color.White))
                }
                Text("YouTube Data API v3 key for searching songs and playlists.", color = pal.dim, fontSize = 14.sp)
                Field(yKey, { yKey = it }, "Paste YouTube Data API v3 Key", secret = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { ytStatus = "Checking…"; scope.launch { ytStatus = Gemini.validateYouTube(yKey.trim()) } }, border = BorderStroke(1.dp, pal.accent)) { Text("VALIDATE KEY", color = pal.accent) }
                }
                Text("Status: $ytStatus", color = Color(0xFFFFB020), fontSize = 13.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showMem) AlertDialog(
        onDismissRequest = { showMem = false },
        confirmButton = { TextButton(onClick = { showMem = false }) { Text("CLOSE") } },
        title = { Text("Memories") },
        text = {
            val m = st.memories()
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (m.isEmpty()) Text("Nothing stored yet.") else m.forEach { Text("• [${it.cat}] ${it.text}") }
            }
        }
    )
}
