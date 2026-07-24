package com.csust.soleprecision

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.csust.soleprecision.accessibility.ScreenNarrator
import com.csust.soleprecision.bluetooth.BleWearableTransport
import com.csust.soleprecision.bluetooth.MockWearableTransport
import com.csust.soleprecision.bluetooth.WearableTransport
import com.csust.soleprecision.device.DeviceTestCommand
import com.csust.soleprecision.device.HexPacketCodec
import com.csust.soleprecision.navigation.AmapInputTipsController
import com.csust.soleprecision.navigation.AmapLocationController
import com.csust.soleprecision.navigation.AmapNavigationController
import com.csust.soleprecision.navigation.AmapPlaceSearchController
import com.csust.soleprecision.navigation.DestinationHistoryStore
import com.csust.soleprecision.navigation.DestinationSearchState
import com.csust.soleprecision.navigation.DestinationSuggestion
import com.csust.soleprecision.navigation.Maneuver
import com.csust.soleprecision.navigation.NavigationInstruction
import com.csust.soleprecision.navigation.PlaceCandidate
import com.csust.soleprecision.navigation.RouteSummary
import com.csust.soleprecision.navigation.UserLocation
import com.csust.soleprecision.navigation.WalkingRouteStep
import com.csust.soleprecision.settings.GuidanceMode
import com.csust.soleprecision.settings.UserPreferences
import com.csust.soleprecision.settings.UserPreferencesStore
import com.csust.soleprecision.ui.SolePrecisionApp
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hasMapConsent by mutableStateOf(false)
    private var navigationStatus by mutableStateOf("Navigation is starting")
    private var locationStatus by mutableStateOf("Location permission is required")
    private var wearableStatus by mutableStateOf("Wearable not connected")
    private var deviceCommandStatus by mutableStateOf("No device command sent")
    private var currentInstruction by mutableStateOf<NavigationInstruction?>(null)
    private var lastPacketHex by mutableStateOf("No packet prepared yet")
    private var currentLocation by mutableStateOf<UserLocation?>(null)
    private var destinationSearchState by mutableStateOf<DestinationSearchState>(
        DestinationSearchState.Idle,
    )
    private var destinationSuggestions by mutableStateOf<List<DestinationSuggestion>>(emptyList())
    private var routeSummary by mutableStateOf<RouteSummary?>(null)
    private var routeOptions by mutableStateOf<List<RouteSummary>>(emptyList())
    private var recentDestinations by mutableStateOf<List<PlaceCandidate>>(emptyList())
    private var userPreferences by mutableStateOf(UserPreferences())
    private var useMockHardware by mutableStateOf(false)
    private var guidancePaused = false
    private var mockRoutePrepared = false
    private var pendingRouteDestination: PlaceCandidate? = null
    private var activeDestination: PlaceCandidate? = null

    private lateinit var navigationController: AmapNavigationController
    private lateinit var locationController: AmapLocationController
    private lateinit var placeSearchController: AmapPlaceSearchController
    private lateinit var inputTipsController: AmapInputTipsController
    private lateinit var bleWearableTransport: BleWearableTransport
    private lateinit var mockWearableTransport: MockWearableTransport
    private lateinit var destinationHistoryStore: DestinationHistoryStore
    private lateinit var userPreferencesStore: UserPreferencesStore
    private lateinit var screenNarrator: ScreenNarrator
    private var speechRecognizer: SpeechRecognizer? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val denied = results.filterValues { granted -> !granted }.keys
        if (hasLocationPermission()) {
            initializeMapServices()
        } else {
            locationStatus = "Location permission was not granted"
            navigationStatus = "Some features need permission: ${denied.joinToString()}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hasMapConsent = getPreferences(MODE_PRIVATE).getBoolean(MAP_CONSENT_KEY, false)
        destinationHistoryStore = DestinationHistoryStore(this)
        recentDestinations = destinationHistoryStore.load()
        userPreferencesStore = UserPreferencesStore(this)
        userPreferences = userPreferencesStore.load()
        screenNarrator = ScreenNarrator(this)
        screenNarrator.setLanguage(userPreferences.language.languageTag)
        useMockHardware = getPreferences(MODE_PRIVATE).getBoolean(
            MOCK_HARDWARE_KEY,
            isProbablyEmulator(),
        )

        bleWearableTransport = BleWearableTransport(
            context = this,
            onStatus = { wearableStatus = it },
            onPacketPrepared = { lastPacketHex = it },
        )
        mockWearableTransport = MockWearableTransport(
            onStatus = { wearableStatus = it },
            onPacketPrepared = { lastPacketHex = it },
            onEvent = { deviceCommandStatus = it },
        )
        if (useMockHardware) {
            mockWearableTransport.connect()
            activateMockLocation()
        }
        navigationController = AmapNavigationController(
            context = this,
            onInstruction = ::handleInstruction,
            onStatus = { navigationStatus = it },
            onRouteReady = { routeSummary = it },
            onRoutesReady = { routeOptions = it },
        )
        locationController = AmapLocationController(
            context = this,
            onLocation = ::handleLocation,
            onStatus = { locationStatus = it },
        )
        placeSearchController = AmapPlaceSearchController(this)
        inputTipsController = AmapInputTipsController(this)
        initializeSpeechRecognizer()

        if (hasMapConsent && hasLocationPermission()) {
            initializeMapServices()
        } else if (hasMapConsent) {
            navigationStatus = "Location permission is required for AMap guidance"
        }

        setContent {
            SolePrecisionApp(
                hasMapConsent = hasMapConsent,
                navigationStatus = navigationStatus,
                locationStatus = locationStatus,
                wearableStatus = wearableStatus,
                deviceCommandStatus = deviceCommandStatus,
                instruction = currentInstruction,
                lastPacketHex = lastPacketHex,
                currentLocation = currentLocation,
                destinationSearchState = destinationSearchState,
                destinationSuggestions = destinationSuggestions,
                routeSummary = routeSummary,
                routeOptions = routeOptions,
                recentDestinations = recentDestinations,
                userPreferences = userPreferences,
                useMockHardware = useMockHardware,
                onAcceptMapPrivacy = ::acceptMapPrivacy,
                onRequestPermissions = ::requestRuntimePermissions,
                onConnectWearable = ::connectWearable,
                onDisconnectWearable = ::disconnectWearable,
                onSetMockHardware = ::setMockHardwareEnabled,
                onStartWalkingRoute = navigationController::calculateWalkingRoute,
                onStopNavigation = ::stopNavigation,
                onDemoInstruction = ::sendDemoInstruction,
                onSendDeviceCommand = ::sendDeviceCommand,
                onSendRawPacket = ::sendRawPacket,
                onSpeakDestination = ::startVoiceDestination,
                onSearchDestination = ::searchDestination,
                onRequestDestinationSuggestions = ::requestDestinationSuggestions,
                onSelectDestinationSuggestion = ::selectDestinationSuggestion,
                onClearDestinationSearch = ::clearDestinationSearch,
                onPlanRoute = ::planRoute,
                onSelectRoute = ::selectRoute,
                onStartPlannedRoute = ::startPlannedRoute,
                onRepeatInstruction = ::repeatCurrentInstruction,
                onPauseGuidance = ::pauseGuidance,
                onResumeGuidance = ::resumeGuidance,
                onSavePreferences = ::savePreferences,
                onClearHistory = ::clearDestinationHistory,
                onAnnounceScreen = screenNarrator::speak,
            )
        }
    }

    private fun initializeMapServices() {
        navigationStatus = "Initializing AMap…"
        navigationController.initializeAfterConsent()
        placeSearchController.initializeAfterConsent()
        placeSearchController.setLanguage(userPreferences.language.languageTag)
        inputTipsController.initializeAfterConsent()
        navigationController.setVoiceEnabled(userPreferences.guidanceMode != GuidanceMode.HAPTIC_ONLY)
        if (useMockHardware) {
            activateMockLocation()
        } else {
            locationController.initializeAfterConsent()
            locationController.refresh()
        }
    }

    private fun acceptMapPrivacy() {
        getPreferences(MODE_PRIVATE)
            .edit()
            .putBoolean(MAP_CONSENT_KEY, true)
            .apply()
        hasMapConsent = true
        requestRuntimePermissions()
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    private fun startVoiceDestination() {
        placeSearchController.cancel()
        inputTipsController.cancel()
        destinationSuggestions = emptyList()
        speechRecognizer?.cancel()
        playListeningTone()
        if (useMockHardware) {
            destinationSearchState = DestinationSearchState.Listening
            mainHandler.postDelayed(
                {
                    if (
                        useMockHardware &&
                        destinationSearchState == DestinationSearchState.Listening
                    ) {
                        destinationSearchState = DestinationSearchState.Results(
                            query = "Mock voice: university",
                            places = MOCK_VOICE_DESTINATIONS,
                        )
                    }
                },
                MOCK_VOICE_DELAY_MS,
            )
            return
        }
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            destinationSearchState = DestinationSearchState.Error(
                "Microphone permission is required. Allow it, then tap the microphone again.",
            )
            requestRuntimePermissions()
            return
        }
        val recognizer = speechRecognizer
        if (recognizer == null) {
            destinationSearchState = DestinationSearchState.Error(
                "No speech recognition service is available on this device.",
            )
            return
        }
        destinationSearchState = DestinationSearchState.Listening
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your destination")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, userPreferences.language.languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer.cancel()
        recognizer.startListening(intent)
    }

    private fun initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        destinationSearchState = DestinationSearchState.Listening
                    }

                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit

                    override fun onError(error: Int) {
                        if (destinationSearchState != DestinationSearchState.Listening) return
                        destinationSearchState = DestinationSearchState.Error(
                            when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH,
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                                -> "No destination was recognized. Tap the microphone and try again."
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                    "Microphone permission is required for voice destination."
                                SpeechRecognizer.ERROR_NETWORK,
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                                -> "Voice recognition needs a working network connection."
                                else -> "Voice recognition stopped. Tap the microphone to try again."
                            },
                        )
                    }

                    override fun onResults(results: Bundle?) {
                        if (destinationSearchState != DestinationSearchState.Listening) return
                        val spokenText = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                        if (spokenText.isNullOrBlank()) {
                            destinationSearchState = DestinationSearchState.Error(
                                "No destination was recognized. Tap the microphone and try again.",
                            )
                        } else {
                            searchDestination(spokenText)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                },
            )
        }
    }

    private fun clearDestinationSearch() {
        speechRecognizer?.cancel()
        placeSearchController.cancel()
        inputTipsController.cancel()
        destinationSearchState = DestinationSearchState.Idle
        destinationSuggestions = emptyList()
    }

    private fun playListeningTone() {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 160)
        mainHandler.postDelayed({ tone.release() }, 240)
    }

    private fun searchDestination(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            destinationSearchState = DestinationSearchState.Error(
                "Say or enter a destination first.",
            )
            return
        }
        destinationSuggestions = emptyList()
        inputTipsController.cancel()
        destinationSearchState = DestinationSearchState.Searching(cleanQuery)
        placeSearchController.search(
            keyword = cleanQuery,
            cityCode = currentLocation?.cityCode,
            currentLocation = currentLocation,
        ) { result ->
            result.onSuccess { places ->
                destinationSearchState = DestinationSearchState.Results(cleanQuery, places)
            }.onFailure { error ->
                destinationSearchState = DestinationSearchState.Error(
                    error.message ?: "Destination search failed",
                )
            }
        }
    }

    private fun requestDestinationSuggestions(query: String) {
        if (!hasMapConsent) return
        inputTipsController.request(
            keyword = query,
            cityCode = currentLocation?.cityCode,
            currentLocation = currentLocation,
        ) { suggestions ->
            destinationSuggestions = suggestions
        }
    }

    private fun selectDestinationSuggestion(suggestion: DestinationSuggestion) {
        destinationSuggestions = emptyList()
        inputTipsController.cancel()
        if (suggestion.poiId.isBlank()) {
            searchDestination(suggestion.name)
            return
        }
        destinationSearchState = DestinationSearchState.Searching(suggestion.name)
        placeSearchController.searchById(suggestion.poiId) { result ->
            result.onSuccess { place ->
                destinationSearchState = DestinationSearchState.Results(
                    query = suggestion.name,
                    places = listOf(place),
                )
            }.onFailure {
                searchDestination(suggestion.name)
            }
        }
    }

    private fun planRoute(destination: PlaceCandidate) {
        activeDestination = destination
        pendingRouteDestination = destination
        routeSummary = null
        routeOptions = emptyList()
        currentInstruction = null
        if (useMockHardware) {
            pendingRouteDestination = null
            prepareMockRoute(destination)
            return
        }
        val location = currentLocation
        if (location == null) {
            locationStatus = "Finding current location for the route…"
            locationController.refresh()
        } else {
            requestRoute(location, destination)
        }
    }

    private fun handleLocation(location: UserLocation) {
        currentLocation = location
        pendingRouteDestination?.let { destination ->
            pendingRouteDestination = null
            requestRoute(location, destination)
        }
    }

    private fun requestRoute(location: UserLocation, destination: PlaceCandidate) {
        pendingRouteDestination = null
        navigationController.planWalkingRoute(
            startLatitude = location.latitude,
            startLongitude = location.longitude,
            destination = destination,
        )
    }

    private fun startPlannedRoute(): Boolean {
        guidancePaused = false
        val started = if (useMockHardware) {
            startMockRoute()
        } else {
            navigationController.startPlannedRoute()
        }
        if (started) {
            activeDestination?.let {
                recentDestinations = destinationHistoryStore.add(it)
            }
        }
        return started
    }

    private fun selectRoute(routeId: Int): Boolean {
        if (useMockHardware) {
            val selected = routeOptions.firstOrNull { it.routeId == routeId } ?: return false
            routeSummary = selected
            return true
        }
        return navigationController.selectRoute(routeId)
    }

    private fun pauseGuidance() {
        guidancePaused = true
        navigationController.setVoiceEnabled(false)
        navigationStatus =
            "Phone route guidance paused; the current app does not verify nearby obstacles"
    }

    private fun resumeGuidance() {
        guidancePaused = false
        navigationController.setVoiceEnabled(userPreferences.guidanceMode != GuidanceMode.HAPTIC_ONLY)
        repeatCurrentInstruction()
        navigationStatus = "Navigation guidance resumed"
    }

    private fun stopNavigation() {
        guidancePaused = false
        navigationController.stop()
        currentInstruction = null
        routeSummary = null
        routeOptions = emptyList()
        activeDestination = null
        pendingRouteDestination = null
        mockRoutePrepared = false
    }

    private fun savePreferences(value: UserPreferences) {
        userPreferences = value
        userPreferencesStore.save(value)
        screenNarrator.setLanguage(value.language.languageTag)
        placeSearchController.setLanguage(value.language.languageTag)
        navigationController.setVoiceEnabled(
            !guidancePaused && value.guidanceMode != GuidanceMode.HAPTIC_ONLY,
        )
    }

    private fun clearDestinationHistory() {
        destinationHistoryStore.clear()
        recentDestinations = emptyList()
    }

    private fun sendDemoInstruction(maneuver: Maneuver, distanceMeters: Int) {
        handleInstruction(
            NavigationInstruction(
                maneuver = maneuver,
                distanceMeters = if (maneuver == Maneuver.ARRIVED) 0 else distanceMeters,
                message = if (maneuver == Maneuver.ARRIVED) {
                    maneuver.spokenLabel
                } else {
                    "${maneuver.spokenLabel} in $distanceMeters metres"
                },
                source = NavigationInstruction.Source.DEMO,
            ),
        )
    }

    private fun sendDeviceCommand(command: DeviceTestCommand) {
        val sent = activeWearableTransport.send(command)
        if (!useMockHardware || !sent) {
            deviceCommandStatus = if (sent) {
                "${command.description()} sent to wearable"
            } else {
                "${command.description()} prepared; wearable offline"
            }
        }
        if (sent && useMockHardware) {
            screenNarrator.speak("Simulation. ${command.description()}.")
        }
    }

    private fun sendRawPacket(input: String) {
        HexPacketCodec.parse(input)
            .onSuccess { packet ->
                val sent = activeWearableTransport.sendRaw(packet)
                if (!useMockHardware || !sent) {
                    deviceCommandStatus = if (sent) {
                        "Raw ${packet.size}-byte packet sent"
                    } else {
                        "Raw ${packet.size}-byte packet prepared; wearable offline"
                    }
                }
            }
            .onFailure { error ->
                deviceCommandStatus = error.message ?: "Invalid raw packet"
            }
    }

    private fun handleInstruction(instruction: NavigationInstruction) {
        currentInstruction = instruction
        if (guidancePaused) return

        val sent = activeWearableTransport.send(instruction)
        navigationStatus = if (sent) {
            if (useMockHardware) {
                "${instruction.message}; executed by simulated backpack"
            } else {
                "${instruction.message}; sent to wearable"
            }
        } else {
            "${instruction.message}; packet prepared, wearable offline"
        }
    }

    private fun activateMockLocation() {
        val location = UserLocation(
            latitude = MOCK_LATITUDE,
            longitude = MOCK_LONGITUDE,
            address = "Simulated GPS position in Changsha",
            cityCode = "0731",
        )
        currentLocation = location
        locationStatus = "Using simulated upper-controller GPS in Changsha"
        pendingRouteDestination?.let { destination ->
            pendingRouteDestination = null
            prepareMockRoute(destination)
        }
    }

    private fun prepareMockRoute(destination: PlaceCandidate) {
        val stableVariation = destination.name.hashCode() and 0x7fffffff
        val distanceMeters = 450 + stableVariation % 550
        routeSummary = RouteSummary(
            distanceMeters = distanceMeters,
            durationSeconds = (distanceMeters / 1.1).toInt(),
            steps = listOf(
                WalkingRouteStep(
                    maneuver = Maneuver.STRAIGHT,
                    distanceMeters = 180,
                    durationSeconds = 150,
                    roadName = "the starting walkway",
                ),
                WalkingRouteStep(
                    maneuver = Maneuver.CROSSWALK,
                    distanceMeters = 25,
                    durationSeconds = 45,
                    roadName = "the mapped crossing",
                    mappedTrafficLightCount = 1,
                ),
                WalkingRouteStep(
                    maneuver = Maneuver.RIGHT,
                    distanceMeters = (distanceMeters - 205).coerceAtLeast(80),
                    durationSeconds = 240,
                    roadName = "the destination approach",
                ),
            ),
            mappedTrafficLightCount = 1,
        )
        routeOptions = listOfNotNull(routeSummary)
        mockRoutePrepared = true
        navigationStatus =
            "Simulated route ready; it does not represent real streets or the selected destination"
    }

    private fun startMockRoute(): Boolean {
        if (!mockRoutePrepared) {
            navigationStatus = "Prepare a simulated route first"
            return false
        }
        handleInstruction(
            NavigationInstruction(
                maneuver = Maneuver.STRAIGHT,
                distanceMeters = 80,
                message = "Simulation: continue straight for 80 metres",
                source = NavigationInstruction.Source.DEMO,
            ),
        )
        return true
    }

    private fun repeatCurrentInstruction() {
        val instruction = currentInstruction ?: return
        if (useMockHardware) {
            handleInstruction(instruction)
            screenNarrator.speak(instruction.message)
        } else {
            navigationController.repeatCurrentInstruction()
        }
    }

    private fun DeviceTestCommand.description(): String = when (this) {
        is DeviceTestCommand.Audio -> "${side.displayName} audio ${cue.displayName}"
        is DeviceTestCommand.Vibration -> "${side.displayName} ${pattern.displayName.lowercase(Locale.ROOT)} vibration"
        DeviceTestCommand.StopAll -> "Stop all outputs"
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        screenNarrator.close()
        locationController.close()
        placeSearchController.cancel()
        inputTipsController.cancel()
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        navigationController.close()
        bleWearableTransport.close()
        mockWearableTransport.close()
        super.onDestroy()
    }

    private val activeWearableTransport: WearableTransport
        get() = if (useMockHardware) mockWearableTransport else bleWearableTransport

    private fun connectWearable() {
        activeWearableTransport.connect()
    }

    private fun disconnectWearable() {
        activeWearableTransport.disconnect()
    }

    private fun setMockHardwareEnabled(enabled: Boolean) {
        if (enabled == useMockHardware) return

        activeWearableTransport.disconnect()
        useMockHardware = enabled
        getPreferences(MODE_PRIVATE)
            .edit()
            .putBoolean(MOCK_HARDWARE_KEY, enabled)
            .apply()

        if (enabled) {
            mockWearableTransport.connect()
            activateMockLocation()
        } else {
            currentLocation = null
            mockRoutePrepared = false
            wearableStatus = "Real hardware selected; connect when its interface is available"
            deviceCommandStatus = "Simulator disabled"
            if (hasMapConsent && hasLocationPermission()) {
                locationController.initializeAfterConsent()
                locationController.refresh()
            } else {
                locationStatus = "Location permission is required"
            }
        }
    }

    private fun isProbablyEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk_gphone", ignoreCase = true) ||
            Build.DEVICE.startsWith("emu", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true)

    private companion object {
        const val MAP_CONSENT_KEY = "amap_privacy_consent"
        const val MOCK_HARDWARE_KEY = "mock_hardware_enabled"
        const val MOCK_LATITUDE = 28.2282
        const val MOCK_LONGITUDE = 112.9388
        const val MOCK_VOICE_DELAY_MS = 900L

        val MOCK_VOICE_DESTINATIONS = listOf(
            PlaceCandidate(
                id = "mock-voice-1",
                name = "Central South University",
                address = "Lushan South Road",
                area = "Yuelu District, Changsha",
                latitude = 28.1714,
                longitude = 112.9252,
            ),
            PlaceCandidate(
                id = "mock-voice-2",
                name = "Hunan University",
                address = "Lushan South Road",
                area = "Yuelu District, Changsha",
                latitude = 28.1817,
                longitude = 112.9443,
            ),
            PlaceCandidate(
                id = "mock-voice-3",
                name = "Changsha University",
                address = "Hongshan Road",
                area = "Kaifu District, Changsha",
                latitude = 28.2602,
                longitude = 113.0386,
            ),
        )
    }
}
