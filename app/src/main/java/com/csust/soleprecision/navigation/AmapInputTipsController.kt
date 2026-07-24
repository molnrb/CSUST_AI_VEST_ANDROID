package com.csust.soleprecision.navigation

import android.content.Context
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.ServiceSettings
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery

class AmapInputTipsController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private var requestSerial = 0

    fun initializeAfterConsent() {
        ServiceSettings.updatePrivacyShow(appContext, true, true)
        ServiceSettings.updatePrivacyAgree(appContext, true)
    }

    fun request(
        keyword: String,
        cityCode: String?,
        currentLocation: UserLocation?,
        onResult: (List<DestinationSuggestion>) -> Unit,
    ) {
        val cleanKeyword = keyword.trim()
        val serial = ++requestSerial
        if (cleanKeyword.length < 2) {
            onResult(emptyList())
            return
        }

        try {
            val query = InputtipsQuery(cleanKeyword, cityCode.orEmpty()).apply {
                setCityLimit(false)
                currentLocation?.let {
                    setLocation(LatLonPoint(it.latitude, it.longitude))
                }
            }
            Inputtips(appContext, query).apply {
                setInputtipsListener { tips, errorCode ->
                    if (serial != requestSerial) return@setInputtipsListener
                    if (errorCode != AMapException.CODE_AMAP_SUCCESS) {
                        onResult(emptyList())
                        return@setInputtipsListener
                    }
                    onResult(
                        tips
                            .orEmpty()
                            .asSequence()
                            .filter { !it.name.isNullOrBlank() }
                            .distinctBy { "${it.poiID}:${it.name}:${it.address}" }
                            .take(3)
                            .map {
                                val point = it.point
                                DestinationSuggestion(
                                    name = it.name.orEmpty(),
                                    address = it.address.orEmpty(),
                                    area = it.district.orEmpty(),
                                    poiId = it.poiID.orEmpty(),
                                    latitude = point?.latitude,
                                    longitude = point?.longitude,
                                )
                            }
                            .toList(),
                    )
                }
                requestInputtipsAsyn()
            }
        } catch (_: AMapException) {
            if (serial == requestSerial) onResult(emptyList())
        }
    }

    fun cancel() {
        requestSerial += 1
    }
}
