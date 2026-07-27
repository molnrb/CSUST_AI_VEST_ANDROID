package com.csust.soleprecision.i18n

import com.csust.soleprecision.navigation.CueStage
import com.csust.soleprecision.navigation.GuidanceCue
import com.csust.soleprecision.navigation.Maneuver
import com.csust.soleprecision.navigation.TurnSide
import com.csust.soleprecision.settings.AppLanguage
import com.csust.soleprecision.settings.SpeechDetail

/**
 * Speech for live walking cues and for directional action lists.
 *
 * Kept separate from [Phrases] on purpose: that table is already near the JVM's
 * 254-parameter constructor ceiling, so new speech groups become their own class
 * rather than more top-level fields.
 */
class GuidancePhrases(
    // Cue lead-ins
    val earlyLead: String, // %1$s maneuver, %2$d metres
    val prepareLead: String, // %1$s maneuver
    val actLead: String, // %1$s maneuver
    val progressLead: String, // %1$d metres, %2$s maneuver
    val confirmLead: String, // %1$s road-or-heading
    val confirmContinue: String, // %1$d metres
    val arrivalLead: String, // %1$d metres
    val offRouteLead: String, // %1$d metres

    // Cue detail fragments
    val ontoRoad: String, // %1$s
    val clockPosition: String, // %1$d
    val turnAngle: String, // %1$d
    val landmarkAnchor: String, // %1$s
    val headingIs: String, // %1$s compass
    val trafficLightOne: String,
    val trafficLightMany: String, // %1$d
    val crossingVerify: String,
    val confirmSurroundings: String,
    val remainingRoute: String, // %1$d metres, %2$d minutes
    val returnToRoute: String,
    val stopNow: String,

    // Directional action list
    val actionsLead: String,
    val swipeRight: String, // %1$s
    val swipeLeft: String, // %1$s
    val swipeUp: String, // %1$s
    val swipeDown: String, // %1$s
    val actionSeparator: String,
    val buttonActionsLead: String,

    // Settings copy for the detailed-guidance switch
    val detailedGuidanceSetting: String,
    val detailedGuidanceSettingSupport: String,
) {
    /**
     * Builds one spoken cue. Detail level controls how much anchoring context is
     * added; the action itself is always stated first so it is never buried.
     */
    fun cueMessage(
        cue: GuidanceCue,
        detail: SpeechDetail,
        phrases: Phrases,
    ): String {
        val maneuverLabel = phrases.maneuverLabel(cue.maneuver)
        val sentence = StringBuilder()
        when (cue.stage) {
            CueStage.EARLY -> sentence.append(earlyLead.format(maneuverLabel, cue.distanceMeters))
            CueStage.PREPARE -> sentence.append(prepareLead.format(maneuverLabel))
            CueStage.ACT -> sentence.append(actLead.format(maneuverLabel))
            CueStage.PROGRESS ->
                sentence.append(progressLead.format(cue.distanceMeters, maneuverLabel))
            CueStage.CONFIRM -> {
                val where = cue.roadName.ifBlank {
                    cue.currentRoadName.ifBlank {
                        phrases.compassDirection(cue.orientation)
                    }
                }
                sentence.append(confirmLead.format(where))
                if (cue.stepDistanceMeters > 0) {
                    sentence.append(confirmContinue.format(cue.stepDistanceMeters))
                }
            }
            CueStage.ARRIVAL -> sentence.append(arrivalLead.format(cue.distanceMeters))
            CueStage.OFF_ROUTE -> {
                sentence.append(offRouteLead.format(cue.offRouteMeters))
                sentence.append(returnToRoute)
            }
        }

        val statesDirection = cue.stage == CueStage.EARLY ||
            cue.stage == CueStage.PREPARE ||
            cue.stage == CueStage.ACT
        if (statesDirection && cue.roadName.isNotBlank() && cue.stage != CueStage.ACT) {
            sentence.append(ontoRoad.format(cue.roadName))
        }

        // Clock position is the most precise thing we can offer a blind walker,
        // so it survives even in concise mode at the moment of action.
        if (statesDirection && cue.clockPosition != null && cue.side != TurnSide.NONE) {
            val includeClock = detail != SpeechDetail.CONCISE || cue.stage == CueStage.ACT
            if (includeClock) {
                sentence.append(clockPosition.format(cue.clockPosition))
            }
        }
        if (
            detail == SpeechDetail.DETAILED &&
            statesDirection &&
            cue.turnAngleDegrees != null &&
            cue.side != TurnSide.NONE
        ) {
            sentence.append(turnAngle.format(cue.turnAngleDegrees))
        }
        if (
            detail != SpeechDetail.CONCISE &&
            cue.landmark.isNotBlank() &&
            (cue.stage == CueStage.PREPARE || cue.stage == CueStage.ACT)
        ) {
            sentence.append(landmarkAnchor.format(cue.landmark))
        }
        if (
            detail == SpeechDetail.DETAILED &&
            cue.stage == CueStage.CONFIRM &&
            cue.orientation.isNotBlank()
        ) {
            sentence.append(headingIs.format(phrases.compassDirection(cue.orientation)))
        }

        // Hazards: always spoken, at every detail level.
        if (cue.trafficLightCount > 0 && cue.stage != CueStage.PROGRESS) {
            sentence.append(
                if (cue.trafficLightCount == 1) {
                    trafficLightOne
                } else {
                    trafficLightMany.format(cue.trafficLightCount)
                },
            )
        }
        if (cue.maneuver == Maneuver.CROSSWALK && cue.stage == CueStage.ACT) {
            sentence.append(stopNow)
            sentence.append(crossingVerify)
        } else if (cue.isHazardManeuver && (cue.stage == CueStage.PREPARE || cue.stage == CueStage.ACT)) {
            sentence.append(confirmSurroundings)
        }

        if (
            detail == SpeechDetail.DETAILED &&
            cue.stage == CueStage.CONFIRM &&
            cue.remainingRouteMeters > 0
        ) {
            sentence.append(
                remainingRoute.format(cue.remainingRouteMeters, cue.remainingRouteMinutes),
            )
        }
        return sentence.toString()
    }

    /**
     * Spoken list of the actions available on the current screen. Generated from the
     * live action map so a button can never be shown without being announced.
     */
    fun actionsSentence(
        title: String,
        right: String?,
        left: String?,
        up: String?,
        down: String?,
        usesButtons: Boolean,
    ): String {
        val parts = buildList {
            right?.let { add(swipeRight.format(it)) }
            left?.let { add(swipeLeft.format(it)) }
            up?.let { add(swipeUp.format(it)) }
            down?.let { add(swipeDown.format(it)) }
        }
        if (parts.isEmpty()) return title
        val lead = if (usesButtons) buttonActionsLead else actionsLead
        return buildString {
            append(title)
            append(". ")
            append(lead)
            append(" ")
            append(parts.joinToString(actionSeparator))
            append(".")
        }
    }

    companion object {
        fun forLanguage(language: AppLanguage): GuidancePhrases = when (language) {
            AppLanguage.ENGLISH -> EnglishGuidancePhrases
            AppLanguage.SIMPLIFIED_CHINESE -> SimplifiedChineseGuidancePhrases
            AppLanguage.TRADITIONAL_CHINESE -> TraditionalChineseGuidancePhrases
        }
    }
}

private val EnglishGuidancePhrases = GuidancePhrases(
    earlyLead = "In %2\$d metres, %1\$s",
    prepareLead = "Get ready: %1\$s",
    actLead = "Now: %1\$s",
    progressLead = "Continue %1\$d metres, then %2\$s",
    confirmLead = "You are now on %1\$s",
    confirmContinue = ", continue for %1\$d metres",
    arrivalLead = "Destination is about %1\$d metres ahead",
    offRouteLead = "Off route by about %1\$d metres",
    ontoRoad = " onto %1\$s",
    clockPosition = ", at %1\$d o'clock",
    turnAngle = ", about %1\$d degrees",
    landmarkAnchor = ", near %1\$s",
    headingIs = ", heading %1\$s",
    trafficLightOne = ". Mapped traffic light here",
    trafficLightMany = ". %1\$d mapped traffic lights here",
    crossingVerify = " Check the real crossing and traffic yourself before stepping out.",
    confirmSurroundings = " Confirm the surroundings before continuing.",
    remainingRoute = ". %1\$d metres left, about %2\$d minutes",
    returnToRoute = ". Stop and turn back toward the route.",
    stopNow = ". Stop at the kerb.",
    actionsLead = "Swipe",
    swipeRight = "right for %1\$s",
    swipeLeft = "left for %1\$s",
    swipeUp = "up for %1\$s",
    swipeDown = "down for %1\$s",
    actionSeparator = ", ",
    buttonActionsLead = "Buttons:",
    detailedGuidanceSetting = "Precise walking guidance",
    detailedGuidanceSettingSupport = "Early, get-ready and act-now cues with clock directions, " +
        "landmarks and off-route warnings, spoken by this app. Turn off to use AMap's own " +
        "driving-style voice instead.",
)

private val SimplifiedChineseGuidancePhrases = GuidancePhrases(
    earlyLead = "前方%2\$d米，%1\$s",
    prepareLead = "准备：%1\$s",
    actLead = "现在：%1\$s",
    progressLead = "继续走%1\$d米，然后%2\$s",
    confirmLead = "您现在位于%1\$s",
    confirmContinue = "，继续走%1\$d米",
    arrivalLead = "目的地在前方约%1\$d米",
    offRouteLead = "已偏离路线约%1\$d米",
    ontoRoad = "，进入%1\$s",
    clockPosition = "，在%1\$d点钟方向",
    turnAngle = "，约%1\$d度",
    landmarkAnchor = "，靠近%1\$s",
    headingIs = "，朝向%1\$s",
    trafficLightOne = "。此处地图标注有红绿灯",
    trafficLightMany = "。此处地图标注有%1\$d处红绿灯",
    crossingVerify = " 过街前请自行确认实际路口和交通状况。",
    confirmSurroundings = " 继续前请确认周围环境。",
    remainingRoute = "。剩余%1\$d米，约%2\$d分钟",
    returnToRoute = "。请停下并转身返回路线。",
    stopNow = "。请在路缘处停下。",
    actionsLead = "滑动方向：",
    swipeRight = "向右%1\$s",
    swipeLeft = "向左%1\$s",
    swipeUp = "向上%1\$s",
    swipeDown = "向下%1\$s",
    actionSeparator = "，",
    buttonActionsLead = "按钮：",
    detailedGuidanceSetting = "精准步行引导",
    detailedGuidanceSettingSupport = "由本应用播报提前、准备和立即执行的提示，" +
        "包含钟点方向、地标和偏离路线警告。关闭后改用高德自带的驾车式语音。",
)

private val TraditionalChineseGuidancePhrases = GuidancePhrases(
    earlyLead = "前方%2\$d公尺，%1\$s",
    prepareLead = "準備：%1\$s",
    actLead = "現在：%1\$s",
    progressLead = "繼續走%1\$d公尺，然後%2\$s",
    confirmLead = "您現在位於%1\$s",
    confirmContinue = "，繼續走%1\$d公尺",
    arrivalLead = "目的地在前方約%1\$d公尺",
    offRouteLead = "已偏離路線約%1\$d公尺",
    ontoRoad = "，進入%1\$s",
    clockPosition = "，在%1\$d點鐘方向",
    turnAngle = "，約%1\$d度",
    landmarkAnchor = "，靠近%1\$s",
    headingIs = "，朝向%1\$s",
    trafficLightOne = "。此處地圖標註有紅綠燈",
    trafficLightMany = "。此處地圖標註有%1\$d處紅綠燈",
    crossingVerify = " 過街前請自行確認實際路口和交通狀況。",
    confirmSurroundings = " 繼續前請確認周圍環境。",
    remainingRoute = "。剩餘%1\$d公尺，約%2\$d分鐘",
    returnToRoute = "。請停下並轉身返回路線。",
    stopNow = "。請在路緣處停下。",
    actionsLead = "滑動方向：",
    swipeRight = "向右%1\$s",
    swipeLeft = "向左%1\$s",
    swipeUp = "向上%1\$s",
    swipeDown = "向下%1\$s",
    actionSeparator = "，",
    buttonActionsLead = "按鈕：",
    detailedGuidanceSetting = "精準步行引導",
    detailedGuidanceSettingSupport = "由本應用播報提前、準備和立即執行的提示，" +
        "包含鐘點方向、地標和偏離路線警告。關閉後改用高德自帶的駕車式語音。",
)
