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
) : AutoCloseable {
    private val appContext = context.applicationContext
    private var amapNavi: AMapNavi? = null
    private var lastInstructionKey: String? = null
    private var pendingNaviType: Int = NaviType.GPS
    private var startWhenRouteReady = true
    private var activeWalkingSteps: List<WalkingRouteStep> = emptyList()
    private var availableRoutes: List<RouteSummary> = emptyList()
    private var latestNaviLocation: AMapNaviLocation? = null
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
            val distance = if (
                liveLocation?.coord != null &&
                mappedStep?.coordinates?.size?.let { it >= 2 } == true
            ) {
                RouteGeometry.remainingDistanceMeters(
                    current = RouteCoordinate(
                        liveLocation.coord.latitude,
                        liveLocation.coord.longitude,
                    ),
                    stepPoints = mappedStep.coordinates,
                )
            } else {
                mappedStep?.distanceMeters ?: 0
            }
            val message = buildString {
                append(maneuver.spokenLabel)
                if (distance > 0) append(" in $distance metres")
                if (road.isNotBlank()) append(" toward $road")
                if (
                    distance in 1..50 &&
                    mappedStep?.mappedTrafficLightCount?.let { it > 0 } == true
                ) {
                    append(". AMap shows a traffic light on this step")
                }
                if (mappedStep?.needsEnvironmentalConfirmation == true) {
                    append(". Confirm the real surroundings before continuing")
                }
                if (liveLocation?.isMatchNaviPath == false) {
                    append(". Position is not matched to the mapped route")
                }
            }

            // AMap updates frequently. Only forward changes or a new 5 m distance bucket.
            val key = "${maneuver.name}:${distance / 5}:$road"
            if (key == lastInstructionKey) return
            lastInstructionKey = key

            onInstruction(
                NavigationInstruction(
                    maneuver = maneuver,
                    distanceMeters = distance,
                    message = message,
                    source = NavigationInstruction.Source.AMAP,
                ),
            )
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
            amapNavi = AMapNavi.getInstance(appContext).also {
                it.addAMapNaviListener(listener)
                it.setUseInnerVoice(true, true)
            }
            true
        } catch (error: Exception) {
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
    ) {
        startWhenRouteReady = false
        requestWalkingRoute(
            startLatitude = startLatitude,
            startLongitude = startLongitude,
            endLatitude = destination.latitude,
            endLongitude = destination.longitude,
            simulateMovement = false,
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
        availableRoutes = emptyList()
        latestNaviLocation = null
        routeCompletionHandled = false
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
        availableRoutes = emptyList()
        latestNaviLocation = null
        onStatus("Navigation stopped")
    }

    private fun activateRouteSummary(route: RouteSummary) {
        activeWalkingSteps = route.steps
        onRouteReady(route)
    }

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
}
