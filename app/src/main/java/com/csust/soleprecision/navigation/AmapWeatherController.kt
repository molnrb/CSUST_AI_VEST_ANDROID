package com.csust.soleprecision.navigation

import android.content.Context
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.ServiceSettings
import com.amap.api.services.weather.LocalWeatherForecastResult
import com.amap.api.services.weather.LocalWeatherLiveResult
import com.amap.api.services.weather.WeatherSearch
import com.amap.api.services.weather.WeatherSearchQuery

data class LocalWeather(
    val description: String,
    val temperatureCelsius: String,
    val windDirection: String,
    val windPower: String,
    val humidityPercent: String,
    val reportTime: String,
)

/**
 * Live local weather from the bundled AMap Search SDK. Used as pre-trip context
 * (rain, wind) before the user commits to a walking route. Never safety-critical.
 */
class AmapWeatherController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private var requestSerial = 0

    fun initializeAfterConsent() {
        ServiceSettings.updatePrivacyShow(appContext, true, true)
        ServiceSettings.updatePrivacyAgree(appContext, true)
    }

    /** [city] accepts an AMap adcode or a city name. */
    fun fetchLiveWeather(
        city: String,
        onResult: (Result<LocalWeather>) -> Unit,
    ) {
        val serial = ++requestSerial
        if (city.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("City is not known yet")))
            return
        }
        try {
            val query = WeatherSearchQuery(city, WeatherSearchQuery.WEATHER_TYPE_LIVE)
            val search = WeatherSearch(appContext)
            search.query = query
            search.setOnWeatherSearchListener(
                object : WeatherSearch.OnWeatherSearchListener {
                    override fun onWeatherLiveSearched(
                        result: LocalWeatherLiveResult?,
                        errorCode: Int,
                    ) {
                        if (serial != requestSerial) return
                        val live = result?.liveResult
                        if (errorCode != AMapException.CODE_AMAP_SUCCESS || live == null) {
                            onResult(
                                Result.failure(
                                    IllegalStateException(
                                        "AMap weather lookup failed ($errorCode)",
                                    ),
                                ),
                            )
                            return
                        }
                        onResult(
                            Result.success(
                                LocalWeather(
                                    description = live.weather.orEmpty(),
                                    temperatureCelsius = live.temperature.orEmpty(),
                                    windDirection = live.windDirection.orEmpty(),
                                    windPower = live.windPower.orEmpty(),
                                    humidityPercent = live.humidity.orEmpty(),
                                    reportTime = live.reportTime.orEmpty(),
                                ),
                            ),
                        )
                    }

                    override fun onWeatherForecastSearched(
                        result: LocalWeatherForecastResult?,
                        errorCode: Int,
                    ) = Unit
                },
            )
            search.searchWeatherAsyn()
        } catch (error: AMapException) {
            if (serial == requestSerial) onResult(Result.failure(error))
        }
    }

    fun cancel() {
        requestSerial += 1
    }
}
