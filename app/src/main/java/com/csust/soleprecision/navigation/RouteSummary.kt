package com.csust.soleprecision.navigation

data class RouteSummary(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val steps: List<WalkingRouteStep> = emptyList(),
    val mappedTrafficLightCount: Int = 0,
) {
    val durationMinutes: Int
        get() = ((durationSeconds + 59) / 60).coerceAtLeast(1)

    val spokenDistance: String
        get() = if (distanceMeters >= 1_000) {
            val kilometres = distanceMeters / 1_000.0
            "%.1f kilometres".format(kilometres)
        } else {
            "$distanceMeters metres"
        }
}

data class WalkingRouteStep(
    val maneuver: Maneuver,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val roadName: String,
    val mappedTrafficLightCount: Int = 0,
) {
    val spokenInstruction: String
        get() = buildString {
            append(maneuver.spokenLabel)
            if (distanceMeters > 0) {
                append(
                    if (maneuver == Maneuver.STRAIGHT) {
                        " for "
                    } else {
                        ", then continue for "
                    },
                )
                append(distanceMeters)
                append(" metres")
            }
            if (roadName.isNotBlank()) {
                append(" on ")
                append(roadName)
            }
            if (mappedTrafficLightCount > 0) {
                append(". AMap shows ")
                append(mappedTrafficLightCount)
                append(
                    if (mappedTrafficLightCount == 1) {
                        " traffic light on this step"
                    } else {
                        " traffic lights on this step"
                    },
                )
            }
        }
}
