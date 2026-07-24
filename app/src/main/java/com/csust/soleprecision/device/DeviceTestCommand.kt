package com.csust.soleprecision.device

enum class OutputSide(
    val mask: Int,
    val displayName: String,
) {
    LEFT(0x01, "Left"),
    RIGHT(0x02, "Right"),
    BOTH(0x03, "Both"),
}

enum class VibrationPattern(
    val wireCode: Int,
    val displayName: String,
) {
    CONTINUOUS(0, "Continuous"),
    PULSE(1, "Pulse"),
    DOUBLE_PULSE(2, "Double"),
    TRIPLE_PULSE(3, "Triple"),
}

enum class AudioCue(
    val wireCode: Int,
    val displayName: String,
) {
    TEST_TONE(1, "Test tone"),
    TURN_LEFT(2, "Turn left"),
    TURN_RIGHT(3, "Turn right"),
    GO_STRAIGHT(4, "Go straight"),
    OBSTACLE(5, "Obstacle"),
    STOP(6, "Stop"),
    ARRIVED(7, "Arrived"),
}

sealed interface DeviceTestCommand {
    data class Vibration(
        val side: OutputSide,
        val intensityPercent: Int,
        val durationMs: Int,
        val pattern: VibrationPattern,
        val repeatCount: Int,
    ) : DeviceTestCommand

    data class Audio(
        val side: OutputSide,
        val cue: AudioCue,
        val volumePercent: Int,
        val repeatCount: Int,
    ) : DeviceTestCommand

    data object StopAll : DeviceTestCommand
}
