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

object LocationValidity {
    const val MAX_FALLBACK_AGE_MS = 120_000L

    fun isValidCoordinate(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0)

    fun isFresh(
        timestampMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        maximumAgeMillis: Long = MAX_FALLBACK_AGE_MS,
    ): Boolean {
        val age = nowMillis - timestampMillis
        return timestampMillis > 0L && age in 0..maximumAgeMillis
    }
}
