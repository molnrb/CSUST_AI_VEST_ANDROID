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
    /** Nearest named point of interest, used as a landmark anchor for cues. */
    val nearestPoiName: String = "",
)

class AmapReverseGeocodeController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private var requestSerial = 0

    init {
        ServiceSettings.updatePrivacyShow(appContext, true, true)
        ServiceSettings.updatePrivacyAgree(appContext, true)
    }

    fun resolve(
        latitude: Double,
        longitude: Double,
        onResult: (Result<ResolvedMapAddress>) -> Unit,
    ) {
        val serial = ++requestSerial
        val search = GeocodeSearch(appContext)
        search.setOnGeocodeSearchListener(
            object : GeocodeSearch.OnGeocodeSearchListener {
                override fun onRegeocodeSearched(result: RegeocodeResult?, errorCode: Int) {
                    if (serial != requestSerial) return
                    if (errorCode != AMapException.CODE_AMAP_SUCCESS) {
                        onResult(
                            Result.failure(
                                IllegalStateException("AMap address lookup failed ($errorCode)"),
                            ),
                        )
                        return
                    }

                    val address = result?.regeocodeAddress
                    val formatted = address?.formatAddress.orEmpty()
                    val nearestPoi = address?.pois.orEmpty().firstOrNull()?.title.orEmpty()
                    val area = listOf(
                        address?.city.orEmpty(),
                        address?.district.orEmpty(),
                    ).filter(String::isNotBlank).distinct().joinToString(", ")
                    onResult(
                        Result.success(
                            ResolvedMapAddress(
                                name = nearestPoi.ifBlank {
                                    formatted.ifBlank { "Pinned map location" }
                                },
                                address = formatted.ifBlank { "Selected on AMap" },
                                area = area,
                                nearestPoiName = nearestPoi,
                            ),
                        ),
                    )
                }

                override fun onGeocodeSearched(result: GeocodeResult?, errorCode: Int) = Unit
            },
        )
        search.getFromLocationAsyn(
            RegeocodeQuery(
                LatLonPoint(latitude, longitude),
                100f,
                GeocodeSearch.AMAP,
            ),
        )
    }

    fun cancel() {
        requestSerial += 1
    }
}
