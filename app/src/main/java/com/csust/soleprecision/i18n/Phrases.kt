package com.csust.soleprecision.i18n

import com.csust.soleprecision.navigation.LocalWeather
import com.csust.soleprecision.navigation.Maneuver
import com.csust.soleprecision.navigation.NavigationInstruction
import com.csust.soleprecision.navigation.NearbyCategory
import com.csust.soleprecision.navigation.PlaceCandidate
import com.csust.soleprecision.navigation.RouteSummary
import com.csust.soleprecision.navigation.WalkingRouteStep
import com.csust.soleprecision.settings.AppLanguage

/**
 * In-app localization table. The app language is a user preference (not the system
 * locale), and the spoken-navigation domain classes are pure-JVM tested, so the
 * translations live in Kotlin instead of Android resources: switching language
 * recomposes instantly with no activity recreation, which matters for blind users
 * mid-flow. English remains the fallback and the engineering console stays English.
 *
 * Every user-visible or user-audible production string must come from here.
 *
 * Deliberately a plain class, not a data class: with ~250 constructor parameters a
 * generated copy$default would exceed the JVM's 255-argument method limit. The JVM
 * constructor limit itself is 254 parameters — if this table grows further, split it
 * into grouped sub-objects instead of adding more top-level fields.
 */
class Phrases(
    // Generic actions
    val back: String,
    val backToHome: String,
    val confirm: String,
    val decline: String,
    val next: String,
    val previous: String,
    val finish: String,
    val clear: String,
    val search: String,
    val cancel: String,

    // Home
    val home: String,
    val navigation: String,
    val settings: String,
    val whereAmI: String,

    // Destination hub
    val chooseDestination: String,
    val newDestination: String,
    val recentAndSaved: String,
    val savedDestinations: String,
    val recentDestinations: String,
    val nearbyEssentials: String,
    val voiceDestination: String,
    val searchOrPointOnMap: String,
    val typeDestination: String,

    // Voice screen
    val listening: String,
    val microphone: String,
    val listeningEllipsis: String,
    val speakPlaceNow: String,
    val tapMicrophoneHint: String,
    val micReady: String,
    val micRecording: String,
    val micStartDescription: String,
    val micRecordingDescription: String,

    // Type/search screens
    val placeNameOrAddress: String,
    val amapSuggestionPrefix: String,
    val useSuggestionPrefix: String,
    val searchAmap: String,
    val destinationSearch: String,
    val tryVoiceAgain: String,
    val waitingForDestination: String,
    val searchingAmapFor: String,
    val listeningForDestination: String,

    // Map screen
    val pointOnAmap: String,
    val tapMapToSelect: String,
    val findingAddress: String,
    val selectedDestination: String,
    val pinnedMapLocation: String,
    val selectedOnAmap: String,
    val usePoint: String,

    // Search results
    val chooseCorrectPlace: String,
    val declineNext: String,
    val noMatchingPlaces: String,
    val lastOption: String,
    val distanceUnavailable: String,
    val optionXofY: String, // %1$d, %2$d
    val resultBrowseHint: String,

    // Recent/saved lists
    val noRecentDestinations: String,
    val noSavedDestinations: String,
    val recentItemAnnouncement: String, // %1$d, %2$d, %3$s
    val savedItemAnnouncement: String, // %1$d, %2$d, %3$s
    val usePlace: String,
    val nextPlace: String,
    val removeFromSaved: String,
    val removedFromSaved: String,

    // Nearby
    val nearbyTitle: String,
    val nearbyIntro: String,
    val nearbySearching: String, // %1$s
    val nearbyNoResults: String, // %1$s
    val nearbyItemAnnouncement: String, // %1$d, %2$d, %3$s, %4$s
    val nearbyCategoryToilet: String,
    val nearbyCategoryBusStop: String,
    val nearbyCategoryMetro: String,
    val nearbyCategoryPharmacy: String,
    val nearbyCategoryHospital: String,
    val nearbyCategorySupermarket: String,
    val nearbyBrowseHint: String,
    val nearbyCategoryAnnouncement: String, // %1$d, %2$d, %3$s
    val metersAway: String, // %1$d
    val kilometersAway: String, // %1$s

    // Where am I
    val whereAmITitle: String,
    val whereAmIWorking: String,
    val whereAmIUnavailable: String,
    val whereAmINearestPoi: String, // %1$s
    val whereAmIAccuracy: String, // %1$d
    val whereAmICaution: String,

    // Confirm place
    val confirmDestination: String,
    val savePlace: String,
    val placeSaved: String,
    val chooseAnotherPlace: String,
    val amapEntranceAvailable: String,
    val floorPrefix: String, // %1$s
    val includesPrefix: String, // %1$s

    // Route options / preview / walkthrough
    val routeOptionsPreparing: String,
    val routeXofY: String, // %1$d, %2$d
    val nextRoute: String,
    val chooseRoute: String,
    val routePreview: String,
    val declineRoute: String,
    val startNavigation: String,
    val reviewFullRoute: String,
    val simulationOnly: String,
    val simulationOnlyLong: String,
    val walkingStepsCount: String, // %1$d
    val stepXofY: String, // %1$d, %2$d
    val routeWalkthroughUnavailable: String,
    val walkthroughHint: String,
    val noRouteSteps: String,
    val crossingWarning: String,
    val mappedFeatureWarning: String,
    val routeGuidanceOnlyNote: String,
    val weatherLine: String, // %1$s weather desc, %2$s temp, %3$s wind
    val weatherUnavailable: String,

    // Active navigation / paused / arrival
    val activeNavigationTitle: String,
    val navigationPausedTitle: String,
    val repeat: String,
    val pause: String,
    val continueAction: String,
    val end: String,
    val waitingFirstInstruction: String,
    val guidancePaused: String,
    val obstacleNotVerifiedNote: String,
    val destinationReached: String,
    val arrivedAt: String, // %1$s
    val arrivedNear: String, // %1$s
    val confirmEntranceNote: String,
    val minutesAbout: String, // %1$d

    // Settings
    val deviceSettings: String,
    val deviceSettingsSupport: String,
    val appSettings: String,
    val appSettingsSupport: String,
    val connection: String,
    val useSimulatedSystem: String,
    val useSimulatedSystemSupport: String,
    val startSimulator: String,
    val stopSimulator: String,
    val connectDevice: String,
    val disconnectDevice: String,
    val lastOutputPrefix: String, // %1$s
    val guidanceFeedback: String,
    val guidanceModeHapticSpeech: String,
    val guidanceModeHapticOnly: String,
    val guidanceModeSpeechOnly: String,
    val vibrationStrength: String,
    val speakerVolume: String,
    val percentValue: String, // %1$d
    val decrease: String,
    val increase: String,
    val testLeft: String,
    val testRight: String,
    val testBothSpeakers: String,
    val safetyOverrideNote: String,
    val voiceRecognitionLanguage: String,
    val navigationSpeechDetail: String,
    val speechDetailConcise: String,
    val speechDetailStandard: String,
    val speechDetailDetailed: String,
    val screenNarration: String,
    val announceEachScreen: String,
    val announceEachScreenSupport: String,
    val simulateMovementSetting: String,
    val simulateMovementSettingSupport: String,
    val destinationHistory: String,
    val recentPlacesSaved: String, // %1$d
    val deleteDestinationHistory: String,
    val developerTools: String,
    val engineeringConsoleNote: String,
    val openEngineeringConsole: String,
    val selectedPrefix: String, // %1$s
    val selected: String,
    val notSelected: String,
    val on: String,
    val off: String,

    // Status / errors surfaced by MainActivity
    val micPermissionNeeded: String,
    val noSpeechService: String,
    val sayDestinationPrompt: String,
    val noDestinationRecognized: String,
    val micPermissionForVoice: String,
    val voiceNeedsNetwork: String,
    val voiceStopped: String,
    val sayOrEnterDestinationFirst: String,
    val speechUnavailableBanner: String,
    val deviceNotConnected: String,
    val deviceReady: String,
    val deviceConnected: String,

    // Spoken screen introductions (assembled with detail level)
    val introHome: String,
    val introHomeDetail: String,
    val introDestination: String,
    val introDestinationMethods: String,
    val introCollections: String,
    val introVoice: String,
    val introType: String,
    val introMap: String,
    val introRecent: String,
    val introSaved: String,
    val introNearby: String,
    val introSearching: String,
    val introSearchResults: String,
    val introConfirmPlace: String, // %1$s place
    val introRouteOptions: String,
    val introRouteOptionsPreparing: String,
    val introRoutePreview: String, // %1$s place
    val introRoutePreviewPreparing: String,
    val introWalkthrough: String,
    val introActiveNavigation: String,
    val introPaused: String,
    val introArrived: String, // %1$s place
    val introSettings: String,
    val introDeviceSettings: String,
    val introAppSettings: String,
    val introEngineering: String,
    val introWhereAmI: String,
    val introSimulationPrefix: String,

    // Maneuvers
    val maneuverLabels: Map<Maneuver, String>,

    // Route summary building blocks
    val summaryAbout: String, // %1$s distance, %2$d minutes
    val summaryStarts: String, // %1$s direction
    val summaryTurnOne: String,
    val summaryTurnMany: String, // %1$d
    val summaryCrosswalkOne: String,
    val summaryCrosswalkMany: String, // %1$d
    val summaryTrafficLightOne: String,
    val summaryTrafficLightMany: String, // %1$d
    val summaryLevelChangeOne: String,
    val summaryLevelChangeMany: String, // %1$d
    val summaryGradeSeparatedOne: String,
    val summaryGradeSeparatedMany: String, // %1$d
    val summaryAmapLabel: String, // %1$s
    val summarySafetyTail: String,
    val metersUnit: String, // %1$d
    val kilometersUnit: String, // %1$s

    // Step instruction building blocks
    val stepContinueFor: String, // straight: "for %1$d metres"
    val stepThenContinueFor: String, // "then continue for %1$d metres"
    val stepOnRoad: String, // %1$s
    val stepDirection: String, // %1$s
    val stepTurnAngle: String, // %1$d
    val stepTrafficLightOne: String,
    val stepTrafficLightMany: String, // %1$d
    val stepConfirmSurroundings: String,

    // Live instruction building blocks
    val instructionInMeters: String, // %1$d
    val instructionToward: String, // %1$s
    val instructionTrafficLight: String,
    val instructionPositionUnmatched: String,

    // Compass directions
    val compassDirections: Map<String, String>,

    // Consent screen
    val consentTitle: String,
    val consentBody: String,
    val consentPrototypeNote: String,
    val consentAgree: String,
    val introConsent: String,

    // Location status parts (rebuilt from the controller's structured status)
    val sourceNames: Map<String, String>,
    val locationOfTemplate: String, // %1$s
    val accurateToAbout: String, // %1$d
    val lowConfidenceTail: String,

    // Controller status dictionary (exact-match English -> localized)
    val statusDictionary: Map<String, String>,
    // Parametric status templates matched by English prefix
    val statusPrefixTemplates: Map<String, String>,
) {
    fun maneuverLabel(maneuver: Maneuver): String =
        maneuverLabels[maneuver] ?: maneuver.spokenLabel

    fun distancePhrase(meters: Int): String = if (meters >= 1_000) {
        kilometersUnit.format("%.1f".format(meters / 1_000.0))
    } else {
        metersUnit.format(meters)
    }

    fun distanceAwayPhrase(meters: Int): String = if (meters >= 1_000) {
        kilometersAway.format("%.1f".format(meters / 1_000.0))
    } else {
        metersAway.format(meters)
    }

    fun compassDirection(english: String): String =
        compassDirections[english] ?: english

    /** Localized rebuild of [RouteSummary.mentalMapSummary]. */
    fun routeSummary(summary: RouteSummary): String = buildString {
        append(summaryAbout.format(distancePhrase(summary.distanceMeters), summary.durationMinutes))
        if (summary.initialDirection.isNotBlank()) {
            append(summaryStarts.format(compassDirection(summary.initialDirection)))
        }
        append(" ")
        append(
            if (summary.turnCount == 1) summaryTurnOne else summaryTurnMany.format(summary.turnCount),
        )
        if (summary.crossingCount > 0) {
            append(
                if (summary.crossingCount == 1) {
                    summaryCrosswalkOne
                } else {
                    summaryCrosswalkMany.format(summary.crossingCount)
                },
            )
        }
        if (summary.mappedTrafficLightCount > 0) {
            append(
                if (summary.mappedTrafficLightCount == 1) {
                    summaryTrafficLightOne
                } else {
                    summaryTrafficLightMany.format(summary.mappedTrafficLightCount)
                },
            )
        }
        if (summary.levelChangeCount > 0) {
            append(
                if (summary.levelChangeCount == 1) {
                    summaryLevelChangeOne
                } else {
                    summaryLevelChangeMany.format(summary.levelChangeCount)
                },
            )
        }
        if (summary.gradeSeparatedCount > 0) {
            append(
                if (summary.gradeSeparatedCount == 1) {
                    summaryGradeSeparatedOne
                } else {
                    summaryGradeSeparatedMany.format(summary.gradeSeparatedCount)
                },
            )
        }
        if (summary.routeLabel.isNotBlank()) {
            append(summaryAmapLabel.format(summary.routeLabel))
        }
        append(summarySafetyTail)
    }

    /** Localized rebuild of [WalkingRouteStep.spokenInstruction]. */
    fun stepInstruction(step: WalkingRouteStep): String = buildString {
        append(maneuverLabel(step.maneuver))
        if (step.distanceMeters > 0) {
            append(
                if (step.maneuver == Maneuver.STRAIGHT) {
                    stepContinueFor.format(step.distanceMeters)
                } else {
                    stepThenContinueFor.format(step.distanceMeters)
                },
            )
        }
        if (step.roadName.isNotBlank()) {
            append(stepOnRoad.format(step.roadName))
        }
        if (step.orientation.isNotBlank()) {
            append(stepDirection.format(compassDirection(step.orientation)))
        }
        step.turnAngleDegrees?.let {
            if (step.maneuver != Maneuver.STRAIGHT && step.maneuver != Maneuver.UNKNOWN) {
                append(stepTurnAngle.format(it))
            }
        }
        if (step.mappedTrafficLightCount > 0) {
            append(
                if (step.mappedTrafficLightCount == 1) {
                    stepTrafficLightOne
                } else {
                    stepTrafficLightMany.format(step.mappedTrafficLightCount)
                },
            )
        }
        if (step.needsEnvironmentalConfirmation) {
            append(stepConfirmSurroundings)
        }
    }

    /** Localized rebuild of a live [NavigationInstruction] message. */
    fun instructionMessage(instruction: NavigationInstruction): String = buildString {
        append(maneuverLabel(instruction.maneuver))
        if (instruction.distanceMeters > 0) {
            append(instructionInMeters.format(instruction.distanceMeters))
        }
        if (instruction.roadName.isNotBlank()) {
            append(instructionToward.format(instruction.roadName))
        }
        if (instruction.trafficLightNearby) append(instructionTrafficLight)
        if (instruction.confirmSurroundings) append(stepConfirmSurroundings)
        if (instruction.positionUnmatched) append(instructionPositionUnmatched)
    }

    fun placeDescription(place: PlaceCandidate): String =
        listOf(place.name, place.address, place.area, placeAccessibilityDetails(place))
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(", ")

    fun placeAccessibilityDetails(place: PlaceCandidate): String = buildList {
        if (place.typeDescription.isNotBlank()) add(place.typeDescription)
        if (place.hasMappedEntrance) add(amapEntranceAvailable)
        if (place.indoorFloorName.isNotBlank()) add(floorPrefix.format(place.indoorFloorName))
        if (place.businessTags.isNotBlank()) add(place.businessTags)
        if (place.childPlaceNames.isNotEmpty()) {
            add(includesPrefix.format(place.childPlaceNames.take(3).joinToString()))
        }
    }.distinct().joinToString(". ")

    fun nearbyCategoryLabel(category: NearbyCategory): String = when (category) {
        NearbyCategory.TOILET -> nearbyCategoryToilet
        NearbyCategory.BUS_STOP -> nearbyCategoryBusStop
        NearbyCategory.METRO_STATION -> nearbyCategoryMetro
        NearbyCategory.PHARMACY -> nearbyCategoryPharmacy
        NearbyCategory.HOSPITAL -> nearbyCategoryHospital
        NearbyCategory.SUPERMARKET -> nearbyCategorySupermarket
    }

    fun weatherSummary(weather: LocalWeather): String = weatherLine.format(
        weather.description,
        weather.temperatureCelsius,
        listOf(weather.windDirection, weather.windPower)
            .filter(String::isNotBlank)
            .joinToString(" "),
    )

    /**
     * Localizes controller status lines. Controllers emit stable English statuses;
     * this dictionary translates the ones users actually hear. Unknown statuses pass
     * through unchanged. Interim mechanism until statuses become typed events.
     */
    fun statusText(status: String): String {
        statusDictionary[status]?.let { return it }
        locationStatusPattern.matchEntire(status)?.let { match ->
            val source = sourceNames[match.groupValues[1]] ?: match.groupValues[1]
            return buildString {
                append(locationOfTemplate.format(source))
                match.groupValues[3].toIntOrNull()?.let { append(accurateToAbout.format(it)) }
                if (match.groupValues[4].isNotEmpty()) append(lowConfidenceTail)
            }
        }
        statusPrefixTemplates.forEach { (prefix, template) ->
            if (status.startsWith(prefix)) {
                return template.format(status.removePrefix(prefix).trim())
            }
        }
        return status
    }

    companion object {
        private val locationStatusPattern = Regex(
            "^(GPS|Network|AMap|System GPS) location" +
                "(, accurate to about (\\d+) metres)?(; guidance confidence is low)?$",
        )

        fun forLanguage(language: AppLanguage): Phrases = when (language) {
            AppLanguage.ENGLISH -> EnglishPhrases
            AppLanguage.SIMPLIFIED_CHINESE -> SimplifiedChinesePhrases
            AppLanguage.TRADITIONAL_CHINESE -> TraditionalChinesePhrases
        }
    }
}
