package com.csust.soleprecision.navigation

import android.content.Context
import com.amap.api.navi.AMapNavi
import com.amap.api.navi.NaviSetting
import com.amap.api.navi.SimpleNaviListener
import com.amap.api.navi.enums.NaviType
import com.amap.api.navi.enums.TravelStrategy
import com.amap.api.navi.model.AMapCalcRouteResult
import com.amap.api.navi.model.AMapNaviLocation
import com.amap.api.navi.model.AMapNaviPath
import com.amap.api.navi.model.NaviInfo
import com.amap.api.navi.model.NaviLatLng
import com.amap.api.navi.model.NaviPoi
import com.amap.api.maps.model.LatLng

class AmapNavigationController(
    context: Context,
    private val onInstruction: (NavigationInstruction) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onRouteReady: (RouteSummary) -> Unit = {},
    private val onRoutesReady: (List<RouteSummary>) -> Unit = {},
    /**
     * Resolves a short landmark name near a maneuver point so cues can say
     * "turn right at <place>". Optional: guidance works without it.
     */
    private val landmarkResolver: ((Double, Double, (String) -> Unit) -> Unit)? = null,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private var amapNavi: AMapNavi? = null
    private val guidanceEngine = PedestrianGuidanceEngine()

    @Volatile
    private var activePathCoordinates: List<RouteCoordinate> = emptyList()

    /** Landmark near the end of each step, filled in lazily as steps are reached. */
    private val stepLandmarks = mutableMapOf<Int, String>()

    @Volatile
    private var landmarkRequestedForStep: Int = -1

    @Volatile
    private var lastCue: GuidanceCue? = null

    // Written from AMap SDK callbacks and read while building instructions; volatile so
    // a callback on a different thread never observes a stale route or step list.
    @Volatile
    private var lastInstructionKey: String? = null

    @Volatile
    private var pendingNaviType: Int = NaviType.GPS

    @Volatile
    private var startWhenRouteReady = true

    @Volatile
    private var activeWalkingSteps: List<WalkingRouteStep> = emptyList()

    @Volatile
    private var availableRoutes: List<RouteSummary> = emptyList()

    @Volatile
    private var latestNaviLocation: AMapNaviLocation? = null

    @Volatile
    private var routeCompletionHandled = false

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private val listener = object : SimpleNaviListener() {
        override fun onInitNaviSuccess() {
            onStatus("AMap navigation ready")
        }

        override fun onInitNaviFailure() {
            onStatus("AMap navigation could not initialize")
        }

        override fun onStartNavi(type: Int) {
            onStatus(
                if (type == NaviType.EMULATOR) {
                    "Simulated AMap walking navigation active"
                } else {
                    "GPS walking navigation active"
                },
            )
        }

        override fun onCalculateRouteSuccess(routeResult: AMapCalcRouteResult?) {
            handleSuccessfulRoutes()
        }

        override fun onCalculateRouteSuccess(routeIds: IntArray?) {
            handleSuccessfulRoutes()
        }

        private fun handleSuccessfulRoutes() {
            if (routeCompletionHandled) return
            routeCompletionHandled = true
            val navi = amapNavi
            val paths = navi
                ?.naviPaths
                .orEmpty()
                .entries
                .sortedBy { it.key }
                .map { (routeId, path) -> routeId to path }
                .ifEmpty {
                    navi?.naviPath?.let { listOf(0 to it) }.orEmpty()
                }
            if (paths.isEmpty()) {
                availableRoutes = emptyList()
                activeWalkingSteps = emptyList()
                onRoutesReady(emptyList())
                onStatus("AMap returned no usable walking route")
                return
            }
            availableRoutes = paths.map { (routeId, path) ->
                path.toRouteSummary(routeId)
            }
            onRoutesReady(availableRoutes)
            availableRoutes.firstOrNull()?.let(::activateRouteSummary)

            val mode = if (pendingNaviType == NaviType.EMULATOR) "Simulated" else "GPS"
            if (startWhenRouteReady) {
                startPlannedRoute()
            } else {
                onStatus("$mode walking route ready")
            }
        }

        override fun onCalculateRouteFailure(errorCode: Int) {
            if (routeCompletionHandled) return
            routeCompletionHandled = true
            onStatus("AMap could not calculate that walking route ($errorCode)")
        }

        override fun onCalculateRouteFailure(routeResult: AMapCalcRouteResult?) {
            if (routeCompletionHandled) return
            routeCompletionHandled = true
            val detail = routeResult?.errorDetail.orEmpty()
            onStatus(
                if (detail.isBlank()) {
                    "AMap could not calculate that walking route"
                } else {
                    "AMap route failed: $detail"
                },
            )
        }

        override fun onReCalculateRouteForYaw() {
            routeCompletionHandled = false
            lastInstructionKey = null
            guidanceEngine.resetForNewRoute()
            onStatus("Off route; AMap is calculating new walking guidance")
        }

        override fun onGpsOpenStatus(enabled: Boolean) {
            if (!enabled) onStatus("Device GPS is off")
        }

        override fun onGpsSignalWeak(isWeak: Boolean) {
            onStatus(
                if (isWeak) {
                    "GPS signal is weak; guidance confidence reduced"
                } else {
                    "GPS signal recovered"
                },
            )
        }

        override fun onLocationChange(location: AMapNaviLocation?) {
            latestNaviLocation = location
        }

        override fun onNaviInfoUpdate(naviInfo: NaviInfo?) {
            naviInfo ?: return
            val maneuver = AmapManeuverMapper.fromIconType(naviInfo.iconType)
            val road = naviInfo.nextRoadName.orEmpty()
            val liveLocation = latestNaviLocation
            val stepIndex = liveLocation
                ?.curStepIndex
                ?.takeIf { it in activeWalkingSteps.indices }
                ?: naviInfo.curStep
            val mappedStep = activeWalkingSteps.getOrNull(stepIndex)
            val currentPoint = liveLocation?.coord?.let {
                RouteCoordinate(it.latitude, it.longitude)
            }
            val distance = if (
                currentPoint != null &&
                mappedStep?.coordinates?.size?.let { it >= 2 } == true
            ) {
                RouteGeometry.remainingDistanceMeters(
                    current = currentPoint,
                    stepPoints = mappedStep.coordinates,
                )
            } else {
                mappedStep?.distanceMeters ?: 0
            }
            requestLandmarkForStep(stepIndex, mappedStep)

            val nextStep = activeWalkingSteps.getOrNull(stepIndex + 1)
            val relativeBearing = liveLocation
                ?.bearing
                ?.takeIf { it.isFinite() }
                ?.let { heading ->
                    val target = nextStep
                        ?.coordinates
                        ?.let(RouteGeometry::initialBearingDegrees)
                    target?.let {
                        RouteGeometry.relativeBearingDegrees(heading.toInt(), it)
                    }
                }
            val offRoute = if (
                liveLocation?.isMatchNaviPath == false &&
                currentPoint != null &&
                activePathCoordinates.isNotEmpty()
            ) {
                RouteGeometry.distanceToPathMeters(currentPoint, activePathCoordinates)
            } else {
                null
            }

            val cue = guidanceEngine.onSnapshot(
                GuidanceSnapshot(
                    stepIndex = stepIndex,
                    maneuver = maneuver,
                    distanceToManeuverMeters = distance,
                    nextRoadName = road,
                    currentRoadName = naviInfo.currentRoadName.orEmpty()
                        .ifBlank { mappedStep?.roadName.orEmpty() },
                    stepDistanceMeters = mappedStep?.distanceMeters ?: 0,
                    orientation = nextStep?.orientation.orEmpty()
                        .ifBlank { mappedStep?.orientation.orEmpty() },
                    relativeBearingDegrees = relativeBearing,
                    turnAngleDegrees = nextStep?.turnAngleDegrees,
                    landmark = stepLandmarks[stepIndex].orEmpty(),
                    trafficLightCount = mappedStep?.mappedTrafficLightCount ?: 0,
                    needsConfirmation = mappedStep?.needsEnvironmentalConfirmation == true,
                    remainingRouteMeters = naviInfo.pathRetainDistance,
                    remainingRouteSeconds = naviInfo.pathRetainTime,
                    offRouteMeters = offRoute,
                ),
            ) ?: return

            lastCue = cue
            lastInstructionKey = "${cue.stage}:${cue.maneuver}:${cue.distanceMeters}"
            onInstruction(cue.toInstruction())
        }

        override fun onArriveDestination() {
            onInstruction(
                NavigationInstruction(
                    maneuver = Maneuver.ARRIVED,
                    distanceMeters = 0,
                    message = Maneuver.ARRIVED.spokenLabel,
                    source = NavigationInstruction.Source.AMAP,
                ),
            )
            onStatus("Destination reached")
        }
    }

    fun initializeAfterConsent(): Boolean {
        if (amapNavi != null) return true
        return try {
            // AMap requires both calls before any SDK API is used.
            NaviSetting.updatePrivacyShow(appContext, true, true)
            NaviSetting.updatePrivacyAgree(appContext, true)
            val navi = AMapNavi.getInstance(appContext)
            navi.setUseInnerVoice(true, true)
            // Slowest supported walking emulation (10–30 km/h) for simulated navigation.
            navi.setEmulatorNaviSpeed(EMULATOR_WALKING_SPEED_KMH)
            // Attach the listener last so a partial initialization never leaks it.
            navi.addAMapNaviListener(listener)
            amapNavi = navi
            true
        } catch (error: Exception) {
            amapNavi = null
            onStatus("AMap setup failed: ${error.message ?: "unknown error"}")
            false
        }
    }

    fun calculateWalkingRoute(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double,
        simulateMovement: Boolean,
    ) {
        startWhenRouteReady = true
        requestWalkingRoute(
            startLatitude = startLatitude,
            startLongitude = startLongitude,
            endLatitude = endLatitude,
            endLongitude = endLongitude,
            simulateMovement = simulateMovement,
            destination = null,
        )
    }

    fun planWalkingRoute(
        startLatitude: Double,
        startLongitude: Double,
        destination: PlaceCandidate,
        simulateMovement: Boolean = false,
    ) {
        startWhenRouteReady = false
        requestWalkingRoute(
            startLatitude = startLatitude,
            startLongitude = startLongitude,
            endLatitude = destination.latitude,
            endLongitude = destination.longitude,
            simulateMovement = simulateMovement,
            destination = destination,
        )
    }

    private fun requestWalkingRoute(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double,
        simulateMovement: Boolean,
        destination: PlaceCandidate?,
    ) {
        val navi = amapNavi
        if (navi == null) {
            onStatus("Accept the map privacy notice first")
            return
        }

        lastInstructionKey = null
        activeWalkingSteps = emptyList()
        activePathCoordinates = emptyList()
        availableRoutes = emptyList()
        latestNaviLocation = null
        routeCompletionHandled = false
        lastCue = null
        stepLandmarks.clear()
        landmarkRequestedForStep = -1
        guidanceEngine.reset()
        pendingNaviType = if (simulateMovement) NaviType.EMULATOR else NaviType.GPS
        onStatus("Calculating walking route…")
        val accepted = if (destination != null) {
            navi.calculateWalkRoute(
                NaviPoi(
                    "Current location",
                    LatLng(startLatitude, startLongitude),
                    "",
                ),
                NaviPoi(
                    destination.name,
                    LatLng(destination.navigationLatitude, destination.navigationLongitude),
                    destination.id,
                ),
                TravelStrategy.MULTIPLE,
            )
        } else {
            navi.calculateWalkRoute(
                NaviLatLng(startLatitude, startLongitude),
                NaviLatLng(endLatitude, endLongitude),
            )
        }
        if (!accepted) {
            routeCompletionHandled = true
            onStatus("AMap rejected the route request")
        }
    }

    fun startPlannedRoute(): Boolean {
        val mode = if (pendingNaviType == NaviType.EMULATOR) "Simulated" else "GPS"
        val started = amapNavi?.startNavi(pendingNaviType) == true
        onStatus(
            if (started) {
                "$mode walking navigation started"
            } else {
                "Route found, but $mode navigation could not start"
            },
        )
        return started
    }

    fun selectRoute(routeId: Int): Boolean {
        val route = availableRoutes.firstOrNull { it.routeId == routeId } ?: return false
        val accepted = routeId == 0 || amapNavi?.selectRouteId(routeId) == true
        if (accepted) {
            lastInstructionKey = null
            activateRouteSummary(route)
            onStatus("Selected walking route ${availableRoutes.indexOf(route) + 1}")
        }
        return accepted
    }

    fun repeatCurrentInstruction(): Boolean =
        amapNavi?.readNaviInfo() == true

    fun setVoiceEnabled(enabled: Boolean) {
        amapNavi?.setUseInnerVoice(enabled, true)
    }

    fun stop() {
        amapNavi?.stopNavi()
        activeWalkingSteps = emptyList()
        activePathCoordinates = emptyList()
        availableRoutes = emptyList()
        latestNaviLocation = null
        lastInstructionKey = null
        lastCue = null
        stepLandmarks.clear()
        landmarkRequestedForStep = -1
        guidanceEngine.reset()
        onStatus("Navigation stopped")
    }

    private fun activateRouteSummary(route: RouteSummary) {
        activeWalkingSteps = route.steps
        activePathCoordinates = route.pathCoordinates
        stepLandmarks.clear()
        landmarkRequestedForStep = -1
        guidanceEngine.resetForNewRoute()
        onRouteReady(route)
    }

    /**
     * Asks for a landmark near the end of the current step once, so the prepare
     * and act cues can anchor the maneuver to something the user can perceive.
     */
    private fun requestLandmarkForStep(stepIndex: Int, step: WalkingRouteStep?) {
        val resolver = landmarkResolver ?: return
        if (landmarkRequestedForStep == stepIndex || stepLandmarks.containsKey(stepIndex)) return
        val maneuverPoint = step?.coordinates?.lastOrNull() ?: return
        landmarkRequestedForStep = stepIndex
        resolver(maneuverPoint.latitude, maneuverPoint.longitude) { landmark ->
            if (landmark.isNotBlank()) {
                stepLandmarks[stepIndex] = landmark
            }
        }
    }

    /** Re-emits the most recent cue so the user can ask "say that again". */
    fun repeatCurrentCue(): NavigationInstruction? = lastCue?.toInstruction()

    private fun AMapNaviPath.toRouteSummary(routeId: Int): RouteSummary {
        val rawSteps = steps.orEmpty().map { step ->
            val coordinates = step.coords.orEmpty().map {
                RouteCoordinate(it.latitude, it.longitude)
            }
            val roadName = step.links
                .orEmpty()
                .asSequence()
                .map { it.roadName.orEmpty().trim() }
                .firstOrNull(String::isNotBlank)
                .orEmpty()
            WalkingRouteStep(
                maneuver = AmapManeuverMapper.fromIconType(step.iconType),
                distanceMeters = step.length.coerceAtLeast(0),
                durationSeconds = step.time.coerceAtLeast(0),
                roadName = roadName,
                mappedTrafficLightCount = step.trafficLightNumber.coerceAtLeast(0),
                orientation = RouteGeometry.compassDirection(coordinates),
                coordinates = coordinates,
            )
        }
        val walkingSteps = rawSteps.mapIndexed { index, step ->
            step.copy(
                turnAngleDegrees = if (index == 0) {
                    null
                } else {
                    RouteGeometry.turnAngleDegrees(rawSteps[index - 1].coordinates, step.coordinates)
                },
            )
        }
        val coordinates = coordList.orEmpty().map {
            RouteCoordinate(it.latitude, it.longitude)
        }
        return RouteSummary(
            distanceMeters = allLength.coerceAtLeast(0),
            durationSeconds = allTime.coerceAtLeast(0),
            steps = walkingSteps,
            mappedTrafficLightCount = trafficLightCount.coerceAtLeast(0),
            routeId = routeId,
            routeLabel = labels.orEmpty(),
            initialDirection = RouteGeometry.compassDirection(coordinates),
            pathCoordinates = coordinates,
        )
    }

    override fun close() {
        val navi = amapNavi
        if (navi != null) {
            navi.stopNavi()
            navi.removeAMapNaviListener(listener)
            AMapNavi.destroy()
        }
        amapNavi = null
    }

    private companion object {
        // AMap walking emulation accepts 10–30 km/h; 10 is closest to a real walking pace.
        const val EMULATOR_WALKING_SPEED_KMH = 10
    }
}
