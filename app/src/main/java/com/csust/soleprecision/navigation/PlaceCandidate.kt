package com.csust.soleprecision.navigation

data class PlaceCandidate(
    val id: String,
    val name: String,
    val address: String,
    val area: String,
    val latitude: Double,
    val longitude: Double,
) {
    val spokenDescription: String
        get() = listOf(name, address, area)
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
