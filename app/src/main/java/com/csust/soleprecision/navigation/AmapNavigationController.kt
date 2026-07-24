package com.csust.soleprecision.navigation

import android.content.Context
import com.amap.api.navi.AMapNavi
import com.amap.api.navi.NaviSetting
import com.amap.api.navi.SimpleNaviListener
import com.amap.api.navi.enums.NaviType
import com.amap.api.navi.enums.TravelStrategy
import com.amap.api.navi.model.AMapCalcRouteResult
import com.amap.api.navi.model.NaviInfo
import com.amap.api.navi.model.NaviLatLng
import com.amap.api.navi.model.NaviPoi
import com.amap.api.maps.model.LatLng

class AmapNavigationController(
    context: Context,
    private val onInstruction: (NavigationInstruction) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onRouteReady: (RouteSummary) -> Unit = {},
) : AutoCloseable {
    private val appContext = context.applicationContext
    private var amapNavi: AMapNavi? = null
    private var lastInstructionKey: String? = null
    private var pendingNaviType: Int = NaviType.GPS
    private var startWhenRouteReady = true
    private var activeWalkingSteps: List<WalkingRouteStep> = emptyList()

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
            val path = amapNavi?.naviPath
            if (path != null) {
                val walkingSteps = path.steps
                    .orEmpty()
                    .map { step ->
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
                            mappedTrafficLightCount =
                                step.trafficLightNumber.coerceAtLeast(0),
                        )
                    }
                activeWalkingSteps = walkingSteps
                onRouteReady(
                    RouteSummary(
                        distanceMeters = path.allLength,
                        durationSeconds = path.allTime,
                        steps = walkingSteps,
                        mappedTrafficLightCount = path.trafficLightCount.coerceAtLeast(0),
                    ),
                )
            }

            val mode = if (pendingNaviType == NaviType.EMULATOR) "Simulated" else "GPS"
            if (startWhenRouteReady) {
                startPlannedRoute()
            } else {
                onStatus("$mode walking route ready")
            }
        }

        override fun onCalculateRouteFailure(routeResult: AMapCalcRouteResult?) {
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

        override fun onNaviInfoUpdate(naviInfo: NaviInfo?) {
            naviInfo ?: return
            val maneuver = AmapManeuverMapper.fromIconType(naviInfo.iconType)
            val distance = naviInfo.curStepRetainDistance.coerceAtLeast(0)
            val road = naviInfo.nextRoadName.orEmpty()
            val mappedStep = activeWalkingSteps.getOrNull(naviInfo.curStep)
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
                if (maneuver == Maneuver.CROSSWALK) {
                    append(". Confirm the real crossing and traffic state before entering")
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
                    LatLng(destination.latitude, destination.longitude),
                    destination.id,
                ),
                TravelStrategy.SINGLE,
            )
        } else {
            navi.calculateWalkRoute(
                NaviLatLng(startLatitude, startLongitude),
                NaviLatLng(endLatitude, endLongitude),
            )
        }
        if (!accepted) {
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

    fun repeatCurrentInstruction(): Boolean =
        amapNavi?.readNaviInfo() == true

    fun setVoiceEnabled(enabled: Boolean) {
        amapNavi?.setUseInnerVoice(enabled, true)
    }

    fun stop() {
        amapNavi?.stopNavi()
        activeWalkingSteps = emptyList()
        onStatus("Navigation stopped")
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
