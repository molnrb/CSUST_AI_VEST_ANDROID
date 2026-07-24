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
import java.util.UUID

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

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            mainHandler.removeCallbacks(scanTimeout)
            adapter?.bluetoothLeScanner?.stopScan(this)
            onStatus("Found wearable; connecting…")
            gatt = result.device.connectGatt(
                appContext,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE,
            )
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

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    onStatus("Wearable connected; discovering controls…")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    writeCharacteristic = null
                    onStatus("Wearable disconnected")
                    gatt.close()
                    if (this@BleWearableTransport.gatt === gatt) {
                        this@BleWearableTransport.gatt = null
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onStatus("Wearable service discovery failed")
                return
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

        onStatus("Looking for Sole Precision wearable…")
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        bluetoothAdapter.bluetoothLeScanner.startScan(listOf(filter), settings, scanCallback)
        mainHandler.postDelayed(scanTimeout, SCAN_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        mainHandler.removeCallbacks(scanTimeout)
        if (hasBluetoothPermissions()) {
            stopScanning()
            gatt?.disconnect()
            gatt?.close()
        }
        gatt = null
        writeCharacteristic = null
        onStatus("Wearable disconnected")
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    override fun send(instruction: NavigationInstruction): Boolean {
        val packet = NavigationPacketEncoder.encode(instruction, sequence++)
        return writePacket(packet)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    override fun send(command: DeviceTestCommand): Boolean {
        val packet = DeviceTestPacketEncoder.encode(command, sequence++)
        return writePacket(packet)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    override fun sendRaw(packet: ByteArray): Boolean = writePacket(packet)

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun writePacket(packet: ByteArray): Boolean {
        onPacketPrepared(NavigationPacketEncoder.toHex(packet))

        if (!hasBluetoothPermissions()) return false
        val currentGatt = gatt ?: return false
        val characteristic = writeCharacteristic ?: return false
        val writeType = if (
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        ) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            currentGatt.writeCharacteristic(characteristic, packet, writeType) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.writeType = writeType
            characteristic.value = packet
            currentGatt.writeCharacteristic(characteristic)
        }
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

        // These UUIDs must be copied exactly into the ESP32 firmware.
        val SERVICE_UUID: UUID = UUID.fromString("5c10a001-9c1b-4c7f-9c6a-43d42f2d1000")
        val COMMAND_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("5c10a002-9c1b-4c7f-9c6a-43d42f2d1000")
    }
}
