package com.csust.soleprecision.navigation

data class NavigationInstruction(
    val maneuver: Maneuver,
    val distanceMeters: Int,
    val message: String,
    val source: Source,
    // Structured fields so the UI can rebuild the spoken message in the user's
    // language; `message` stays English for logs, tests and the engineering console.
    val roadName: String = "",
    val trafficLightNearby: Boolean = false,
    val confirmSurroundings: Boolean = false,
    val positionUnmatched: Boolean = false,
    /** Present for live AMap guidance; carries the precise pedestrian cue detail. */
    val cue: GuidanceCue? = null,
) {
    enum class Source {
        AMAP,
        DEMO,
    }
}
