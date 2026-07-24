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
        place.typeDescription,
        place.entranceLatitude?.toString().orEmpty(),
        place.entranceLongitude?.toString().orEmpty(),
        place.exitLatitude?.toString().orEmpty(),
        place.exitLongitude?.toString().orEmpty(),
        place.indoorFloorName,
        place.businessTags,
        place.childPlaceNames.joinToString(CHILD_SEPARATOR),
    ).joinToString(SEPARATOR) { Uri.encode(it) }

    private fun decode(value: String): PlaceCandidate? {
        val parts = value.split(SEPARATOR)
        if (parts.size < LEGACY_FIELD_COUNT) return null
        return PlaceCandidate(
            id = Uri.decode(parts[0]),
            name = Uri.decode(parts[1]),
            address = Uri.decode(parts[2]),
            area = Uri.decode(parts[3]),
            latitude = Uri.decode(parts[4]).toDoubleOrNull() ?: return null,
            longitude = Uri.decode(parts[5]).toDoubleOrNull() ?: return null,
            typeDescription = parts.decodedOrEmpty(6),
            entranceLatitude = parts.decodedOrEmpty(7).toDoubleOrNull(),
            entranceLongitude = parts.decodedOrEmpty(8).toDoubleOrNull(),
            exitLatitude = parts.decodedOrEmpty(9).toDoubleOrNull(),
            exitLongitude = parts.decodedOrEmpty(10).toDoubleOrNull(),
            indoorFloorName = parts.decodedOrEmpty(11),
            businessTags = parts.decodedOrEmpty(12),
            childPlaceNames = parts
                .decodedOrEmpty(13)
                .split(CHILD_SEPARATOR)
                .filter(String::isNotBlank),
        )
    }

    private fun List<String>.decodedOrEmpty(index: Int): String =
        getOrNull(index)?.let(Uri::decode).orEmpty()

    private companion object {
        const val FILE_NAME = "destination_history"
        const val KEY_RECENT = "recent_places"
        const val SEPARATOR = "|"
        const val CHILD_SEPARATOR = "\u001F"
        const val LEGACY_FIELD_COUNT = 6
        const val MAX_RECENT = 8
    }
}
