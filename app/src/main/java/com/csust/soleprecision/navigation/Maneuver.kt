package com.csust.soleprecision.navigation

enum class Maneuver(
    val wireCode: Int,
    val spokenLabel: String,
) {
    UNKNOWN(0, "Continue with caution"),
    STRAIGHT(1, "Continue straight"),
    SLIGHT_LEFT(2, "Bear left"),
    LEFT(3, "Turn left"),
    SHARP_LEFT(4, "Turn sharply left"),
    SLIGHT_RIGHT(5, "Bear right"),
    RIGHT(6, "Turn right"),
    SHARP_RIGHT(7, "Turn sharply right"),
    U_TURN(8, "Make a U-turn"),
    CROSSWALK(9, "Crosswalk ahead"),
    STAIRS(10, "Stairs ahead"),
    ELEVATOR(11, "Elevator ahead"),
    ARRIVED(12, "Destination reached"),
}
