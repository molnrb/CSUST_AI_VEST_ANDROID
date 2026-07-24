package com.csust.soleprecision.navigation

data class RouteSummary(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val steps: List<WalkingRouteStep> = emptyList(),
    val mappedTrafficLightCount: Int = 0,
    val routeId: Int = 0,
    val routeLabel: String = "",
    val initialDirection: String = "",
    val pathCoordinates: List<RouteCoordinate> = emptyList(),
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

    val turnCount: Int
        get() = steps.count {
            it.maneuver in setOf(
                Maneuver.SLIGHT_LEFT,
                Maneuver.LEFT,
                Maneuver.SHARP_LEFT,
                Maneuver.SLIGHT_RIGHT,
                Maneuver.RIGHT,
                Maneuver.SHARP_RIGHT,
                Maneuver.U_TURN,
                Maneuver.ROUNDABOUT,
            )
        }

    val crossingCount: Int
        get() = steps.count { it.maneuver == Maneuver.CROSSWALK }

    val levelChangeCount: Int
        get() = steps.count {
            it.maneuver in setOf(
                Maneuver.STAIRS,
                Maneuver.ELEVATOR,
                Maneuver.ESCALATOR,
                Maneuver.RAMP,
            )
        }

    val gradeSeparatedCount: Int
        get() = steps.count {
            it.maneuver in setOf(
                Maneuver.OVERPASS,
                Maneuver.UNDERPASS,
                Maneuver.BRIDGE,
                Maneuver.TUNNEL,
                Maneuver.SUBWAY_PASSAGE,
            )
        }

    val mentalMapSummary: String
        get() = buildString {
            append(spokenDistance)
            append(", about ")
            append(durationMinutes)
            append(" minutes")
            if (initialDirection.isNotBlank()) {
                append(". Starts ")
                append(initialDirection)
            }
            append(". ")
            append(turnCount)
            append(if (turnCount == 1) " turn" else " turns")
            if (crossingCount > 0) {
                append(", ")
                append(crossingCount)
                append(if (crossingCount == 1) " mapped crosswalk" else " mapped crosswalks")
            }
            if (mappedTrafficLightCount > 0) {
                append(", ")
                append(mappedTrafficLightCount)
                append(
                    if (mappedTrafficLightCount == 1) {
                        " mapped traffic light"
                    } else {
                        " mapped traffic lights"
                    },
                )
            }
            if (levelChangeCount > 0) {
                append(", ")
                append(levelChangeCount)
                append(if (levelChangeCount == 1) " level change" else " level changes")
            }
            if (gradeSeparatedCount > 0) {
                append(", ")
                append(gradeSeparatedCount)
                append(
                    if (gradeSeparatedCount == 1) {
                        " bridge, tunnel, or passage segment"
                    } else {
                        " bridge, tunnel, or passage segments"
                    },
                )
            }
            if (routeLabel.isNotBlank()) {
                append(". AMap label: ")
                append(routeLabel)
            }
            append(". Map data does not confirm real-time crossing safety")
        }
}

data class WalkingRouteStep(
    val maneuver: Maneuver,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val roadName: String,
    val mappedTrafficLightCount: Int = 0,
    val orientation: String = "",
    val turnAngleDegrees: Int? = null,
    val coordinates: List<RouteCoordinate> = emptyList(),
) {
    val needsEnvironmentalConfirmation: Boolean
        get() = maneuver in setOf(
            Maneuver.CROSSWALK,
            Maneuver.OVERPASS,
            Maneuver.UNDERPASS,
            Maneuver.STAIRS,
            Maneuver.ESCALATOR,
            Maneuver.ELEVATOR,
            Maneuver.RAMP,
            Maneuver.BRIDGE,
            Maneuver.TUNNEL,
            Maneuver.SUBWAY_PASSAGE,
            Maneuver.FERRY,
        )

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
            if (orientation.isNotBlank()) {
                append(". Direction ")
                append(orientation)
            }
            turnAngleDegrees?.let {
                if (maneuver != Maneuver.STRAIGHT && maneuver != Maneuver.UNKNOWN) {
                    append(". About ")
                    append(it)
                    append(" degrees")
                }
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
            if (needsEnvironmentalConfirmation) {
                append(". Confirm the real surroundings before continuing")
            }
        }
}

data class RouteCoordinate(
    val latitude: Double,
    val longitude: Double,
)
