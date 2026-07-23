package com.csust.soleprecision.navigation

import com.amap.api.navi.enums.IconType

object AmapManeuverMapper {
    fun fromIconType(iconType: Int): Maneuver = when (iconType) {
        IconType.LEFT -> Maneuver.LEFT
        IconType.LEFT_FRONT, IconType.MERGE_LEFT -> Maneuver.SLIGHT_LEFT
        IconType.LEFT_BACK -> Maneuver.SHARP_LEFT
        IconType.RIGHT -> Maneuver.RIGHT
        IconType.RIGHT_FRONT, IconType.MERGE_RIGHT -> Maneuver.SLIGHT_RIGHT
        IconType.RIGHT_BACK -> Maneuver.SHARP_RIGHT
        IconType.LEFT_TURN_AROUND, IconType.U_TURN_RIGHT -> Maneuver.U_TURN
        IconType.STRAIGHT, IconType.SPECIAL_CONTINUE -> Maneuver.STRAIGHT
        IconType.CROSSWALK -> Maneuver.CROSSWALK
        IconType.STAIRCASE, IconType.BY_STAIR, IconType.LADDER -> Maneuver.STAIRS
        IconType.LIFT, IconType.BY_ELEVATOR -> Maneuver.ELEVATOR
        IconType.ARRIVED_DESTINATION -> Maneuver.ARRIVED
        else -> Maneuver.UNKNOWN
    }
}
