package com.csust.soleprecision.bluetooth

import com.csust.soleprecision.device.DeviceTestCommand
import com.csust.soleprecision.device.DeviceTestPacketEncoder
import com.csust.soleprecision.navigation.NavigationInstruction
import com.csust.soleprecision.navigation.NavigationPacketEncoder
import java.util.Locale

/**
 * Emulator-safe stand-in for the upper controller, USB-to-TTL link, ESP32 and outputs.
 *
 * It deliberately uses the same command boundary as the temporary real transport so
 * the UI and navigation logic do not need simulator-specific branches.
 */
class MockWearableTransport(
    private val onStatus: (String) -> Unit,
    private val onPacketPrepared: (String) -> Unit,
    private val onEvent: (String) -> Unit,
) : WearableTransport {
    private var connected = false
    private var sequence = 0

    override fun connect() {
        connected = true
        onStatus("Simulator ready: upper controller and ESP32 lower controller")
        onEvent("Simulated system connected; no physical hardware is required")
    }

    override fun disconnect() {
        connected = false
        onStatus("Simulated system disconnected")
        onEvent("Simulated system disconnected")
    }

    override fun send(instruction: NavigationInstruction): Boolean {
        val packet = NavigationPacketEncoder.encode(instruction, sequence++)
        onPacketPrepared(NavigationPacketEncoder.toHex(packet))
        if (!connected) return false

        onEvent(
            "SIMULATED NAVIGATION · ${instruction.message} · " +
                "upper controller forwarded cue to ESP32",
        )
        return true
    }

    override fun send(command: DeviceTestCommand): Boolean {
        val packet = DeviceTestPacketEncoder.encode(command, sequence++)
        onPacketPrepared(NavigationPacketEncoder.toHex(packet))
        if (!connected) return false

        onEvent(command.simulatedEvent())
        return true
    }

    override fun sendRaw(packet: ByteArray): Boolean {
        onPacketPrepared(NavigationPacketEncoder.toHex(packet))
        if (!connected) return false

        onEvent("SIMULATED RAW LINK · accepted ${packet.size} USB-to-TTL bytes")
        return true
    }

    override fun close() {
        connected = false
    }

    private fun DeviceTestCommand.simulatedEvent(): String = when (this) {
        is DeviceTestCommand.Audio ->
            "SIMULATED SPEAKER · ${side.displayName} · ${cue.displayName} · " +
                "$volumePercent% · repeat $repeatCount"

        is DeviceTestCommand.Vibration ->
            "SIMULATED VIBRATION · ${side.displayName} · " +
                "${pattern.displayName.lowercase(Locale.ROOT)} · $intensityPercent% · " +
                "$durationMs ms · repeat $repeatCount"

        DeviceTestCommand.StopAll ->
            "SIMULATED SAFETY COMMAND · both speakers and vibration motors stopped"
    }
}
