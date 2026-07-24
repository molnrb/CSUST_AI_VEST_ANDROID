package com.csust.soleprecision.navigation

import android.content.Context
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItemV2
import com.amap.api.services.core.ServiceSettings
import com.amap.api.services.poisearch.PoiResultV2
import com.amap.api.services.poisearch.PoiSearchV2
import com.amap.api.services.poisearch.VisualSearchResult

class AmapPlaceSearchController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private var activeSearch: PoiSearchV2? = null
    private var requestSerial = 0

    fun initializeAfterConsent() {
        ServiceSettings.updatePrivacyShow(appContext, true, true)
        ServiceSettings.updatePrivacyAgree(appContext, true)
    }

    fun setLanguage(languageTag: String) {
        ServiceSettings.getInstance().language =
            if (languageTag.startsWith("en", ignoreCase = true)) {
                ServiceSettings.ENGLISH
            } else {
                ServiceSettings.CHINESE
            }
    }

    fun search(
        keyword: String,
        cityCode: String?,
        currentLocation: UserLocation? = null,
        onResult: (Result<List<PlaceCandidate>>) -> Unit,
    ) {
        val serial = ++requestSerial
        val cleanKeyword = keyword.trim()
        if (cleanKeyword.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Say or enter a destination first")))
            return
        }

        try {
            val query = if (cityCode.isNullOrBlank()) {
                PoiSearchV2.Query(cleanKeyword, "")
            } else {
                PoiSearchV2.Query(cleanKeyword, "", cityCode)
            }.apply {
                pageSize = 5
                pageNum = 1
                cityLimit = !cityCode.isNullOrBlank()
                showFields = PoiSearchV2.ShowFields(PoiSearchV2.ShowFields.ALL)
                currentLocation?.let {
                    location = LatLonPoint(it.latitude, it.longitude)
                    isDistanceSort = true
                }
            }
            activeSearch = PoiSearchV2(appContext, query).also {
                it.setOnPoiSearchListener(
                    requestListener(
                        onPois = { result, errorCode ->
                            if (serial != requestSerial) return@requestListener
                            activeSearch = null
                            if (errorCode != AMapException.CODE_AMAP_SUCCESS) {
                                onResult(
                                    Result.failure(
                                        IllegalStateException(
                                            "AMap place search failed ($errorCode)",
                                        ),
                                    ),
                                )
                                return@requestListener
                            }
                            val places = result
                                ?.pois
                                .orEmpty()
                                .mapNotNull(::toCandidate)
                            onResult(Result.success(places))
                        },
                    ),
                )
                it.searchPOIAsyn()
            }
        } catch (error: AMapException) {
            if (serial == requestSerial) onResult(Result.failure(error))
        }
    }

    fun searchById(
        poiId: String,
        onResult: (Result<PlaceCandidate>) -> Unit,
    ) {
        val serial = ++requestSerial
        if (poiId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("AMap suggestion has no POI ID")))
            return
        }
        try {
            activeSearch = PoiSearchV2(appContext, null).also {
                it.setOnPoiSearchListener(
                    requestListener(
                        onItem = { item, errorCode ->
                            if (serial != requestSerial) return@requestListener
                            activeSearch = null
                            if (errorCode != AMapException.CODE_AMAP_SUCCESS || item == null) {
                                onResult(
                                    Result.failure(
                                        IllegalStateException(
                                            "AMap POI detail search failed ($errorCode)",
                                        ),
                                    ),
                                )
                                return@requestListener
                            }
                            val candidate = toCandidate(item)
                            if (candidate == null) {
                                onResult(
                                    Result.failure(
                                        IllegalStateException("AMap POI has no coordinates"),
                                    ),
                                )
                            } else {
                                onResult(Result.success(candidate))
                            }
                        },
                    ),
                )
                it.searchPOIIdAsyn(poiId)
            }
        } catch (error: AMapException) {
            if (serial == requestSerial) onResult(Result.failure(error))
        }
    }

    fun cancel() {
        requestSerial += 1
        activeSearch = null
    }

    private fun requestListener(
        onPois: (PoiResultV2?, Int) -> Unit = { _, _ -> },
        onItem: (PoiItemV2?, Int) -> Unit = { _, _ -> },
    ): PoiSearchV2.OnPoiSearchListener = object : PoiSearchV2.OnPoiSearchListener {
        override fun onPoiSearched(result: PoiResultV2?, errorCode: Int) {
            onPois(result, errorCode)
        }

        override fun onPoiItemSearched(item: PoiItemV2?, errorCode: Int) {
            onItem(item, errorCode)
        }

        override fun onVisualSearched(result: VisualSearchResult?, errorCode: Int) = Unit
    }

    private fun toCandidate(item: PoiItemV2): PlaceCandidate? {
        val point = item.latLonPoint ?: return null
        val entrance = item.poiNavi?.enter
        val exit = item.poiNavi?.exit
        val indoor = item.indoorData
        val business = item.business
        return PlaceCandidate(
            id = item.poiId.orEmpty(),
            name = item.title.orEmpty().ifBlank { "Unnamed place" },
            address = item.snippet.orEmpty(),
            area = listOf(item.cityName.orEmpty(), item.adName.orEmpty())
                .filter(String::isNotBlank)
                .distinct()
                .joinToString(", "),
            latitude = point.latitude,
            longitude = point.longitude,
            typeDescription = item.typeDes.orEmpty(),
            entranceLatitude = entrance?.latitude,
            entranceLongitude = entrance?.longitude,
            exitLatitude = exit?.latitude,
            exitLongitude = exit?.longitude,
            indoorFloorName = indoor?.floorName.orEmpty(),
            businessTags = listOf(
                business?.tag.orEmpty(),
                business?.alias.orEmpty(),
            ).filter(String::isNotBlank).distinct().joinToString(", "),
            childPlaceNames = item.subPois
                .orEmpty()
                .map { it.title.orEmpty() }
                .filter(String::isNotBlank)
                .distinct()
                .take(5),
        )
    }
}
