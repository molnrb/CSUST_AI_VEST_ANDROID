package com.csust.soleprecision.device

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class DeviceTestPacketEncoderTest {
    @Test
    fun encodesVibrationParameters() {
        val packet = DeviceTestPacketEncoder.encode(
            DeviceTestCommand.Vibration(
                side = OutputSide.LEFT,
                intensityPercent = 75,
                durationMs = 500,
                pattern = VibrationPattern.DOUBLE_PULSE,
                repeatCount = 3,
            ),
            sequence = 0x1234,
        )

        assertArrayEquals(
            byteArrayOf(
                0x53,
                0x01,
                0x10,
                0x34,
                0x12,
                0x01,
                0x4B,
                0x02,
                0xF4.toByte(),
                0x01,
                0x03,
                0xDA.toByte(),
            ),
            packet,
        )
    }

    @Test
    fun encodesAudioCueAndSide() {
        val packet = DeviceTestPacketEncoder.encode(
            DeviceTestCommand.Audio(
                side = OutputSide.BOTH,
                cue = AudioCue.OBSTACLE,
                volumePercent = 80,
                repeatCount = 2,
            ),
            sequence = 1,
        )

        assertArrayEquals(
            byteArrayOf(
                0x53,
                0x01,
                0x11,
                0x01,
                0x00,
                0x03,
                0x05,
                0x50,
                0x02,
                0x00,
                0x00,
                0x16,
            ),
            packet,
        )
    }

    @Test
    fun encodesStopAllCommand() {
        val packet = DeviceTestPacketEncoder.encode(
            DeviceTestCommand.StopAll,
            sequence = 2,
        )

        assertArrayEquals(
            byteArrayOf(
                0x53,
                0x01,
                0x12,
                0x02,
                0x00,
                0x03,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x41,
            ),
            packet,
        )
    }
}
