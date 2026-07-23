package com.csust.soleprecision.navigation

import android.content.Context
import com.amap.api.navi.AMapNavi
import com.amap.api.navi.NaviSetting
import com.amap.api.navi.SimpleNaviListener
import com.amap.api.navi.enums.NaviType
import com.amap.api.navi.model.AMapCalcRouteResult
import com.amap.api.navi.model.NaviInfo
import com.amap.api.navi.model.NaviLatLng

class AmapNavigationController(
    context: Context,
    private val onInstruction: (NavigationInstruction) -> Unit,
    private val onStatus: (String) -> Unit,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private var amapNavi: AMapNavi? = null
    private var lastInstructionKey: String? = null

    private val listener = object : SimpleNaviListener() {
        override fun onInitNaviSuccess() {
            onStatus("AMap navigation ready")
        }

        override fun onCalculateRouteSuccess(routeResult: AMapCalcRouteResult?) {
            val started = amapNavi?.startNavi(NaviType.GPS) == true
            onStatus(if (started) "Walking navigation started" else "Route found, but GPS navigation could not start")
        }

        override fun onCalculateRouteFailure(routeResult: AMapCalcRouteResult?) {
            onStatus("AMap could not calculate that walking route")
        }

        override fun onNaviInfoUpdate(naviInfo: NaviInfo?) {
            naviInfo ?: return
            val maneuver = AmapManeuverMapper.fromIconType(naviInfo.iconType)
            val distance = naviInfo.curStepRetainDistance.coerceAtLeast(0)
            val road = naviInfo.nextRoadName.orEmpty()
            val message = buildString {
                append(maneuver.spokenLabel)
                if (distance > 0) append(" in $distance metres")
                if (road.isNotBlank()) append(" toward $road")
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
    ) {
        val navi = amapNavi
        if (navi == null) {
            onStatus("Accept the map privacy notice first")
            return
        }

        lastInstructionKey = null
        onStatus("Calculating walking route…")
        val accepted = navi.calculateWalkRoute(
            NaviLatLng(startLatitude, startLongitude),
            NaviLatLng(endLatitude, endLongitude),
        )
        if (!accepted) {
            onStatus("AMap rejected the route request")
        }
    }

    fun stop() {
        amapNavi?.stopNavi()
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
