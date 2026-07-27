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
import com.csust.soleprecision.accessibility.NarrationPriority
import com.csust.soleprecision.accessibility.ScreenNarrator
import com.csust.soleprecision.bluetooth.BleWearableTransport
import com.csust.soleprecision.bluetooth.MockWearableTransport
import com.csust.soleprecision.bluetooth.WearableTransport
import com.csust.soleprecision.device.DeviceTestCommand
import com.csust.soleprecision.device.HexPacketCodec
import com.csust.soleprecision.feedback.HapticGuidance
import com.csust.soleprecision.i18n.GuidancePhrases
import com.csust.soleprecision.i18n.Phrases
import com.csust.soleprecision.navigation.AmapInputTipsController
import com.csust.soleprecision.navigation.AmapLocationController
import com.csust.soleprecision.navigation.AmapNavigationController
import com.csust.soleprecision.navigation.AmapNearbySearchController
import com.csust.soleprecision.navigation.AmapPlaceSearchController
import com.csust.soleprecision.navigation.AmapReverseGeocodeController
import com.csust.soleprecision.navigation.AmapWeatherController
import com.csust.soleprecision.navigation.CueStage
import com.csust.soleprecision.navigation.DestinationHistoryStore
import com.csust.soleprecision.navigation.DestinationSearchState
import com.csust.soleprecision.navigation.DestinationSuggestion
import com.csust.soleprecision.navigation.FavoriteDestinationsStore
import com.csust.soleprecision.navigation.LocalWeather
import com.csust.soleprecision.navigation.Maneuver
import com.csust.soleprecision.navigation.NavigationInstruction
import com.csust.soleprecision.navigation.NearbyCategory
import com.csust.soleprecision.navigation.PlaceCandidate
import com.csust.soleprecision.navigation.RouteSummary
import com.csust.soleprecision.navigation.UserLocation
import com.csust.soleprecision.navigation.WalkingRouteStep
import com.csust.soleprecision.device.DeviceTestPacketEncoder
import com.csust.soleprecision.device.OutputSide
import com.csust.soleprecision.device.VibrationPattern
import com.csust.soleprecision.settings.GuidanceMode
import com.csust.soleprecision.settings.SpeechDetail
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
    private var favoriteDestinations by mutableStateOf<List<PlaceCandidate>>(emptyList())
    private var userPreferences by mutableStateOf(UserPreferences())
    private var useMockHardware by mutableStateOf(false)
    private var speechUnavailable by mutableStateOf(false)
    private var whereAmIText by mutableStateOf<String?>(null)
    private var nearbyResults by mutableStateOf<List<PlaceCandidate>?>(null)
    private var nearbyStatus by mutableStateOf("")
    private var routeWeather by mutableStateOf<LocalWeather?>(null)
    private var guidancePaused = false
    private var mockRoutePrepared = false
    private var pendingRouteDestination: PlaceCandidate? = null
    private var activeDestination: PlaceCandidate? = null

    private lateinit var navigationController: AmapNavigationController
    private lateinit var locationController: AmapLocationController
    private lateinit var placeSearchController: AmapPlaceSearchController
    private lateinit var inputTipsController: AmapInputTipsController
    private lateinit var nearbySearchController: AmapNearbySearchController
    private lateinit var reverseGeocodeController: AmapReverseGeocodeController
    private lateinit var weatherController: AmapWeatherController
    private lateinit var bleWearableTransport: BleWearableTransport
    private lateinit var mockWearableTransport: MockWearableTransport
    private lateinit var destinationHistoryStore: DestinationHistoryStore
    private lateinit var favoriteDestinationsStore: FavoriteDestinationsStore
    private lateinit var userPreferencesStore: UserPreferencesStore
    private lateinit var screenNarrator: ScreenNarrator
    private lateinit var hapticGuidance: HapticGuidance
    private var speechRecognizer: SpeechRecognizer? = null

    private val phrases: Phrases
        get() = Phrases.forLanguage(userPreferences.language)

    private val guidancePhrases: GuidancePhrases
        get() = GuidancePhrases.forLanguage(userPreferences.language)

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
        favoriteDestinationsStore = FavoriteDestinationsStore(this)
        favoriteDestinations = favoriteDestinationsStore.load()
        userPreferencesStore = UserPreferencesStore(this)
        userPreferences = userPreferencesStore.load()
        screenNarrator = ScreenNarrator(this) { message ->
            speechUnavailable = true
            navigationStatus = message
        }
        screenNarrator.setLanguage(userPreferences.language.languageTag)
        screenNarrator.setVolume(userPreferences.speakerVolume)
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
        hapticGuidance = HapticGuidance(this)
        reverseGeocodeController = AmapReverseGeocodeController(this)
        navigationController = AmapNavigationController(
            context = this,
            onInstruction = ::handleInstruction,
            onStatus = { navigationStatus = it },
            onRouteReady = { routeSummary = it },
            onRoutesReady = { routeOptions = it },
            landmarkResolver = ::resolveLandmark,
        )
        locationController = AmapLocationController(
            context = this,
            onLocation = ::handleLocation,
            onStatus = { locationStatus = it },
        )
        placeSearchController = AmapPlaceSearchController(this)
        inputTipsController = AmapInputTipsController(this)
        nearbySearchController = AmapNearbySearchController(this)
        weatherController = AmapWeatherController(this)
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
                favoriteDestinations = favoriteDestinations,
                userPreferences = userPreferences,
                useMockHardware = useMockHardware,
                speechUnavailable = speechUnavailable,
                whereAmIText = whereAmIText,
                nearbyResults = nearbyResults,
                nearbyStatus = nearbyStatus,
                routeWeather = routeWeather,
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
                onAnnounceScreen = ::announce,
                onAnnounceActions = ::announceActions,
                onWhereAmI = ::requestWhereAmI,
                onSaveFavorite = ::saveFavorite,
                onRemoveFavorite = ::removeFavorite,
                onNearbySearch = ::searchNearby,
                onClearNearby = ::clearNearby,
            )
        }
    }

    private fun announce(message: String) {
        screenNarrator.speak(message, NarrationPriority.HIGH)
    }

    /**
     * Action lists are queued behind the screen context so the user hears
     * "where am I and what is here" before "what can I do".
     */
    private fun announceActions(message: String) {
        screenNarrator.speak(message, NarrationPriority.NORMAL)
    }

    /** One-shot nearest-place lookup used to anchor turn cues to a landmark. */
    private fun resolveLandmark(
        latitude: Double,
        longitude: Double,
        onResolved: (String) -> Unit,
    ) {
        if (useMockHardware) {
            onResolved("")
            return
        }
        reverseGeocodeController.resolve(latitude, longitude) { result ->
            onResolved(result.getOrNull()?.nearestPoiName.orEmpty())
        }
    }

    private fun initializeMapServices() {
        navigationStatus = "Initializing AMap…"
        navigationController.initializeAfterConsent()
        placeSearchController.initializeAfterConsent()
        placeSearchController.setLanguage(userPreferences.language.languageTag)
        inputTipsController.initializeAfterConsent()
        nearbySearchController.initializeAfterConsent()
        weatherController.initializeAfterConsent()
        applyVoiceOwnership()
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
            destinationSearchState = DestinationSearchState.Error(phrases.micPermissionNeeded)
            requestRuntimePermissions()
            return
        }
        val recognizer = speechRecognizer
        if (recognizer == null) {
            destinationSearchState = DestinationSearchState.Error(phrases.noSpeechService)
            return
        }
        destinationSearchState = DestinationSearchState.Listening
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, phrases.sayDestinationPrompt)
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
                                -> phrases.noDestinationRecognized
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                    phrases.micPermissionForVoice
                                SpeechRecognizer.ERROR_NETWORK,
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                                -> phrases.voiceNeedsNetwork
                                else -> phrases.voiceStopped
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
                                phrases.noDestinationRecognized,
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
                phrases.sayOrEnterDestinationFirst,
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
                    phrases.statusText(error.message ?: "Destination search failed"),
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
        fetchRouteWeather()
        if (useMockHardware) {
            pendingRouteDestination = null
            prepareMockRoute(destination)
            return
        }
        val location = currentLocation
        if (location == null) {
            locationStatus = "Finding current location…"
            locationController.refresh()
        } else {
            requestRoute(location, destination)
        }
    }

    private fun fetchRouteWeather() {
        routeWeather = null
        if (useMockHardware) {
            routeWeather = LocalWeather(
                description = "晴",
                temperatureCelsius = "24",
                windDirection = "东北",
                windPower = "3",
                humidityPercent = "60",
                reportTime = "",
            )
            return
        }
        val location = currentLocation ?: return
        val city = location.adCode.ifBlank { location.cityName }
        if (city.isBlank()) return
        weatherController.fetchLiveWeather(city) { result ->
            result.onSuccess { routeWeather = it }
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
            simulateMovement = userPreferences.simulateNavigationMovement,
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
        screenNarrator.stopSpeaking()
        hapticGuidance.stop()
        navigationStatus =
            "Phone route guidance paused; the current app does not verify nearby obstacles"
    }

    private fun resumeGuidance() {
        guidancePaused = false
        applyVoiceOwnership()
        repeatCurrentInstruction()
        navigationStatus = "Navigation guidance resumed"
    }

    /**
     * Exactly one voice owns route guidance. With detailed pedestrian guidance on,
     * the app speaks precise walking cues and AMap's driving-style voice is muted;
     * with it off, AMap's native voice speaks and the app stays quiet.
     */
    private fun applyVoiceOwnership() {
        val appSpeaks = userPreferences.detailedPedestrianGuidance
        val speechAllowed = !guidancePaused &&
            userPreferences.guidanceMode != GuidanceMode.HAPTIC_ONLY
        navigationController.setVoiceEnabled(speechAllowed && !appSpeaks)
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
        routeWeather = null
        weatherController.cancel()
    }

    private fun savePreferences(value: UserPreferences) {
        userPreferences = value
        userPreferencesStore.save(value)
        screenNarrator.setLanguage(value.language.languageTag)
        screenNarrator.setVolume(value.speakerVolume)
        placeSearchController.setLanguage(value.language.languageTag)
        applyVoiceOwnership()
    }

    private fun clearDestinationHistory() {
        destinationHistoryStore.clear()
        recentDestinations = emptyList()
    }

    private fun saveFavorite(place: PlaceCandidate) {
        favoriteDestinations = favoriteDestinationsStore.add(place)
        announce(phrases.placeSaved)
    }

    private fun removeFavorite(place: PlaceCandidate) {
        favoriteDestinations = favoriteDestinationsStore.remove(place)
        announce(phrases.removedFromSaved)
    }

    private fun requestWhereAmI() {
        val currentPhrases = phrases
        val location = currentLocation
        if (location == null) {
            whereAmIText = currentPhrases.whereAmIUnavailable
            if (!useMockHardware) locationController.refresh()
            return
        }
        whereAmIText = currentPhrases.whereAmIWorking
        if (useMockHardware) {
            whereAmIText = composeWhereAmI(
                currentPhrases,
                address = location.address,
                nearestPoi = "",
                accuracyMeters = location.accuracyMeters,
            )
            return
        }
        reverseGeocodeController.resolve(location.latitude, location.longitude) { result ->
            result.onSuccess { resolved ->
                whereAmIText = composeWhereAmI(
                    currentPhrases,
                    address = resolved.address,
                    nearestPoi = resolved.name.takeIf { it != resolved.address }.orEmpty(),
                    accuracyMeters = location.accuracyMeters,
                )
            }.onFailure {
                whereAmIText = composeWhereAmI(
                    currentPhrases,
                    address = location.address.ifBlank { currentPhrases.whereAmIUnavailable },
                    nearestPoi = "",
                    accuracyMeters = location.accuracyMeters,
                )
            }
        }
    }

    private fun composeWhereAmI(
        currentPhrases: Phrases,
        address: String,
        nearestPoi: String,
        accuracyMeters: Float?,
    ): String = buildList {
        add(address)
        if (nearestPoi.isNotBlank()) {
            add(currentPhrases.whereAmINearestPoi.format(nearestPoi))
        }
        accuracyMeters?.let { add(currentPhrases.whereAmIAccuracy.format(it.toInt())) }
        add(currentPhrases.whereAmICaution)
    }.joinToString(separator = ". ")

    private fun searchNearby(category: NearbyCategory) {
        val currentPhrases = phrases
        nearbyResults = null
        nearbyStatus = currentPhrases.nearbySearching.format(
            currentPhrases.nearbyCategoryLabel(category),
        )
        if (useMockHardware) {
            nearbyResults = mockNearbyResults(category)
            nearbyStatus = ""
            return
        }
        val location = currentLocation
        if (location == null) {
            nearbyResults = emptyList()
            nearbyStatus = currentPhrases.whereAmIUnavailable
            locationController.refresh()
            return
        }
        nearbySearchController.search(
            category = category,
            latitude = location.latitude,
            longitude = location.longitude,
        ) { result ->
            result.onSuccess { places ->
                nearbyResults = places
                nearbyStatus = if (places.isEmpty()) {
                    currentPhrases.nearbyNoResults.format(
                        currentPhrases.nearbyCategoryLabel(category),
                    )
                } else {
                    ""
                }
            }.onFailure { error ->
                nearbyResults = emptyList()
                nearbyStatus = currentPhrases.statusText(
                    error.message ?: "AMap nearby search failed",
                )
            }
        }
    }

    private fun clearNearby() {
        nearbySearchController.cancel()
        nearbyResults = null
        nearbyStatus = ""
    }

    private fun mockNearbyResults(category: NearbyCategory): List<PlaceCandidate> {
        val label = phrases.nearbyCategoryLabel(category)
        return listOf(
            PlaceCandidate(
                id = "mock-nearby-1-${category.name}",
                name = "$label 1",
                address = "Simulated result near the mock position",
                area = "Changsha",
                latitude = MOCK_LATITUDE + 0.001,
                longitude = MOCK_LONGITUDE + 0.001,
            ),
            PlaceCandidate(
                id = "mock-nearby-2-${category.name}",
                name = "$label 2",
                address = "Second simulated result",
                area = "Changsha",
                latitude = MOCK_LATITUDE - 0.002,
                longitude = MOCK_LONGITUDE + 0.002,
            ),
        )
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
            screenNarrator.speak("Simulation. ${command.description()}.", NarrationPriority.NORMAL)
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

        speakGuidance(instruction)
        val sent = activeWearableTransport.send(instruction)
        navigationStatus = if (sent) {
            if (useMockHardware) {
                "Instruction executed by simulated backpack"
            } else {
                "Instruction sent to wearable"
            }
        } else {
            "Instruction prepared, wearable offline"
        }
    }

    /**
     * Speaks a live guidance cue and fires the matching directional haptics.
     * Urgency maps to narration priority so an act-now cue can interrupt chatter
     * but nothing can interrupt it.
     */
    private fun speakGuidance(instruction: NavigationInstruction) {
        val cue = instruction.cue
        val mode = userPreferences.guidanceMode
        if (cue == null) {
            if (mode != GuidanceMode.HAPTIC_ONLY) {
                screenNarrator.speak(
                    phrases.instructionMessage(instruction),
                    NarrationPriority.HIGH,
                )
            }
            return
        }

        if (mode != GuidanceMode.SPEECH_ONLY) {
            hapticGuidance.cue(cue, userPreferences.vibrationStrength)
            sendDirectionalWearableCue(cue)
        }
        if (mode == GuidanceMode.HAPTIC_ONLY) return
        if (!shouldSpeak(cue.stage, userPreferences.speechDetail)) return
        if (!userPreferences.detailedPedestrianGuidance) return

        val priority = when (cue.stage) {
            CueStage.ACT,
            CueStage.OFF_ROUTE,
            CueStage.ARRIVAL,
            -> NarrationPriority.CRITICAL

            CueStage.PREPARE -> NarrationPriority.HIGH
            CueStage.EARLY,
            CueStage.CONFIRM,
            CueStage.PROGRESS,
            -> NarrationPriority.NORMAL
        }
        screenNarrator.speak(
            guidancePhrases.cueMessage(cue, userPreferences.speechDetail, phrases),
            priority,
        )
    }

    /** Speech density by detail level; safety stages are never filtered out. */
    private fun shouldSpeak(stage: CueStage, detail: SpeechDetail): Boolean = when (stage) {
        CueStage.ACT,
        CueStage.PREPARE,
        CueStage.OFF_ROUTE,
        CueStage.ARRIVAL,
        -> true

        CueStage.EARLY,
        CueStage.CONFIRM,
        -> detail != SpeechDetail.CONCISE

        CueStage.PROGRESS -> detail == SpeechDetail.DETAILED
    }

    /**
     * Left/right buzz on the wearable at the moment of action, using the existing
     * temporary vibration packet type — the same thing the ESP32 will do for real.
     */
    private fun sendDirectionalWearableCue(cue: com.csust.soleprecision.navigation.GuidanceCue) {
        if (cue.stage != CueStage.ACT) return
        val side = when (cue.side) {
            com.csust.soleprecision.navigation.TurnSide.LEFT -> OutputSide.LEFT
            com.csust.soleprecision.navigation.TurnSide.RIGHT -> OutputSide.RIGHT
            com.csust.soleprecision.navigation.TurnSide.NONE ->
                if (cue.isHazardManeuver) OutputSide.BOTH else return
        }
        activeWearableTransport.send(
            DeviceTestCommand.Vibration(
                side = side,
                intensityPercent = userPreferences.vibrationStrength,
                durationMs = if (cue.isHazardManeuver) 250 else 400,
                pattern = if (cue.isHazardManeuver) {
                    VibrationPattern.TRIPLE_PULSE
                } else {
                    VibrationPattern.DOUBLE_PULSE
                },
                repeatCount = 1,
            ),
        )
    }

    private fun activateMockLocation() {
        val location = UserLocation(
            latitude = MOCK_LATITUDE,
            longitude = MOCK_LONGITUDE,
            address = "Simulated GPS position in Changsha",
            cityCode = "0731",
            cityName = "长沙市",
            adCode = "430100",
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
        val cueInstruction = navigationController.repeatCurrentCue()
        if (cueInstruction != null && userPreferences.detailedPedestrianGuidance) {
            currentInstruction = cueInstruction
            cueInstruction.cue?.let { cue ->
                screenNarrator.speak(
                    guidancePhrases.cueMessage(cue, userPreferences.speechDetail, phrases),
                    NarrationPriority.HIGH,
                )
            }
            return
        }
        val instruction = currentInstruction ?: return
        if (useMockHardware || userPreferences.detailedPedestrianGuidance) {
            screenNarrator.speak(
                phrases.instructionMessage(instruction),
                NarrationPriority.HIGH,
            )
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
        hapticGuidance.stop()
        locationController.close()
        placeSearchController.cancel()
        inputTipsController.cancel()
        nearbySearchController.cancel()
        reverseGeocodeController.cancel()
        weatherController.cancel()
        try {
            speechRecognizer?.cancel()
        } finally {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
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
