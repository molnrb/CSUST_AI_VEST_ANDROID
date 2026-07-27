package com.csust.soleprecision.navigation

import android.content.Context
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItemV2
import com.amap.api.services.core.ServiceSettings
import com.amap.api.services.poisearch.PoiResultV2
import com.amap.api.services.poisearch.PoiSearchV2
import com.amap.api.services.poisearch.VisualSearchResult

/**
 * Focused nearby categories a blind pedestrian actually asks for.
 * Deliberately short: the app must never narrate "everything nearby".
 * The query terms are AMap POI category names (Chinese map data).
 */
enum class NearbyCategory(val amapQuery: String) {
    TOILET("公共厕所"),
    BUS_STOP("公交车站"),
    METRO_STATION("地铁站"),
    PHARMACY("药店"),
    HOSPITAL("医院"),
    SUPERMARKET("超市"),
}

/**
 * Nearby point-of-interest search around the current position using the bundled
 * AMap Search SDK (no web service, no backend). Results are distance sorted and
 * capped so screens can present them one at a time.
 */
class AmapNearbySearchController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private var activeSearch: PoiSearchV2? = null
    private var requestSerial = 0

    fun initializeAfterConsent() {
        ServiceSettings.updatePrivacyShow(appContext, true, true)
        ServiceSettings.updatePrivacyAgree(appContext, true)
    }

    fun search(
        category: NearbyCategory,
        latitude: Double,
        longitude: Double,
        onResult: (Result<List<PlaceCandidate>>) -> Unit,
    ) {
        val serial = ++requestSerial
        if (!LocationValidity.isValidCoordinate(latitude, longitude)) {
            onResult(
                Result.failure(IllegalArgumentException("Current location is not known yet")),
            )
            return
        }
        try {
            val query = PoiSearchV2.Query(category.amapQuery, "", "").apply {
                pageSize = MAX_RESULTS
                pageNum = 1
                showFields = PoiSearchV2.ShowFields(PoiSearchV2.ShowFields.ALL)
                location = LatLonPoint(latitude, longitude)
                isDistanceSort = true
            }
            activeSearch = PoiSearchV2(appContext, query).also { search ->
                search.bound = PoiSearchV2.SearchBound(
                    LatLonPoint(latitude, longitude),
                    SEARCH_RADIUS_METERS,
                    true,
                )
                search.setOnPoiSearchListener(
                    object : PoiSearchV2.OnPoiSearchListener {
                        override fun onPoiSearched(result: PoiResultV2?, errorCode: Int) {
                            if (serial != requestSerial) return
                            activeSearch = null
                            if (errorCode != AMapException.CODE_AMAP_SUCCESS) {
                                onResult(
                                    Result.failure(
                                        IllegalStateException(
                                            "AMap nearby search failed ($errorCode)",
                                        ),
                                    ),
                                )
                                return
                            }
                            val places = result
                                ?.pois
                                .orEmpty()
                                .mapNotNull(::toCandidate)
                                .take(MAX_RESULTS)
                            onResult(Result.success(places))
                        }

                        override fun onPoiItemSearched(item: PoiItemV2?, errorCode: Int) = Unit

                        override fun onVisualSearched(
                            result: VisualSearchResult?,
                            errorCode: Int,
                        ) = Unit
                    },
                )
                search.searchPOIAsyn()
            }
        } catch (error: AMapException) {
            if (serial == requestSerial) onResult(Result.failure(error))
        }
    }

    fun cancel() {
        requestSerial += 1
        activeSearch = null
    }

    private fun toCandidate(item: PoiItemV2): PlaceCandidate? {
        val point = item.latLonPoint ?: return null
        val entrance = item.poiNavi?.enter
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
        )
    }

    private companion object {
        const val SEARCH_RADIUS_METERS = 1_000
        const val MAX_RESULTS = 5
    }
}
