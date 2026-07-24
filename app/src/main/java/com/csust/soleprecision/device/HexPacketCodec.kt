package com.csust.soleprecision.device

object HexPacketCodec {
    const val MAX_DEFAULT_BLE_PAYLOAD_BYTES = 20

    fun parse(input: String): Result<ByteArray> = runCatching {
        val compact = input.replace(Regex("[\\s,:-]"), "")
        require(compact.isNotEmpty()) { "Enter at least one byte" }
        require(compact.length % 2 == 0) { "Hex data must contain complete byte pairs" }
        require(compact.length / 2 <= MAX_DEFAULT_BLE_PAYLOAD_BYTES) {
            "Raw packet cannot exceed $MAX_DEFAULT_BLE_PAYLOAD_BYTES bytes"
        }
        require(compact.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "Raw packet contains a non-hex character"
        }

        ByteArray(compact.length / 2) { index ->
            compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
