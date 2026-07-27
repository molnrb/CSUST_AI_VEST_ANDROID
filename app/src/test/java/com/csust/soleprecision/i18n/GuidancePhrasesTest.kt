package com.csust.soleprecision.i18n

import com.csust.soleprecision.navigation.CueStage
import com.csust.soleprecision.navigation.GuidanceCue
import com.csust.soleprecision.navigation.Maneuver
import com.csust.soleprecision.navigation.TurnSide
import com.csust.soleprecision.settings.AppLanguage
import com.csust.soleprecision.settings.SpeechDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidancePhrasesTest {
    private val english = GuidancePhrases.forLanguage(AppLanguage.ENGLISH)
    private val englishPhrases = Phrases.forLanguage(AppLanguage.ENGLISH)

    private fun cue(
        stage: CueStage,
        maneuver: Maneuver = Maneuver.RIGHT,
        distance: Int = 30,
        clock: Int? = 3,
        landmark: String = "Hunan Technology Building",
        trafficLights: Int = 0,
        side: TurnSide = TurnSide.RIGHT,
        roadName: String = "Lushan Road",
        offRoute: Int = 0,
    ) = GuidanceCue(
        stage = stage,
        maneuver = maneuver,
        distanceMeters = distance,
        roadName = roadName,
        currentRoadName = "Yinpenling Street",
        stepDistanceMeters = 160,
        clockPosition = clock,
        turnAngleDegrees = 88,
        orientation = "north",
        landmark = landmark,
        trafficLightCount = trafficLights,
        needsConfirmation = maneuver == Maneuver.CROSSWALK,
        remainingRouteMeters = 220,
        remainingRouteMinutes = 4,
        offRouteMeters = offRoute,
        side = side,
    )

    @Test
    fun stagesReadAsDistinctInstructions() {
        val early = english.cueMessage(cue(CueStage.EARLY, distance = 110), SpeechDetail.STANDARD, englishPhrases)
        val prepare = english.cueMessage(cue(CueStage.PREPARE), SpeechDetail.STANDARD, englishPhrases)
        val act = english.cueMessage(cue(CueStage.ACT, distance = 5), SpeechDetail.STANDARD, englishPhrases)

        assertEquals(
            "In 110 metres, Turn right onto Lushan Road, at 3 o'clock",
            early,
        )
        assertTrue(prepare.startsWith("Get ready: Turn right"))
        assertTrue(act.startsWith("Now: Turn right"))
        // The landmark anchors the two close-range cues, not the early notice.
        assertTrue(prepare.contains("near Hunan Technology Building"))
        assertTrue(act.contains("near Hunan Technology Building"))
        assertFalse(early.contains("near Hunan Technology Building"))
    }

    @Test
    fun confirmCueNamesTheNewRoadAndLength() {
        val message = english.cueMessage(
            cue(CueStage.CONFIRM),
            SpeechDetail.STANDARD,
            englishPhrases,
        )
        assertEquals("You are now on Lushan Road, continue for 160 metres", message)
    }

    @Test
    fun crossingActCueTellsTheUserToStopAndVerify() {
        val message = english.cueMessage(
            cue(CueStage.ACT, maneuver = Maneuver.CROSSWALK, distance = 8, side = TurnSide.NONE, trafficLights = 1),
            SpeechDetail.CONCISE,
            englishPhrases,
        )
        assertTrue(message.contains("Stop at the kerb"))
        assertTrue(message.contains("Check the real crossing"))
        // Hazard information survives even the most concise setting.
        assertTrue(message.contains("Mapped traffic light here"))
    }

    @Test
    fun offRouteCueStatesDriftAndRecovery() {
        val message = english.cueMessage(
            cue(CueStage.OFF_ROUTE, offRoute = 18),
            SpeechDetail.STANDARD,
            englishPhrases,
        )
        assertTrue(message.startsWith("Off route by about 18 metres"))
        assertTrue(message.contains("turn back toward the route"))
    }

    @Test
    fun clockDirectionSurvivesConciseModeAtTheMomentOfAction() {
        val act = english.cueMessage(
            cue(CueStage.ACT, distance = 5),
            SpeechDetail.CONCISE,
            englishPhrases,
        )
        assertTrue(act.contains("3 o'clock"))
        val early = english.cueMessage(
            cue(CueStage.EARLY, distance = 110),
            SpeechDetail.CONCISE,
            englishPhrases,
        )
        assertFalse(early.contains("o'clock"))
    }

    @Test
    fun detailedModeAddsAngleAndRemainingRoute() {
        val prepare = english.cueMessage(cue(CueStage.PREPARE), SpeechDetail.DETAILED, englishPhrases)
        assertTrue(prepare.contains("88 degrees"))
        val confirm = english.cueMessage(cue(CueStage.CONFIRM), SpeechDetail.DETAILED, englishPhrases)
        assertTrue(confirm.contains("220 metres left"))
    }

    @Test
    fun everyStageHasSpeechInEveryLanguage() {
        AppLanguage.entries.forEach { language ->
            val guidance = GuidancePhrases.forLanguage(language)
            val phrases = Phrases.forLanguage(language)
            CueStage.entries.forEach { stage ->
                SpeechDetail.entries.forEach { detail ->
                    val message = guidance.cueMessage(cue(stage), detail, phrases)
                    assertTrue("Blank cue for $stage/$detail in $language", message.isNotBlank())
                }
            }
        }
    }

    @Test
    fun chineseCuesUseChineseManeuverLabels() {
        val guidance = GuidancePhrases.forLanguage(AppLanguage.SIMPLIFIED_CHINESE)
        val phrases = Phrases.forLanguage(AppLanguage.SIMPLIFIED_CHINESE)
        val message = guidance.cueMessage(cue(CueStage.ACT, distance = 5), SpeechDetail.STANDARD, phrases)
        assertTrue(message.contains("右转"))
        assertTrue(message.contains("点钟方向"))
    }

    @Test
    fun actionSentenceListsEveryAvailableControl() {
        val sentence = english.actionsSentence(
            title = "Route preview",
            right = "Start navigation",
            left = "Decline route",
            up = "Review full route",
            down = "Back",
            usesButtons = false,
        )
        assertEquals(
            "Route preview. Swipe right for Start navigation, left for Decline route, " +
                "up for Review full route, down for Back.",
            sentence,
        )
    }

    @Test
    fun actionSentenceSkipsUnavailableDirectionsAndAdaptsToButtons() {
        val sentence = english.actionsSentence(
            title = "Home",
            right = "Navigation",
            left = null,
            up = null,
            down = null,
            usesButtons = true,
        )
        assertEquals("Home. Buttons: right for Navigation.", sentence)
    }

    @Test
    fun actionSentenceFallsBackToTitleWhenNothingIsAvailable() {
        assertEquals(
            "Searching",
            english.actionsSentence("Searching", null, null, null, null, false),
        )
    }
}
