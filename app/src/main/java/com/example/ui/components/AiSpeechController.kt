package com.example.ui.components

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.domain.model.AppLanguage
import java.util.Locale

@Stable
class AiTtsPlayer(context: Context) {

    var speakingMessageId by mutableStateOf<String?>(null)
        private set

    private var ttsReady by mutableStateOf(false)
    private var tts: TextToSpeech? = null

    init {
        runCatching {
            val engine = TextToSpeech(context.applicationContext) { status ->
                ttsReady = (status == TextToSpeech.SUCCESS)
            }
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    speakingMessageId = utteranceId
                }

                override fun onDone(utteranceId: String?) {
                    if (speakingMessageId == utteranceId) {
                        speakingMessageId = null
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (speakingMessageId == utteranceId) {
                        speakingMessageId = null
                    }
                }
            })
            tts = engine
        }
    }

    fun speak(messageId: String, rawText: String, language: AppLanguage) {
        val cleanText = cleanForSpeech(rawText)
        if (cleanText.isBlank()) return
        val engine = tts ?: return
        if (!ttsReady) return
        runCatching {
            val targetLocale = if (language == AppLanguage.AR) Locale("ar", "EG") else Locale.US
            val res = engine.setLanguage(targetLocale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(if (language == AppLanguage.AR) Locale("ar") else Locale.getDefault())
            }
            speakingMessageId = messageId
            engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, messageId)
        }.onFailure {
            speakingMessageId = null
        }
    }

    fun toggleSpeak(messageId: String, rawText: String, language: AppLanguage) {
        if (speakingMessageId == messageId) {
            stop()
        } else {
            speak(messageId, rawText, language)
        }
    }

    fun stop() {
        runCatching { tts?.stop() }
        speakingMessageId = null
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ttsReady = false
        speakingMessageId = null
    }

    companion object {
        fun cleanForSpeech(input: String): String {
            return input
                .replace("**", "")
                .replace("*", "")
                .replace("•", "، ")
                .replace("·", "، ")
                .replace(Regex("""#+\s*"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim()
        }
    }
}

@Composable
fun rememberAiTtsPlayer(): AiTtsPlayer {
    val context = LocalContext.current
    val player = remember(context) { AiTtsPlayer(context) }
    DisposableEffect(player) {
        onDispose {
            player.shutdown()
        }
    }
    return player
}
