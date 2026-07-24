package com.csust.soleprecision.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibleNavigationModelTest {
    @Test
    fun `route summary rounds duration up and formats short distance`() {
        val summary = RouteSummary(distanceMeters = 850, durationSeconds = 61)

        assertEquals(2, summary.durationMinutes)
        assertEquals("850 metres", summary.spokenDistance)
    }

    @Test
    fun `route summary formats kilometre distance`() {
        val summary = RouteSummary(distanceMeters = 3_250, durationSeconds = 2_400)

        assertEquals("3.3 kilometres", summary.spokenDistance)
        assertEquals(40, summary.durationMinutes)
    }

    @Test
    fun `place description omits duplicate and empty address parts`() {
        val place = PlaceCandidate(
            id = "poi",
            name = "Hunan University",
            address = "",
            area = "Yuelu District",
            latitude = 28.18,
            longitude = 112.94,
        )

        assertEquals("Hunan University, Yuelu District", place.spokenDescription)
    }

    @Test
    fun `walking step describes distance road and mapped traffic light`() {
        val step = WalkingRouteStep(
            maneuver = Maneuver.CROSSWALK,
            distanceMeters = 35,
            durationSeconds = 50,
            roadName = "Lushan South Road",
            mappedTrafficLightCount = 1,
        )

        assertEquals(
            "Crosswalk ahead, then continue for 35 metres on Lushan South Road. " +
                "AMap shows 1 traffic light on this step. " +
                "Confirm the real surroundings before continuing",
            step.spokenInstruction,
        )
    }

    @Test
    fun `place prefers mapped entrance for navigation`() {
        val place = PlaceCandidate(
            id = "poi",
            name = "Library",
            address = "Campus",
            area = "Changsha",
            latitude = 28.1,
            longitude = 112.9,
            typeDescription = "Public library",
            entranceLatitude = 28.1002,
            entranceLongitude = 112.9003,
            indoorFloorName = "1F",
        )

        assertEquals(28.1002, place.navigationLatitude, 0.0)
        assertEquals(112.9003, place.navigationLongitude, 0.0)
        assertEquals(
            "Public library. AMap entrance available. Floor 1F",
            place.accessibilityDetails,
        )
    }

    @Test
    fun `route mental map summarizes mapped walking complexity`() {
        val summary = RouteSummary(
            distanceMeters = 620,
            durationSeconds = 540,
            steps = listOf(
                WalkingRouteStep(Maneuver.STRAIGHT, 200, 180, "Campus Road"),
                WalkingRouteStep(Maneuver.CROSSWALK, 20, 40, "Main Road"),
                WalkingRouteStep(Maneuver.RIGHT, 400, 320, "Library Walk"),
            ),
            mappedTrafficLightCount = 1,
            initialDirection = "north-east",
        )

        assertEquals(1, summary.turnCount)
        assertEquals(1, summary.crossingCount)
        assert(summary.mentalMapSummary.contains("Starts north-east"))
        assert(summary.mentalMapSummary.contains("does not confirm real-time crossing safety"))
    }

    @Test
    fun `route geometry derives direction and remaining walking distance`() {
        val points = listOf(
            RouteCoordinate(28.0, 112.0),
            RouteCoordinate(28.0001, 112.0),
            RouteCoordinate(28.0002, 112.0),
        )

        assertEquals("north", RouteGeometry.compassDirection(points))
        val remaining = RouteGeometry.remainingDistanceMeters(
            RouteCoordinate(28.0001, 112.0),
            points,
        )
        assert(remaining in 10..13)
    }

    @Test
    fun `location validation rejects null island and stale fallback fixes`() {
        assertEquals(false, LocationValidity.isValidCoordinate(0.0, 0.0))
        assertEquals(false, LocationValidity.isValidCoordinate(91.0, 112.0))
        assertEquals(true, LocationValidity.isValidCoordinate(28.2282, 112.9388))

        val now = 1_000_000L
        assertEquals(true, LocationValidity.isFresh(now - 60_000L, now))
        assertEquals(false, LocationValidity.isFresh(now - 180_000L, now))
        assertEquals(false, LocationValidity.isFresh(now + 1L, now))
    }
}
