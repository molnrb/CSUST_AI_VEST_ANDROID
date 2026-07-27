package com.csust.soleprecision.navigation

import android.content.Context
import android.net.Uri

/**
 * Persistent saved/favourite destinations, separate from the rolling recent history.
 * Uses the same line-per-place encoding as [DestinationHistoryStore].
 */
class FavoriteDestinationsStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): List<PlaceCandidate> = preferences
        .getString(KEY_FAVORITES, null)
        .orEmpty()
        .lineSequence()
        .mapNotNull(PlaceCandidateCodec::decode)
        .toList()

    fun isSaved(place: PlaceCandidate): Boolean =
        load().any { keyOf(it) == keyOf(place) }

    fun add(place: PlaceCandidate): List<PlaceCandidate> {
        val updated = (listOf(place) + load())
            .distinctBy(::keyOf)
            .take(MAX_FAVORITES)
        persist(updated)
        return updated
    }

    fun remove(place: PlaceCandidate): List<PlaceCandidate> {
        val updated = load().filterNot { keyOf(it) == keyOf(place) }
        persist(updated)
        return updated
    }

    fun clear() {
        preferences.edit().remove(KEY_FAVORITES).apply()
    }

    private fun persist(places: List<PlaceCandidate>) {
        preferences.edit()
            .putString(
                KEY_FAVORITES,
                places.joinToString("\n", transform = PlaceCandidateCodec::encode),
            )
            .apply()
    }

    private fun keyOf(place: PlaceCandidate): String =
        place.id.ifBlank { "${place.latitude}:${place.longitude}" }

    private companion object {
        const val FILE_NAME = "favorite_destinations"
        const val KEY_FAVORITES = "favorite_places"
        const val MAX_FAVORITES = 20
    }
}

/** Shared line-based persistence codec for [PlaceCandidate]. */
internal object PlaceCandidateCodec {
    private const val SEPARATOR = "|"
    private const val CHILD_SEPARATOR = ""
    private const val LEGACY_FIELD_COUNT = 6

    fun encode(place: PlaceCandidate): String = listOf(
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

    fun decode(value: String): PlaceCandidate? {
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
}
