package com.csust.soleprecision.accessibility

import android.content.Context
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityManager
import java.util.Locale

class ScreenNarrator(
    context: Context,
) : TextToSpeech.OnInitListener, AutoCloseable {
    private val appContext = context.applicationContext
    private val accessibilityManager =
        appContext.getSystemService(AccessibilityManager::class.java)
    private val textToSpeech = TextToSpeech(appContext, this)
    private var isReady = false
    private var pendingMessage: String? = null
    private var requestedLocale: Locale = Locale.ENGLISH

    override fun onInit(status: Int) {
        isReady = status == TextToSpeech.SUCCESS
        if (!isReady) return

        textToSpeech.language = requestedLocale
        pendingMessage?.let {
            pendingMessage = null
            speak(it)
        }
    }

    fun setLanguage(languageTag: String) {
        requestedLocale = Locale.forLanguageTag(languageTag)
        if (isReady) {
            textToSpeech.language = requestedLocale
        }
    }

    fun speak(message: String) {
        if (message.isBlank() || accessibilityManager?.isTouchExplorationEnabled == true) {
            return
        }
        if (!isReady) {
            pendingMessage = message
            return
        }
        textToSpeech.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            SCREEN_ANNOUNCEMENT_ID,
        )
    }

    override fun close() {
        pendingMessage = null
        textToSpeech.stop()
        textToSpeech.shutdown()
        isReady = false
    }

    private companion object {
        const val SCREEN_ANNOUNCEMENT_ID = "sole_precision_screen"
    }
}
