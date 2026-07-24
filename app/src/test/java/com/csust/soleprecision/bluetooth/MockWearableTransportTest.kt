package com.csust.soleprecision.bluetooth

import com.csust.soleprecision.device.DeviceTestCommand
import com.csust.soleprecision.device.OutputSide
import com.csust.soleprecision.device.VibrationPattern
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockWearableTransportTest {
    @Test
    fun `commands fail offline and succeed after simulated connection`() {
        val events = mutableListOf<String>()
        val transport = MockWearableTransport(
            onStatus = events::add,
            onPacketPrepared = events::add,
            onEvent = events::add,
        )
        val command = DeviceTestCommand.Vibration(
            side = OutputSide.LEFT,
            intensityPercent = 80,
            durationMs = 400,
            pattern = VibrationPattern.DOUBLE_PULSE,
            repeatCount = 1,
        )

        assertFalse(transport.send(command))
        transport.connect()
        assertTrue(transport.send(command))
        assertTrue(events.any { it.contains("SIMULATED VIBRATION") })
    }
}
