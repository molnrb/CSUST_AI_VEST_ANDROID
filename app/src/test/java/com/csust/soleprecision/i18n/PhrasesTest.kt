package com.csust.soleprecision.i18n

import com.csust.soleprecision.navigation.Maneuver
import com.csust.soleprecision.navigation.NavigationInstruction
import com.csust.soleprecision.navigation.RouteSummary
import com.csust.soleprecision.navigation.WalkingRouteStep
import com.csust.soleprecision.settings.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhrasesTest {
    private val allPhrases = AppLanguage.entries.map(Phrases::forLanguage)

    @Test
    fun everyLanguageCoversEveryManeuver() {
        allPhrases.forEach { phrases ->
            Maneuver.entries.forEach { maneuver ->
                assertTrue(
                    "Missing label for $maneuver",
                    phrases.maneuverLabels[maneuver]?.isNotBlank() == true,
                )
            }
        }
    }

    @Test
    fun everyLanguageCoversEveryCompassDirection() {
        val directions = listOf(
            "north", "north-east", "east", "south-east",
            "south", "south-west", "west", "north-west",
        )
        allPhrases.forEach { phrases ->
            directions.forEach { direction ->
                assertTrue(
                    "Missing compass direction $direction",
                    phrases.compassDirections[direction]?.isNotBlank() == true,
                )
            }
        }
    }

    @Test
    fun englishRouteSummaryMatchesDomainSummary() {
        val summary = RouteSummary(
            distanceMeters = 500,
            durationSeconds = 400,
            steps = listOf(
                WalkingRouteStep(Maneuver.STRAIGHT, 180, 150, "Main Road"),
                WalkingRouteStep(Maneuver.CROSSWALK, 25, 40, "", mappedTrafficLightCount = 1),
                WalkingRouteStep(Maneuver.RIGHT, 295, 210, "Side Road"),
            ),
            mappedTrafficLightCount = 1,
            initialDirection = "north",
        )
        assertEquals(
            summary.mentalMapSummary,
            Phrases.forLanguage(AppLanguage.ENGLISH).routeSummary(summary),
        )
    }

    @Test
    fun englishStepInstructionMatchesDomainInstruction() {
        val steps = listOf(
            WalkingRouteStep(Maneuver.STRAIGHT, 180, 150, "Main Road", orientation = "north"),
            WalkingRouteStep(
                Maneuver.CROSSWALK,
                25,
                40,
                "",
                mappedTrafficLightCount = 2,
            ),
            WalkingRouteStep(
                Maneuver.LEFT,
                60,
                50,
                "Side Road",
                turnAngleDegrees = 85,
            ),
        )
        val phrases = Phrases.forLanguage(AppLanguage.ENGLISH)
        steps.forEach { step ->
            assertEquals(step.spokenInstruction, phrases.stepInstruction(step))
        }
    }

    @Test
    fun englishInstructionMessageMatchesControllerFormat() {
        val phrases = Phrases.forLanguage(AppLanguage.ENGLISH)
        val instruction = NavigationInstruction(
            maneuver = Maneuver.RIGHT,
            distanceMeters = 50,
            message = "unused",
            source = NavigationInstruction.Source.AMAP,
            roadName = "Lushan Road",
            trafficLightNearby = true,
            confirmSurroundings = false,
            positionUnmatched = false,
        )
        assertEquals(
            "Turn right in 50 metres toward Lushan Road. " +
                "AMap shows a traffic light on this step",
            phrases.instructionMessage(instruction),
        )
    }

    @Test
    fun chineseInstructionMessageUsesChineseLabels() {
        val phrases = Phrases.forLanguage(AppLanguage.SIMPLIFIED_CHINESE)
        val instruction = NavigationInstruction(
            maneuver = Maneuver.RIGHT,
            distanceMeters = 50,
            message = "unused",
            source = NavigationInstruction.Source.AMAP,
        )
        assertEquals("右转，前方50米", phrases.instructionMessage(instruction))
    }

    @Test
    fun statusDictionaryTranslatesExactMatches() {
        val phrases = Phrases.forLanguage(AppLanguage.SIMPLIFIED_CHINESE)
        assertEquals("高德导航已就绪", phrases.statusText("AMap navigation ready"))
        assertEquals("导航已停止", phrases.statusText("Navigation stopped"))
    }

    @Test
    fun statusPrefixTemplatesCarryDetail() {
        val phrases = Phrases.forLanguage(AppLanguage.SIMPLIFIED_CHINESE)
        assertEquals(
            "高德路线计算失败:no road",
            phrases.statusText("AMap route failed: no road"),
        )
    }

    @Test
    fun locationStatusPatternIsRebuiltInChinese() {
        val phrases = Phrases.forLanguage(AppLanguage.SIMPLIFIED_CHINESE)
        assertEquals(
            "GPS定位，精度约12米",
            phrases.statusText("GPS location, accurate to about 12 metres"),
        )
        assertEquals(
            "网络定位，精度约80米；导航可信度较低",
            phrases.statusText(
                "Network location, accurate to about 80 metres; guidance confidence is low",
            ),
        )
    }

    @Test
    fun unknownStatusPassesThroughUnchanged() {
        val phrases = Phrases.forLanguage(AppLanguage.TRADITIONAL_CHINESE)
        assertEquals("Some unmapped status", phrases.statusText("Some unmapped status"))
    }

    @Test
    fun englishStatusTextIsIdentity() {
        val phrases = Phrases.forLanguage(AppLanguage.ENGLISH)
        listOf(
            "AMap navigation ready",
            "GPS location, accurate to about 12 metres",
            "Anything else",
        ).forEach { status ->
            assertEquals(status, phrases.statusText(status))
        }
    }

    @Test
    fun distancePhrasesFormatBothUnits() {
        val en = Phrases.forLanguage(AppLanguage.ENGLISH)
        assertEquals("450 metres", en.distancePhrase(450))
        assertEquals("1.2 kilometres", en.distancePhrase(1_234))
        val zh = Phrases.forLanguage(AppLanguage.SIMPLIFIED_CHINESE)
        assertEquals("450米", zh.distancePhrase(450))
        assertEquals("1.2公里", zh.distancePhrase(1_234))
    }
}
