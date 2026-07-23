package com.csust.soleprecision.navigation

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationPacketEncoderTest {
    @Test
    fun encodesStableLittleEndianPacket() {
        val packet = NavigationPacketEncoder.encode(
            instruction = NavigationInstruction(
                maneuver = Maneuver.LEFT,
                distanceMeters = 300,
                message = "Turn left",
                source = NavigationInstruction.Source.DEMO,
            ),
            sequence = 0x1234,
            ttlMs = 2_000,
        )

        assertEquals(NavigationPacketEncoder.PACKET_SIZE, packet.size)
        assertArrayEquals(
            byteArrayOf(
                0x53,
                0x01,
                0x01,
                0x34,
                0x12,
                0x03,
                0x2C,
                0x01,
                0x14,
                0x00,
                0x02,
                0x4D,
            ),
            packet,
        )
    }

    @Test
    fun clampsDistanceToUnsignedSixteenBitRange() {
        val packet = NavigationPacketEncoder.encode(
            NavigationInstruction(
                maneuver = Maneuver.STRAIGHT,
                distanceMeters = 90_000,
                message = "Straight",
                source = NavigationInstruction.Source.AMAP,
            ),
            sequence = 1,
        )

        assertEquals(0xFF.toByte(), packet[6])
        assertEquals(0xFF.toByte(), packet[7])
    }
}
