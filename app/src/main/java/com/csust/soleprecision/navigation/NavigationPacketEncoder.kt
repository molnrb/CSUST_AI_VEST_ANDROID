package com.csust.soleprecision.navigation

/**
 * Compact BLE packet sent from the phone to the ESP32.
 *
 * Byte layout (little-endian):
 * 0 magic 'S' | 1 version | 2 type | 3..4 sequence | 5 maneuver |
 * 6..7 distance metres | 8..9 TTL in 100 ms units | 10 flags | 11 XOR checksum
 */
object NavigationPacketEncoder {
    const val PACKET_SIZE = 12
    private const val MAGIC = 0x53
    private const val VERSION = 1
    private const val TYPE_NAVIGATION = 1

    fun encode(
        instruction: NavigationInstruction,
        sequence: Int,
        ttlMs: Int = 2_000,
    ): ByteArray {
        val distance = instruction.distanceMeters.coerceIn(0, 65_535)
        val ttlUnits = (ttlMs / 100).coerceIn(0, 65_535)
        val bytes = ByteArray(PACKET_SIZE)

        bytes[0] = MAGIC.toByte()
        bytes[1] = VERSION.toByte()
        bytes[2] = TYPE_NAVIGATION.toByte()
        bytes[3] = sequence.toByte()
        bytes[4] = (sequence ushr 8).toByte()
        bytes[5] = instruction.maneuver.wireCode.toByte()
        bytes[6] = distance.toByte()
        bytes[7] = (distance ushr 8).toByte()
        bytes[8] = ttlUnits.toByte()
        bytes[9] = (ttlUnits ushr 8).toByte()
        bytes[10] = when (instruction.source) {
            NavigationInstruction.Source.AMAP -> 0x01
            NavigationInstruction.Source.DEMO -> 0x02
        }

        bytes[11] = bytes.take(11).fold(0) { checksum, byte ->
            checksum xor (byte.toInt() and 0xFF)
        }.toByte()
        return bytes
    }

    fun toHex(bytes: ByteArray): String =
        bytes.joinToString(separator = " ") { "%02X".format(it.toInt() and 0xFF) }
}
