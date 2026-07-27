package com.csust.soleprecision.navigation

/**
 * What a cue is for. Ordered by urgency: a walking user needs to know about an
 * action early enough to slow down, again while preparing, and precisely at the
 * point of action — the cadence recommended in the project safety notes.
 * Driving-style "turn right in 50 metres, turn right" is not enough.
 */
enum class CueStage {
    /** First notice of the upcoming maneuver, far enough away to plan. */
    EARLY,

    /** The action is close: slow down and locate the feature. */
    PREPARE,

    /** Act now, at the maneuver point. */
    ACT,

    /** The maneuver is done: confirm the new road, heading and length. */
    CONFIRM,

    /** Reassurance while walking a long segment. */
    PROGRESS,

    /** Position drifted away from the mapped route. */
    OFF_ROUTE,

    /** Final approach to the destination. */
    ARRIVAL,
}

/** Which body side a directional haptic cue belongs to. */
enum class TurnSide {
    LEFT,
    RIGHT,
    NONE,
}

/**
 * One thing to say (and optionally vibrate) at one moment. Purely structured so
 * it can be rendered in any language and mapped to haptics without re-parsing text.
 */
data class GuidanceCue(
    val stage: CueStage,
    val maneuver: Maneuver,
    val distanceMeters: Int,
    val roadName: String = "",
    val currentRoadName: String = "",
    val stepDistanceMeters: Int = 0,
    /** 1–12 clock position of the maneuver relative to the current heading. */
    val clockPosition: Int? = null,
    val turnAngleDegrees: Int? = null,
    val orientation: String = "",
    val landmark: String = "",
    val trafficLightCount: Int = 0,
    val needsConfirmation: Boolean = false,
    val remainingRouteMeters: Int = 0,
    val remainingRouteMinutes: Int = 0,
    val offRouteMeters: Int = 0,
    val side: TurnSide = TurnSide.NONE,
) {
    /** Crossings and level changes always deserve an explicit verification cue. */
    val isHazardManeuver: Boolean
        get() = maneuver == Maneuver.CROSSWALK || needsConfirmation
}

/**
 * English rendering used by logs, unit tests and the engineering console.
 * The user-facing sentence is rebuilt from the cue's structured fields in the
 * chosen language; see `GuidancePhrases`.
 */
fun GuidanceCue.toInstruction(): NavigationInstruction = NavigationInstruction(
    maneuver = maneuver,
    distanceMeters = distanceMeters,
    message = buildString {
        append(stage.name.lowercase().replaceFirstChar(Char::titlecase))
        append(": ")
        append(maneuver.spokenLabel)
        if (distanceMeters > 0 && stage != CueStage.CONFIRM) {
            append(" in $distanceMeters metres")
        }
        if (roadName.isNotBlank()) append(" toward $roadName")
        clockPosition?.let { append(", at $it o'clock") }
        if (landmark.isNotBlank()) append(", near $landmark")
        if (offRouteMeters > 0) append("; $offRouteMeters metres off route")
    },
    source = NavigationInstruction.Source.AMAP,
    roadName = roadName,
    trafficLightNearby = trafficLightCount > 0 && distanceMeters <= 50,
    confirmSurroundings = needsConfirmation,
    positionUnmatched = offRouteMeters > 0,
    cue = this,
)

/** Everything known about the current moment on the route. */
data class GuidanceSnapshot(
    val stepIndex: Int,
    val maneuver: Maneuver,
    val distanceToManeuverMeters: Int,
    val nextRoadName: String = "",
    val currentRoadName: String = "",
    val stepDistanceMeters: Int = 0,
    val orientation: String = "",
    val relativeBearingDegrees: Int? = null,
    val turnAngleDegrees: Int? = null,
    val landmark: String = "",
    val trafficLightCount: Int = 0,
    val needsConfirmation: Boolean = false,
    val remainingRouteMeters: Int = 0,
    val remainingRouteSeconds: Int = 0,
    /** Perpendicular drift from the mapped route, or null while matched to it. */
    val offRouteMeters: Int? = null,
)

/**
 * Turns a stream of AMap position updates into a small number of precise walking
 * cues. The engine is deliberately stateful and pure: it decides *whether* to
 * speak, the caller decides how loudly and in which language.
 *
 * It never repeats a stage for the same step, so speech density stays low enough
 * that the user can still hear traffic.
 */
class PedestrianGuidanceEngine {
    private val announcedStages = mutableMapOf<Int, MutableSet<CueStage>>()
    private var lastStepIndex: Int? = null
    private var lastProgressBucket: Int? = null
    private var lastProgressDistance: Int? = null
    private var lastOffRouteMeters: Int? = null
    private var arrivalAnnounced = false

    fun reset() {
        announcedStages.clear()
        lastStepIndex = null
        lastProgressBucket = null
        lastProgressDistance = null
        lastOffRouteMeters = null
        arrivalAnnounced = false
    }

    /** Called when AMap recalculates: keep position state but allow cues again. */
    fun resetForNewRoute() = reset()

    fun onSnapshot(snapshot: GuidanceSnapshot): GuidanceCue? {
        val stepChanged = lastStepIndex != null && lastStepIndex != snapshot.stepIndex
        if (stepChanged) {
            lastProgressBucket = null
            lastProgressDistance = null
        }
        val previousStep = lastStepIndex
        lastStepIndex = snapshot.stepIndex

        // 1. Drift correction outranks everything except nothing — a blind walker
        // needs to know immediately that they have left the mapped path.
        val drift = snapshot.offRouteMeters
        if (drift != null && drift >= OFF_ROUTE_MINIMUM_METERS) {
            val previousDrift = lastOffRouteMeters
            if (previousDrift == null || kotlin.math.abs(drift - previousDrift) >= OFF_ROUTE_STEP_METERS) {
                lastOffRouteMeters = drift
                return snapshot.cue(CueStage.OFF_ROUTE, offRouteMeters = drift)
            }
            return null
        }
        if (drift == null) lastOffRouteMeters = null

        // 2. Final approach.
        if (
            !arrivalAnnounced &&
            snapshot.remainingRouteMeters in 1..ARRIVAL_APPROACH_METERS
        ) {
            arrivalAnnounced = true
            return snapshot.cue(CueStage.ARRIVAL)
        }

        // 3. A maneuver was completed: name the new road so the user can confirm it.
        if (
            stepChanged &&
            previousStep != null &&
            snapshot.stepDistanceMeters >= CONFIRM_MINIMUM_STEP_METERS &&
            markAnnounced(snapshot.stepIndex, CueStage.CONFIRM)
        ) {
            return snapshot.cue(CueStage.CONFIRM)
        }

        // 4–6. Act / prepare / early, closest first, once per step.
        val actThreshold = if (snapshot.isHazard()) {
            HAZARD_ACT_METERS
        } else {
            ACT_METERS
        }
        if (
            snapshot.distanceToManeuverMeters <= actThreshold &&
            markAnnounced(snapshot.stepIndex, CueStage.ACT)
        ) {
            return snapshot.cue(CueStage.ACT)
        }
        if (
            snapshot.distanceToManeuverMeters <= PREPARE_METERS &&
            markAnnounced(snapshot.stepIndex, CueStage.PREPARE)
        ) {
            return snapshot.cue(CueStage.PREPARE)
        }
        if (
            snapshot.distanceToManeuverMeters <= EARLY_METERS &&
            snapshot.stepDistanceMeters > EARLY_MINIMUM_STEP_METERS &&
            markAnnounced(snapshot.stepIndex, CueStage.EARLY)
        ) {
            return snapshot.cue(CueStage.EARLY)
        }

        // 7. Reassurance on long segments, spaced out by remaining distance so it
        // is frequent near a decision point and sparse in the middle of a block.
        val bucketSize = progressBucketSize(snapshot.distanceToManeuverMeters) ?: return null
        val bucket = snapshot.distanceToManeuverMeters / bucketSize
        if (bucket == lastProgressBucket) return null
        // Bucket edges alone would fire twice in a row when a boundary is crossed
        // a metre after the previous cue, so also require real distance walked.
        val walkedEnough = lastProgressDistance?.let { previous ->
            previous - snapshot.distanceToManeuverMeters >= bucketSize / 2
        } ?: true
        if (!walkedEnough) return null
        lastProgressBucket = bucket
        lastProgressDistance = snapshot.distanceToManeuverMeters
        return snapshot.cue(CueStage.PROGRESS)
    }

    private fun markAnnounced(stepIndex: Int, stage: CueStage): Boolean {
        val stages = announcedStages.getOrPut(stepIndex) { mutableSetOf() }
        return stages.add(stage)
    }

    private fun GuidanceSnapshot.isHazard(): Boolean =
        maneuver == Maneuver.CROSSWALK || needsConfirmation

    private fun GuidanceSnapshot.cue(
        stage: CueStage,
        offRouteMeters: Int = 0,
    ): GuidanceCue = GuidanceCue(
        stage = stage,
        maneuver = maneuver,
        distanceMeters = distanceToManeuverMeters,
        roadName = nextRoadName,
        currentRoadName = currentRoadName,
        stepDistanceMeters = stepDistanceMeters,
        clockPosition = relativeBearingDegrees?.let(::clockPositionFor),
        turnAngleDegrees = turnAngleDegrees,
        orientation = orientation,
        landmark = landmark,
        trafficLightCount = trafficLightCount,
        needsConfirmation = needsConfirmation,
        remainingRouteMeters = remainingRouteMeters,
        remainingRouteMinutes = ((remainingRouteSeconds + 59) / 60).coerceAtLeast(0),
        offRouteMeters = offRouteMeters,
        side = maneuver.turnSide(),
    )

    companion object {
        const val EARLY_METERS = 120
        const val PREPARE_METERS = 30
        const val ACT_METERS = 8
        const val HAZARD_ACT_METERS = 12
        const val ARRIVAL_APPROACH_METERS = 25
        const val EARLY_MINIMUM_STEP_METERS = 60
        const val CONFIRM_MINIMUM_STEP_METERS = 15
        const val OFF_ROUTE_MINIMUM_METERS = 8
        const val OFF_ROUTE_STEP_METERS = 10

        /** Null means "close enough that prepare/act cues already cover it". */
        fun progressBucketSize(distanceMeters: Int): Int? = when {
            distanceMeters > 150 -> 50
            distanceMeters > PREPARE_METERS + 30 -> 20
            else -> null
        }

        /**
         * Clock face relative to the current heading: 12 is straight ahead,
         * 3 is a right angle to the right, 9 to the left. Blind travellers are
         * routinely trained on clock directions, which are far more precise than
         * "turn right".
         */
        fun clockPositionFor(relativeBearingDegrees: Int): Int {
            val normalized = ((relativeBearingDegrees % 360) + 360) % 360
            val hour = Math.round(normalized / 30.0).toInt() % 12
            return if (hour == 0) 12 else hour
        }

        fun Maneuver.turnSide(): TurnSide = when (this) {
            Maneuver.SLIGHT_LEFT,
            Maneuver.LEFT,
            Maneuver.SHARP_LEFT,
            -> TurnSide.LEFT

            Maneuver.SLIGHT_RIGHT,
            Maneuver.RIGHT,
            Maneuver.SHARP_RIGHT,
            -> TurnSide.RIGHT

            else -> TurnSide.NONE
        }
    }
}
