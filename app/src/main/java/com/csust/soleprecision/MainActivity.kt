package com.csust.soleprecision

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.csust.soleprecision.bluetooth.BleWearableTransport
import com.csust.soleprecision.navigation.AmapNavigationController
import com.csust.soleprecision.navigation.Maneuver
import com.csust.soleprecision.navigation.NavigationInstruction
import com.csust.soleprecision.ui.SolePrecisionApp

class MainActivity : ComponentActivity() {
    private var hasMapConsent by mutableStateOf(false)
    private var navigationStatus by mutableStateOf("Demo mode is ready")
    private var wearableStatus by mutableStateOf("Wearable not connected")
    private var currentInstruction by mutableStateOf<NavigationInstruction?>(null)
    private var lastPacketHex by mutableStateOf("No packet prepared yet")

    private lateinit var navigationController: AmapNavigationController
    private lateinit var wearableTransport: BleWearableTransport

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val denied = results.filterValues { granted -> !granted }.keys
        if (denied.isEmpty()) {
            navigationStatus = "Permissions granted; route guidance is ready"
        } else {
            navigationStatus = "Some features need permission: ${denied.joinToString()}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hasMapConsent = getPreferences(MODE_PRIVATE).getBoolean(MAP_CONSENT_KEY, false)
        wearableTransport = BleWearableTransport(
            context = this,
            onStatus = { wearableStatus = it },
            onPacketPrepared = { lastPacketHex = it },
        )
        navigationController = AmapNavigationController(
            context = this,
            onInstruction = ::handleInstruction,
            onStatus = { navigationStatus = it },
        )

        if (hasMapConsent) {
            navigationController.initializeAfterConsent()
        }

        setContent {
            SolePrecisionApp(
                hasMapConsent = hasMapConsent,
                navigationStatus = navigationStatus,
                wearableStatus = wearableStatus,
                instruction = currentInstruction,
                lastPacketHex = lastPacketHex,
                onAcceptMapPrivacy = ::acceptMapPrivacy,
                onRequestPermissions = ::requestRuntimePermissions,
                onConnectWearable = wearableTransport::connect,
                onDisconnectWearable = wearableTransport::disconnect,
                onStartWalkingRoute = navigationController::calculateWalkingRoute,
                onStopNavigation = navigationController::stop,
                onDemoInstruction = ::sendDemoInstruction,
            )
        }
    }

    private fun acceptMapPrivacy() {
        getPreferences(MODE_PRIVATE)
            .edit()
            .putBoolean(MAP_CONSENT_KEY, true)
            .apply()
        hasMapConsent = true
        navigationController.initializeAfterConsent()
        requestRuntimePermissions()
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun sendDemoInstruction(maneuver: Maneuver) {
        handleInstruction(
            NavigationInstruction(
                maneuver = maneuver,
                distanceMeters = if (maneuver == Maneuver.ARRIVED) 0 else 20,
                message = if (maneuver == Maneuver.ARRIVED) {
                    maneuver.spokenLabel
                } else {
                    "${maneuver.spokenLabel} in 20 metres"
                },
                source = NavigationInstruction.Source.DEMO,
            ),
        )
    }

    private fun handleInstruction(instruction: NavigationInstruction) {
        currentInstruction = instruction
        val sent = wearableTransport.send(instruction)
        navigationStatus = if (sent) {
            "${instruction.message}; sent to wearable"
        } else {
            "${instruction.message}; packet prepared, wearable offline"
        }
    }

    override fun onDestroy() {
        navigationController.close()
        wearableTransport.close()
        super.onDestroy()
    }

    private companion object {
        const val MAP_CONSENT_KEY = "amap_privacy_consent"
    }
}
