package com.csust.soleprecision.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PedestrianGuidanceEngineTest {
    private val engine = PedestrianGuidanceEngine()

    private fun snapshot(
        distance: Int,
        stepIndex: Int = 0,
        maneuver: Maneuver = Maneuver.RIGHT,
        stepDistance: Int = 200,
        offRoute: Int? = null,
        remaining: Int = 400,
        relativeBearing: Int? = null,
        trafficLights: Int = 0,
        needsConfirmation: Boolean = false,
    ) = GuidanceSnapshot(
        stepIndex = stepIndex,
        maneuver = maneuver,
        distanceToManeuverMeters = distance,
        nextRoadName = "Lushan Road",
        currentRoadName = "Yinpenling Street",
        stepDistanceMeters = stepDistance,
        orientation = "north",
        relativeBearingDegrees = relativeBearing,
        turnAngleDegrees = 88,
        trafficLightCount = trafficLights,
        needsConfirmation = needsConfirmation,
        remainingRouteMeters = remaining,
        remainingRouteSeconds = 300,
        offRouteMeters = offRoute,
    )

    @Test
    fun walkingTowardATurnProducesEarlyThenPrepareThenAct() {
        // Far away: reassurance only.
        assertEquals(CueStage.PROGRESS, engine.onSnapshot(snapshot(200))?.stage)
        assertEquals(CueStage.EARLY, engine.onSnapshot(snapshot(115))?.stage)
        assertEquals(CueStage.PREPARE, engine.onSnapshot(snapshot(28))?.stage)
        assertEquals(CueStage.ACT, engine.onSnapshot(snapshot(6))?.stage)
    }

    @Test
    fun eachStageIsSpokenOnlyOncePerStep() {
        engine.onSnapshot(snapshot(115))
        assertNull(engine.onSnapshot(snapshot(114))?.stage?.takeIf { it == CueStage.EARLY })
        engine.onSnapshot(snapshot(25))
        val repeated = engine.onSnapshot(snapshot(24))
        assertTrue(repeated == null || repeated.stage != CueStage.PREPARE)
    }

    @Test
    fun progressCadenceWidensWithDistance() {
        assertEquals(50, PedestrianGuidanceEngine.progressBucketSize(400))
        assertEquals(20, PedestrianGuidanceEngine.progressBucketSize(120))
        // Inside prepare range the prepare and act cues already cover the user.
        assertNull(PedestrianGuidanceEngine.progressBucketSize(25))
    }

    @Test
    fun progressIsNotRepeatedJustBecauseABucketEdgeWasCrossed() {
        // 400 -> 399 crosses a 50 m bucket edge after one metre of walking.
        assertEquals(CueStage.PROGRESS, engine.onSnapshot(snapshot(400))?.stage)
        assertNull(engine.onSnapshot(snapshot(399)))
        // Once real distance has been covered, reassurance resumes.
        assertEquals(CueStage.PROGRESS, engine.onSnapshot(snapshot(360))?.stage)
    }

    @Test
    fun progressIsNotRepeatedInsideTheSameBucket() {
        assertEquals(CueStage.PROGRESS, engine.onSnapshot(snapshot(380))?.stage)
        // Still inside the same 50 m bucket: stay quiet.
        assertNull(engine.onSnapshot(snapshot(370)))
        // Crossing into the next bucket speaks again.
        assertEquals(CueStage.PROGRESS, engine.onSnapshot(snapshot(340))?.stage)
    }

    @Test
    fun completingAStepConfirmsTheNewRoad() {
        engine.onSnapshot(snapshot(50, stepIndex = 0))
        val cue = engine.onSnapshot(snapshot(180, stepIndex = 1))
        assertEquals(CueStage.CONFIRM, cue?.stage)
        assertEquals("Lushan Road", cue?.roadName)
        assertEquals(200, cue?.stepDistanceMeters)
    }

    @Test
    fun veryShortStepsDoNotTriggerAConfirmation() {
        engine.onSnapshot(snapshot(50, stepIndex = 0))
        val cue = engine.onSnapshot(snapshot(10, stepIndex = 1, stepDistance = 8))
        assertTrue(cue == null || cue.stage != CueStage.CONFIRM)
    }

    @Test
    fun crossingsGetTheirActCueEarlierThanPlainTurns() {
        val crossing = PedestrianGuidanceEngine()
        val cue = crossing.onSnapshot(
            snapshot(11, maneuver = Maneuver.CROSSWALK, trafficLights = 1),
        )
        assertEquals(CueStage.ACT, cue?.stage)
        assertTrue(cue!!.isHazardManeuver)
        // A plain turn at the same distance is still only "prepare".
        val turn = PedestrianGuidanceEngine()
        assertEquals(CueStage.PREPARE, turn.onSnapshot(snapshot(11))?.stage)
    }

    @Test
    fun driftOffTheRouteOutranksOtherCues() {
        val cue = engine.onSnapshot(snapshot(20, offRoute = 14))
        assertEquals(CueStage.OFF_ROUTE, cue?.stage)
        assertEquals(14, cue?.offRouteMeters)
        // Small changes are not repeated.
        assertNull(engine.onSnapshot(snapshot(20, offRoute = 16)))
        // A significant change is.
        assertEquals(
            CueStage.OFF_ROUTE,
            engine.onSnapshot(snapshot(20, offRoute = 30))?.stage,
        )
    }

    @Test
    fun smallDriftIsIgnoredBecauseGpsIsNoisy() {
        val cue = engine.onSnapshot(snapshot(60, offRoute = 4))
        assertTrue(cue == null || cue.stage != CueStage.OFF_ROUTE)
    }

    @Test
    fun finalApproachAnnouncesOnceOnly() {
        assertEquals(CueStage.ARRIVAL, engine.onSnapshot(snapshot(60, remaining = 20))?.stage)
        val second = engine.onSnapshot(snapshot(55, remaining = 15))
        assertTrue(second == null || second.stage != CueStage.ARRIVAL)
    }

    @Test
    fun turnSideIsDerivedForHaptics() {
        val left = engine.onSnapshot(snapshot(6, maneuver = Maneuver.LEFT))
        assertEquals(TurnSide.LEFT, left?.side)
        val fresh = PedestrianGuidanceEngine()
        assertEquals(TurnSide.RIGHT, fresh.onSnapshot(snapshot(6, maneuver = Maneuver.RIGHT))?.side)
        val straight = PedestrianGuidanceEngine()
        assertEquals(
            TurnSide.NONE,
            straight.onSnapshot(snapshot(6, maneuver = Maneuver.STRAIGHT))?.side,
        )
    }

    @Test
    fun clockPositionsFollowTheHeading() {
        assertEquals(12, PedestrianGuidanceEngine.clockPositionFor(0))
        assertEquals(3, PedestrianGuidanceEngine.clockPositionFor(90))
        assertEquals(9, PedestrianGuidanceEngine.clockPositionFor(270))
        assertEquals(1, PedestrianGuidanceEngine.clockPositionFor(28))
        assertEquals(12, PedestrianGuidanceEngine.clockPositionFor(355))
        assertEquals(6, PedestrianGuidanceEngine.clockPositionFor(-180))
    }

    @Test
    fun clockPositionIsCarriedIntoTheCue() {
        val cue = engine.onSnapshot(snapshot(6, relativeBearing = 92))
        assertEquals(3, cue?.clockPosition)
    }

    @Test
    fun resettingClearsStageMemoryForANewRoute() {
        engine.onSnapshot(snapshot(6))
        engine.reset()
        assertNotNull(engine.onSnapshot(snapshot(6)))
    }
}
