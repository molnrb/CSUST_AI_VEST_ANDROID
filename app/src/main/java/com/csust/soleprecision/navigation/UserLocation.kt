package com.csust.soleprecision.navigation

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val cityCode: String,
    val accuracyMeters: Float? = null,
    val source: String = "Unknown",
    val confidence: LocationConfidence = LocationConfidence.UNKNOWN,
)

enum class LocationConfidence {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN,
}
