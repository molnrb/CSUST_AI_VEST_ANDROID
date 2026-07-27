package com.csust.soleprecision.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.csust.soleprecision.navigation.CueStage
import com.csust.soleprecision.navigation.GuidanceCue
import com.csust.soleprecision.navigation.TurnSide

/**
 * Directional haptics on the phone itself, so turn cues are perceivable without
 * audio and before the backpack exists. Side is encoded in rhythm, not position:
 * left is a double tap, right is one long buzz, hazards are a triple burst.
 *
 * This is a convenience channel, never a safety guarantee — the wearable's local
 * obstacle haptics still outrank anything generated here.
 */
class HapticGuidance(context: Context) {
    private val appContext = context.applicationContext

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Vibrator::class.java)
    }

    fun cue(cue: GuidanceCue, intensityPercent: Int) {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        val pattern = patternFor(cue) ?: return
        val amplitude = (intensityPercent.coerceIn(10, 100) * 255 / 100)
        val amplitudes = IntArray(pattern.size) { index ->
            // Even indices are waits, odd indices are buzzes.
            if (index % 2 == 0) 0 else amplitude
        }
        runCatching {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
        }
    }

    fun stop() {
        runCatching { vibrator?.cancel() }
    }

    private fun patternFor(cue: GuidanceCue): LongArray? = when (cue.stage) {
        // Act-now is the strongest, most distinct pattern.
        CueStage.ACT -> when {
            cue.isHazardManeuver -> longArrayOf(0, 90, 70, 90, 70, 90)
            cue.side == TurnSide.LEFT -> longArrayOf(0, 110, 90, 110)
            cue.side == TurnSide.RIGHT -> longArrayOf(0, 320)
            else -> longArrayOf(0, 160)
        }
        // Prepare is the same rhythm, softer and shorter.
        CueStage.PREPARE -> when (cue.side) {
            TurnSide.LEFT -> longArrayOf(0, 60, 80, 60)
            TurnSide.RIGHT -> longArrayOf(0, 180)
            TurnSide.NONE -> if (cue.isHazardManeuver) longArrayOf(0, 60, 60, 60, 60, 60) else null
        }
        CueStage.OFF_ROUTE -> longArrayOf(0, 220, 120, 220)
        CueStage.ARRIVAL -> longArrayOf(0, 90, 60, 90, 60, 260)
        CueStage.EARLY,
        CueStage.PROGRESS,
        CueStage.CONFIRM,
        -> null
    }
}
