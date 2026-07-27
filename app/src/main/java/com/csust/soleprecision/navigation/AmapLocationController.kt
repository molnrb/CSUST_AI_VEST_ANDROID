package com.csust.soleprecision.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.LatLng

class AmapLocationController(
    context: Context,
    private val onLocation: (UserLocation) -> Unit,
    private val onStatus: (String) -> Unit,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private var client: AMapLocationClient? = null
    private var systemFallbackRequested = false
    private var legacyLocationListener: LocationListener? = null
    private var lastStatusKey: String? = null

    fun initializeAfterConsent(): Boolean {
        if (client != null) return true
        return try {
            MapsInitializer.updatePrivacyShow(appContext, true, true)
            MapsInitializer.updatePrivacyAgree(appContext, true)
            AMapLocationClient.updatePrivacyShow(appContext, true, true)
            AMapLocationClient.updatePrivacyAgree(appContext, true)
            val locationClient = AMapLocationClient(appContext)
            try {
                locationClient.setLocationListener(::handleLocation)
                locationClient.setLocationOption(
                    AMapLocationClientOption().apply {
                        locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                        isOnceLocation = false
                        interval = 2_000
                        // Address, city and adcode feed "Where am I" and weather context.
                        isNeedAddress = true
                        isSensorEnable = true
                    },
                )
            } catch (error: Exception) {
                locationClient.onDestroy()
                throw error
            }
            client = locationClient
            true
        } catch (error: Exception) {
            onStatus("Location setup failed: ${error.message ?: "unknown error"}")
            false
        }
    }

    fun refresh() {
        if (!initializeAfterConsent()) return
        onStatus("Finding current location…")
        client?.stopLocation()
        client?.startLocation()
    }

    private fun handleLocation(location: AMapLocation?) {
        location ?: return
        if (location.errorCode != AMapLocation.LOCATION_SUCCESS) {
            if (!useSystemLocationFallback()) {
                onStatus("Current location unavailable (${location.errorCode})")
            }
            return
        }
        if (!LocationValidity.isValidCoordinate(location.latitude, location.longitude)) {
            if (!useSystemLocationFallback()) {
                onStatus("AMap returned an invalid current position")
            }
            return
        }
        val accuracy = location.accuracy.takeIf { it.isFinite() && it > 0f }
        val confidence = when {
            accuracy == null -> LocationConfidence.UNKNOWN
            accuracy <= 20f -> LocationConfidence.HIGH
            accuracy <= 50f -> LocationConfidence.MEDIUM
            else -> LocationConfidence.LOW
        }
        val source = when (location.locationType) {
            AMapLocation.LOCATION_TYPE_GPS -> "GPS"
            AMapLocation.LOCATION_TYPE_WIFI,
            AMapLocation.LOCATION_TYPE_CELL,
            AMapLocation.LOCATION_TYPE_NETWORK,
            -> "Network"
            else -> "AMap"
        }
        onLocation(
            UserLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                address = location.address.orEmpty().ifBlank { "Current position" },
                cityCode = location.cityCode.orEmpty(),
                accuracyMeters = accuracy,
                source = source,
                confidence = confidence,
                cityName = location.city.orEmpty(),
                adCode = location.adCode.orEmpty(),
            ),
        )
        val statusKey = "$source:$confidence:${accuracy?.toInt()}"
        if (statusKey != lastStatusKey) {
            lastStatusKey = statusKey
            onStatus(
                buildString {
                    append("$source location")
                    accuracy?.let { append(", accurate to about ${it.toInt()} metres") }
                    if (confidence == LocationConfidence.LOW) {
                        append("; guidance confidence is low")
                    }
                },
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun useSystemLocationFallback(): Boolean {
        val manager = appContext.getSystemService(LocationManager::class.java) ?: return false
        val rawLocation = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        )
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .filter {
                LocationValidity.isValidCoordinate(it.latitude, it.longitude) &&
                    LocationValidity.isFresh(it.time)
            }
            .maxByOrNull { it.time }
        if (rawLocation != null && emitSystemLocation(rawLocation)) return true

        if (systemFallbackRequested) return true
        systemFallbackRequested = true
        onStatus("Waiting for current device GPS position…")
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.getCurrentLocation(
                    LocationManager.GPS_PROVIDER,
                    null,
                    appContext.mainExecutor,
                ) { location ->
                    systemFallbackRequested = false
                    if (location == null || !emitSystemLocation(location)) {
                        onStatus("Current device GPS position unavailable")
                    }
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        legacyLocationListener = null
                        systemFallbackRequested = false
                        if (!emitSystemLocation(location)) {
                            onStatus("Current device GPS position unavailable")
                        }
                    }

                    @Deprecated("Deprecated in Android")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }
                legacyLocationListener = listener
                manager.requestSingleUpdate(
                    LocationManager.GPS_PROVIDER,
                    listener,
                    Looper.getMainLooper(),
                )
            }
            true
        }.getOrElse {
            systemFallbackRequested = false
            false
        }
    }

    private fun emitSystemLocation(rawLocation: Location): Boolean {
        if (
            !LocationValidity.isValidCoordinate(rawLocation.latitude, rawLocation.longitude) ||
            !LocationValidity.isFresh(rawLocation.time)
        ) {
            return false
        }
        val converted = runCatching {
            CoordinateConverter(appContext)
                .from(CoordinateConverter.CoordType.GPS)
                .coord(LatLng(rawLocation.latitude, rawLocation.longitude))
                .convert()
        }.getOrNull() ?: return false

        val accuracy = rawLocation.accuracy.takeIf { it.isFinite() && it > 0f }
        onLocation(
            UserLocation(
                latitude = converted.latitude,
                longitude = converted.longitude,
                address = "Current GPS position",
                cityCode = "",
                accuracyMeters = accuracy,
                source = "System GPS",
                confidence = when {
                    accuracy == null -> LocationConfidence.UNKNOWN
                    accuracy <= 20f -> LocationConfidence.HIGH
                    accuracy <= 50f -> LocationConfidence.MEDIUM
                    else -> LocationConfidence.LOW
                },
            ),
        )
        onStatus("Using current device GPS position")
        return true
    }

    override fun close() {
        val manager = appContext.getSystemService(LocationManager::class.java)
        legacyLocationListener?.let { listener ->
            manager?.removeUpdates(listener)
        }
        legacyLocationListener = null
        client?.stopLocation()
        client?.onDestroy()
        client = null
    }
}
