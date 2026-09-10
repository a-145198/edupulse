package com.edupulse.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.edupulse.app.ui.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e("TtsManager", "Failed to instantiate TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _speakingMessageId.value = utteranceId
                }

                override fun onDone(utteranceId: String?) {
                    if (_speakingMessageId.value == utteranceId) {
                        _speakingMessageId.value = null
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (_speakingMessageId.value == utteranceId) {
                        _speakingMessageId.value = null
                    }
                }
            })
            Log.d("TtsManager", "TextToSpeech initialized successfully")
        } else {
            Log.w("TtsManager", "TextToSpeech init failed with status: $status")
        }
    }

    fun speak(messageId: String, text: String, language: AppLanguage) {
        if (!isInitialized || tts == null) {
            Log.w("TtsManager", "TTS not initialized yet")
            return
        }

        // If already speaking this message, toggle stop
        if (_speakingMessageId.value == messageId) {
            stop()
            return
        }

        stop()

        val locale = when (language) {
            AppLanguage.ENGLISH -> Locale.US
            AppLanguage.TELUGU -> Locale("te", "IN")
            AppLanguage.HINDI -> Locale("hi", "IN")
        }

        try {
            val res = tts?.setLanguage(locale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to English if regional voice data is not present
                Log.w("TtsManager", "Locale $locale not fully supported, falling back to US")
                tts?.setLanguage(Locale.US)
            }
        } catch (e: Exception) {
            Log.w("TtsManager", "Error setting language $locale: ${e.message}")
        }

        val speechCleanText = cleanForSpeech(text)
        _speakingMessageId.value = messageId
        tts?.speak(speechCleanText, TextToSpeech.QUEUE_FLUSH, null, messageId)
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
        _speakingMessageId.value = null
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isInitialized = false
    }

    private fun cleanForSpeech(text: String): String {
        return text
            .replace(Regex("""\*{1,3}"""), "") // Bold / italics
            .replace(Regex("""#{1,6}\s*"""), "") // Headers
            .replace(Regex("""---"""), "") // Dividers
            .replace(Regex("""\[DIAGRAM:.*?\]""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""\[/DIAGRAM\]"""), "")
            .replace(Regex("""`{1,3}[^`]*`{1,3}"""), "") // Code
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
