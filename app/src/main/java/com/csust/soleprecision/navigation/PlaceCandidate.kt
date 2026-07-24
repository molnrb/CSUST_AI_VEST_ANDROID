package com.csust.soleprecision.navigation

data class PlaceCandidate(
    val id: String,
    val name: String,
    val address: String,
    val area: String,
    val latitude: Double,
    val longitude: Double,
    val typeDescription: String = "",
    val entranceLatitude: Double? = null,
    val entranceLongitude: Double? = null,
    val exitLatitude: Double? = null,
    val exitLongitude: Double? = null,
    val indoorFloorName: String = "",
    val businessTags: String = "",
    val childPlaceNames: List<String> = emptyList(),
) {
    val navigationLatitude: Double
        get() = entranceLatitude ?: latitude

    val navigationLongitude: Double
        get() = entranceLongitude ?: longitude

    val hasMappedEntrance: Boolean
        get() = entranceLatitude != null && entranceLongitude != null

    val accessibilityDetails: String
        get() = buildList {
            if (typeDescription.isNotBlank()) add(typeDescription)
            if (hasMappedEntrance) add("AMap entrance available")
            if (indoorFloorName.isNotBlank()) add("Floor $indoorFloorName")
            if (businessTags.isNotBlank()) add(businessTags)
            if (childPlaceNames.isNotEmpty()) {
                add("Includes ${childPlaceNames.take(3).joinToString()}")
            }
        }.distinct().joinToString(". ")

    val spokenDescription: String
        get() = listOf(name, address, area, accessibilityDetails)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(", ")
}

data class DestinationSuggestion(
    val name: String,
    val address: String,
    val area: String,
    val poiId: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val supportingText: String
        get() = listOf(address, area)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(", ")
}

sealed interface DestinationSearchState {
    data object Idle : DestinationSearchState
    data object Listening : DestinationSearchState
    data class Searching(val query: String) : DestinationSearchState
    data class Results(
        val query: String,
        val places: List<PlaceCandidate>,
    ) : DestinationSearchState

    data class Error(val message: String) : DestinationSearchState
}
