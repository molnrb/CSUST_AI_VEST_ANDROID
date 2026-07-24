package com.csust.soleprecision.bluetooth

import com.csust.soleprecision.device.DeviceTestCommand
import com.csust.soleprecision.navigation.NavigationInstruction

interface WearableTransport : AutoCloseable {
    fun connect()
    fun disconnect()
    fun send(instruction: NavigationInstruction): Boolean
    fun send(command: DeviceTestCommand): Boolean
    fun sendRaw(packet: ByteArray): Boolean
}
