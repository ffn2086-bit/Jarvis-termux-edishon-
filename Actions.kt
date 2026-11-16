package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

class Actions(private val ctx: Context, private val store: Store) {
    private val hybridOnly = setOf("scan_malware", "termux_install", "a11y")

    private fun start(i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i) }

    private fun findNumber(name: String): String? {
        if (name.matches(Regex("[+0-9 -]{6,}"))) return name.filter { it.isDigit() || it == '+' }
        val c = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), null
        ) ?: return null
        c.use { if (it.moveToFirst()) return it.getString(0) }
        return null
    }

    private fun waNumber(raw: String): String {
        val d = raw.filter { it.isDigit() }
        return if (d.length == 10) "91$d" else d   // default country code: India
    }

    private fun whatsappCallId(name: String, video: Boolean): Long? {
        val mime = if (video) "vnd.android.cursor.item/vnd.com.whatsapp.video.call"
        else "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
        val c = ctx.contentResolver.query(
            ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.Data._ID),
            "${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.Data.DISPLAY_NAME} LIKE ?",
            arrayOf(mime, "%$name%"), null
        ) ?: return null
        c.use { if (it.moveToFirst()) return it.getLong(0) }
        return null
    }

    suspend fun run(a: JSONObject): String? {
        val type = a.optString("type")
        if (type in hybridOnly && !store.hybrid)
            return "ഈ സൗകര്യം ഹൈബ്രിഡ് മോഡിൽ ലഭ്യമാണ് സർ. മുകളിലെ സ്വിച്ച് ഉപയോഗിച്ച് മാറാം."
        return try {
            when (type) {
                "flashlight" -> {
                    val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val id = cm.cameraIdList.first {
                        cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    }
                    cm.setTorchMode(id, a.optBoolean("on", true)); null
                }
                "brightness" -> {
                    if (!Settings.System.canWrite(ctx)) {
                        start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                        return "ബ്രൈറ്റ്നസ് മാറ്റാൻ ഈ സ്ക്രീനിൽ അനുമതി നൽകൂ സർ."
                    }
                    val cr = ctx.contentResolver
                    val cur = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
                    val v = if (a.has("percent")) a.getInt("percent") * 255 / 100
                    else (cur + a.optInt("delta", 20) * 255 / 100)
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, v.coerceIn(1, 255)); null
                }
                "volume" -> {
                    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    if (a.has("percent")) {
                        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, a.getInt("percent").coerceIn(0, 100) * max / 100, AudioManager.FLAG_SHOW_UI)
                    } else {
                        val d = if (a.optString("direction") == "down") AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE
                        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, d, AudioManager.FLAG_SHOW_UI)
                    }
                    null
                }
                "open_app" -> {
                    val pm = ctx.packageManager
                    val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    val name = a.optString("name")
                    val ri = pm.queryIntentActivities(q, 0).firstOrNull { it.loadLabel(pm).toString().contains(name, true) }
                    val li = ri?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
                    if (li != null) { start(li); null } else "$name തുറക്കാൻ ആ ആപ്പ് ഫോണിൽ കണ്ടെത്താനായില്ല സർ."
                }
                "whatsapp_send" -> {
                    val num = findNumber(a.optString("contact"))
                        ?: return "ആ പേര് കോൺടാക്ടിൽ കാണാൻ കഴിഞ്ഞില്ല സർ. നമ്പർ പറഞ്ഞാലും മതി."
                    val uri = Uri.parse("https://wa.me/${waNumber(num)}?text=${Uri.encode(a.optString("message"))}")
                    start(Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp"))
                    delay(3000)
                    val svc = JarvisAccessibilityService.instance
                    if (svc != null && (svc.clickDesc("Send") || svc.clickDesc("അയയ്ക്കുക"))) "സന്ദേശം അയച്ചിരിക്കുന്നു സർ."
                    else "WhatsApp തുറന്നിട്ടുണ്ട് സർ, അയക്കുക ബട്ടൺ അമർത്തിയാൽ മതി."
                }
                "whatsapp_call" -> {
                    val id = whatsappCallId(a.optString("contact"), a.optBoolean("video", false))
                        ?: return "WhatsApp കോൾ ചെയ്യാൻ ആ കോൺടാക്ട് കണ്ടെത്താനായില്ല സർ."
                    start(Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.contacts/data/$id")).setPackage("com.whatsapp")); null
                }
                "phone_call" -> {
                    val num = findNumber(a.optString("contact")) ?: return "ആ പേര് കോൺടാക്ടിൽ കാണാൻ കഴിഞ്ഞില്ല സർ."
                    val ok = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CALL_PHONE) == 0
                    start(Intent(if (ok) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:$num"))); null
                }
                "sms" -> {
                    val num = findNumber(a.optString("contact")) ?: return "ആ പേര് കോൺടാക്ടിൽ കാണാൻ കഴിഞ്ഞില്ല സർ."
                    val sm = ctx.getSystemService(SmsManager::class.java)
                    sm.sendMultipartTextMessage(num, null, sm.divideMessage(a.optString("message")), null, null)
                    "സന്ദേശം അയച്ചിരിക്കുന്നു സർ."
                }
                "web_search" -> {
                    start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(a.optString("query"))))); null
                }
                "youtube" -> {
                    val q = a.optString("query")
                    val id = if (store.ytEnabled && store.ytKey.isNotBlank()) Gemini.youtubeSearch(store.ytKey, q) else null
                    val uri = if (id != null) "https://www.youtube.com/watch?v=$id"
                    else "https://www.youtube.com/results?search_query=" + Uri.encode(q)
                    start(Intent(Intent.ACTION_VIEW, Uri.parse(uri))); null
                }
                "remember" -> { store.addMemory(a.optString("category", "note"), a.optString("text")); null }
                "forget" -> { store.forget(a.optString("text")); null }
                "scan_malware" -> withContext(Dispatchers.Default) { Scanner.scan(ctx) }
                "termux_install" -> termux(a)
                "a11y" -> a11y(a)
                else -> null
            }
        } catch (e: SecurityException) {
            "ഇതിനുള്ള അനുമതി ക്രമീകരണത്തിൽ നൽകിയാൽ ഉടൻ ചെയ്യാം സർ."
        } catch (e: Exception) {
            "ഇത് പൂർത്തിയാക്കാൻ ഒരു ചെറിയ തടസ്സം നേരിട്ടു സർ. ഒരിക്കൽ കൂടി ശ്രമിക്കാം."
        }
    }

    private fun a11y(a: JSONObject): String? {
        val svc = JarvisAccessibilityService.instance
        if (svc == null) {
            start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return "Jarvis ആക്സസിബിലിറ്റി സർവീസ് ഓൺ ചെയ്താൽ ഉടൻ നിയന്ത്രിക്കാം സർ."
        }
        val ok = when (val op = a.optString("op")) {
            "click" -> svc.clickText(a.optString("text"))
            "scroll_down" -> svc.scroll(true)
            "scroll_up" -> svc.scroll(false)
            else -> svc.global(op)
        }
        return if (ok) null else "ആ ഭാഗം സ്ക്രീനിൽ കണ്ടെത്താനായില്ല സർ."
    }

    private fun termux(a: JSONObject): String {
        val repo = a.optString("repo")
        if (!repo.matches(Regex("^https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(\\.git)?$")))
            return "സാധുവായ GitHub ലിങ്ക് (github.com/ഉടമ/റിപ്പോ) നൽകിയാൽ ഉടൻ ഇൻസ്റ്റാൾ ചെയ്യാം സർ."
        val pk = a.optJSONArray("packages")
        val pkgs = (0 until (pk?.length() ?: 0)).map { pk!!.getString(it) }.filter { it.matches(Regex("^[a-z0-9+._-]+$")) }
        val script = "pkg update -y && pkg install -y git ${pkgs.joinToString(" ")} && mkdir -p ~/tools && cd ~/tools && git clone $repo; echo; echo 'Jarvis: finished'"
        val i = Intent("com.termux.RUN_COMMAND").setClassName("com.termux", "com.termux.app.RunCommandService")
            .putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", script))
            .putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
        return try {
            ctx.startService(i)
            "Termux-ൽ ഇൻസ്റ്റലേഷൻ തുടങ്ങിയിട്ടുണ്ട് സർ. ~/tools ഫോൾഡറിൽ ലഭിക്കും."
        } catch (e: Exception) {
            "Termux ഇൻസ്റ്റാൾ ചെയ്ത്, termux.properties-ൽ allow-external-apps=true നൽകിയാൽ ഉടൻ ചെയ്യാം സർ."
        }
    }
}
