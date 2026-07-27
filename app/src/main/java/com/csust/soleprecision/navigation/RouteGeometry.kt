package com.csust.soleprecision.navigation

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object RouteGeometry {
    fun compassDirection(points: List<RouteCoordinate>): String {
        val first = points.firstOrNull() ?: return ""
        val last = points.drop(1).firstOrNull { distanceMeters(first, it) >= 3.0 }
            ?: return ""
        val bearing = bearingDegrees(first, last)
        val directions = listOf(
            "north",
            "north-east",
            "east",
            "south-east",
            "south",
            "south-west",
            "west",
            "north-west",
        )
        return directions[((bearing + 22.5) / 45.0).toInt() % directions.size]
    }

    fun turnAngleDegrees(
        previousPoints: List<RouteCoordinate>,
        nextPoints: List<RouteCoordinate>,
    ): Int? {
        val previousEnd = previousPoints.lastOrNull() ?: return null
        val previousStart = previousPoints
            .dropLast(1)
            .lastOrNull { distanceMeters(it, previousEnd) >= 2.0 }
            ?: return null
        val nextEnd = nextPoints
            .drop(1)
            .firstOrNull { distanceMeters(previousEnd, it) >= 2.0 }
            ?: return null
        val before = bearingDegrees(previousStart, previousEnd)
        val after = bearingDegrees(previousEnd, nextEnd)
        val signed = ((after - before + 540.0) % 360.0) - 180.0
        return kotlin.math.abs(signed).roundToInt()
    }

    fun remainingDistanceMeters(
        current: RouteCoordinate,
        stepPoints: List<RouteCoordinate>,
    ): Int {
        if (stepPoints.size < 2) return 0
        val latitudeScale = EARTH_RADIUS_METERS * PI / 180.0
        val longitudeScale = latitudeScale * cos(current.latitude.toRadians())
        fun local(point: RouteCoordinate): Pair<Double, Double> =
            (point.longitude - current.longitude) * longitudeScale to
                (point.latitude - current.latitude) * latitudeScale

        var nearestSegment = 0
        var nearestFraction = 0.0
        var nearestSquaredDistance = Double.MAX_VALUE
        for (index in 0 until stepPoints.lastIndex) {
            val (startX, startY) = local(stepPoints[index])
            val (endX, endY) = local(stepPoints[index + 1])
            val segmentX = endX - startX
            val segmentY = endY - startY
            val segmentSquared = segmentX * segmentX + segmentY * segmentY
            val fraction = if (segmentSquared == 0.0) {
                0.0
            } else {
                (-(startX * segmentX + startY * segmentY) / segmentSquared)
                    .coerceIn(0.0, 1.0)
            }
            val projectedX = startX + fraction * segmentX
            val projectedY = startY + fraction * segmentY
            val squaredDistance = projectedX * projectedX + projectedY * projectedY
            if (squaredDistance < nearestSquaredDistance) {
                nearestSquaredDistance = squaredDistance
                nearestSegment = index
                nearestFraction = fraction
            }
        }

        var remaining =
            distanceMeters(stepPoints[nearestSegment], stepPoints[nearestSegment + 1]) *
                (1.0 - nearestFraction)
        for (index in nearestSegment + 1 until stepPoints.lastIndex) {
            remaining += distanceMeters(stepPoints[index], stepPoints[index + 1])
        }
        return remaining.roundToInt().coerceAtLeast(0)
    }

    /**
     * Shortest distance from [current] to the polyline, i.e. how far the user has
     * drifted off the mapped route. Uses the same local flat-earth projection as
     * [remainingDistanceMeters]; accurate well beyond the tens of metres that matter.
     */
    fun distanceToPathMeters(
        current: RouteCoordinate,
        points: List<RouteCoordinate>,
    ): Int? {
        if (points.isEmpty()) return null
        if (points.size == 1) return distanceMeters(current, points.first()).roundToInt()
        val latitudeScale = EARTH_RADIUS_METERS * PI / 180.0
        val longitudeScale = latitudeScale * cos(current.latitude.toRadians())
        fun local(point: RouteCoordinate): Pair<Double, Double> =
            (point.longitude - current.longitude) * longitudeScale to
                (point.latitude - current.latitude) * latitudeScale

        var nearestSquaredDistance = Double.MAX_VALUE
        for (index in 0 until points.lastIndex) {
            val (startX, startY) = local(points[index])
            val (endX, endY) = local(points[index + 1])
            val segmentX = endX - startX
            val segmentY = endY - startY
            val segmentSquared = segmentX * segmentX + segmentY * segmentY
            val fraction = if (segmentSquared == 0.0) {
                0.0
            } else {
                (-(startX * segmentX + startY * segmentY) / segmentSquared)
                    .coerceIn(0.0, 1.0)
            }
            val projectedX = startX + fraction * segmentX
            val projectedY = startY + fraction * segmentY
            val squaredDistance = projectedX * projectedX + projectedY * projectedY
            if (squaredDistance < nearestSquaredDistance) {
                nearestSquaredDistance = squaredDistance
            }
        }
        return sqrt(nearestSquaredDistance).roundToInt()
    }

    /**
     * Compass bearing the user should be facing when they begin walking [points],
     * or null when the geometry is too short to be meaningful.
     */
    fun initialBearingDegrees(points: List<RouteCoordinate>): Int? {
        val first = points.firstOrNull() ?: return null
        val ahead = points.drop(1).firstOrNull { distanceMeters(first, it) >= 3.0 } ?: return null
        return bearingDegrees(first, ahead).roundToInt()
    }

    /** Signed difference between a heading and a target bearing, normalized to 0–359. */
    fun relativeBearingDegrees(headingDegrees: Int, targetBearingDegrees: Int): Int =
        ((targetBearingDegrees - headingDegrees) % 360 + 360) % 360

    fun distanceMeters(first: RouteCoordinate, second: RouteCoordinate): Double {
        val lat1 = first.latitude.toRadians()
        val lat2 = second.latitude.toRadians()
        val deltaLat = (second.latitude - first.latitude).toRadians()
        val deltaLon = (second.longitude - first.longitude).toRadians()
        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
        return EARTH_RADIUS_METERS * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    private fun bearingDegrees(first: RouteCoordinate, second: RouteCoordinate): Double {
        val lat1 = first.latitude.toRadians()
        val lat2 = second.latitude.toRadians()
        val deltaLon = (second.longitude - first.longitude).toRadians()
        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        return (atan2(y, x) * 180.0 / PI + 360.0) % 360.0
    }

    private fun Double.toRadians(): Double = this * PI / 180.0

    private const val EARTH_RADIUS_METERS = 6_371_000.0
}
