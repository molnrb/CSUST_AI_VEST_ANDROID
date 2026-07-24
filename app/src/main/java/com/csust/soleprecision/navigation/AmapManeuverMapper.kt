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
        IconType.OVERPASS, IconType.SKY_CHANNEL -> Maneuver.OVERPASS
        IconType.UNDERPASS, IconType.CHANNEL -> Maneuver.UNDERPASS
        IconType.BY_ESCALATOR -> Maneuver.ESCALATOR
        IconType.SLOPE -> Maneuver.RAMP
        IconType.BRIDGE -> Maneuver.BRIDGE
        IconType.ARRIVED_TUNNEL -> Maneuver.TUNNEL
        IconType.WALK_ROAD, IconType.LOW_TRAFFIC_CROSS, IconType.LOW_CROSS ->
            Maneuver.PEDESTRIAN_WAY
        IconType.ENTER_BUILDING -> Maneuver.ENTER_BUILDING
        IconType.LEAVE_BUILDING -> Maneuver.LEAVE_BUILDING
        IconType.SUBWAY -> Maneuver.SUBWAY_PASSAGE
        IconType.FERRY, IconType.CRUISE_ROUTE -> Maneuver.FERRY
        IconType.ENTER_ROUNDABOUT, IconType.OUT_ROUNDABOUT -> Maneuver.ROUNDABOUT
        IconType.SQUARE, IconType.PARK -> Maneuver.PARK_OR_SQUARE
        IconType.ARRIVED_DESTINATION -> Maneuver.ARRIVED
        else -> Maneuver.UNKNOWN
    }
}
