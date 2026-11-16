package com.jarvis.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

data class Memory(val cat: String, val text: String)

class Store(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    private fun str(k: String, d: String) = object : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = p.getString(k, d) ?: d
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            p.edit().putString(k, value).apply()
        }
    }

    private fun bool(k: String, d: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = p.getBoolean(k, d)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            p.edit().putBoolean(k, value).apply()
        }
    }

    var geminiKey by str("gk", "")
    var ytKey by str("yk", "")
    var ytEnabled by bool("yte", true)
    var model by str("model", "gemini-2.5-flash")
    var voice by str("voice", "Puck")
    var userName by str("uname", "")
    var lang by str("lang", "ml-IN")
    var hybrid by bool("hybrid", false)

    fun memories(): List<Memory> {
        val a = JSONArray(p.getString("mem", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Memory(o.getString("c"), o.getString("t"))
        }
    }

    private fun save(l: List<Memory>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("c", it.cat).put("t", it.text)) }
        p.edit().putString("mem", a.toString()).apply()
    }

    fun addMemory(cat: String, text: String) = save(memories() + Memory(cat, text))

    fun forget(q: String): Int {
        val all = memories()
        val kept = all.filterNot { it.text.contains(q, true) }
        save(kept)
        return all.size - kept.size
    }

    fun clearMemories() = p.edit().remove("mem").apply()
}
