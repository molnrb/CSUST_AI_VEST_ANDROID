package com.csust.soleprecision.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.csust.soleprecision.device.DeviceTestCommand
import com.csust.soleprecision.device.DeviceTestPacketEncoder
import com.csust.soleprecision.navigation.NavigationInstruction
import com.csust.soleprecision.navigation.NavigationPacketEncoder
import java.util.ArrayDeque
import java.util.UUID

/**
 * Temporary BLE transport hardened for engineering use:
 * - GATT writes are serialized through a queue and confirmed via onCharacteristicWrite,
 *   because Android allows only one write in flight per connection;
 * - unexpected disconnects trigger a bounded reconnect with backoff;
 * - connecting has a timeout so a silent peripheral cannot hang the state machine;
 * - a larger MTU is requested before service discovery.
 * The 12-byte wire format and UUIDs are unchanged placeholders from
 * docs/TEMPORARY_DEVICE_PROTOCOL.md until the hardware team's real contract exists.
 */
class BleWearableTransport(
    context: Context,
    private val onStatus: (String) -> Unit,
    private val onPacketPrepared: (String) -> Unit,
) : WearableTransport {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter get() = bluetoothManager?.adapter
    private val mainHandler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var sequence = 0
    private var userRequestedDisconnect = false
    private var reconnectAttempts = 0
    private var lastDevice: BluetoothDevice? = null

    private val writeQueue = ArrayDeque<ByteArray>()
    private var writeInFlight = false

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            mainHandler.removeCallbacks(scanTimeout)
            if (hasBluetoothPermissions()) {
                adapter?.bluetoothLeScanner?.stopScan(this)
            }
            onStatus("Found wearable; connecting…")
            connectToDevice(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            mainHandler.removeCallbacks(scanTimeout)
            onStatus("Bluetooth scan failed ($errorCode)")
        }
    }

    private val scanTimeout = Runnable {
        stopScanning()
        onStatus("No Sole Precision wearable found")
    }

    private val connectTimeout = Runnable {
        if (writeCharacteristic == null) {
            onStatus("Wearable connection timed out")
            closeGattQuietly()
            scheduleReconnectIfWanted()
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        lastDevice = device
        mainHandler.removeCallbacks(connectTimeout)
        mainHandler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
        gatt = device.connectGatt(
            appContext,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE,
        )
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    mainHandler.post {
                        mainHandler.removeCallbacks(connectTimeout)
                        mainHandler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
                        reconnectAttempts = 0
                        onStatus("Wearable connected; negotiating link…")
                        if (!gatt.requestMtu(REQUESTED_MTU)) {
                            gatt.discoverServices()
                        }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    mainHandler.post {
                        mainHandler.removeCallbacks(connectTimeout)
                        writeCharacteristic = null
                        clearWriteQueue()
                        gatt.close()
                        if (this@BleWearableTransport.gatt === gatt) {
                            this@BleWearableTransport.gatt = null
                        }
                        if (userRequestedDisconnect) {
                            onStatus("Wearable disconnected")
                        } else {
                            onStatus("Wearable connection lost")
                            scheduleReconnectIfWanted()
                        }
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            mainHandler.post {
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            mainHandler.post {
                mainHandler.removeCallbacks(connectTimeout)
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    onStatus("Wearable service discovery failed ($status)")
                    return@post
                }
                writeCharacteristic = gatt
                    .getService(SERVICE_UUID)
                    ?.getCharacteristic(COMMAND_CHARACTERISTIC_UUID)

                onStatus(
                    if (writeCharacteristic != null) {
                        "Wearable ready"
                    } else {
                        "Connected, but command control was not found"
                    },
                )
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            mainHandler.post {
                writeInFlight = false
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    onStatus("Wearable write failed ($status)")
                }
                drainWriteQueue()
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun connect() {
        if (!hasBluetoothPermissions()) {
            onStatus("Bluetooth permission is required")
            return
        }
        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null) {
            onStatus("This phone has no Bluetooth adapter")
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            onStatus("Turn on Bluetooth, then try again")
            return
        }

        userRequestedDisconnect = false
        reconnectAttempts = 0
        startScan()
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (!hasBluetoothPermissions()) {
            onStatus("Bluetooth permission is required")
            return
        }
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            onStatus("Turn on Bluetooth, then try again")
            return
        }
        onStatus("Looking for Sole Precision wearable…")
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(listOf(filter), settings, scanCallback)
        mainHandler.removeCallbacks(scanTimeout)
        mainHandler.postDelayed(scanTimeout, SCAN_TIMEOUT_MS)
    }

    private fun scheduleReconnectIfWanted() {
        if (userRequestedDisconnect) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            onStatus("Wearable reconnect failed; connect manually when it is available")
            return
        }
        reconnectAttempts += 1
        val delayMs = RECONNECT_BASE_DELAY_MS shl (reconnectAttempts - 1)
        onStatus("Reconnecting to wearable (attempt $reconnectAttempts)…")
        mainHandler.postDelayed(
            {
                if (!userRequestedDisconnect && gatt == null) {
                    startScan()
                }
            },
            delayMs,
        )
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        userRequestedDisconnect = true
        mainHandler.removeCallbacks(scanTimeout)
        mainHandler.removeCallbacks(connectTimeout)
        clearWriteQueue()
        if (hasBluetoothPermissions()) {
            stopScanning()
            gatt?.disconnect()
            gatt?.close()
        }
        gatt = null
        writeCharacteristic = null
        onStatus("Wearable disconnected")
    }

    override fun send(instruction: NavigationInstruction): Boolean {
        val packet = NavigationPacketEncoder.encode(instruction, sequence++)
        return enqueuePacket(packet)
    }

    override fun send(command: DeviceTestCommand): Boolean {
        val packet = DeviceTestPacketEncoder.encode(command, sequence++)
        return enqueuePacket(packet)
    }

    override fun sendRaw(packet: ByteArray): Boolean = enqueuePacket(packet)

    private fun enqueuePacket(packet: ByteArray): Boolean {
        onPacketPrepared(NavigationPacketEncoder.toHex(packet))

        if (!hasBluetoothPermissions()) return false
        if (gatt == null || writeCharacteristic == null) return false

        // Drop the oldest waiting packets rather than delivering stale guidance late.
        while (writeQueue.size >= MAX_QUEUED_PACKETS) {
            writeQueue.pollFirst()
        }
        writeQueue.addLast(packet)
        drainWriteQueue()
        return true
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun drainWriteQueue() {
        if (writeInFlight) return
        val currentGatt = gatt ?: return clearWriteQueue()
        val characteristic = writeCharacteristic ?: return clearWriteQueue()
        val packet = writeQueue.pollFirst() ?: return
        if (!hasBluetoothPermissions()) return clearWriteQueue()

        val writeType = if (
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        ) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            currentGatt.writeCharacteristic(characteristic, packet, writeType) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.writeType = writeType
            characteristic.value = packet
            currentGatt.writeCharacteristic(characteristic)
        }
        if (accepted) {
            writeInFlight = true
        } else {
            onStatus("Wearable write was rejected; command dropped")
            // Try the next packet so one rejection does not stall the queue.
            mainHandler.post { drainWriteQueue() }
        }
    }

    private fun clearWriteQueue() {
        writeQueue.clear()
        writeInFlight = false
    }

    @SuppressLint("MissingPermission")
    private fun closeGattQuietly() {
        if (hasBluetoothPermissions()) {
            gatt?.disconnect()
            gatt?.close()
        }
        gatt = null
        writeCharacteristic = null
        clearWriteQueue()
    }

    override fun close() = disconnect()

    private fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.BLUETOOTH_SCAN,
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        if (hasBluetoothPermissions()) {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        }
    }

    companion object {
        private const val SCAN_TIMEOUT_MS = 10_000L
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val RECONNECT_BASE_DELAY_MS = 2_000L
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val MAX_QUEUED_PACKETS = 8
        private const val REQUESTED_MTU = 64

        // These UUIDs must be copied exactly into the ESP32 firmware.
        val SERVICE_UUID: UUID = UUID.fromString("5c10a001-9c1b-4c7f-9c6a-43d42f2d1000")
        val COMMAND_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("5c10a002-9c1b-4c7f-9c6a-43d42f2d1000")
    }
}
