package com.csust.soleprecision.device

/**
 * Temporary engineering protocol. Replace this encoder when the hardware team
 * supplies the production interface.
 *
 * All packets are 12 bytes:
 * 0 magic 'S' | 1 version | 2 type | 3..4 sequence | 5 side |
 * 6..10 command arguments | 11 XOR checksum.
 */
object DeviceTestPacketEncoder {
    const val PACKET_SIZE = 12
    private const val MAGIC = 0x53
    private const val VERSION = 1
    private const val TYPE_VIBRATION = 0x10
    private const val TYPE_AUDIO = 0x11
    private const val TYPE_STOP_ALL = 0x12

    fun encode(command: DeviceTestCommand, sequence: Int): ByteArray {
        val bytes = ByteArray(PACKET_SIZE)
        bytes[0] = MAGIC.toByte()
        bytes[1] = VERSION.toByte()
        bytes[3] = sequence.toByte()
        bytes[4] = (sequence ushr 8).toByte()

        when (command) {
            is DeviceTestCommand.Vibration -> {
                val duration = command.durationMs.coerceIn(10, 10_000)
                bytes[2] = TYPE_VIBRATION.toByte()
                bytes[5] = command.side.mask.toByte()
                bytes[6] = command.intensityPercent.coerceIn(0, 100).toByte()
                bytes[7] = command.pattern.wireCode.toByte()
                bytes[8] = duration.toByte()
                bytes[9] = (duration ushr 8).toByte()
                bytes[10] = command.repeatCount.coerceIn(1, 10).toByte()
            }

            is DeviceTestCommand.Audio -> {
                bytes[2] = TYPE_AUDIO.toByte()
                bytes[5] = command.side.mask.toByte()
                bytes[6] = command.cue.wireCode.toByte()
                bytes[7] = command.volumePercent.coerceIn(0, 100).toByte()
                bytes[8] = command.repeatCount.coerceIn(1, 10).toByte()
            }

            DeviceTestCommand.StopAll -> {
                bytes[2] = TYPE_STOP_ALL.toByte()
                bytes[5] = OutputSide.BOTH.mask.toByte()
            }
        }

        bytes[11] = checksum(bytes)
        return bytes
    }

    private fun checksum(bytes: ByteArray): Byte =
        bytes.take(11).fold(0) { checksum, byte ->
            checksum xor (byte.toInt() and 0xFF)
        }.toByte()
}
