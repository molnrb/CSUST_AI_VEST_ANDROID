package com.csust.soleprecision.accessibility

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.accessibility.AccessibilityManager
import java.util.ArrayDeque
import java.util.Locale

/**
 * Priority ladder for app narration. Mirrors the project safety hierarchy:
 * safety/critical output must never be cut off by routine narration.
 */
enum class NarrationPriority {
    /** Safety or failure messages. Interrupt everything; nothing may cut them off. */
    CRITICAL,

    /** Screen changes and item browsing. Replaces routine speech, never a critical one. */
    HIGH,

    /** Optional context. Queued behind whatever is currently speaking. */
    NORMAL,
}

class ScreenNarrator(
    context: Context,
    private val onUnavailable: (String) -> Unit = {},
) : TextToSpeech.OnInitListener, AutoCloseable {
    private val appContext = context.applicationContext
    private val accessibilityManager =
        appContext.getSystemService(AccessibilityManager::class.java)
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val textToSpeech = TextToSpeech(appContext, this)
    private var isReady = false
    private var initFailed = false
    private var pendingMessages = ArrayDeque<QueuedMessage>()
    private var requestedLocale: Locale = Locale.ENGLISH
    private var volumePercent: Int = 100
    private var utteranceSerial = 0
    private var speakingPriority: NarrationPriority? = null
    private var hasAudioFocus = false

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusRequest = AudioFocusRequest
        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        .build()

    override fun onInit(status: Int) {
        isReady = status == TextToSpeech.SUCCESS
        if (!isReady) {
            initFailed = true
            pendingMessages.clear()
            mainHandler.post {
                onUnavailable(
                    "Speech output is unavailable on this phone. " +
                        "Screen narration cannot be spoken.",
                )
            }
            return
        }

        textToSpeech.setAudioAttributes(audioAttributes)
        textToSpeech.setOnUtteranceProgressListener(progressListener)
        textToSpeech.language = requestedLocale
        val queued = pendingMessages
        pendingMessages = ArrayDeque()
        queued.forEach { speak(it.message, it.priority) }
    }

    fun setLanguage(languageTag: String) {
        requestedLocale = Locale.forLanguageTag(languageTag)
        if (isReady) {
            textToSpeech.language = requestedLocale
        }
    }

    /** Applies the shared speaker-volume preference (0–100) to app narration. */
    fun setVolume(percent: Int) {
        volumePercent = percent.coerceIn(0, 100)
    }

    fun speak(message: String, priority: NarrationPriority = NarrationPriority.HIGH) {
        if (message.isBlank() || accessibilityManager?.isTouchExplorationEnabled == true) {
            return
        }
        if (initFailed) {
            onUnavailable("Speech output is unavailable on this phone.")
            return
        }
        if (!isReady) {
            // Keep at most one non-critical message plus any critical ones for init replay.
            if (priority != NarrationPriority.CRITICAL) {
                pendingMessages.removeAll { it.priority != NarrationPriority.CRITICAL }
            }
            pendingMessages.addLast(QueuedMessage(message, priority))
            return
        }

        val interruptsCurrent = when (priority) {
            NarrationPriority.CRITICAL -> true
            NarrationPriority.HIGH -> speakingPriority != NarrationPriority.CRITICAL
            NarrationPriority.NORMAL -> speakingPriority == null
        }
        requestAudioFocus()
        val params = android.os.Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volumePercent / 100f)
        }
        val utteranceId = "sole_narration_${priority.name}_${utteranceSerial++}"
        speakingPriority = maxPriority(speakingPriority.takeIf { !interruptsCurrent }, priority)
        textToSpeech.speak(
            message,
            if (interruptsCurrent) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
            params,
            utteranceId,
        )
    }

    /** Immediately stops all narration, e.g. when higher-priority device audio must win. */
    fun stopSpeaking() {
        if (isReady) {
            textToSpeech.stop()
        }
        speakingPriority = null
        abandonAudioFocus()
    }

    override fun close() {
        pendingMessages.clear()
        textToSpeech.stop()
        textToSpeech.shutdown()
        abandonAudioFocus()
        isReady = false
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            mainHandler.post { handleUtteranceFinished() }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            mainHandler.post { handleUtteranceFinished() }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            mainHandler.post { handleUtteranceFinished() }
        }
    }

    private fun handleUtteranceFinished() {
        if (!textToSpeech.isSpeaking) {
            speakingPriority = null
            abandonAudioFocus()
        }
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        val manager = audioManager ?: return
        hasAudioFocus =
            manager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        audioManager?.abandonAudioFocusRequest(focusRequest)
        hasAudioFocus = false
    }

    private fun maxPriority(
        current: NarrationPriority?,
        new: NarrationPriority,
    ): NarrationPriority = when {
        current == null -> new
        current.ordinal <= new.ordinal -> current
        else -> new
    }

    private data class QueuedMessage(
        val message: String,
        val priority: NarrationPriority,
    )
}
