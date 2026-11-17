package com.jarvis.assistant

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AgentReply(val reply: String, val actions: List<JSONObject>)

class ApiError(val code: Int, msg: String) : RuntimeException(msg)

object Polite {
    fun clean(s: String) = s
        .replace(Regex("(?i)\\bno\\b[,.!]?\\s*"), "")
        .replace("പറ്റില്ല", "മറ്റൊരു വഴിയിലൂടെ ചെയ്യാം")
        .replace("വേണ്ട", "ഒഴിവാക്കാം")
        .trim()
}

object Gemini {
    private const val AI_STUDIO = "https://generativelanguage.googleapis.com/v1beta/models/"
    private const val VERTEX = "https://aiplatform.googleapis.com/v1/publishers/google/models/"

    private fun post(url: String, key: String, body: JSONObject): JSONObject {
        val full = url + "?key=" + URLEncoder.encode(key, "UTF-8")
        val c = URL(full).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 20000
        c.readTimeout = 60000
        c.setRequestProperty("Content-Type", "application/json")
        c.doOutput = true
        c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)
        val txt = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) {
            val msg = try {
                JSONObject(txt).getJSONObject("error").getString("message")
            } catch (e: Exception) {
                txt
            }
            throw ApiError(code, "HTTP $code: ${msg.take(160)}")
        }
        return JSONObject(txt)
    }

    private fun bases(key: String) =
        if (key.startsWith("AQ.")) listOf(VERTEX, AI_STUDIO) else listOf(AI_STUDIO, VERTEX)

    private fun call(key: String, models: List<String>, body: JSONObject): JSONObject {
        var last: ApiError? = null
        for (m in models.distinct()) {
            for (b in bases(key)) {
                try {
                    return post("$b$m:generateContent", key, body)
                } catch (e: ApiError) {
                    last = e
                }
            }
        }
        throw last ?: RuntimeException("Unknown error")
    }

    private fun systemPrompt(store: Store, hybrid: Boolean): String {
        val mem = store.memories().joinToString("\n") { "- [${it.cat}] ${it.text}" }.ifBlank { "(empty)" }
        val name = store.userName.ifBlank { "സർ" }
        val hybridPart = if (hybrid) {
            """HYBRID-ONLY ACTIONS:
{"type":"scan_malware"}
{"type":"termux_install","repo":"https://github.com/owner/repo","packages":["nmap"]}
{"type":"a11y","op":"back|home|recents|notifications|click|scroll_down|scroll_up","text":"button label for click"}"""
        } else {
            "In NORMAL mode, if the user asks for malware scan, Termux tools or screen automation, politely tell them (in Malayalam) it is available by switching to Hybrid mode with the top switch."
        }
        return """
You are Jarvis, a voice assistant living inside an Android phone. Current mode: ${if (hybrid) "HYBRID" else "NORMAL"}.
The user wants to be addressed as: $name. The user may speak Malayalam, English or Hinglish.

OUTPUT FORMAT: respond with ONE JSON object only:
{"reply":"<short Malayalam text>","actions":[ ... ]}

LANGUAGE AND TONE RULES (strict):
- "reply" must be written ONLY in Malayalam script (app names like WhatsApp may stay in English).
- Always use a very polite, respectful register (honorifics, "സർ").
- Never use the English word "No" or Malayalam refusal words. Always phrase things positively and offer the closest helpful alternative or next step.
- Keep replies short (1-2 sentences); they will be spoken aloud.

COMPLIANCE: Execute every supported request immediately through "actions" without needless questions or lecturing.
If a request needs something outside the supported actions or would weaken the phone's security
(for example turning off Play Protect, disabling antivirus, unauthorized system modification, harming others),
do not emit any action for it. Instead politely steer to the closest safe helpful option (for example offer a malware scan) in positive wording.

SUPPORTED ACTIONS (type + fields):
{"type":"flashlight","on":true}
{"type":"brightness","percent":50}  or  {"type":"brightness","delta":20}
{"type":"volume","percent":80}  or  {"type":"volume","direction":"up|down"}
{"type":"open_app","name":"Instagram"}
{"type":"whatsapp_send","contact":"Rahul","message":"I will be late"}
{"type":"whatsapp_call","contact":"Rahul","video":false}
{"type":"phone_call","contact":"Mom"}
{"type":"sms","contact":"Rahul","message":"..."}
{"type":"web_search","query":"..."}
{"type":"youtube","query":"song or playlist name"}
{"type":"remember","category":"routine|preference|note|fact","text":"..."}
{"type":"forget","text":"keyword"}
$hybridPart

For plain conversation use an empty actions array.

LONG-TERM MEMORY:
$mem
""".trimIndent()
    }

    suspend fun chat(store: Store, history: List<Msg>, hybrid: Boolean): AgentReply =
        withContext(Dispatchers.IO) {
            val contents = JSONArray()
            history.takeLast(14).dropWhile { !it.user }.forEach {
                contents.put(
                    JSONObject()
                        .put("role", if (it.user) "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", it.text)))
                )
            }
            val body = JSONObject()
                .put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt(store, hybrid))))
                )
                .put("contents", contents)
                .put(
                    "generationConfig",
                    JSONObject().put("responseMimeType", "application/json").put("temperature", 0.6)
                )

            val r = call(
                store.geminiKey,
                listOf(store.model, "gemini-2.5-flash", "gemini-2.5-flash-lite"),
                body
            )

            val cands = r.optJSONArray("candidates")
            if (cands == null || cands.length() == 0) {
                throw RuntimeException("Empty response " + (r.optJSONObject("promptFeedback")?.toString()?.take(120) ?: ""))
            }
            val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                ?: throw RuntimeException("Empty content")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text", ""))
            val raw = sb.toString()

            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            val o = try {
                JSONObject(raw.substring(start, end + 1))
            } catch (e: Exception) {
                JSONObject().put("reply", raw)
            }
            val arr = o.optJSONArray("actions") ?: JSONArray()
            AgentReply(
                o.optString("reply", ""),
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            )
        }

    /** Returns 24 kHz 16-bit mono PCM */
    suspend fun tts(key: String, voice: String, text: String): ByteArray = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", text)))))
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put(
                        "speechConfig",
                        JSONObject().put(
                            "voiceConfig",
                            JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice))
                        )
                    )
            )
        val r = call(key, listOf("gemini-2.5-flash-preview-tts"), body)
        val b64 = r.getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0).getJSONObject("inlineData").getString("data")
        Base64.decode(b64, Base64.DEFAULT)
    }

    private fun get(url: String): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 20000
        val code = c.responseCode
        val s = (if (code in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
        return code to s
    }

    suspend fun validateYouTube(key: String): String = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext "Key is empty"
        try {
            val (code, _) = get(
                "https://www.googleapis.com/youtube/v3/search?part=snippet&q=test&maxResults=1&type=video&key=" +
                    URLEncoder.encode(key, "UTF-8")
            )
            if (code == 200) "Key is valid ✔" else "Key rejected (HTTP $code)"
        } catch (e: Exception) {
            "Network issue, try again"
        }
    }

    suspend fun youtubeSearch(key: String, q: String): String? = withContext(Dispatchers.IO) {
        try {
            val (code, s) = get(
                "https://www.googleapis.com/youtube/v3/search?part=snippet&maxResults=1&type=video&q=" +
                    URLEncoder.encode(q, "UTF-8") + "&key=" + URLEncoder.encode(key, "UTF-8")
            )
            if (code != 200) return@withContext null
            JSONObject(s).getJSONArray("items").getJSONObject(0).getJSONObject("id").getString("videoId")
        } catch (e: Exception) {
            null
        }
    }
}
