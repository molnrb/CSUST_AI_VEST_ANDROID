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
                "AMap shows 1 traffic light on this step",
            step.spokenInstruction,
        )
    }
}
