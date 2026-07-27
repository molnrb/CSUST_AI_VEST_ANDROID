package com.csust.soleprecision.ui

import com.csust.soleprecision.i18n.Phrases
import com.csust.soleprecision.navigation.LocalWeather
import com.csust.soleprecision.navigation.PlaceCandidate
import com.csust.soleprecision.navigation.RouteSummary
import com.csust.soleprecision.settings.SpeechDetail

/**
 * Context for a screen, spoken before its action list.
 *
 * This is *only* the extra information a user cannot infer from the controls: the
 * place, the route summary, a layout description for non-directional screens.
 * Plain directional menus return an empty string on purpose — every available
 * control is announced from the live action map instead, which is what stops a
 * button from being silently omitted.
 *
 * CONCISE keeps the first sentence, STANDARD the whole thing, DETAILED adds extras.
 */
internal fun screenIntroduction(
    screen: ProductionScreen,
    phrases: Phrases,
    detail: SpeechDetail,
    place: PlaceCandidate?,
    routeSummary: RouteSummary?,
    isSimulation: Boolean,
    weather: LocalWeather?,
): String {
    val standard = when (screen) {
        ProductionScreen.HOME -> phrases.introHome
        ProductionScreen.DESTINATION -> phrases.introDestination
        ProductionScreen.DESTINATION_METHODS -> phrases.introDestinationMethods
        ProductionScreen.DESTINATION_COLLECTIONS -> phrases.introCollections
        ProductionScreen.VOICE_DESTINATION -> phrases.introVoice
        ProductionScreen.TYPE_DESTINATION -> phrases.introType
        ProductionScreen.MAP_DESTINATION -> phrases.introMap
        ProductionScreen.RECENT_PLACES -> phrases.introRecent
        ProductionScreen.SAVED_PLACES -> phrases.introSaved
        ProductionScreen.NEARBY_CATEGORIES -> phrases.introNearby
        ProductionScreen.NEARBY_RESULTS -> phrases.introSearchResults
        ProductionScreen.SEARCHING -> phrases.introSearching
        ProductionScreen.SEARCH_RESULTS -> phrases.introSearchResults
        ProductionScreen.WHERE_AM_I -> phrases.introWhereAmI

        ProductionScreen.CONFIRM_PLACE -> phrases.introConfirmPlace.format(
            when (detail) {
                SpeechDetail.CONCISE -> place?.name.orEmpty()
                SpeechDetail.STANDARD -> listOfNotNull(place?.name, place?.address)
                    .filter(String::isNotBlank)
                    .joinToString(", ")
                SpeechDetail.DETAILED -> place?.let(phrases::placeDescription).orEmpty()
            },
        )

        ProductionScreen.ROUTE_OPTIONS -> routeSummary?.let {
            "${phrases.introRouteOptions} ${phrases.routeSummary(it)}"
        } ?: phrases.introRouteOptionsPreparing

        ProductionScreen.ROUTE_PREVIEW -> routeSummary?.let { summary ->
            buildString {
                if (isSimulation) append(phrases.introSimulationPrefix)
                append(phrases.introRoutePreview.format(place?.name ?: phrases.selectedDestination))
                append(" ")
                append(phrases.routeSummary(summary))
                if (summary.steps.isNotEmpty()) {
                    append(" ")
                    append(phrases.walkingStepsCount.format(summary.steps.size))
                }
            }
        } ?: phrases.introRoutePreviewPreparing

        ProductionScreen.ROUTE_WALKTHROUGH -> phrases.introWalkthrough
        ProductionScreen.ACTIVE_NAVIGATION -> phrases.introActiveNavigation
        ProductionScreen.PAUSED -> phrases.introPaused
        ProductionScreen.ARRIVED -> phrases.introArrived.format(
            place?.name ?: phrases.selectedDestination,
        )

        ProductionScreen.SETTINGS -> phrases.introSettings
        ProductionScreen.DEVICE_SETTINGS -> phrases.introDeviceSettings
        ProductionScreen.APP_SETTINGS -> phrases.introAppSettings
        ProductionScreen.ENGINEERING -> phrases.introEngineering
    }

    return when (detail) {
        SpeechDetail.CONCISE -> firstSentence(standard)
        SpeechDetail.STANDARD -> standard
        SpeechDetail.DETAILED -> buildString {
            append(standard)
            when (screen) {
                ProductionScreen.HOME -> {
                    append(" ")
                    append(phrases.introHomeDetail)
                }
                ProductionScreen.ROUTE_PREVIEW -> weather?.let {
                    append(" ")
                    append(phrases.weatherSummary(it))
                }
                else -> Unit
            }
        }
    }.trim()
}

/** First sentence of an introduction, tolerant of English and Chinese punctuation. */
internal fun firstSentence(text: String): String {
    val indices = listOf(text.indexOf(". "), text.indexOf("。"))
        .filter { it >= 0 }
    val cut = indices.minOrNull() ?: return text
    val terminator = if (text[cut] == '。') "。" else "."
    return text.substring(0, cut) + terminator
}
