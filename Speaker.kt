package com.jarvis.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

class Speaker(ctx: Context) {
    private var ready = false
    private var tts: TextToSpeech? = null
    private var track: AudioTrack? = null

    init {
        tts = TextToSpeech(ctx.applicationContext) { st ->
            ready = st == TextToSpeech.SUCCESS
            if (ready) tts?.language = Locale("ml", "IN")
        }
    }

    suspend fun speak(text: String, store: Store) {
        stop()
        if (store.geminiKey.isNotBlank()) {
            val pcm = try { Gemini.tts(store.geminiKey, store.voice, text) } catch (e: Exception) { null }
            if (pcm != null) { play(pcm); return }
        }
        if (ready) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    private suspend fun play(pcm: ByteArray) = withContext(Dispatchers.IO) {
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(24000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(pcm.size)
            .setTransferMode(AudioTrack.MODE_STATIC).build()
        t.write(pcm, 0, pcm.size)
        track = t
        t.play()
        delay(pcm.size / 2 * 1000L / 24000 + 200)
        try { t.release() } catch (e: Exception) {}
    }

    fun stop() {
        try { tts?.stop() } catch (e: Exception) {}
        try { track?.stop(); track?.release() } catch (e: Exception) {}
        track = null
    }
}
