package com.csust.soleprecision.navigation

data class NavigationInstruction(
    val maneuver: Maneuver,
    val distanceMeters: Int,
    val message: String,
    val source: Source,
) {
    enum class Source {
        AMAP,
        DEMO,
    }
}
