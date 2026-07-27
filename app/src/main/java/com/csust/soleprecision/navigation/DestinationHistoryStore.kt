package com.csust.soleprecision.navigation

import android.content.Context

class DestinationHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): List<PlaceCandidate> = preferences
        .getString(KEY_RECENT, null)
        .orEmpty()
        .lineSequence()
        .mapNotNull(PlaceCandidateCodec::decode)
        .toList()

    fun add(place: PlaceCandidate): List<PlaceCandidate> {
        val updated = (listOf(place) + load())
            .distinctBy { it.id.ifBlank { "${it.latitude}:${it.longitude}" } }
            .take(MAX_RECENT)
        preferences.edit()
            .putString(
                KEY_RECENT,
                updated.joinToString("\n", transform = PlaceCandidateCodec::encode),
            )
            .apply()
        return updated
    }

    fun clear() {
        preferences.edit().remove(KEY_RECENT).apply()
    }

    private companion object {
        const val FILE_NAME = "destination_history"
        const val KEY_RECENT = "recent_places"
        const val MAX_RECENT = 8
    }
}
