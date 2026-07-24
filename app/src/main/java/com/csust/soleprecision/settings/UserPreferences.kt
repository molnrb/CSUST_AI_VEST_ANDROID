package com.csust.soleprecision.settings

import android.content.Context

enum class GuidanceMode(val displayName: String) {
    HAPTIC_AND_SPEECH("Vibration and speech"),
    HAPTIC_ONLY("Vibration only"),
    SPEECH_ONLY("Speech only"),
}

enum class SpeechDetail(val displayName: String) {
    CONCISE("Concise"),
    STANDARD("Standard"),
    DETAILED("Detailed"),
}

enum class AppLanguage(val displayName: String, val languageTag: String) {
    ENGLISH("English", "en"),
    SIMPLIFIED_CHINESE("简体中文", "zh-CN"),
    TRADITIONAL_CHINESE("繁體中文", "zh-TW"),
}

data class UserPreferences(
    val guidanceMode: GuidanceMode = GuidanceMode.HAPTIC_AND_SPEECH,
    val vibrationStrength: Int = 70,
    val speakerVolume: Int = 70,
    val speechDetail: SpeechDetail = SpeechDetail.STANDARD,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val extraSpokenPrompts: Boolean = true,
)

class UserPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): UserPreferences = UserPreferences(
        guidanceMode = enumValueOrDefault(
            preferences.getString(KEY_GUIDANCE_MODE, null),
            GuidanceMode.HAPTIC_AND_SPEECH,
        ),
        vibrationStrength = preferences.getInt(KEY_VIBRATION_STRENGTH, 70).coerceIn(20, 100),
        speakerVolume = preferences.getInt(KEY_SPEAKER_VOLUME, 70).coerceIn(0, 100),
        speechDetail = enumValueOrDefault(
            preferences.getString(KEY_SPEECH_DETAIL, null),
            SpeechDetail.STANDARD,
        ),
        language = enumValueOrDefault(
            preferences.getString(KEY_LANGUAGE, null),
            AppLanguage.ENGLISH,
        ),
        extraSpokenPrompts = preferences.getBoolean(KEY_EXTRA_PROMPTS, true),
    )

    fun save(value: UserPreferences) {
        preferences.edit()
            .putString(KEY_GUIDANCE_MODE, value.guidanceMode.name)
            .putInt(KEY_VIBRATION_STRENGTH, value.vibrationStrength.coerceIn(20, 100))
            .putInt(KEY_SPEAKER_VOLUME, value.speakerVolume.coerceIn(0, 100))
            .putString(KEY_SPEECH_DETAIL, value.speechDetail.name)
            .putString(KEY_LANGUAGE, value.language.name)
            .putBoolean(KEY_EXTRA_PROMPTS, value.extraSpokenPrompts)
            .remove(KEY_NAVIGATION_CONTROL_STYLE)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(
        stored: String?,
        fallback: T,
    ): T = stored
        ?.let { value -> enumValues<T>().firstOrNull { it.name == value } }
        ?: fallback

    private companion object {
        const val FILE_NAME = "user_preferences"
        const val KEY_GUIDANCE_MODE = "guidance_mode"
        const val KEY_VIBRATION_STRENGTH = "vibration_strength"
        const val KEY_SPEAKER_VOLUME = "speaker_volume"
        const val KEY_SPEECH_DETAIL = "speech_detail"
        const val KEY_LANGUAGE = "language"
        const val KEY_EXTRA_PROMPTS = "extra_spoken_prompts"
        // Kept only to remove values written by older builds.
        const val KEY_NAVIGATION_CONTROL_STYLE = "navigation_control_style"
    }
}
