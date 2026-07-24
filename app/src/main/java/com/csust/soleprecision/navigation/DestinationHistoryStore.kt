package com.csust.soleprecision.navigation

import android.content.Context
import android.net.Uri

class DestinationHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): List<PlaceCandidate> = preferences
        .getString(KEY_RECENT, null)
        .orEmpty()
        .lineSequence()
        .mapNotNull(::decode)
        .toList()

    fun add(place: PlaceCandidate): List<PlaceCandidate> {
        val updated = (listOf(place) + load())
            .distinctBy { it.id.ifBlank { "${it.latitude}:${it.longitude}" } }
            .take(MAX_RECENT)
        preferences.edit()
            .putString(KEY_RECENT, updated.joinToString("\n", transform = ::encode))
            .apply()
        return updated
    }

    fun clear() {
        preferences.edit().remove(KEY_RECENT).apply()
    }

    private fun encode(place: PlaceCandidate): String = listOf(
        place.id,
        place.name,
        place.address,
        place.area,
        place.latitude.toString(),
        place.longitude.toString(),
    ).joinToString(SEPARATOR) { Uri.encode(it) }

    private fun decode(value: String): PlaceCandidate? {
        val parts = value.split(SEPARATOR)
        if (parts.size != 6) return null
        return PlaceCandidate(
            id = Uri.decode(parts[0]),
            name = Uri.decode(parts[1]),
            address = Uri.decode(parts[2]),
            area = Uri.decode(parts[3]),
            latitude = Uri.decode(parts[4]).toDoubleOrNull() ?: return null,
            longitude = Uri.decode(parts[5]).toDoubleOrNull() ?: return null,
        )
    }

    private companion object {
        const val FILE_NAME = "destination_history"
        const val KEY_RECENT = "recent_places"
        const val SEPARATOR = "|"
        const val MAX_RECENT = 8
    }
}
