package com.csust.soleprecision.ui

import com.csust.soleprecision.i18n.Phrases
import com.csust.soleprecision.navigation.Maneuver
import com.csust.soleprecision.navigation.RouteSummary
import com.csust.soleprecision.navigation.WalkingRouteStep
import com.csust.soleprecision.settings.AppLanguage
import com.csust.soleprecision.settings.SpeechDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationTextTest {
    private val route = RouteSummary(
        distanceMeters = 313,
        durationSeconds = 300,
        steps = listOf(
            WalkingRouteStep(Maneuver.STRAIGHT, 150, 120, "Yinpenling Street"),
            WalkingRouteStep(Maneuver.RIGHT, 163, 150, "Lushan Road"),
        ),
        initialDirection = "west",
    )

    private fun intro(
        screen: ProductionScreen,
        detail: SpeechDetail,
        language: AppLanguage = AppLanguage.ENGLISH,
        summary: RouteSummary? = null,
    ) = screenIntroduction(
        screen = screen,
        phrases = Phrases.forLanguage(language),
        detail = detail,
        place = null,
        routeSummary = summary,
        isSimulation = false,
        weather = null,
    )

    @Test
    fun firstSentenceHandlesEnglishAndChinesePunctuation() {
        assertEquals("Home.", firstSentence("Home. Swipe right for Navigation."))
        assertEquals("主页。", firstSentence("主页。向右滑动进入导航。"))
        assertEquals("No punctuation", firstSentence("No punctuation"))
    }

    /**
     * Menu screens deliberately have no context sentence: the generated action list
     * carries every control, which is what guarantees nothing is left unspoken.
     */
    @Test
    fun plainMenuScreensHaveNoContextSentence() {
        listOf(
            ProductionScreen.HOME,
            ProductionScreen.DESTINATION,
            ProductionScreen.DESTINATION_METHODS,
            ProductionScreen.DESTINATION_COLLECTIONS,
            ProductionScreen.RECENT_PLACES,
            ProductionScreen.SAVED_PLACES,
            ProductionScreen.WHERE_AM_I,
        ).forEach { screen ->
            assertEquals("", intro(screen, SpeechDetail.STANDARD))
        }
    }

    @Test
    fun screensWithContentDescribeThatContentInEveryLanguage() {
        val contentScreens = listOf(
            ProductionScreen.MAP_DESTINATION,
            ProductionScreen.CONFIRM_PLACE,
            ProductionScreen.ROUTE_PREVIEW,
            ProductionScreen.ROUTE_OPTIONS,
            ProductionScreen.ROUTE_WALKTHROUGH,
            ProductionScreen.ACTIVE_NAVIGATION,
            ProductionScreen.PAUSED,
            ProductionScreen.SETTINGS,
            ProductionScreen.DEVICE_SETTINGS,
            ProductionScreen.APP_SETTINGS,
            ProductionScreen.ENGINEERING,
        )
        AppLanguage.entries.forEach { language ->
            contentScreens.forEach { screen ->
                assertTrue(
                    "Blank context for $screen in $language",
                    intro(screen, SpeechDetail.STANDARD, language).isNotBlank(),
                )
            }
        }
    }

    @Test
    fun detailLevelsScaleTheRoutePreviewContext() {
        val concise = intro(ProductionScreen.ROUTE_PREVIEW, SpeechDetail.CONCISE, summary = route)
        val standard = intro(ProductionScreen.ROUTE_PREVIEW, SpeechDetail.STANDARD, summary = route)
        assertTrue(concise.length < standard.length)
        assertTrue(standard.contains("313 metres"))
        assertTrue(standard.contains("2 walking steps"))
    }

    @Test
    fun detailedHomeAddsTheGestureModelExplanation() {
        val standard = intro(ProductionScreen.HOME, SpeechDetail.STANDARD)
        val detailed = intro(ProductionScreen.HOME, SpeechDetail.DETAILED)
        assertEquals("", standard)
        assertTrue(detailed.startsWith("The whole screen is one card"))
    }

    @Test
    fun introductionsNeverHaveStrayWhitespace() {
        AppLanguage.entries.forEach { language ->
            ProductionScreen.entries.forEach { screen ->
                SpeechDetail.entries.forEach { detail ->
                    val text = intro(screen, detail, language, route)
                    assertEquals(text, text.trim())
                }
            }
        }
    }
}
