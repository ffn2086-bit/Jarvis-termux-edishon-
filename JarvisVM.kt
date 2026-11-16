package com.jarvis.assistant

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Msg(val user: Boolean, val text: String, val time: String)

private val YES = setOf("അതെ", "ശരി", "ഓക്കെ", "ok", "okay", "yes", "yeah", "send", "confirm", "ഉവ്വ്", "ആം", "ചെയ്യൂ", "അയക്കൂ", "haan", "ha", "han")
private val CANCEL = listOf("വേണ്ട", "cancel", "stop", "ഒഴിവാക്കൂ", "നിർത്തൂ", "don't", "no")
private val CONFIRM = setOf("whatsapp_send", "sms", "termux_install")

class JarvisVM(private val app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    private val actions = Actions(app, store)
    private val speaker = Speaker(app)
    private var recognizer: SpeechRecognizer? = null
    private var speakJob: Job? = null
    private var pending: List<JSONObject>? = null

    val messages = mutableStateListOf<Msg>()
    var hybrid by mutableStateOf(store.hybrid); private set
    var status by mutableStateOf(idle())
    var listening by mutableStateOf(false)
    var speaking by mutableStateOf(false)
    var busy by mutableStateOf(false)

    init {
        messages.add(Msg(false, "സിസ്റ്റം തയ്യാറാണ് സർ. എന്ത് ചെയ്യണം, പറഞ്ഞാലും.", now()))
    }

    private fun now() = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
    private fun idle() = if (store.hybrid) "ഹൈബ്രിഡ് — പൂർണ്ണ സജ്ജം" else "ശാന്തം — നിരീക്ഷിക്കുന്നു"

    fun setHybrid(h: Boolean) {
        if (h == hybrid) return
        store.hybrid = h; hybrid = h; pending = null; status = idle()
        say(if (h) "ഹൈബ്രിഡ് മോഡ് സജീവമായി സർ. കൂടുതൽ നിയന്ത്രണങ്ങൾ ഇപ്പോൾ ലഭ്യമാണ്." else "സാധാരണ മോഡിലേക്ക് മടങ്ങിയിരിക്കുന്നു സർ.")
    }

    private fun say(raw: String) {
        val t = Polite.clean(raw)
        messages.add(Msg(false, t, now()))
        speakJob = viewModelScope.launch {
            speaking = true
            try { speaker.speak(t, store) } finally { speaking = false }
        }
    }

    fun stopAll() {
        speakJob?.cancel(); speaker.stop(); speaking = false
        try { recognizer?.cancel() } catch (e: Exception) {}
        listening = false
    }

    fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(app)) {
            say("ഈ ഫോണിൽ ശബ്ദ തിരിച്ചറിയൽ ലഭ്യമാക്കാൻ Google ആപ്പ് അപ്ഡേറ്റ് ചെയ്യൂ സർ. അതുവരെ ടൈപ്പ് ചെയ്താലും മതി.")
            return
        }
        stopAll()
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(app)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { status = "കേൾക്കുന്നു…" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(e: Int) { listening = false; status = idle() }
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onResults(b: Bundle?) {
                listening = false
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) send(t) else status = idle()
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, store.lang)
        listening = true
        r.startListening(i)
    }

    private fun confirmText(a: JSONObject): String = when (a.optString("type")) {
        "whatsapp_send" -> "${a.optString("contact")}-ന് \"${a.optString("message")}\" എന്ന സന്ദേശം WhatsApp-ൽ അയക്കട്ടെ സർ? 'ശരി' എന്ന് പറഞ്ഞാൽ അയക്കാം."
        "sms" -> "${a.optString("contact")}-ന് \"${a.optString("message")}\" എന്ന SMS അയക്കട്ടെ സർ? 'ശരി' എന്ന് പറഞ്ഞാൽ അയക്കാം."
        else -> "${a.optString("repo")} Termux-ൽ ക്ലോൺ ചെയ്ത് ഇൻസ്റ്റാൾ ചെയ്യട്ടെ സർ? 'ശരി' എന്ന് പറഞ്ഞാൽ തുടങ്ങാം."
    }

    private suspend fun runActions(list: List<JSONObject>, reply: String?) {
        val extra = list.mapNotNull { actions.run(it) }
        val txt = (listOfNotNull(reply?.takeIf { it.isNotBlank() }) + extra).joinToString(" ")
        say(if (txt.isBlank()) "ശരി സർ, ചെയ്തിരിക്കുന്നു." else txt)
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        speakJob?.cancel(); speaker.stop(); speaking = false
        messages.add(Msg(true, t, now()))
        viewModelScope.launch {
            busy = true; status = "ചിന്തിക്കുന്നു…"
            try {
                val p = pending
                if (p != null) {
                    pending = null
                    val low = t.lowercase()
                    if (CANCEL.any { low.contains(it) }) { say("ശരി സർ, ഒഴിവാക്കിയിരിക്കുന്നു."); return@launch }
                    if (low.split(Regex("[\\s,.!?]+")).any { it in YES }) { runActions(p, "ശരി സർ."); return@launch }
                }
                if (store.geminiKey.isBlank()) {
                    say("ക്രമീകരണത്തിൽ Gemini API കീ ചേർത്താൽ ഉടൻ തുടങ്ങാം സർ.")
                    return@launch
                }
                val r = Gemini.chat(store, messages.toList(), hybrid)
                val conf = r.actions.filter { it.optString("type") in CONFIRM }
                if (conf.isNotEmpty()) { pending = r.actions; say(confirmText(conf.first())) }
                else runActions(r.actions, r.reply)
            } catch (e: Exception) {
                say("കണക്ഷനിൽ ചെറിയ തടസ്സം നേരിട്ടു സർ. API കീ പരിശോധിച്ച് ഒരിക്കൽ കൂടി ശ്രമിക്കാം.")
            } finally {
                busy = false; status = idle()
            }
        }
    }

    override fun onCleared() { stopAll(); recognizer?.destroy() }
}
