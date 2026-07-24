package com.csust.soleprecision.navigation

import android.content.Context
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.PoiItemV2
import com.amap.api.services.core.ServiceSettings
import com.amap.api.services.poisearch.PoiResultV2
import com.amap.api.services.poisearch.PoiSearchV2
import com.amap.api.services.poisearch.VisualSearchResult

class AmapPlaceSearchController(
    context: Context,
) : PoiSearchV2.OnPoiSearchListener {
    private val appContext = context.applicationContext
    private var callback: ((Result<List<PlaceCandidate>>) -> Unit)? = null
    private var itemCallback: ((Result<PlaceCandidate>) -> Unit)? = null
    private var activeSearch: PoiSearchV2? = null

    fun initializeAfterConsent() {
        ServiceSettings.updatePrivacyShow(appContext, true, true)
        ServiceSettings.updatePrivacyAgree(appContext, true)
    }

    fun search(
        keyword: String,
        cityCode: String?,
        onResult: (Result<List<PlaceCandidate>>) -> Unit,
    ) {
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
            }
            callback = onResult
            activeSearch = PoiSearchV2(appContext, query).also {
                it.setOnPoiSearchListener(this)
                it.searchPOIAsyn()
            }
        } catch (error: AMapException) {
            callback = null
            onResult(Result.failure(error))
        }
    }

    fun searchById(
        poiId: String,
        onResult: (Result<PlaceCandidate>) -> Unit,
    ) {
        if (poiId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("AMap suggestion has no POI ID")))
            return
        }
        try {
            itemCallback = onResult
            activeSearch = PoiSearchV2(appContext, null).also {
                it.setOnPoiSearchListener(this)
                it.searchPOIIdAsyn(poiId)
            }
        } catch (error: AMapException) {
            itemCallback = null
            onResult(Result.failure(error))
        }
    }

    override fun onPoiSearched(result: PoiResultV2?, errorCode: Int) {
        val pendingCallback = callback ?: return
        callback = null

        if (errorCode != AMapException.CODE_AMAP_SUCCESS) {
            pendingCallback(Result.failure(IllegalStateException("AMap place search failed ($errorCode)")))
            return
        }

        val places = result
            ?.pois
            .orEmpty()
            .mapNotNull(::toCandidate)
        pendingCallback(Result.success(places))
    }

    override fun onPoiItemSearched(item: PoiItemV2?, errorCode: Int) {
        val pendingCallback = itemCallback ?: return
        itemCallback = null
        if (errorCode != AMapException.CODE_AMAP_SUCCESS || item == null) {
            pendingCallback(
                Result.failure(IllegalStateException("AMap POI detail search failed ($errorCode)")),
            )
            return
        }
        val candidate = toCandidate(item)
        if (candidate == null) {
            pendingCallback(Result.failure(IllegalStateException("AMap POI has no coordinates")))
        } else {
            pendingCallback(Result.success(candidate))
        }
    }

    override fun onVisualSearched(result: VisualSearchResult?, errorCode: Int) = Unit

    private fun toCandidate(item: PoiItemV2): PlaceCandidate? {
        val point = item.latLonPoint ?: return null
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
        )
    }
}
