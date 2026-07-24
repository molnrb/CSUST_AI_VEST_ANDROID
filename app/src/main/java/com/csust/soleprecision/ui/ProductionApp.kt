package com.csust.soleprecision.ui

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.csust.soleprecision.device.AudioCue
import com.csust.soleprecision.device.DeviceTestCommand
import com.csust.soleprecision.device.OutputSide
import com.csust.soleprecision.device.VibrationPattern
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.navi.AMapNaviView
import com.amap.api.navi.AMapNaviViewOptions
import com.csust.soleprecision.navigation.DestinationSearchState
import com.csust.soleprecision.navigation.DestinationSuggestion
import com.csust.soleprecision.navigation.Maneuver
import com.csust.soleprecision.navigation.NavigationInstruction
import com.csust.soleprecision.navigation.AmapReverseGeocodeController
import com.csust.soleprecision.navigation.PlaceCandidate
import com.csust.soleprecision.navigation.ResolvedMapAddress
import com.csust.soleprecision.navigation.RouteSummary
import com.csust.soleprecision.navigation.UserLocation
import com.csust.soleprecision.settings.AppLanguage
import com.csust.soleprecision.settings.GuidanceMode
import com.csust.soleprecision.settings.SpeechDetail
import com.csust.soleprecision.settings.UserPreferences
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay

private val ProductionColors = darkColorScheme(
    primary = Color(0xFFFFD54F),
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF151515),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color.White,
    error = Color(0xFFFF6B6B),
)

private enum class ProductionScreen {
    HOME,
    DESTINATION,
    DESTINATION_METHODS,
    DESTINATION_COLLECTIONS,
    VOICE_DESTINATION,
    TYPE_DESTINATION,
    MAP_DESTINATION,
    RECENT_PLACES,
    SEARCHING,
    SEARCH_RESULTS,
    CONFIRM_PLACE,
    ROUTE_PREVIEW,
    ROUTE_WALKTHROUGH,
    ACTIVE_NAVIGATION,
    PAUSED,
    ARRIVED,
    SETTINGS,
    DEVICE_SETTINGS,
    APP_SETTINGS,
    ENGINEERING,
}

@Composable
fun SolePrecisionApp(
    hasMapConsent: Boolean,
    navigationStatus: String,
    wearableStatus: String,
    deviceCommandStatus: String,
    instruction: NavigationInstruction?,
    lastPacketHex: String,
    currentLocation: UserLocation?,
    locationStatus: String,
    destinationSearchState: DestinationSearchState,
    destinationSuggestions: List<DestinationSuggestion>,
    routeSummary: RouteSummary?,
    recentDestinations: List<PlaceCandidate>,
    userPreferences: UserPreferences,
    useMockHardware: Boolean,
    onAcceptMapPrivacy: () -> Unit,
    onRequestPermissions: () -> Unit,
    onConnectWearable: () -> Unit,
    onDisconnectWearable: () -> Unit,
    onSetMockHardware: (Boolean) -> Unit,
    onStartWalkingRoute: (Double, Double, Double, Double, Boolean) -> Unit,
    onStopNavigation: () -> Unit,
    onDemoInstruction: (Maneuver, Int) -> Unit,
    onSendDeviceCommand: (DeviceTestCommand) -> Unit,
    onSendRawPacket: (String) -> Unit,
    onSpeakDestination: () -> Unit,
    onSearchDestination: (String) -> Unit,
    onRequestDestinationSuggestions: (String) -> Unit,
    onSelectDestinationSuggestion: (DestinationSuggestion) -> Unit,
    onClearDestinationSearch: () -> Unit,
    onPlanRoute: (PlaceCandidate) -> Unit,
    onStartPlannedRoute: () -> Boolean,
    onRepeatInstruction: () -> Unit,
    onPauseGuidance: () -> Unit,
    onResumeGuidance: () -> Unit,
    onAnnounceScreen: (String) -> Unit,
    onSavePreferences: (UserPreferences) -> Unit,
    onClearHistory: () -> Unit,
) {
    MaterialTheme(colorScheme = ProductionColors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (!hasMapConsent) {
                LaunchedEffect(userPreferences.extraSpokenPrompts) {
                    if (userPreferences.extraSpokenPrompts) {
                        onAnnounceScreen(
                            "AMap navigation consent. Review the location and data notice. " +
                                "I agree and continue is the main button.",
                        )
                    }
                }
                MapPrivacyScreen(onAccept = onAcceptMapPrivacy)
            } else {
                var screen by rememberSaveable { mutableStateOf(ProductionScreen.HOME) }
                var selectedPlace by remember { mutableStateOf<PlaceCandidate?>(null) }
                var typedDestination by rememberSaveable { mutableStateOf("") }
                val nativeNavigationView = rememberNativeAmapNavigationView()

                LaunchedEffect(
                    screen,
                    if (screen == ProductionScreen.ROUTE_PREVIEW) routeSummary else null,
                    userPreferences.extraSpokenPrompts,
                ) {
                    if (userPreferences.extraSpokenPrompts) {
                        onAnnounceScreen(
                            screen.spokenIntroduction(
                                place = selectedPlace,
                                routeSummary = routeSummary,
                                isSimulation = useMockHardware,
                                useDirectionalLayout = true,
                            ),
                        )
                    }
                }

                LaunchedEffect(destinationSearchState) {
                    when (destinationSearchState) {
                        DestinationSearchState.Listening,
                        -> {
                            if (
                                screen != ProductionScreen.ENGINEERING &&
                                screen != ProductionScreen.VOICE_DESTINATION
                            ) {
                                screen = ProductionScreen.SEARCHING
                            }
                        }

                        is DestinationSearchState.Searching -> {
                            if (screen != ProductionScreen.ENGINEERING) {
                                screen = ProductionScreen.SEARCHING
                            }
                        }

                        is DestinationSearchState.Results -> {
                            if (screen != ProductionScreen.ENGINEERING) {
                                screen = ProductionScreen.SEARCH_RESULTS
                            }
                        }

                        is DestinationSearchState.Error,
                        DestinationSearchState.Idle,
                        -> Unit
                    }
                }

                LaunchedEffect(instruction?.maneuver) {
                    if (
                        instruction?.maneuver == Maneuver.ARRIVED &&
                        screen == ProductionScreen.ACTIVE_NAVIGATION
                    ) {
                        screen = ProductionScreen.ARRIVED
                    }
                }

                BackHandler(enabled = screen != ProductionScreen.HOME) {
                    when (screen) {
                        ProductionScreen.ACTIVE_NAVIGATION -> {
                            onPauseGuidance()
                            screen = ProductionScreen.PAUSED
                        }

                        ProductionScreen.PAUSED -> {
                            onResumeGuidance()
                            screen = ProductionScreen.ACTIVE_NAVIGATION
                        }

                        ProductionScreen.ROUTE_PREVIEW -> {
                            onStopNavigation()
                            screen = ProductionScreen.CONFIRM_PLACE
                        }

                        ProductionScreen.ARRIVED -> {
                            onStopNavigation()
                            screen = ProductionScreen.HOME
                        }

                        else -> screen = screen.backDestination()
                    }
                }

                when (screen) {
                    ProductionScreen.HOME -> HomeScreen(
                        wearableStatus = wearableStatus,
                        currentLocation = currentLocation,
                        locationStatus = locationStatus,
                        useDirectionalLayout = true,
                        onNavigation = { screen = ProductionScreen.DESTINATION },
                        onSettings = { screen = ProductionScreen.SETTINGS },
                    )

                    ProductionScreen.DESTINATION -> DestinationScreen(
                        showNavigationDemo = useMockHardware,
                        useDirectionalLayout = true,
                        onBack = { screen = ProductionScreen.HOME },
                        onSpeak = onSpeakDestination,
                        onType = { screen = ProductionScreen.TYPE_DESTINATION },
                        onMap = { screen = ProductionScreen.MAP_DESTINATION },
                        onRecent = { screen = ProductionScreen.RECENT_PLACES },
                        onMore = { screen = ProductionScreen.DESTINATION_METHODS },
                        onCollections = { screen = ProductionScreen.DESTINATION_COLLECTIONS },
                        onDemo = {
                            selectedPlace = SimulatedDestination
                            screen = ProductionScreen.CONFIRM_PLACE
                        },
                    )

                    ProductionScreen.DESTINATION_METHODS -> DestinationMethodsScreen(
                        onBack = { screen = ProductionScreen.DESTINATION },
                        onSearchMap = { screen = ProductionScreen.MAP_DESTINATION },
                        onVoice = { screen = ProductionScreen.VOICE_DESTINATION },
                    )

                    ProductionScreen.DESTINATION_COLLECTIONS -> DestinationCollectionsScreen(
                        onBack = { screen = ProductionScreen.DESTINATION },
                        onSaved = { screen = ProductionScreen.RECENT_PLACES },
                        onRecent = { screen = ProductionScreen.RECENT_PLACES },
                    )

                    ProductionScreen.VOICE_DESTINATION -> VoiceDestinationScreen(
                        state = destinationSearchState,
                        onBack = {
                            onClearDestinationSearch()
                            screen = ProductionScreen.DESTINATION_METHODS
                        },
                        onMicrophone = onSpeakDestination,
                    )

                    ProductionScreen.TYPE_DESTINATION -> TypeDestinationScreen(
                        value = typedDestination,
                        onValueChange = { typedDestination = it },
                        suggestions = destinationSuggestions,
                        useDirectionalLayout = true,
                        onBack = { screen = ProductionScreen.DESTINATION },
                        onSearch = { onSearchDestination(typedDestination) },
                        onRequestSuggestions = onRequestDestinationSuggestions,
                        onSelectSuggestion = {
                            typedDestination = it.name
                            onSelectDestinationSuggestion(it)
                        },
                    )

                    ProductionScreen.MAP_DESTINATION -> MapDestinationScreen(
                        currentLocation = currentLocation,
                        query = typedDestination,
                        onQueryChange = { typedDestination = it },
                        suggestions = destinationSuggestions,
                        onRequestSuggestions = onRequestDestinationSuggestions,
                        onSearch = { onSearchDestination(typedDestination) },
                        onResolveSuggestion = onSelectDestinationSuggestion,
                        onBack = { screen = ProductionScreen.DESTINATION_METHODS },
                        onSelect = {
                            selectedPlace = it
                            screen = ProductionScreen.CONFIRM_PLACE
                        },
                    )

                    ProductionScreen.RECENT_PLACES -> RecentPlacesScreen(
                        places = recentDestinations,
                        useDirectionalLayout = true,
                        onAnnounce = onAnnounceScreen,
                        onBack = { screen = ProductionScreen.DESTINATION },
                        onSelect = {
                            selectedPlace = it
                            screen = ProductionScreen.CONFIRM_PLACE
                        },
                    )

                    ProductionScreen.SEARCHING -> SearchStatusScreen(
                        state = destinationSearchState,
                        useDirectionalLayout = true,
                        onBack = {
                            onClearDestinationSearch()
                            screen = ProductionScreen.DESTINATION
                        },
                        onRetryVoice = onSpeakDestination,
                        onType = { screen = ProductionScreen.TYPE_DESTINATION },
                    )

                    ProductionScreen.SEARCH_RESULTS -> SearchResultsScreen(
                        state = destinationSearchState,
                        currentLocation = currentLocation,
                        onAnnounce = onAnnounceScreen,
                        useDirectionalLayout = true,
                        onBack = {
                            onClearDestinationSearch()
                            screen = ProductionScreen.DESTINATION
                        },
                        onSelect = {
                            selectedPlace = it
                            screen = ProductionScreen.CONFIRM_PLACE
                        },
                    )

                    ProductionScreen.CONFIRM_PLACE -> selectedPlace?.let { place ->
                        ConfirmPlaceScreen(
                            place = place,
                            useDirectionalLayout = true,
                            onBack = { screen = ProductionScreen.DESTINATION },
                            onConfirm = {
                                onPlanRoute(place)
                                screen = ProductionScreen.ROUTE_PREVIEW
                            },
                            onChooseAnother = {
                                selectedPlace = null
                                screen = ProductionScreen.DESTINATION
                            },
                        )
                    } ?: run {
                        screen = ProductionScreen.DESTINATION
                    }

                    ProductionScreen.ROUTE_PREVIEW -> RoutePreviewScreen(
                        place = selectedPlace,
                        summary = routeSummary,
                        status = navigationStatus,
                        locationStatus = locationStatus,
                        currentLocation = currentLocation,
                        isSimulation = useMockHardware,
                        useDirectionalLayout = true,
                        onBack = {
                            onStopNavigation()
                            screen = ProductionScreen.CONFIRM_PLACE
                        },
                        onStart = {
                            if (onStartPlannedRoute()) {
                                screen = ProductionScreen.ACTIVE_NAVIGATION
                            }
                        },
                        onReview = { screen = ProductionScreen.ROUTE_WALKTHROUGH },
                    )

                    ProductionScreen.ROUTE_WALKTHROUGH -> RouteWalkthroughScreen(
                        summary = routeSummary,
                        onAnnounce = onAnnounceScreen,
                        onBack = { screen = ProductionScreen.ROUTE_PREVIEW },
                    )

                    ProductionScreen.ACTIVE_NAVIGATION -> ActiveNavigationScreen(
                        instruction = instruction,
                        navigationStatus = navigationStatus,
                        wearableStatus = wearableStatus,
                        routeSummary = routeSummary,
                        naviView = nativeNavigationView,
                        onRepeat = onRepeatInstruction,
                        onPause = {
                            onPauseGuidance()
                            screen = ProductionScreen.PAUSED
                        },
                    )

                    ProductionScreen.PAUSED -> PausedScreen(
                        instruction = instruction,
                        naviView = nativeNavigationView,
                        onContinue = {
                            onResumeGuidance()
                            screen = ProductionScreen.ACTIVE_NAVIGATION
                        },
                        onEnd = {
                            onStopNavigation()
                            screen = ProductionScreen.HOME
                        },
                    )

                    ProductionScreen.ARRIVED -> ArrivalScreen(
                        place = selectedPlace,
                        useDirectionalLayout = true,
                        onFinish = {
                            onStopNavigation()
                            screen = ProductionScreen.HOME
                        },
                    )

                    ProductionScreen.SETTINGS -> SettingsScreen(
                        onBack = { screen = ProductionScreen.HOME },
                        onDeviceSettings = { screen = ProductionScreen.DEVICE_SETTINGS },
                        onAppSettings = { screen = ProductionScreen.APP_SETTINGS },
                    )

                    ProductionScreen.DEVICE_SETTINGS -> DeviceSettingsScreen(
                        wearableStatus = wearableStatus,
                        deviceCommandStatus = deviceCommandStatus,
                        preferences = userPreferences,
                        useMockHardware = useMockHardware,
                        onBack = { screen = ProductionScreen.SETTINGS },
                        onConnect = onConnectWearable,
                        onDisconnect = onDisconnectWearable,
                        onSetMockHardware = onSetMockHardware,
                        onSave = onSavePreferences,
                        onTestCommand = onSendDeviceCommand,
                    )

                    ProductionScreen.APP_SETTINGS -> AppSettingsScreen(
                        preferences = userPreferences,
                        recentCount = recentDestinations.size,
                        onBack = { screen = ProductionScreen.SETTINGS },
                        onSave = onSavePreferences,
                        onClearHistory = onClearHistory,
                        onOpenEngineering = { screen = ProductionScreen.ENGINEERING },
                    )

                    ProductionScreen.ENGINEERING -> EngineeringTestConsole(
                        navigationStatus = navigationStatus,
                        wearableStatus = wearableStatus,
                        deviceCommandStatus = deviceCommandStatus,
                        instruction = instruction,
                        lastPacketHex = lastPacketHex,
                        useMockHardware = useMockHardware,
                        onRequestPermissions = onRequestPermissions,
                        onConnectWearable = onConnectWearable,
                        onDisconnectWearable = onDisconnectWearable,
                        onSetMockHardware = onSetMockHardware,
                        onStartWalkingRoute = onStartWalkingRoute,
                        onStopNavigation = onStopNavigation,
                        onDemoInstruction = onDemoInstruction,
                        onSendDeviceCommand = onSendDeviceCommand,
                        onSendRawPacket = onSendRawPacket,
                        onExit = { screen = ProductionScreen.APP_SETTINGS },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    wearableStatus: String,
    currentLocation: UserLocation?,
    locationStatus: String,
    useDirectionalLayout: Boolean,
    onNavigation: () -> Unit,
    onSettings: () -> Unit,
) {
    val deviceState = when {
        wearableStatus.contains("not connected", ignoreCase = true) ||
            wearableStatus.contains("disconnected", ignoreCase = true) -> "Device not connected"
        wearableStatus.contains("ready", ignoreCase = true) -> "Device ready"
        wearableStatus.contains("connected", ignoreCase = true) -> "Device connected"
        else -> "Device not connected"
    }
    if (useDirectionalLayout) {
        SwipeOnlyScreen(
            title = "Home",
            actions = mapOf(
                SwipeDirection.RIGHT to SwipeAction(
                    label = "Navigation",
                    symbol = "",
                    color = MaterialTheme.colorScheme.primary,
                ),
                SwipeDirection.LEFT to SwipeAction(
                    label = "Settings",
                    symbol = "",
                    color = Color.White,
                ),
            ),
            onSwipe = { direction ->
                when (direction) {
                    SwipeDirection.RIGHT -> onNavigation()
                    SwipeDirection.LEFT -> onSettings()
                    else -> Unit
                }
            },
            layout = SwipeScreenLayout.MENU,
        ) {}
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(12.dp)
            .semantics { paneTitle = "Home" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HomeButton(
            label = "Navigation",
            supportingText = buildString {
                append(deviceState)
                if (currentLocation == null) {
                    append(" · ")
                    append(locationStatus)
                }
            },
            onClick = onNavigation,
            modifier = Modifier.weight(1f),
        )
        HomeButton(
            label = "Settings",
            supportingText = "Device and application settings",
            onClick = onSettings,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DestinationScreen(
    showNavigationDemo: Boolean,
    useDirectionalLayout: Boolean,
    onBack: () -> Unit,
    onSpeak: () -> Unit,
    onType: () -> Unit,
    onMap: () -> Unit,
    onRecent: () -> Unit,
    onMore: () -> Unit,
    onCollections: () -> Unit,
    onDemo: () -> Unit,
) {
    if (useDirectionalLayout) {
        DirectionalDestinationScreen(
            onBack = onBack,
            onMore = onMore,
            onCollections = onCollections,
        )
        return
    }

    StandardScreen("Choose destination", onBack) {
        if (showNavigationDemo) {
            LargeAction(
                label = "Start navigation demo",
                supportingText = "No GPS, movement or destination search required",
                onClick = onDemo,
                modifier = Modifier.weight(2f),
            )
        }
        LargeAction(
            label = "Speak destination",
            supportingText = "Use Android voice recognition",
            onClick = onSpeak,
            modifier = Modifier.weight(if (showNavigationDemo) 1f else 2f),
        )
        LargeAction(
            label = "Recent destinations",
            supportingText = "Choose a previously used destination",
            onClick = onRecent,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LargeOutlinedAction(
                label = "Type destination",
                onClick = onType,
                modifier = Modifier.weight(1f),
            )
            LargeOutlinedAction(
                label = "Point on map",
                supportingText = "For a helper",
                onClick = onMap,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DirectionalDestinationScreen(
    onBack: () -> Unit,
    onMore: () -> Unit,
    onCollections: () -> Unit,
) {
    SwipeOnlyScreen(
        title = "Choose destination",
        actions = mapOf(
            SwipeDirection.RIGHT to SwipeAction(
                label = "New destination",
                symbol = "",
                color = MaterialTheme.colorScheme.primary,
            ),
            SwipeDirection.LEFT to SwipeAction(
                label = "Recent and saved destinations",
                symbol = "",
                color = Color.White,
            ),
            SwipeDirection.DOWN to SwipeAction(
                label = "Back",
                symbol = "",
                color = Color.White,
            ),
        ),
        onSwipe = { direction ->
            when (direction) {
                SwipeDirection.RIGHT -> onMore()
                SwipeDirection.LEFT -> onCollections()
                SwipeDirection.DOWN -> onBack()
                else -> Unit
            }
        },
        layout = SwipeScreenLayout.MENU,
    ) {}
}

@Composable
private fun DestinationMethodsScreen(
    onBack: () -> Unit,
    onSearchMap: () -> Unit,
    onVoice: () -> Unit,
) {
    SwipeOnlyScreen(
        title = "New destination",
        actions = mapOf(
            SwipeDirection.RIGHT to SwipeAction(
                label = "Voice destination",
                symbol = "",
                color = MaterialTheme.colorScheme.primary,
            ),
            SwipeDirection.LEFT to SwipeAction(
                label = "Search or point on map",
                symbol = "",
                color = Color.White,
            ),
            SwipeDirection.DOWN to SwipeAction(
                label = "Back",
                symbol = "",
                color = Color.White,
            ),
        ),
        onSwipe = { direction ->
            when (direction) {
                SwipeDirection.RIGHT -> onVoice()
                SwipeDirection.LEFT -> onSearchMap()
                SwipeDirection.DOWN -> onBack()
                SwipeDirection.UP -> Unit
            }
        },
        layout = SwipeScreenLayout.MENU,
    ) {}
}

@Composable
private fun VoiceDestinationScreen(
    state: DestinationSearchState,
    onBack: () -> Unit,
    onMicrophone: () -> Unit,
) {
    val isListening = state == DestinationSearchState.Listening
    SwipeOnlyScreen(
        title = if (isListening) "Listening" else "Voice destination",
        actions = mapOf(
            SwipeDirection.DOWN to SwipeAction(
                label = "Back",
                symbol = "",
                color = Color.White,
            ),
        ),
        onSwipe = { direction ->
            if (direction == SwipeDirection.DOWN) onBack()
        },
    ) {
        Button(
            onClick = onMicrophone,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isListening) {
                    Color(0xFFE53935)
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = if (isListening) Color.White else Color.Black,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(240.dp)
                .semantics {
                    stateDescription = if (isListening) "Recording" else "Ready"
                    contentDescription =
                        if (isListening) "Microphone recording destination" else
                            "Start destination voice recognition"
                },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "🎙",
                    fontSize = 96.sp,
                    lineHeight = 104.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    if (isListening) "Listening…" else "Microphone",
                    fontSize = 30.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Text(
            when (state) {
                DestinationSearchState.Listening -> "Speak the place name now"
                is DestinationSearchState.Error -> state.message
                else -> "Tap once, wait for the sound, then say your destination"
            },
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
}

@Composable
private fun DestinationCollectionsScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onRecent: () -> Unit,
) {
    SwipeOnlyScreen(
        title = "Recent and saved destinations",
        actions = mapOf(
            SwipeDirection.RIGHT to SwipeAction(
                label = "Saved destinations",
                symbol = "",
                color = MaterialTheme.colorScheme.primary,
            ),
            SwipeDirection.LEFT to SwipeAction(
                label = "Recent destinations",
                symbol = "",
                color = Color.White,
            ),
            SwipeDirection.DOWN to SwipeAction(
                label = "Back",
                symbol = "",
                color = Color.White,
            ),
        ),
        onSwipe = { direction ->
            when (direction) {
                SwipeDirection.RIGHT -> onSaved()
                SwipeDirection.LEFT -> onRecent()
                SwipeDirection.DOWN -> onBack()
                else -> Unit
            }
        },
        layout = SwipeScreenLayout.MENU,
    ) {}
}

@Composable
private fun TypeDestinationScreen(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<DestinationSuggestion>,
    useDirectionalLayout: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onRequestSuggestions: (String) -> Unit,
    onSelectSuggestion: (DestinationSuggestion) -> Unit,
) {
    LaunchedEffect(value) {
        delay(350)
        onRequestSuggestions(value)
    }

    if (useDirectionalLayout) {
        val firstSuggestion = suggestions.firstOrNull()
        val actions = buildMap {
            put(
                SwipeDirection.LEFT,
                SwipeAction("Clear", "✕", Color(0xFFFF8A80)),
            )
            if (value.isNotBlank()) {
                put(
                    SwipeDirection.RIGHT,
                    SwipeAction("Search", "✓", MaterialTheme.colorScheme.primary),
                )
            }
            if (firstSuggestion != null) {
                put(
                    SwipeDirection.UP,
                    SwipeAction(
                        "Use ${firstSuggestion.name}",
                        "↑",
                        MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            put(
                SwipeDirection.DOWN,
                SwipeAction("Back", "↩", Color.White),
            )
        }
        SwipeOnlyScreen(
            title = "Type destination",
            actions = actions,
            onSwipe = { direction ->
                when (direction) {
                    SwipeDirection.LEFT -> onValueChange("")
                    SwipeDirection.RIGHT -> if (value.isNotBlank()) onSearch()
                    SwipeDirection.UP -> firstSuggestion?.let(onSelectSuggestion)
                    SwipeDirection.DOWN -> onBack()
                    else -> Unit
                }
            },
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Place name or address") },
                minLines = 3,
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth(),
            )
            firstSuggestion?.let {
                Text(
                    text = "AMap suggestion: ${it.name}\n${it.supportingText}",
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        return
    }

    StandardScreen(
        title = "Type destination",
        onBack = onBack,
        scrollable = true,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Place name or address") },
            singleLine = false,
            minLines = 3,
            textStyle = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        LargeAction(
            label = "Search for this place",
            onClick = onSearch,
            enabled = value.isNotBlank(),
        )
        suggestions.forEach { suggestion ->
            LargeAction(
                label = suggestion.name,
                supportingText = suggestion.supportingText,
                onClick = { onSelectSuggestion(suggestion) },
            )
        }
    }
}

@Composable
private fun MapDestinationScreen(
    currentLocation: UserLocation?,
    query: String,
    onQueryChange: (String) -> Unit,
    suggestions: List<DestinationSuggestion>,
    onRequestSuggestions: (String) -> Unit,
    onSearch: () -> Unit,
    onResolveSuggestion: (DestinationSuggestion) -> Unit,
    onBack: () -> Unit,
    onSelect: (PlaceCandidate) -> Unit,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapsInitializer.updatePrivacyShow(context, true, true)
        MapsInitializer.updatePrivacyAgree(context, true)
        TextureMapView(context).apply {
            onCreate(Bundle())
            onResume()
            map.apply {
                uiSettings.apply {
                    isScaleControlsEnabled = true
                    isZoomControlsEnabled = false
                    isCompassEnabled = true
                    isMyLocationButtonEnabled = true
                    isIndoorSwitchEnabled = true
                }
                showIndoorMap(true)
                myLocationStyle = MyLocationStyle()
                    .myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
                    .interval(2_000)
                isMyLocationEnabled = true
            }
        }
    }
    var pointedLocation by remember { mutableStateOf<LatLng?>(null) }
    var resolvedAddress by remember { mutableStateOf<ResolvedMapAddress?>(null) }
    var pointStatus by remember { mutableStateOf("Tap the map to select a destination") }
    val reverseGeocoder = remember { AmapReverseGeocodeController(context) }

    LaunchedEffect(query) {
        delay(350)
        onRequestSuggestions(query)
    }

    LaunchedEffect(mapView, currentLocation) {
        val center = currentLocation?.let { LatLng(it.latitude, it.longitude) }
            ?: LatLng(28.2282, 112.9388)
        mapView.map.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 16f))
    }

    val selectPoint = {
        pointedLocation?.let { point ->
            val resolved = resolvedAddress
            onSelect(
                PlaceCandidate(
                    id = "map-${point.latitude}-${point.longitude}",
                    name = resolved?.name ?: "Pinned map location",
                    address = resolved?.address ?: "Selected on AMap",
                    area = resolved?.area.orEmpty(),
                    latitude = point.latitude,
                    longitude = point.longitude,
                ),
            )
        }
        Unit
    }
    val markPoint: (LatLng) -> Unit = { point ->
        pointedLocation = point
        resolvedAddress = null
        pointStatus = "Finding address…"
        mapView.map.apply {
            clear()
            addMarker(
                MarkerOptions()
                    .position(point)
                    .title("Selected destination"),
            )
        }
        reverseGeocoder.resolve(point.latitude, point.longitude) { result ->
            result.onSuccess { address ->
                if (pointedLocation == point) {
                    resolvedAddress = address
                    pointStatus = address.name
                }
            }.onFailure {
                if (pointedLocation == point) {
                    pointStatus = "%.5f, %.5f".format(point.latitude, point.longitude)
                }
            }
        }
    }
    val currentMarkPoint by rememberUpdatedState(markPoint)
    val markSuggestion: (DestinationSuggestion) -> Unit = { suggestion ->
        val latitude = suggestion.latitude
        val longitude = suggestion.longitude
        if (latitude != null && longitude != null) {
            onRequestSuggestions("")
            val point = LatLng(latitude, longitude)
            pointedLocation = point
            resolvedAddress = ResolvedMapAddress(
                name = suggestion.name,
                address = suggestion.address.ifBlank { "Selected from AMap search" },
                area = suggestion.area,
            )
            pointStatus = suggestion.name
            mapView.map.apply {
                clear()
                addMarker(
                    MarkerOptions()
                        .position(point)
                        .title(suggestion.name),
                )
                animateCamera(CameraUpdateFactory.newLatLngZoom(point, 17f))
            }
        } else {
            onResolveSuggestion(suggestion)
        }
    }

    DisposableEffect(mapView) {
        mapView.map.setOnMapClickListener { point ->
            currentMarkPoint(point)
        }
        onDispose {
            mapView.map.setOnMapClickListener(null)
            mapView.map.isMyLocationEnabled = false
            mapView.onPause()
            mapView.onDestroy()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { paneTitle = "Point on AMap" },
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )

        Surface(
            color = Color.Black.copy(alpha = 0.82f),
            shape = MaterialTheme.shapes.extraLarge,
            shadowElevation = 10.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(12.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        label = { Text("Search AMap") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = onSearch,
                        enabled = query.isNotBlank(),
                        modifier = Modifier.height(64.dp),
                    ) {
                        Text("Search", fontSize = 18.sp, fontWeight = FontWeight.Black)
                    }
                }
                suggestions.take(3).forEach { suggestion ->
                    Button(
                        onClick = { markSuggestion(suggestion) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF333333),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            listOf(suggestion.name, suggestion.supportingText)
                                .filter(String::isNotBlank)
                                .joinToString(" · "),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 18.sp,
                            lineHeight = 24.sp,
                        )
                    }
                }
                Text(
                    pointStatus,
                    color = Color.White,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.88f),
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp),
            ) {
                Text("← Back", fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
            OutlinedButton(
                onClick = {
                    pointedLocation = null
                    resolvedAddress = null
                    pointStatus = "Tap the map to select a destination"
                    mapView.map.clear()
                },
                enabled = pointedLocation != null,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.88f),
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp),
            ) {
                Text("Clear", fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
            Button(
                onClick = selectPoint,
                enabled = pointedLocation != null,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 10.dp),
                modifier = Modifier
                    .weight(1.25f)
                    .height(72.dp),
            ) {
                Text("Use point", fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun RecentPlacesScreen(
    places: List<PlaceCandidate>,
    useDirectionalLayout: Boolean,
    onAnnounce: (String) -> Unit,
    onBack: () -> Unit,
    onSelect: (PlaceCandidate) -> Unit,
) {
    if (useDirectionalLayout) {
        var index by rememberSaveable(places.size) { mutableStateOf(0) }
        val current = places.getOrNull(index.coerceAtMost((places.size - 1).coerceAtLeast(0)))
        LaunchedEffect(index, current?.id) {
            current?.let {
                onAnnounce(
                    "Recent destination ${index + 1} of ${places.size}. ${it.name}. " +
                        "Swipe right to confirm, left for the next place, or down to go back.",
                )
            }
        }
        val actions = buildMap {
            if (current != null && index < places.lastIndex) {
                put(
                    SwipeDirection.LEFT,
                    SwipeAction("Next place", "✕", Color(0xFFFF8A80)),
                )
            }
            if (current != null) {
                put(
                    SwipeDirection.RIGHT,
                    SwipeAction("Use this place", "✓", MaterialTheme.colorScheme.primary),
                )
            }
            put(
                SwipeDirection.DOWN,
                SwipeAction("Back", "↩", Color.White),
            )
        }
        SwipeOnlyScreen(
            title = "Recent destinations",
            actions = actions,
            onSwipe = { direction ->
                when (direction) {
                    SwipeDirection.LEFT -> {
                        if (index < places.lastIndex) index += 1
                    }
                    SwipeDirection.RIGHT -> current?.let(onSelect)
                    SwipeDirection.UP -> Unit
                    SwipeDirection.DOWN -> onBack()
                }
            },
        ) {
            if (current == null) {
                Text(
                    "No recent destinations yet.",
                    fontSize = 25.sp,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    current.name,
                    fontSize = 40.sp,
                    lineHeight = 48.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    StandardScreen(
        title = "Recent destinations",
        onBack = onBack,
        scrollable = true,
    ) {
        if (places.isEmpty()) {
            Text(
                "No recent destinations yet. Use Speak destination or Type destination first.",
                fontSize = 23.sp,
                lineHeight = 32.sp,
            )
        } else {
            places.forEach { place ->
                LargeAction(
                    label = place.name,
                    supportingText = place.area.ifBlank { place.address },
                    onClick = { onSelect(place) },
                )
            }
        }
    }
}

@Composable
private fun SearchStatusScreen(
    state: DestinationSearchState,
    useDirectionalLayout: Boolean,
    onBack: () -> Unit,
    onRetryVoice: () -> Unit,
    onType: () -> Unit,
) {
    val message = when (state) {
        is DestinationSearchState.Searching -> "Searching AMap for ${state.query}"
        DestinationSearchState.Listening -> "Listening for your destination…"
        is DestinationSearchState.Error -> state.message
        else -> "Waiting for a destination"
    }
    if (useDirectionalLayout) {
        val actions = buildMap {
            if (state is DestinationSearchState.Error) {
                put(
                    SwipeDirection.LEFT,
                    SwipeAction("Try voice again", "✕", Color(0xFFFF8A80)),
                )
                put(
                    SwipeDirection.RIGHT,
                    SwipeAction("Type destination", "✓", MaterialTheme.colorScheme.primary),
                )
            }
            put(
                SwipeDirection.DOWN,
                SwipeAction("Back", "↩", Color.White),
            )
        }
        SwipeOnlyScreen(
            title = "Destination search",
            actions = actions,
            onSwipe = { direction ->
                when (direction) {
                    SwipeDirection.LEFT -> if (state is DestinationSearchState.Error) {
                        onRetryVoice()
                    }
                    SwipeDirection.RIGHT -> if (state is DestinationSearchState.Error) onType()
                    SwipeDirection.DOWN -> onBack()
                    else -> Unit
                }
            },
        ) {
            Text(
                message,
                fontSize = 28.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    StandardScreen(
        title = "Destination search",
        onBack = onBack,
    ) {
        Text(
            text = message,
            fontSize = 28.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (state is DestinationSearchState.Error) {
            LargeAction("Try speaking again", onClick = onRetryVoice)
            LargeOutlinedAction("Type destination", onClick = onType)
        }
    }
}

@Composable
private fun SearchResultsScreen(
    state: DestinationSearchState,
    currentLocation: UserLocation?,
    onAnnounce: (String) -> Unit,
    useDirectionalLayout: Boolean,
    onBack: () -> Unit,
    onSelect: (PlaceCandidate) -> Unit,
) {
    if (!useDirectionalLayout) {
        SearchResultsButtonScreen(
            state = state,
            currentLocation = currentLocation,
            onBack = onBack,
            onSelect = onSelect,
        )
        return
    }

    val resultState = state as? DestinationSearchState.Results
    val results = resultState?.places.orEmpty()
    var index by rememberSaveable(resultState?.query) { mutableStateOf(0) }
    val current = if (results.isEmpty()) {
        null
    } else {
        results[index.coerceIn(0, results.lastIndex)]
    }
    val distance = current?.let { place ->
        currentLocation?.let { location ->
            formatDistance(
                distanceMeters(
                    location.latitude,
                    location.longitude,
                    place.latitude,
                    place.longitude,
                ),
            )
        }
    } ?: "Distance unavailable"

    val next = {
        if (index < results.lastIndex) {
            index += 1
        } else {
            onAnnounce("This is the last option.")
        }
    }
    val select = {
        current?.let(onSelect)
        Unit
    }

    LaunchedEffect(index, current?.id, distance) {
        current?.let { place ->
            val address = listOf(place.address, place.area)
                .filter(String::isNotBlank)
                .distinct()
                .joinToString(", ")
            onAnnounce(
                "Option ${index + 1} of ${results.size}. ${place.name}. $address. " +
                    "$distance. Swipe right to confirm, left to decline and hear the next option, " +
                    "or down to go back.",
            )
        }
    }

    val actions = buildMap {
        if (current != null && index < results.lastIndex) {
            put(
                SwipeDirection.LEFT,
                SwipeAction("Decline · next", "✕", Color(0xFFFF8A80)),
            )
        }
        if (current != null) {
            put(
                SwipeDirection.RIGHT,
                SwipeAction("Confirm", "✓", MaterialTheme.colorScheme.primary),
            )
        }
        put(
            SwipeDirection.DOWN,
            SwipeAction("Back", "↩", Color.White),
        )
    }
    SwipeOnlyScreen(
        title = "Choose the correct place",
        actions = actions,
        onSwipe = { direction ->
            when (direction) {
                SwipeDirection.LEFT -> next()
                SwipeDirection.RIGHT -> select()
                SwipeDirection.UP -> Unit
                SwipeDirection.DOWN -> onBack()
            }
        },
    ) {
        if (current == null) {
            Text(
                "No matching places were found. Swipe down to go back and try again.",
                fontSize = 24.sp,
                lineHeight = 34.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else {
            Text(
                current.name,
                fontSize = 40.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SearchResultsButtonScreen(
    state: DestinationSearchState,
    currentLocation: UserLocation?,
    onBack: () -> Unit,
    onSelect: (PlaceCandidate) -> Unit,
) {
    val results = (state as? DestinationSearchState.Results)?.places.orEmpty()
    StandardScreen("Choose the correct place", onBack, scrollable = true) {
        if (results.isEmpty()) {
            Text(
                "No matching places were found. Go back and try a more specific name.",
                fontSize = 23.sp,
                lineHeight = 32.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else {
            results.forEachIndexed { index, place ->
                val distance = currentLocation?.let { location ->
                    formatDistance(
                        distanceMeters(
                            location.latitude,
                            location.longitude,
                            place.latitude,
                            place.longitude,
                        ),
                    )
                } ?: "Distance unavailable"
                LargeAction(
                    label = place.name,
                    supportingText = listOf(
                        place.address,
                        place.area,
                        distance,
                    ).filter(String::isNotBlank).joinToString(" · "),
                    onClick = { onSelect(place) },
                    stateDescription = "Result ${index + 1} of ${results.size}",
                )
            }
        }
    }
}

@Composable
private fun ConfirmPlaceScreen(
    place: PlaceCandidate,
    useDirectionalLayout: Boolean,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    onChooseAnother: () -> Unit,
) {
    if (useDirectionalLayout) {
        SwipeOnlyScreen(
            title = "Confirm destination",
            actions = mapOf(
                SwipeDirection.LEFT to SwipeAction(
                    label = "Decline",
                    symbol = "✕",
                    color = Color(0xFFFF8A80),
                ),
                SwipeDirection.RIGHT to SwipeAction(
                    label = "Confirm",
                    symbol = "✓",
                    color = MaterialTheme.colorScheme.primary,
                ),
                SwipeDirection.DOWN to SwipeAction(
                    label = "Back",
                    symbol = "↩",
                    color = Color.White,
                ),
            ),
            onSwipe = { direction ->
                when (direction) {
                    SwipeDirection.LEFT -> onChooseAnother()
                    SwipeDirection.RIGHT -> onConfirm()
                    SwipeDirection.DOWN -> onBack()
                    else -> Unit
                }
            },
        ) {
            Text(
                place.name,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    StandardScreen(
        title = "Confirm destination",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.Center,
        ) {
            Text(place.name, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))
            Text(
                listOf(place.address, place.area).filter(String::isNotBlank).joinToString(", "),
                fontSize = 23.sp,
                lineHeight = 32.sp,
            )
        }
        LargeAction(
            "Yes, use this place",
            onClick = onConfirm,
        )
        LargeOutlinedAction(
            "Choose another place",
            onClick = onChooseAnother,
        )
    }
}

@Composable
private fun RoutePreviewScreen(
    place: PlaceCandidate?,
    summary: RouteSummary?,
    status: String,
    locationStatus: String,
    currentLocation: UserLocation?,
    isSimulation: Boolean,
    useDirectionalLayout: Boolean,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onReview: () -> Unit,
) {
    if (useDirectionalLayout) {
        val actions = buildMap {
            put(
                SwipeDirection.LEFT,
                SwipeAction("Decline route", "✕", Color(0xFFFF8A80)),
            )
            if (summary != null) {
                put(
                    SwipeDirection.RIGHT,
                    SwipeAction("Start navigation", "✓", MaterialTheme.colorScheme.primary),
                )
                if (summary.steps.isNotEmpty()) {
                    put(
                        SwipeDirection.UP,
                        SwipeAction("Review full route", "↑", Color.White),
                    )
                }
            }
            put(
                SwipeDirection.DOWN,
                SwipeAction("Back", "↩", Color.White),
            )
        }
        SwipeOnlyScreen(
            title = "Route preview",
            actions = actions,
            onSwipe = { direction ->
                when (direction) {
                    SwipeDirection.LEFT,
                    SwipeDirection.DOWN,
                    -> onBack()
                    SwipeDirection.RIGHT -> if (summary != null) onStart()
                    SwipeDirection.UP -> if (summary?.steps?.isNotEmpty() == true) onReview()
                    else -> Unit
                }
            },
        ) {
            Text(
                place?.name ?: "Selected destination",
                fontSize = 34.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            if (summary == null) {
                Text(
                    if (currentLocation == null) locationStatus else status,
                    fontSize = 24.sp,
                    lineHeight = 34.sp,
                    textAlign = TextAlign.Center,
                )
            } else {
                if (isSimulation) {
                    Text(
                        "SIMULATION ONLY",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Text(
                    "${summary.spokenDistance}, approximately ${summary.durationMinutes} minutes.",
                    fontSize = 27.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                if (summary.steps.isNotEmpty()) {
                    Text(
                        "${summary.steps.size} walking steps. Swipe up to review every step.",
                        fontSize = 22.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        return
    }

    StandardScreen(
        title = "Route preview",
        onBack = onBack,
        scrollable = true,
    ) {
        Text(
            place?.name ?: "Selected destination",
            fontSize = 32.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Black,
        )
        if (summary == null) {
            Text(
                if (currentLocation == null) locationStatus else status,
                fontSize = 24.sp,
                lineHeight = 34.sp,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else {
            if (isSimulation) {
                Text(
                    "SIMULATION ONLY — this route does not represent real streets or distance.",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                "${summary.spokenDistance}, approximately ${summary.durationMinutes} minutes.",
                fontSize = 28.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(
                "Obstacle detection remains local to the backpack and has priority over route guidance.",
                fontSize = 20.sp,
                lineHeight = 29.sp,
            )
        }
        LargeAction(
            label = "Start navigation",
            supportingText = when {
                summary == null -> "Waiting for route"
                isSimulation -> "Begin software-only demonstration"
                else -> "Begin walking guidance"
            },
            onClick = onStart,
            enabled = summary != null,
        )
    }
}

@Composable
private fun RouteWalkthroughScreen(
    summary: RouteSummary?,
    onAnnounce: (String) -> Unit,
    onBack: () -> Unit,
) {
    val steps = summary?.steps.orEmpty()
    var index by rememberSaveable(steps.size) { mutableStateOf(0) }
    val step = steps.getOrNull(index)

    LaunchedEffect(index, step) {
        if (step != null) {
            onAnnounce(
                "Route step ${index + 1} of ${steps.size}. ${step.spokenInstruction}. " +
                    when {
                        steps.size == 1 -> "Swipe down to return to the route preview."
                        index == 0 -> "Swipe right for the next step or down to return."
                        index == steps.lastIndex ->
                            "Swipe left for the previous step or down to return."
                        else -> "Swipe right for the next step, left for the previous step, " +
                            "or down to return."
                    },
            )
        }
    }

    val actions = buildMap {
        if (index > 0) {
            put(
                SwipeDirection.LEFT,
                SwipeAction("Previous", "←", Color.White),
            )
        }
        if (index < steps.lastIndex) {
            put(
                SwipeDirection.RIGHT,
                SwipeAction("Next", "→", MaterialTheme.colorScheme.primary),
            )
        }
        put(
            SwipeDirection.DOWN,
            SwipeAction("Route preview", "↩", Color.White),
        )
    }

    SwipeOnlyScreen(
        title = if (step == null) "Route walkthrough unavailable" else
            "Step ${index + 1} of ${steps.size}",
        actions = actions,
        onSwipe = { direction ->
            when (direction) {
                SwipeDirection.LEFT -> if (index > 0) index -= 1
                SwipeDirection.RIGHT -> if (index < steps.lastIndex) index += 1
                SwipeDirection.DOWN -> onBack()
                SwipeDirection.UP -> Unit
            }
        },
    ) {
        Text(
            step?.spokenInstruction ?: "AMap did not provide route steps for this route.",
            fontSize = 32.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        if (step?.maneuver == Maneuver.CROSSWALK) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Mapped crossing: confirm the real crossing and traffic state with the " +
                    "camera system before entering it.",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 22.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ActiveNavigationScreen(
    instruction: NavigationInstruction?,
    navigationStatus: String,
    wearableStatus: String,
    routeSummary: RouteSummary?,
    naviView: AMapNaviView,
    onRepeat: () -> Unit,
    onPause: () -> Unit,
) {
    NativeNavigationMap(
        instruction = instruction,
        navigationStatus = navigationStatus,
        wearableStatus = wearableStatus,
        routeSummary = routeSummary,
        naviView = naviView,
        isPaused = false,
        onPrimary = onRepeat,
        onSecondary = onPause,
    )
}

@Composable
private fun PausedScreen(
    instruction: NavigationInstruction?,
    naviView: AMapNaviView,
    onContinue: () -> Unit,
    onEnd: () -> Unit,
) {
    NativeNavigationMap(
        instruction = instruction,
        navigationStatus = "Guidance paused",
        wearableStatus = "Obstacle detection remains active",
        routeSummary = null,
        naviView = naviView,
        isPaused = true,
        onPrimary = onContinue,
        onSecondary = onEnd,
    )
}

@Composable
private fun NativeNavigationMap(
    instruction: NavigationInstruction?,
    navigationStatus: String,
    wearableStatus: String,
    routeSummary: RouteSummary?,
    naviView: AMapNaviView,
    isPaused: Boolean,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    LaunchedEffect(naviView, isPaused) {
        delay(750)
        if (isPaused) {
            naviView.displayOverview()
        } else {
            naviView.recoverLockMode()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                paneTitle = if (isPaused) "Navigation paused" else "Active AMap navigation"
                contentDescription = buildString {
                    append(instruction?.message ?: "Waiting for first instruction")
                    append(". ")
                    routeSummary?.let {
                        append("${it.spokenDistance}, ${it.durationMinutes} minutes. ")
                    }
                    append(navigationStatus)
                    append(". ")
                    append(wearableStatus)
                }
            },
    ) {
        AndroidView(
            factory = { naviView },
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onPrimary,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp),
            ) {
                Text(
                    if (isPaused) "▶ Continue" else "↻ Repeat",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Button(
                onClick = onSecondary,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPaused) Color(0xFFD32F2F) else Color.Black.copy(alpha = 0.88f),
                    contentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp),
            ) {
                Text(
                    if (isPaused) "■ End" else "Ⅱ Pause",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun rememberNativeAmapNavigationView(): AMapNaviView {
    val context = LocalContext.current
    val naviView = remember {
        val options = AMapNaviViewOptions().apply {
            setLayoutVisible(true)
            setAutoDrawRoute(true)
            setAutoChangeZoom(true)
            setCompassEnabled(true)
            setTrafficLayerEnabled(true)
            setTrafficLine(true)
            setTrafficBarEnabled(true)
            setRouteListButtonShow(true)
            setLaneInfoShow(true)
            setRealCrossDisplayShow(true)
            setModeCrossDisplayShow(true)
            setSecondActionVisible(true)
            setAutoDisplayOverview(true)
        }
        AMapNaviView(context, options).apply {
            onCreate(Bundle())
            onResume()
        }
    }
    DisposableEffect(naviView) {
        onDispose {
            naviView.onPause()
            naviView.onDestroy()
        }
    }
    return naviView
}

@Composable
private fun ArrivalScreen(
    place: PlaceCandidate?,
    useDirectionalLayout: Boolean,
    onFinish: () -> Unit,
) {
    if (useDirectionalLayout) {
        SwipeOnlyScreen(
            title = "Destination reached",
            actions = mapOf(
                SwipeDirection.RIGHT to SwipeAction(
                    label = "Finish",
                    symbol = "✓",
                    color = MaterialTheme.colorScheme.primary,
                ),
                SwipeDirection.DOWN to SwipeAction(
                    label = "Back to home",
                    symbol = "↩",
                    color = Color.White,
                ),
            ),
            onSwipe = { direction ->
                if (direction == SwipeDirection.RIGHT || direction == SwipeDirection.DOWN) {
                    onFinish()
                }
            },
        ) {
            Text(
                "Arrived at ${place?.name ?: "destination"}",
                fontSize = 38.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    StandardScreen(
        title = "Destination reached",
        onBack = onFinish,
    ) {
        Text(
            "You have arrived near ${place?.name ?: "the destination"}. Confirm the exact entrance using your mobility aid and the backpack.",
            fontSize = 30.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    liveRegion = LiveRegionMode.Assertive
                },
        )
        LargeAction(
            "Finish navigation",
            onClick = onFinish,
        )
    }
}

@Composable
private fun SettingsScreen(
    onBack: () -> Unit,
    onDeviceSettings: () -> Unit,
    onAppSettings: () -> Unit,
) {
    StandardScreen("Settings", onBack) {
        LargeAction(
            label = "Device settings",
            supportingText = "Connection, vibration, speakers and signal tests",
            onClick = onDeviceSettings,
            modifier = Modifier.weight(1f),
        )
        LargeAction(
            label = "App settings",
            supportingText = "Language, speech, history and developer tools",
            onClick = onAppSettings,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DeviceSettingsScreen(
    wearableStatus: String,
    deviceCommandStatus: String,
    preferences: UserPreferences,
    useMockHardware: Boolean,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSetMockHardware: (Boolean) -> Unit,
    onSave: (UserPreferences) -> Unit,
    onTestCommand: (DeviceTestCommand) -> Unit,
) {
    StandardScreen("Device settings", onBack, scrollable = true) {
        SettingHeading("Connection")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Use simulated system",
                    fontSize = 23.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Runs the upper controller, ESP32, speakers and vibration outputs " +
                        "without physical hardware.",
                    fontSize = 19.sp,
                    lineHeight = 27.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Switch(
                checked = useMockHardware,
                onCheckedChange = onSetMockHardware,
                modifier = Modifier.semantics {
                    contentDescription = "Use simulated system"
                    stateDescription = if (useMockHardware) "On" else "Off"
                },
            )
        }
        Text(
            wearableStatus,
            fontSize = 21.sp,
            lineHeight = 29.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        LargeAction(
            if (useMockHardware) "Start simulator" else "Connect device",
            onClick = onConnect,
        )
        LargeOutlinedAction(
            if (useMockHardware) "Stop simulator" else "Disconnect device",
            onClick = onDisconnect,
        )
        Text(
            "Last output: $deviceCommandStatus",
            fontSize = 20.sp,
            lineHeight = 28.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        )

        SettingHeading("Guidance feedback")
        GuidanceMode.entries.forEach { mode ->
            SelectionAction(
                label = mode.displayName,
                selected = preferences.guidanceMode == mode,
            ) {
                onSave(preferences.copy(guidanceMode = mode))
            }
        }

        SettingHeading("Vibration strength")
        StepSetting(
            value = "${preferences.vibrationStrength} percent",
            onDecrease = {
                onSave(
                    preferences.copy(
                        vibrationStrength = (preferences.vibrationStrength - 10).coerceAtLeast(20),
                    ),
                )
            },
            onIncrease = {
                onSave(
                    preferences.copy(
                        vibrationStrength = (preferences.vibrationStrength + 10).coerceAtMost(100),
                    ),
                )
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    onTestCommand(
                        DeviceTestCommand.Vibration(
                            side = OutputSide.LEFT,
                            intensityPercent = preferences.vibrationStrength,
                            durationMs = 500,
                            pattern = VibrationPattern.DOUBLE_PULSE,
                            repeatCount = 1,
                        ),
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp),
            ) {
                Text("Test left")
            }
            OutlinedButton(
                onClick = {
                    onTestCommand(
                        DeviceTestCommand.Vibration(
                            side = OutputSide.RIGHT,
                            intensityPercent = preferences.vibrationStrength,
                            durationMs = 500,
                            pattern = VibrationPattern.DOUBLE_PULSE,
                            repeatCount = 1,
                        ),
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp),
            ) {
                Text("Test right")
            }
        }

        SettingHeading("Speaker volume")
        StepSetting(
            value = "${preferences.speakerVolume} percent",
            onDecrease = {
                onSave(
                    preferences.copy(
                        speakerVolume = (preferences.speakerVolume - 10).coerceAtLeast(0),
                    ),
                )
            },
            onIncrease = {
                onSave(
                    preferences.copy(
                        speakerVolume = (preferences.speakerVolume + 10).coerceAtMost(100),
                    ),
                )
            },
        )
        LargeOutlinedAction(
            label = "Test both speakers",
            onClick = {
            onTestCommand(
                DeviceTestCommand.Audio(
                    side = OutputSide.BOTH,
                    cue = AudioCue.TEST_TONE,
                    volumePercent = preferences.speakerVolume,
                    repeatCount = 1,
                ),
            )
            },
        )

        Text(
            "Immediate obstacle detection cannot be disabled here. It must always override navigation guidance.",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 20.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AppSettingsScreen(
    preferences: UserPreferences,
    recentCount: Int,
    onBack: () -> Unit,
    onSave: (UserPreferences) -> Unit,
    onClearHistory: () -> Unit,
    onOpenEngineering: () -> Unit,
) {
    StandardScreen("App settings", onBack, scrollable = true) {
        SettingHeading("Voice recognition language")
        AppLanguage.entries.forEach { language ->
            SelectionAction(
                label = language.displayName,
                selected = preferences.language == language,
            ) {
                onSave(preferences.copy(language = language))
            }
        }

        SettingHeading("Navigation speech detail")
        SpeechDetail.entries.forEach { detail ->
            SelectionAction(
                label = detail.displayName,
                selected = preferences.speechDetail == detail,
            ) {
                onSave(preferences.copy(speechDetail = detail))
            }
        }

        SettingHeading("Screen narration")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Announce each screen",
                    fontSize = 23.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Speaks the screen title, button names and their positions. " +
                        "Automatically stays silent while TalkBack touch exploration is active.",
                    fontSize = 19.sp,
                    lineHeight = 27.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Switch(
                checked = preferences.extraSpokenPrompts,
                onCheckedChange = {
                    onSave(preferences.copy(extraSpokenPrompts = it))
                },
                modifier = Modifier.semantics {
                    contentDescription = "Announce each screen"
                    stateDescription = if (preferences.extraSpokenPrompts) "On" else "Off"
                },
            )
        }

        SettingHeading("Destination history")
        Text("$recentCount recent places saved", fontSize = 20.sp)
        LargeOutlinedAction("Delete destination history", onClick = onClearHistory)

        SettingHeading("Developer tools")
        Text(
            "The engineering console is not intended for blind-user operation.",
            fontSize = 18.sp,
            lineHeight = 26.sp,
        )
        LargeOutlinedAction("Open engineering console", onClick = onOpenEngineering)
    }
}

@Composable
private fun StandardScreen(
    title: String,
    onBack: () -> Unit,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val contentModifier = if (scrollable) {
        Modifier.verticalScroll(rememberScrollState())
    } else {
        Modifier
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .then(contentModifier)
            .padding(16.dp)
            .semantics { paneTitle = title },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Text(
                "Back",
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            title,
            fontSize = 42.sp,
            lineHeight = 50.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
        )
        content()
    }
}

@Composable
private fun HomeButton(
    label: String,
    supportingText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                label,
                fontSize = 48.sp,
                lineHeight = 56.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                supportingText,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LargeAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    stateDescription: String? = null,
    live: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = if (destructive) {
            ButtonDefaults.buttonColors(
                containerColor = Color(0xFFB3261E),
                contentColor = Color.White,
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 128.dp)
            .then(
                if (stateDescription != null || live) {
                    Modifier.semantics {
                        stateDescription?.let { this.stateDescription = it }
                        if (live) liveRegion = LiveRegionMode.Assertive
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                label,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            supportingText?.let {
                Text(
                    it,
                    fontSize = 21.sp,
                    lineHeight = 29.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun LargeOutlinedAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 116.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                label,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            supportingText?.let {
                Text(
                    it,
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SelectionAction(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .semantics {
                stateDescription = if (selected) "Selected" else "Not selected"
            },
    ) {
        Text(
            if (selected) "Selected: $label" else label,
            fontSize = 23.sp,
            lineHeight = 30.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SettingHeading(text: String) {
    Text(
        text,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .semantics { heading() },
    )
}

@Composable
private fun StepSetting(
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Text(
        value,
        fontSize = 27.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            onClick = onDecrease,
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
        ) {
            Text("Decrease", fontSize = 18.sp)
        }
        OutlinedButton(
            onClick = onIncrease,
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
        ) {
            Text("Increase", fontSize = 18.sp)
        }
    }
}

private fun ProductionScreen.spokenIntroduction(
    place: PlaceCandidate?,
    routeSummary: RouteSummary?,
    isSimulation: Boolean,
    useDirectionalLayout: Boolean,
): String = when (this) {
    ProductionScreen.HOME ->
        if (useDirectionalLayout) {
            "Home. Swipe right for Navigation or left for Settings."
        } else {
            "Home screen. Navigation is the top half of the screen. Settings is the bottom half."
        }

    ProductionScreen.DESTINATION ->
        if (useDirectionalLayout) {
            "Choose destination. Swipe right for a new destination, left for recent and saved " +
                "destinations, or down to go back."
        } else if (isSimulation) {
            "Choose destination. Start navigation demo is the first large button and needs no " +
                "GPS or search. Speak, recent and typed destinations are below it."
        } else {
            "Choose destination. Back is at the top. Speak destination is the large upper button. " +
                "Recent destinations is in the middle. Type destination is at the bottom."
        }

    ProductionScreen.DESTINATION_METHODS ->
        "New destination. Swipe right for voice destination, left to search AMap or point on " +
            "the map, or down to go back."

    ProductionScreen.DESTINATION_COLLECTIONS ->
        "Recent and saved destinations. Swipe right for saved destinations, left for recent " +
            "destinations, or down to go back."

    ProductionScreen.VOICE_DESTINATION ->
        "Voice destination. Tap the large microphone to start listening. Swipe down to go back."

    ProductionScreen.TYPE_DESTINATION ->
        if (useDirectionalLayout) {
            "Type destination helper mode. Enter a place, then swipe right to search. " +
                "Swipe left to clear or down to go back."
        } else {
            "Type destination. Back is at the top. The destination text field is in the middle. " +
                "Search for this place is below it."
        }

    ProductionScreen.MAP_DESTINATION ->
        "AMap destination search. Type a place and choose an AMap suggestion, or tap the " +
            "full-screen map to place a marker. Floating controls are at the bottom."

    ProductionScreen.RECENT_PLACES ->
        if (useDirectionalLayout) {
            "Recent destinations. Swipe right to use the current place, left for the next place, " +
                "or down to go back."
        } else {
            "Recent destinations. Back is at the top. Saved destinations are listed from top to bottom."
        }

    ProductionScreen.SEARCHING ->
        if (useDirectionalLayout) {
            "Destination search. Search status is in the center. Swipe down to go back."
        } else {
            "Destination search. Back is at the top. Search status is in the center."
        }

    ProductionScreen.SEARCH_RESULTS ->
        if (useDirectionalLayout) {
            "Choose the correct place. One result is shown and announced at a time."
        } else {
            "Choose the correct place. Back is at the top. Search results are arranged from top to bottom."
        }

    ProductionScreen.CONFIRM_PLACE ->
        if (useDirectionalLayout) {
            "Confirm destination ${place?.name ?: ""}. Swipe right to confirm, left to decline, " +
                "or down to go back."
        } else {
            "Confirm destination ${place?.name ?: ""}. " +
                "Yes, use this place is above Choose another place."
        }

    ProductionScreen.ROUTE_PREVIEW -> routeSummary?.let {
        val mode = if (isSimulation) {
            "Simulation only. This is not a real route. "
        } else {
            ""
        }
        mode + "Route preview for ${place?.name ?: "the selected destination"}. " +
            "The route is ${it.spokenDistance}, approximately ${it.durationMinutes} minutes. " +
            if (useDirectionalLayout) {
                if (it.steps.isNotEmpty()) {
                    "Swipe up to review all ${it.steps.size} walking steps, right to start, " +
                        "left to decline, or down to go back."
                } else {
                    "Swipe right to start, left to decline, or down to go back."
                }
            } else {
                "Start navigation is at the bottom."
            }
    } ?: if (useDirectionalLayout) {
        "Route preview. The route is being prepared. Swipe down to go back."
    } else {
        "Route preview. The route is being prepared. Back is at the top. " +
            "Start navigation will become available at the bottom."
    }

    ProductionScreen.ROUTE_WALKTHROUGH ->
        "Route walkthrough. Each AMap walking step is announced individually."

    ProductionScreen.ACTIVE_NAVIGATION ->
        "Active navigation. The native AMap guidance map fills the screen. " +
            "Repeat and Pause controls float at the bottom."

    ProductionScreen.PAUSED ->
        "Navigation paused. The AMap route remains visible. Continue and End controls float at the bottom."

    ProductionScreen.ARRIVED ->
        if (useDirectionalLayout) {
            "Destination reached near ${place?.name ?: "the destination"}. " +
                "Swipe right to finish or down to return home."
        } else {
            "Destination reached near ${place?.name ?: "the destination"}. " +
                "Finish navigation is at the bottom."
        }

    ProductionScreen.SETTINGS ->
        "Settings. Back is at the top. Device settings is the upper button. " +
            "App settings is the lower button."

    ProductionScreen.DEVICE_SETTINGS ->
        "Device settings. Back is at the top. Scroll down for connection, guidance, vibration, " +
            "speaker and safety controls."

    ProductionScreen.APP_SETTINGS ->
        "App settings. Back is at the top. Scroll down for recognition language, speech detail, " +
            "screen introductions, history and developer tools."

    ProductionScreen.ENGINEERING ->
        "Engineering test console. This screen is intended for development and hardware testing."
}

private val SimulatedDestination = PlaceCandidate(
    id = "simulated-destination",
    name = "Simulated destination",
    address = "Software-only navigation demonstration",
    area = "Changsha",
    latitude = 28.2310,
    longitude = 112.9440,
)

private fun distanceMeters(
    startLatitude: Double,
    startLongitude: Double,
    endLatitude: Double,
    endLongitude: Double,
): Int {
    val latitudeDelta = Math.toRadians(endLatitude - startLatitude)
    val longitudeDelta = Math.toRadians(endLongitude - startLongitude)
    val startLatitudeRadians = Math.toRadians(startLatitude)
    val endLatitudeRadians = Math.toRadians(endLatitude)
    val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
        cos(startLatitudeRadians) * cos(endLatitudeRadians) *
        sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
    val angularDistance = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (6_371_000 * angularDistance).toInt().coerceAtLeast(0)
}

private fun formatDistance(metres: Int): String = if (metres >= 1_000) {
    "%.1f kilometres away".format(metres / 1_000.0)
} else {
    "$metres metres away"
}

private fun ProductionScreen.backDestination(): ProductionScreen = when (this) {
    ProductionScreen.HOME -> ProductionScreen.HOME
    ProductionScreen.DESTINATION,
    ProductionScreen.SETTINGS,
    -> ProductionScreen.HOME

    ProductionScreen.DESTINATION_METHODS,
    ProductionScreen.DESTINATION_COLLECTIONS,
    -> ProductionScreen.DESTINATION

    ProductionScreen.VOICE_DESTINATION -> ProductionScreen.DESTINATION_METHODS
    ProductionScreen.TYPE_DESTINATION,
    ProductionScreen.RECENT_PLACES,
    ProductionScreen.SEARCHING,
    ProductionScreen.SEARCH_RESULTS,
    -> ProductionScreen.DESTINATION

    ProductionScreen.MAP_DESTINATION -> ProductionScreen.DESTINATION_METHODS
    ProductionScreen.CONFIRM_PLACE -> ProductionScreen.DESTINATION
    ProductionScreen.ROUTE_PREVIEW -> ProductionScreen.CONFIRM_PLACE
    ProductionScreen.ROUTE_WALKTHROUGH -> ProductionScreen.ROUTE_PREVIEW
    ProductionScreen.ACTIVE_NAVIGATION -> ProductionScreen.PAUSED
    ProductionScreen.PAUSED -> ProductionScreen.ACTIVE_NAVIGATION
    ProductionScreen.ARRIVED -> ProductionScreen.HOME
    ProductionScreen.DEVICE_SETTINGS,
    ProductionScreen.APP_SETTINGS,
    -> ProductionScreen.SETTINGS

    ProductionScreen.ENGINEERING -> ProductionScreen.APP_SETTINGS
}
