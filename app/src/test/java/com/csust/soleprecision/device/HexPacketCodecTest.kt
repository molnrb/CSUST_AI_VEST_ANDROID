package com.csust.soleprecision.device

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HexPacketCodecTest {
    @Test
    fun parsesCommonSeparators() {
        val parsed = HexPacketCodec.parse("53 01:10-FF,00").getOrThrow()

        assertArrayEquals(
            byteArrayOf(0x53, 0x01, 0x10, 0xFF.toByte(), 0x00),
            parsed,
        )
    }

    @Test
    fun rejectsMoreThanDefaultBlePayload() {
        val oversized = "00".repeat(HexPacketCodec.MAX_DEFAULT_BLE_PAYLOAD_BYTES + 1)

        assertTrue(HexPacketCodec.parse(oversized).isFailure)
    }

    @Test
    fun rejectsNonHexInput() {
        assertTrue(HexPacketCodec.parse("53 ZZ").isFailure)
    }
}
