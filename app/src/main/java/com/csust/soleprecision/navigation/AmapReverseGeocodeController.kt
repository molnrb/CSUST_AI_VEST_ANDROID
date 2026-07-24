package com.csust.soleprecision.navigation

import android.content.Context
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.ServiceSettings
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult

data class ResolvedMapAddress(
    val name: String,
    val address: String,
    val area: String,
)

class AmapReverseGeocodeController(
    context: Context,
) : GeocodeSearch.OnGeocodeSearchListener {
    private val appContext = context.applicationContext
    private val search = GeocodeSearch(
        appContext.also {
            ServiceSettings.updatePrivacyShow(it, true, true)
            ServiceSettings.updatePrivacyAgree(it, true)
        },
    ).also {
        it.setOnGeocodeSearchListener(this)
    }
    private var callback: ((Result<ResolvedMapAddress>) -> Unit)? = null

    fun resolve(
        latitude: Double,
        longitude: Double,
        onResult: (Result<ResolvedMapAddress>) -> Unit,
    ) {
        callback = onResult
        search.getFromLocationAsyn(
            RegeocodeQuery(
                LatLonPoint(latitude, longitude),
                100f,
                GeocodeSearch.AMAP,
            ),
        )
    }

    override fun onRegeocodeSearched(result: RegeocodeResult?, errorCode: Int) {
        val pending = callback ?: return
        callback = null
        if (errorCode != AMapException.CODE_AMAP_SUCCESS) {
            pending(Result.failure(IllegalStateException("AMap address lookup failed ($errorCode)")))
            return
        }

        val address = result?.regeocodeAddress
        val formatted = address?.formatAddress.orEmpty()
        val nearestPoi = address?.pois.orEmpty().firstOrNull()?.title.orEmpty()
        val area = listOf(
            address?.city.orEmpty(),
            address?.district.orEmpty(),
        ).filter(String::isNotBlank).distinct().joinToString(", ")
        pending(
            Result.success(
                ResolvedMapAddress(
                    name = nearestPoi.ifBlank { formatted.ifBlank { "Pinned map location" } },
                    address = formatted.ifBlank { "Selected on AMap" },
                    area = area,
                ),
            ),
        )
    }

    override fun onGeocodeSearched(result: GeocodeResult?, errorCode: Int) = Unit
}
