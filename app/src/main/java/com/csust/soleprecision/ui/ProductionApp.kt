package com.csust.soleprecision.ui

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.navi.AMapNaviView
import com.amap.api.navi.AMapNaviViewOptions
import com.csust.soleprecision.device.AudioCue
import com.csust.soleprecision.device.DeviceTestCommand
import com.csust.soleprecision.device.OutputSide
import com.csust.soleprecision.device.VibrationPattern
import com.csust.soleprecision.i18n.GuidancePhrases
import com.csust.soleprecision.i18n.Phrases
import com.csust.soleprecision.navigation.CueStage
import com.csust.soleprecision.navigation.DestinationSearchState
import com.csust.soleprecision.navigation.DestinationSuggestion
import com.csust.soleprecision.navigation.LocalWeather
import com.csust.soleprecision.navigation.Maneuver
import com.csust.soleprecision.navigation.NavigationInstruction
import com.csust.soleprecision.navigation.AmapReverseGeocodeController
import com.csust.soleprecision.navigation.NearbyCategory
import com.csust.soleprecision.navigation.PlaceCandidate
import com.csust.soleprecision.navigation.ResolvedMapAddress
import com.csust.soleprecision.navigation.RouteSummary
import com.csust.soleprecision.navigation.UserLocation
import com.csust.soleprecision.settings.AppLanguage
import com.csust.soleprecision.settings.GuidanceMode
import com.csust.soleprecision.settings.SpeechDetail
import com.csust.soleprecision.settings.UserPreferences
import kotlinx.coroutines.delay

internal enum class ProductionScreen {
    HOME,
    WHERE_AM_I,
    DESTINATION,
    DESTINATION_METHODS,
    DESTINATION_COLLECTIONS,
    NEARBY_CATEGORIES,
    NEARBY_RESULTS,
    VOICE_DESTINATION,
    TYPE_DESTINATION,
    MAP_DESTINATION,
    RECENT_PLACES,
    SAVED_PLACES,
    SEARCHING,
    SEARCH_RESULTS,
    CONFIRM_PLACE,
    ROUTE_OPTIONS,
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
    routeOptions: List<RouteSummary>,
    recentDestinations: List<PlaceCandidate>,
    favoriteDestinations: List<PlaceCandidate>,
    userPreferences: UserPreferences,
    useMockHardware: Boolean,
    speechUnavailable: Boolean,
    whereAmIText: String?,
    nearbyResults: List<PlaceCandidate>?,
    nearbyStatus: String,
    routeWeather: LocalWeather?,
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
    onSelectRoute: (Int) -> Boolean,
    onStartPlannedRoute: () -> Boolean,
    onRepeatInstruction: () -> Unit,
    onPauseGuidance: () -> Unit,
    onResumeGuidance: () -> Unit,
    onAnnounceScreen: (String) -> Unit,
    onAnnounceActions: (String) -> Unit,
    onSavePreferences: (UserPreferences) -> Unit,
    onClearHistory: () -> Unit,
    onWhereAmI: () -> Unit,
    onSaveFavorite: (PlaceCandidate) -> Unit,
    onRemoveFavorite: (PlaceCandidate) -> Unit,
    onNearbySearch: (NearbyCategory) -> Unit,
    onClearNearby: () -> Unit,
) {
    val p = remember(userPreferences.language) {
        Phrases.forLanguage(userPreferences.language)
    }
    val guidancePhrases = remember(userPreferences.language) {
        GuidancePhrases.forLanguage(userPreferences.language)
    }
    val announceActions: (ScreenActions) -> Unit = { screenActions ->
        if (userPreferences.extraSpokenPrompts) {
            onAnnounceActions(
                guidancePhrases.actionsSentence(
                    title = screenActions.title,
                    right = screenActions.right,
                    left = screenActions.left,
                    up = screenActions.up,
                    down = screenActions.down,
                    usesButtons = screenActions.usesButtons,
                ),
            )
        }
    }
    CompositionLocalProvider(LocalActionAnnouncer provides announceActions) {
    MaterialTheme(
        colorScheme = ProductionColorScheme,
        typography = ProductionTypography,
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (!hasMapConsent) {
                LaunchedEffect(userPreferences.extraSpokenPrompts) {
                    if (userPreferences.extraSpokenPrompts) {
                        onAnnounceScreen(p.introConsent)
                    }
                }
                LocalizedMapPrivacyScreen(p = p, onAccept = onAcceptMapPrivacy)
            } else {
                var screen by rememberSaveable { mutableStateOf(ProductionScreen.HOME) }
                var selectedPlace by remember { mutableStateOf<PlaceCandidate?>(null) }
                var typedDestination by rememberSaveable { mutableStateOf("") }
                val nativeNavigationView = rememberNativeAmapNavigationView()

                LaunchedEffect(
                    screen,
                    if (
                        screen == ProductionScreen.ROUTE_PREVIEW ||
                        screen == ProductionScreen.ROUTE_OPTIONS
                    ) {
                        routeSummary
                    } else {
                        null
                    },
                    routeOptions,
                    userPreferences.extraSpokenPrompts,
                ) {
                    // During real navigation the AMap voice owns route audio; the app
                    // narrator stays silent so two voices never overlap.
                    val narratorOwnsScreen =
                        screen != ProductionScreen.ACTIVE_NAVIGATION || useMockHardware
                    if (userPreferences.extraSpokenPrompts && narratorOwnsScreen) {
                        onAnnounceScreen(
                            screenIntroduction(
                                screen = screen,
                                phrases = p,
                                detail = userPreferences.speechDetail,
                                place = selectedPlace,
                                routeSummary = routeSummary,
                                isSimulation = useMockHardware ||
                                    userPreferences.simulateNavigationMovement,
                                weather = routeWeather,
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

                        ProductionScreen.ROUTE_OPTIONS -> {
                            onStopNavigation()
                            screen = ProductionScreen.CONFIRM_PLACE
                        }

                        ProductionScreen.ARRIVED -> {
                            onStopNavigation()
                            screen = ProductionScreen.HOME
                        }

                        ProductionScreen.NEARBY_RESULTS -> {
                            onClearNearby()
                            screen = ProductionScreen.NEARBY_CATEGORIES
                        }

                        else -> screen = screen.backDestination()
                    }
                }

                when (screen) {
                    ProductionScreen.HOME -> HomeScreen(
                        p = p,
                        wearableStatus = wearableStatus,
                        currentLocation = currentLocation,
                        locationStatus = locationStatus,
                        speechUnavailable = speechUnavailable,
                        onNavigation = { screen = ProductionScreen.DESTINATION },
                        onSettings = { screen = ProductionScreen.SETTINGS },
                        onWhereAmI = { screen = ProductionScreen.WHERE_AM_I },
                    )

                    ProductionScreen.WHERE_AM_I -> WhereAmIScreen(
                        p = p,
                        text = whereAmIText,
                        onRefresh = onWhereAmI,
                        onAnnounce = onAnnounceScreen,
                        onBack = { screen = ProductionScreen.HOME },
                    )

                    ProductionScreen.DESTINATION -> DirectionalDestinationScreen(
                        p = p,
                        onBack = { screen = ProductionScreen.HOME },
                        onMore = { screen = ProductionScreen.DESTINATION_METHODS },
                        onCollections = { screen = ProductionScreen.DESTINATION_COLLECTIONS },
                        onNearby = { screen = ProductionScreen.NEARBY_CATEGORIES },
                    )

                    ProductionScreen.DESTINATION_METHODS -> DestinationMethodsScreen(
                        p = p,
                        onBack = { screen = ProductionScreen.DESTINATION },
                        onSearchMap = { screen = ProductionScreen.MAP_DESTINATION },
                        onVoice = { screen = ProductionScreen.VOICE_DESTINATION },
                    )

                    ProductionScreen.DESTINATION_COLLECTIONS -> DestinationCollectionsScreen(
                        p = p,
                        onBack = { screen = ProductionScreen.DESTINATION },
                        onSaved = { screen = ProductionScreen.SAVED_PLACES },
                        onRecent = { screen = ProductionScreen.RECENT_PLACES },
                    )

                    ProductionScreen.NEARBY_CATEGORIES -> NearbyCategoriesScreen(
                        p = p,
                        onAnnounce = onAnnounceScreen,
                        onBack = { screen = ProductionScreen.DESTINATION },
                        onSearch = { category ->
                            onNearbySearch(category)
                            screen = ProductionScreen.NEARBY_RESULTS
                        },
                    )

                    ProductionScreen.NEARBY_RESULTS -> NearbyResultsScreen(
                        p = p,
                        results = nearbyResults,
                        status = nearbyStatus,
                        currentLocation = currentLocation,
                        onAnnounce = onAnnounceScreen,
                        onBack = {
                            onClearNearby()
                            screen = ProductionScreen.NEARBY_CATEGORIES
                        },
                        onSelect = {
                            selectedPlace = it
                            screen = ProductionScreen.CONFIRM_PLACE
                        },
                    )

                    ProductionScreen.VOICE_DESTINATION -> VoiceDestinationScreen(
                        p = p,
                        state = destinationSearchState,
                        onBack = {
                            onClearDestinationSearch()
                            screen = ProductionScreen.DESTINATION_METHODS
                        },
                        onMicrophone = onSpeakDestination,
                    )

                    ProductionScreen.TYPE_DESTINATION -> TypeDestinationScreen(
                        p = p,
                        value = typedDestination,
                        onValueChange = { typedDestination = it },
                        suggestions = destinationSuggestions,
                        onBack = { screen = ProductionScreen.DESTINATION },
                        onSearch = { onSearchDestination(typedDestination) },
                        onRequestSuggestions = onRequestDestinationSuggestions,
                        onSelectSuggestion = {
                            typedDestination = it.name
                            onSelectDestinationSuggestion(it)
                        },
                    )

                    ProductionScreen.MAP_DESTINATION -> MapDestinationScreen(
                        p = p,
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

                    ProductionScreen.RECENT_PLACES -> PlaceListScreen(
                        p = p,
                        title = p.recentDestinations,
                        places = recentDestinations,
                        emptyText = p.noRecentDestinations,
                        announcementTemplate = p.recentItemAnnouncement,
                        allowRemove = false,
                        onAnnounce = onAnnounceScreen,
                        onBack = { screen = ProductionScreen.DESTINATION },
                        onRemove = {},
                        onSelect = {
                            selectedPlace = it
                            screen = ProductionScreen.CONFIRM_PLACE
                        },
                    )

                    ProductionScreen.SAVED_PLACES -> PlaceListScreen(
                        p = p,
                        title = p.savedDestinations,
                        places = favoriteDestinations,
                        emptyText = p.noSavedDestinations,
                        announcementTemplate = p.savedItemAnnouncement,
                        allowRemove = true,
                        onAnnounce = onAnnounceScreen,
                        onBack = { screen = ProductionScreen.DESTINATION_COLLECTIONS },
                        onRemove = onRemoveFavorite,
                        onSelect = {
                            selectedPlace = it
                            screen = ProductionScreen.CONFIRM_PLACE
                        },
                    )

                    ProductionScreen.SEARCHING -> SearchStatusScreen(
                        p = p,
                        state = destinationSearchState,
                        onBack = {
                            onClearDestinationSearch()
                            screen = ProductionScreen.DESTINATION
                        },
                        onRetryVoice = onSpeakDestination,
                        onType = { screen = ProductionScreen.TYPE_DESTINATION },
                    )

                    ProductionScreen.SEARCH_RESULTS -> SearchResultsScreen(
                        p = p,
                        state = destinationSearchState,
                        currentLocation = currentLocation,
                        speechDetail = userPreferences.speechDetail,
                        onAnnounce = onAnnounceScreen,
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
                            p = p,
                            place = place,
                            isSaved = favoriteDestinations.any {
                                it.id.ifBlank { "${it.latitude}:${it.longitude}" } ==
                                    place.id.ifBlank { "${place.latitude}:${place.longitude}" }
                            },
                            onBack = { screen = ProductionScreen.DESTINATION },
                            onConfirm = {
                                onPlanRoute(place)
                                screen = ProductionScreen.ROUTE_OPTIONS
                            },
                            onChooseAnother = {
                                selectedPlace = null
                                screen = ProductionScreen.DESTINATION
                            },
                            onSave = { onSaveFavorite(place) },
                        )
                    } ?: run {
                        screen = ProductionScreen.DESTINATION
                    }

                    ProductionScreen.ROUTE_OPTIONS -> RouteOptionsScreen(
                        p = p,
                        routes = routeOptions,
                        status = navigationStatus,
                        locationStatus = locationStatus,
                        currentLocation = currentLocation,
                        onAnnounce = onAnnounceScreen,
                        onBack = {
                            onStopNavigation()
                            screen = ProductionScreen.CONFIRM_PLACE
                        },
                        onSelect = { route ->
                            if (onSelectRoute(route.routeId)) {
                                screen = ProductionScreen.ROUTE_PREVIEW
                            }
                        },
                    )

                    ProductionScreen.ROUTE_PREVIEW -> RoutePreviewScreen(
                        p = p,
                        place = selectedPlace,
                        summary = routeSummary,
                        status = navigationStatus,
                        locationStatus = locationStatus,
                        currentLocation = currentLocation,
                        isSimulation = useMockHardware,
                        isMovementSimulated = userPreferences.simulateNavigationMovement,
                        weather = routeWeather,
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
                        p = p,
                        summary = routeSummary,
                        speechDetail = userPreferences.speechDetail,
                        onAnnounce = onAnnounceScreen,
                        onBack = { screen = ProductionScreen.ROUTE_PREVIEW },
                    )

                    ProductionScreen.ACTIVE_NAVIGATION -> ActiveNavigationScreen(
                        p = p,
                        guidancePhrases = guidancePhrases,
                        speechDetail = userPreferences.speechDetail,
                        instruction = instruction,
                        navigationStatus = navigationStatus,
                        wearableStatus = wearableStatus,
                        routeSummary = routeSummary,
                        naviView = nativeNavigationView,
                        onAnnounce = onAnnounceScreen,
                        onRepeat = onRepeatInstruction,
                        onPause = {
                            onPauseGuidance()
                            screen = ProductionScreen.PAUSED
                        },
                    )

                    ProductionScreen.PAUSED -> PausedScreen(
                        p = p,
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
                        p = p,
                        place = selectedPlace,
                        onFinish = {
                            onStopNavigation()
                            screen = ProductionScreen.HOME
                        },
                    )

                    ProductionScreen.SETTINGS -> SettingsScreen(
                        p = p,
                        onBack = { screen = ProductionScreen.HOME },
                        onDeviceSettings = { screen = ProductionScreen.DEVICE_SETTINGS },
                        onAppSettings = { screen = ProductionScreen.APP_SETTINGS },
                    )

                    ProductionScreen.DEVICE_SETTINGS -> DeviceSettingsScreen(
                        p = p,
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
                        guidancePhrases = guidancePhrases,
                    )

                    ProductionScreen.APP_SETTINGS -> AppSettingsScreen(
                        p = p,
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
}

@Composable
private fun LocalizedMapPrivacyScreen(p: Phrases, onAccept: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .semantics { paneTitle = p.consentTitle },
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = p.consentTitle,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = p.consentBody,
            fontSize = 20.sp,
            lineHeight = 29.sp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = p.consentPrototypeNote,
            color = SemanticColors.Optional,
            fontSize = 18.sp,
            lineHeight = 26.sp,
        )
        Spacer(Modifier.height(28.dp))
        LargeAction(p.consentAgree, onClick = onAccept)
    }
}

@Composable
private fun HomeScreen(
    p: Phrases,
    wearableStatus: String,
    currentLocation: UserLocation?,
    locationStatus: String,
    speechUnavailable: Boolean,
    onNavigation: () -> Unit,
    onSettings: () -> Unit,
    onWhereAmI: () -> Unit,
) {
    SwipeOnlyScreen(
        title = p.home,
        actions = mapOf(
            SwipeDirection.RIGHT to SwipeAction(
                label = p.navigation,
                symbol = "",
                color = SemanticColors.Confirm,
            ),
            SwipeDirection.LEFT to SwipeAction(
                label = p.settings,
                symbol = "",
                color = SemanticColors.Neutral,
            ),
            SwipeDirection.UP to SwipeAction(
                label = p.whereAmI,
                symbol = "",
                color = SemanticColors.Optional,
            ),
        ),
        onSwipe = { direction ->
            when (direction) {
                SwipeDirection.RIGHT -> onNavigation()
                SwipeDirection.LEFT -> onSettings()
                SwipeDirection.UP -> onWhereAmI()
                else -> Unit
            }
        },
        layout = SwipeScreenLayout.MENU,
    ) {}
    if (speechUnavailable) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp),
        ) {
            Text(
                p.speechUnavailableBanner,
                color = Color.Black,
                fontSize = 20.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.error)
                    .padding(12.dp)
                    .semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
    }
}

@Composable
private fun WhereAmIScreen(
    p: Phrases,
    text: String?,
    onRefresh: () -> Unit,
    onAnnounce: (String) -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        onRefresh()
    }
    LaunchedEffect(text) {
        if (!text.isNullOrBlank() && text != p.whereAmIWorking) {
            onAnnounce(text)
        }
    }
    SwipeOnlyScreen(
        title = p.whereAmITitle,
        actions = mapOf(
            SwipeDirection.RIGHT to SwipeAction(
                label = p.repeat,
                symbol = "✓",
                color = SemanticColors.Confirm,
            ),
            SwipeDirection.DOWN to SwipeAction(
                label = p.back,
                symbol = "↩",
                color = SemanticColors.Neutral,
            ),
        ),
        onSwipe = { direction ->
            when (direction) {
                SwipeDirection.DOWN -> onBack()
                SwipeDirection.RIGHT -> onRefresh()
                else -> Unit
            }
        },
    ) {
        Text(
            text ?: p.whereAmIWorking,
            fontSize = 28.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun DirectionalDestinationScreen(
    p: Phrases,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onCollections: () -> Unit,
    onNearby: () -> Unit,
) {
    SwipeOnlyScreen(
        title = p.chooseDestination,
        actions = mapOf(
            SwipeDirection.RIGHT to SwipeAction(
                label = p.newDestination,
                symbol = "",
                color = SemanticColors.Confirm,
            ),
            SwipeDirection.LEFT to SwipeAction(
                label = p.recentAndSaved,
                symbol = "",
                color = SemanticColors.Neutral,
            ),
            SwipeDirection.UP to SwipeAction(
                label = p.nearbyEssentials,
                symbol = "",
                color = SemanticColors.Optional,
            ),
            SwipeDirection.DOWN to SwipeAction(
                label = p.back,
                symbol = "",
                color = SemanticColors.Neutral,
            ),
        ),
        onSwipe = { direction ->
            when (direction) {
                SwipeDirection.RIGHT -> onMore()
                SwipeDirection.LEFT -> onCollections()
                SwipeDirection.UP -> onNearby()
                SwipeDirection.DOWN -> onBack()
            }
        },
        layout = SwipeScreenLayout.MENU,
    ) {}
}

@Composable
private fun DestinationMethodsScreen(
    p: Phrases,
    onBack: () -> Unit,
    onSearchMap: () -> Unit,
    onVoice: () -> Unit,
) {
    SwipeOnlyScreen(
        title = p.newDestination,
        actions = mapOf(
            SwipeDirection.RIGHT to SwipeAction(
                label = p.voiceDestination,
                symbol = "",
                color = SemanticColors.Confirm,
            ),
            SwipeDirection.LEFT to SwipeAction(
                label = p.searchOrPointOnMap,
                symbol = "",
                color = SemanticColors.Neutral,
            ),
            SwipeDirection.DOWN to SwipeAction(
                label = p.back,
                symbol = "",
                color = SemanticColors.Neutral,
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
private fun DestinationCollectionsScreen(
    p: Phrases,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onRecent: () -> Unit,
) {
    SwipeOnlyScreen(
        title = p.recentAndSaved,
        actions = mapOf(
            SwipeDirection.RIGHT to SwipeAction(
                label = p.savedDestinations,
                symbol = "",
                color = SemanticColors.Confirm,
            ),
            SwipeDirection.LEFT to SwipeAction(
                label = p.recentDestinations,
                symbol = "",
                color = SemanticColors.Neutral,
            ),
            SwipeDirection.DOWN to SwipeAction(
                label = p.back,
                symbol = "",
                color = SemanticColors.Neutral,
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
private fun NearbyCategoriesScreen(
    p: Phrases,
    onAnnounce: (String) -> Unit,
    onBack: () -> Unit,
    onSearch: (NearbyCategory) -> Unit,
) {
    val categories = NearbyCategory.entries
    var index by rememberSaveable { mutableStateOf(0) }
    val current = categories[index.coerceIn(0, categories.lastIndex)]

    LaunchedEffect(index) {
        onAnnounce(
            p.nearbyCategoryAnnouncement.format(
                index + 1,
                categories.size,
                p.nearbyCategoryLabel(current),
            ),
        )
    }

    SwipeOnlyScreen(
        title = p.nearbyTitle,
        actions = mapOf(
            SwipeDirection.RIGHT to SwipeAction(
                label = p.search,
                symbol = "✓",
                color = SemanticColors.Confirm,
            ),
            SwipeDirection.LEFT to SwipeAction(
                label = p.next,
                symbol = "✕",
                color = SemanticColors.Neutral,
            ),
            SwipeDirection.DOWN to SwipeAction(
                label = p.back,
                symbol = "↩",
                color = SemanticColors.Neutral,
            ),
        ),
        onSwipe = { direction ->
            when (direction) {
                SwipeDirection.RIGHT -> onSearch(current)
                SwipeDirection.LEFT -> index = (index + 1) % categories.size
                SwipeDirection.DOWN -> onBack()
                SwipeDirection.UP -> Unit
            }
        },
    ) {
        Text(
            p.nearbyCategoryLabel(current),
            fontSize = 40.sp,
            lineHeight = 48.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            p.nearbyIntro,
            fontSize = 22.sp,
            lineHeight = 30.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NearbyResultsScreen(
    p: Phrases,
    results: List<PlaceCandidate>?,
    status: String,
    currentLocation: UserLocation?,
    onAnnounce: (String) -> Unit,
    onBack: () -> Unit,
    onSelect: (PlaceCandidate) -> Unit,
) {
    val places = results.orEmpty()
    var index by rememberSaveable(results?.size) { mutableStateOf(0) }
    val current = places.getOrNull(index.coerceIn(0, (places.size - 1).coerceAtLeast(0)))
    val distanceText = current?.let { place ->
        currentLocation?.let { location ->
            p.distanceAwayPhrase(
                distanceMeters(
                    location.latitude,
                    location.longitude,
                    place.latitude,
                    place.longitude,
                ),
            )
        }
    } ?: p.distanceUnavailable

    LaunchedEffect(index, current?.id, results != null) {
        when {
            results == null -> Unit
            current == null -> onAnnounce(status.ifBlank { p.noMatchingPlaces })
            else -> onAnnounce(
                p.nearbyItemAnnouncement.format(
                    index + 1,
                    places.size,
                    listOf(current.name, current.address)
                        .filter(String::isNotBlank)
                        .joinToString(", "),
                    distanceText,
                ),
            )
        }
    }

    val actions = buildMap {
        if (current != null) {
            put(
                SwipeDirection.RIGHT,
                SwipeAction(p.startNavigation, "✓", SemanticColors.Confirm),
            )
            if (index < places.lastIndex) {
                put(SwipeDirection.LEFT, SwipeAction(p.next, "✕", SemanticColors.Decline))
            }
        }
        put(SwipeDirection.DOWN, SwipeAction(p.back, "↩", SemanticColors.Neutral))
    }

    SwipeOnlyScreen(
        title = p.nearbyTitle,
        actions = actions,
        onSwipe = { direction ->
            when (direction) {
                SwipeDirection.RIGHT -> current?.let(onSelect)
                SwipeDirection.LEFT -> if (index < places.lastIndex) index += 1
                SwipeDirection.DOWN -> onBack()
                SwipeDirection.UP -> Unit
            }
        },
    ) {
        when {
            results == null -> Text(
                status.ifBlank { p.findingAddress },
                fontSize = 26.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            current == null -> Text(
                status.ifBlank { p.noMatchingPlaces },
                fontSize = 26.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            else -> {
                Text(
                    current.name,
                    fontSize = 36.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    distanceText,
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun VoiceDestinationScreen(
    p: Phrases,
    state: DestinationSearchState,
    onBack: () -> Unit,
    onMicrophone: () -> Unit,
) {
    val isListening = state == DestinationSearchState.Listening
    SwipeOnlyScreen(
        title = if (isListening) p.listening else p.voiceDestination,
        actions = mapOf(
            SwipeDirection.DOWN to SwipeAction(
                label = p.back,
                symbol = "",
                color = SemanticColors.Neutral,
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
                    SemanticColors.Decline
                } else {
                    SemanticColors.Confirm
                },
                contentColor = SemanticColors.OnLight,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(240.dp)
                .semantics {
                    stateDescription = if (isListening) p.micRecording else p.micReady
                    contentDescription =
                        if (isListening) p.micRecordingDescription else p.micStartDescription
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
                    if (isListening) p.listeningEllipsis else p.microphone,
                    fontSize = 30.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Text(
            when (state) {
                DestinationSearchState.Listening -> p.speakPlaceNow
                is DestinationSearchState.Error -> p.statusText(state.message)
                else -> p.tapMicrophoneHint
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
private fun TypeDestinationScreen(
    p: Phrases,
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<DestinationSuggestion>,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onRequestSuggestions: (String) -> Unit,
    onSelectSuggestion: (DestinationSuggestion) -> Unit,
) {
    LaunchedEffect(value) {
        delay(350)
        onRequestSuggestions(value)
    }

    val firstSuggestion = suggestions.firstOrNull()
    val actions = buildMap {
        put(
            SwipeDirection.LEFT,
            SwipeAction(p.clear, "✕", SemanticColors.Decline),
        )
        if (value.isNotBlank()) {
            put(
                SwipeDirection.RIGHT,
                SwipeAction(p.search, "✓", SemanticColors.Confirm),
            )
        }
        if (firstSuggestion != null) {
            put(
                SwipeDirection.UP,
                SwipeAction(
                    p.useSuggestionPrefix.format(firstSuggestion.name),
                    "↑",
                    SemanticColors.Confirm,
                ),
            )
        }
        put(
            SwipeDirection.DOWN,
            SwipeAction(p.back, "↩", SemanticColors.Neutral),
        )
    }
    SwipeOnlyScreen(
        title = p.typeDestination,
        actions = actions,
        onSwipe = { direction ->
            when (direction) {
                SwipeDirection.LEFT -> onValueChange("")
                SwipeDirection.RIGHT -> if (value.isNotBlank()) onSearch()
                SwipeDirection.UP -> firstSuggestion?.let(onSelectSuggestion)
                SwipeDirection.DOWN -> onBack()
            }
        },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(p.placeNameOrAddress) },
            minLines = 3,
            textStyle = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        firstSuggestion?.let {
            Text(
                text = p.amapSuggestionPrefix.format(it.name) + "\n" + it.supportingText,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MapDestinationScreen(
    p: Phrases,
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
    var pointedSuggestion by remember { mutableStateOf<DestinationSuggestion?>(null) }
    var resolvedAddress by remember { mutableStateOf<ResolvedMapAddress?>(null) }
    var pointStatus by remember { mutableStateOf(p.tapMapToSelect) }
    var hasCenteredMap by remember(mapView) { mutableStateOf(false) }
    val reverseGeocoder = remember { AmapReverseGeocodeController(context) }

    LaunchedEffect(query) {
        delay(350)
        onRequestSuggestions(query)
    }

    LaunchedEffect(currentLocation) {
        if (!hasCenteredMap && currentLocation != null) {
            val center = LatLng(currentLocation.latitude, currentLocation.longitude)
            mapView.map.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 16f))
            hasCenteredMap = true
        }
    }

    LaunchedEffect(mapView) {
        delay(1_200)
        if (!hasCenteredMap) {
            mapView.map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(28.2282, 112.9388), 16f),
            )
            hasCenteredMap = true
        }
    }

    val selectPoint = {
        val suggestion = pointedSuggestion
        if (suggestion != null && suggestion.poiId.isNotBlank()) {
            onResolveSuggestion(suggestion)
        } else {
            pointedLocation?.let { point ->
                val resolved = resolvedAddress
                onSelect(
                    PlaceCandidate(
                        id = "map-${point.latitude}-${point.longitude}",
                        name = resolved?.name ?: p.pinnedMapLocation,
                        address = resolved?.address ?: p.selectedOnAmap,
                        area = resolved?.area.orEmpty(),
                        latitude = point.latitude,
                        longitude = point.longitude,
                    ),
                )
            }
        }
        Unit
    }
    val markPoint: (LatLng) -> Unit = { point ->
        pointedLocation = point
        pointedSuggestion = null
        resolvedAddress = null
        pointStatus = p.findingAddress
        mapView.map.apply {
            clear()
            addMarker(
                MarkerOptions()
                    .position(point)
                    .title(p.selectedDestination),
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
            pointedSuggestion = suggestion
            resolvedAddress = ResolvedMapAddress(
                name = suggestion.name,
                address = suggestion.address.ifBlank { p.selectedOnAmap },
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
            reverseGeocoder.cancel()
            mapView.map.setOnMapClickListener(null)
            mapView.map.isMyLocationEnabled = false
            mapView.onPause()
            mapView.onDestroy()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { paneTitle = p.pointOnAmap },
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
                        label = { Text(p.searchAmap) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = onSearch,
                        enabled = query.isNotBlank(),
                        modifier = Modifier.height(64.dp),
                    ) {
                        Text(p.search, fontSize = 18.sp, fontWeight = FontWeight.Black)
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
                Text(p.back, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
            OutlinedButton(
                onClick = {
                    pointedLocation = null
                    pointedSuggestion = null
                    resolvedAddress = null
                    pointStatus = p.tapMapToSelect
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
                Text(p.clear, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
            Button(
                onClick = selectPoint,
                enabled = pointedLocation != null,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 10.dp),
                modifier = Modifier
                    .weight(1.25f)
                    .height(72.dp),
            ) {
                Text(p.usePoint, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

/** Shared browser for recent and saved destination lists, one place at a time. */
@Composable
private fun PlaceListScreen(
    p: Phrases,
    title: String,
    places: List<PlaceCandidate>,
    emptyText: String,
    announcementTemplate: String,
    allowRemove: Boolean,
    onAnnounce: (String) -> Unit,
    onBack: () -> Unit,
    onRemove: (PlaceCandidate) -> Unit,
    onSelect: (PlaceCandidate) -> Unit,
) {
    var index by rememberSaveable(places.size) { mutableStateOf(0) }
    val current = places.getOrNull(index.coerceAtMost((places.size - 1).coerceAtLeast(0)))
    LaunchedEffect(index, current?.id) {
        current?.let {
            onAnnounce(announcementTemplate.format(index + 1, places.size, it.name))
        }
    }
    val actions = buildMap {
        if (current != null && index < places.lastIndex) {
            put(
                SwipeDirection.LEFT,
                SwipeAction(p.nextPlace, "✕", SemanticColors.Decline),
            )
        }
        if (current != null) {
            put(
                SwipeDirection.RIGHT,
                SwipeAction(p.usePlace, "✓", SemanticColors.Confirm),
            )
            if (allowRemove) {
                put(
                    SwipeDirection.UP,
                    SwipeAction(p.removeFromSaved, "↑", SemanticColors.Optional),
                )
            }
        }
        put(
            SwipeDirection.DOWN,
            SwipeAction(p.back, "↩", SemanticColors.Neutral),
        )
    }
    SwipeOnlyScreen(
        title = title,
        actions = actions,
        onSwipe = { direction ->
            when (direction) {
                SwipeDirection.LEFT -> {
                    if (index < places.lastIndex) index += 1
                }
                SwipeDirection.RIGHT -> current?.let(onSelect)
                SwipeDirection.UP -> if (allowRemove) {
                    current?.let {
                        onRemove(it)
                        index = index.coerceAtMost((places.size - 2).coerceAtLeast(0))
                    }
                }
                SwipeDirection.DOWN -> onBack()
            }
        },
    ) {
        if (current == null) {
            Text(
                emptyText,
                fontSize = 25.sp,
                lineHeight = 33.sp,
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
}

@Composable
private fun SearchStatusScreen(
    p: Phrases,
    state: DestinationSearchState,
    onBack: () -> Unit,
    onRetryVoice: () -> Unit,
    onType: () -> Unit,
) {
    val message = when (state) {
        is DestinationSearchState.Searching -> p.searchingAmapFor.format(state.query)
        DestinationSearchState.Listening -> p.listeningForDestination
        is DestinationSearchState.Error -> p.statusText(state.message)
        else -> p.waitingForDestination
    }
    val actions = buildMap {
        if (state is DestinationSearchState.Error) {
            put(
                SwipeDirection.LEFT,
                SwipeAction(p.tryVoiceAgain, "✕", SemanticColors.Decline),
            )
            put(
                SwipeDirection.RIGHT,
                SwipeAction(p.typeDestination, "✓", SemanticColors.Confirm),
            )
        }
        put(
            SwipeDirection.DOWN,
            SwipeAction(p.back, "↩", SemanticColors.Neutral),
        )
    }
    SwipeOnlyScreen(
        title = p.destinationSearch,
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
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun SearchResultsScreen(
    p: Phrases,
    state: DestinationSearchState,
    currentLocation: UserLocation?,
    speechDetail: SpeechDetail,
    onAnnounce: (String) -> Unit,
    onBack: () -> Unit,
    onSelect: (PlaceCandidate) -> Unit,
) {
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
            p.distanceAwayPhrase(
                distanceMeters(
                    location.latitude,
                    location.longitude,
                    place.latitude,
                    place.longitude,
                ),
            )
        }
    } ?: p.distanceUnavailable

    val next = {
        if (index < results.lastIndex) {
            index += 1
        } else {
            onAnnounce(p.lastOption)
        }
    }
    val select = {
        current?.let(onSelect)
        Unit
    }

    LaunchedEffect(index, current?.id, distance) {
        current?.let { place ->
            val description = when (speechDetail) {
                SpeechDetail.CONCISE -> place.name
                SpeechDetail.STANDARD -> listOf(place.name, place.address)
                    .filter(String::isNotBlank)
                    .joinToString(", ")
                SpeechDetail.DETAILED -> p.placeDescription(place)
            }
            onAnnounce(
                p.optionXofY.format(index + 1, results.size) + ". $description. $distance.",
            )
        }
    }

    val actions = buildMap {
        if (current != null && index < results.lastIndex) {
            put(
                SwipeDirection.LEFT,
                SwipeAction(p.declineNext, "✕", SemanticColors.Decline),
            )
        }
        if (current != null) {
            put(
                SwipeDirection.RIGHT,
                SwipeAction(p.confirm, "✓", SemanticColors.Confirm),
            )
        }
        put(
            SwipeDirection.DOWN,
            SwipeAction(p.back, "↩", SemanticColors.Neutral),
        )
    }
    SwipeOnlyScreen(
        title = p.chooseCorrectPlace,
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
                p.noMatchingPlaces,
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
private fun ConfirmPlaceScreen(
    p: Phrases,
    place: PlaceCandidate,
    isSaved: Boolean,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    onChooseAnother: () -> Unit,
    onSave: () -> Unit,
) {
    val actions = buildMap {
        put(
            SwipeDirection.LEFT,
            SwipeAction(p.decline, "✕", SemanticColors.Decline),
        )
        put(
            SwipeDirection.RIGHT,
            SwipeAction(p.confirm, "✓", SemanticColors.Confirm),
        )
        if (!isSaved) {
            put(
                SwipeDirection.UP,
                SwipeAction(p.savePlace, "↑", SemanticColors.Optional),
            )
        }
        put(
            SwipeDirection.DOWN,
            SwipeAction(p.back, "↩", SemanticColors.Neutral),
        )
    }
    SwipeOnlyScreen(
        title = p.confirmDestination,
        actions = actions,
        onSwipe = { direction ->
            when (direction) {
                SwipeDirection.LEFT -> onChooseAnother()
                SwipeDirection.RIGHT -> onConfirm()
                SwipeDirection.UP -> if (!isSaved) onSave()
                SwipeDirection.DOWN -> onBack()
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
        val details = p.placeAccessibilityDetails(place)
        if (details.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                details,
                fontSize = 22.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        if (isSaved) {
            Spacer(Modifier.height(10.dp))
            Text(
                p.placeSaved,
                color = SemanticColors.Optional,
                fontSize = 20.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RouteOptionsScreen(
    p: Phrases,
    routes: List<RouteSummary>,
    status: String,
    locationStatus: String,
    currentLocation: UserLocation?,
    onAnnounce: (String) -> Unit,
    onBack: () -> Unit,
    onSelect: (RouteSummary) -> Unit,
) {
    var index by rememberSaveable(routes.map { it.routeId }) { mutableStateOf(0) }
    LaunchedEffect(routes.map { it.routeId }) {
        if (index !in routes.indices) index = 0
    }
    val route = routes.getOrNull(index)

    LaunchedEffect(index, route, status) {
        if (route != null) {
            onAnnounce(
                p.routeXofY.format(index + 1, routes.size) + ". " + p.routeSummary(route),
            )
        }
    }

    val actions = buildMap {
        if (route != null && routes.size > 1) {
            put(
                SwipeDirection.LEFT,
                SwipeAction(p.nextRoute, "←", SemanticColors.Neutral),
            )
        }
        if (route != null) {
            put(
                SwipeDirection.RIGHT,
                SwipeAction(p.chooseRoute, "✓", SemanticColors.Confirm),
            )
        }
        put(
            SwipeDirection.DOWN,
            SwipeAction(p.back, "↩", SemanticColors.Neutral),
        )
    }

    SwipeOnlyScreen(
        title = if (route == null) {
            p.routeOptionsPreparing
        } else {
            p.routeXofY.format(index + 1, routes.size)
        },
        actions = actions,
        onSwipe = { direction ->
            when (direction) {
                SwipeDirection.LEFT -> if (routes.size > 1) {
                    index = (index + 1) % routes.size
                }
                SwipeDirection.RIGHT -> route?.let(onSelect)
                SwipeDirection.DOWN -> onBack()
                SwipeDirection.UP -> Unit
            }
        },
    ) {
        Text(
            route?.let(p::routeSummary)
                ?: if (currentLocation == null) {
                    p.statusText(locationStatus)
                } else {
                    p.statusText(status)
                },
            fontSize = 28.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun RoutePreviewScreen(
    p: Phrases,
    place: PlaceCandidate?,
    summary: RouteSummary?,
    status: String,
    locationStatus: String,
    currentLocation: UserLocation?,
    isSimulation: Boolean,
    isMovementSimulated: Boolean,
    weather: LocalWeather?,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onReview: () -> Unit,
) {
    val actions = buildMap {
        put(
            SwipeDirection.LEFT,
            SwipeAction(p.declineRoute, "✕", SemanticColors.Decline),
        )
        if (summary != null) {
            put(
                SwipeDirection.RIGHT,
                SwipeAction(p.startNavigation, "✓", SemanticColors.Confirm),
            )
            if (summary.steps.isNotEmpty()) {
                put(
                    SwipeDirection.UP,
                    SwipeAction(p.reviewFullRoute, "↑", SemanticColors.Optional),
                )
            }
        }
        put(
            SwipeDirection.DOWN,
            SwipeAction(p.back, "↩", SemanticColors.Neutral),
        )
    }
    SwipeOnlyScreen(
        title = p.routePreview,
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
            place?.name ?: p.selectedDestination,
            fontSize = 34.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        if (summary == null) {
            Text(
                p.statusText(if (currentLocation == null) locationStatus else status),
                fontSize = 24.sp,
                lineHeight = 34.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else {
            if (isSimulation || isMovementSimulated) {
                Text(
                    p.simulationOnly,
                    color = SemanticColors.Optional,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Text(
                p.routeSummary(summary),
                fontSize = 27.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            weather?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    p.weatherSummary(it),
                    fontSize = 21.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center,
                )
            }
            if (summary.steps.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    p.walkingStepsCount.format(summary.steps.size),
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun RouteWalkthroughScreen(
    p: Phrases,
    summary: RouteSummary?,
    speechDetail: SpeechDetail,
    onAnnounce: (String) -> Unit,
    onBack: () -> Unit,
) {
    val steps = summary?.steps.orEmpty()
    var index by rememberSaveable(
        summary?.routeId,
        summary?.pathCoordinates?.hashCode(),
        steps.size,
    ) {
        mutableStateOf(0)
    }
    val step = steps.getOrNull(index)

    LaunchedEffect(index, step) {
        if (step != null) {
            onAnnounce(
                p.stepXofY.format(index + 1, steps.size) + ". " +
                    p.stepInstruction(step) + ".",
            )
        }
    }

    val actions = buildMap {
        if (index > 0) {
            put(
                SwipeDirection.LEFT,
                SwipeAction(p.previous, "←", SemanticColors.Neutral),
            )
        }
        if (index < steps.lastIndex) {
            put(
                SwipeDirection.RIGHT,
                SwipeAction(p.next, "→", SemanticColors.Confirm),
            )
        }
        put(
            SwipeDirection.DOWN,
            SwipeAction(p.routePreview, "↩", SemanticColors.Neutral),
        )
    }

    SwipeOnlyScreen(
        title = if (step == null) {
            p.routeWalkthroughUnavailable
        } else {
            p.stepXofY.format(index + 1, steps.size)
        },
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
            step?.let(p::stepInstruction) ?: p.noRouteSteps,
            fontSize = 32.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        if (step?.maneuver == Maneuver.CROSSWALK) {
            Spacer(Modifier.height(12.dp))
            Text(
                p.crossingWarning,
                color = SemanticColors.Optional,
                fontSize = 22.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        if (step?.needsEnvironmentalConfirmation == true && step.maneuver != Maneuver.CROSSWALK) {
            Spacer(Modifier.height(12.dp))
            Text(
                p.mappedFeatureWarning,
                color = SemanticColors.Optional,
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
    p: Phrases,
    guidancePhrases: GuidancePhrases,
    speechDetail: SpeechDetail,
    instruction: NavigationInstruction?,
    navigationStatus: String,
    wearableStatus: String,
    routeSummary: RouteSummary?,
    naviView: AMapNaviView,
    onAnnounce: (String) -> Unit,
    onRepeat: () -> Unit,
    onPause: () -> Unit,
) {
    NativeNavigationMap(
        p = p,
        guidancePhrases = guidancePhrases,
        speechDetail = speechDetail,
        instruction = instruction,
        navigationStatus = p.statusText(navigationStatus),
        wearableStatus = p.statusText(wearableStatus),
        routeSummary = routeSummary,
        naviView = naviView,
        isPaused = false,
        onPrimary = onRepeat,
        onSecondary = onPause,
    )
}

@Composable
private fun PausedScreen(
    p: Phrases,
    instruction: NavigationInstruction?,
    naviView: AMapNaviView,
    onContinue: () -> Unit,
    onEnd: () -> Unit,
) {
    NativeNavigationMap(
        p = p,
        guidancePhrases = null,
        speechDetail = SpeechDetail.STANDARD,
        instruction = instruction,
        navigationStatus = p.guidancePaused,
        wearableStatus = p.obstacleNotVerifiedNote,
        routeSummary = null,
        naviView = naviView,
        isPaused = true,
        onPrimary = onContinue,
        onSecondary = onEnd,
    )
}

@Composable
private fun NativeNavigationMap(
    p: Phrases,
    guidancePhrases: GuidancePhrases?,
    speechDetail: SpeechDetail,
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

    // The same precise sentence the user hears, shown in large type for anyone with
    // usable residual vision. AMap's own banner stays visible underneath it.
    val cueText = instruction?.cue?.let { cue ->
        guidancePhrases?.cueMessage(cue, speechDetail, p)
    } ?: instruction?.let(p::instructionMessage)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                paneTitle = if (isPaused) p.navigationPausedTitle else p.activeNavigationTitle
                contentDescription = buildString {
                    append(cueText ?: p.waitingFirstInstruction)
                    append(". ")
                    routeSummary?.let {
                        append(p.distancePhrase(it.distanceMeters))
                        append(", ")
                        append(p.minutesAbout.format(it.durationMinutes))
                        append(". ")
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

        if (cueText != null) {
            val isUrgent = instruction?.cue?.stage == CueStage.ACT ||
                instruction?.cue?.stage == CueStage.OFF_ROUTE
            Surface(
                color = if (isUrgent) SemanticColors.Optional else Color.Black.copy(alpha = 0.9f),
                contentColor = if (isUrgent) SemanticColors.OnLight else SemanticColors.OnDark,
                shape = RoundedCornerShape(22.dp),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 104.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    cueText,
                    style = labelStyle(26, FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 14.dp)
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
                    if (isPaused) p.continueAction else p.repeat,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Button(
                onClick = onSecondary,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPaused) {
                        Color(0xFFD32F2F)
                    } else {
                        Color.Black.copy(alpha = 0.88f)
                    },
                    contentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp),
            ) {
                Text(
                    if (isPaused) p.end else p.pause,
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
    p: Phrases,
    place: PlaceCandidate?,
    onFinish: () -> Unit,
) {
    SwipeOnlyScreen(
        title = p.destinationReached,
        actions = mapOf(
            SwipeDirection.RIGHT to SwipeAction(
                label = p.finish,
                symbol = "✓",
                color = SemanticColors.Confirm,
            ),
            SwipeDirection.DOWN to SwipeAction(
                label = p.backToHome,
                symbol = "↩",
                color = SemanticColors.Neutral,
            ),
        ),
        onSwipe = { direction ->
            if (direction == SwipeDirection.RIGHT || direction == SwipeDirection.DOWN) {
                onFinish()
            }
        },
    ) {
        Text(
            p.arrivedAt.format(place?.name ?: p.selectedDestination),
            fontSize = 38.sp,
            lineHeight = 46.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            p.confirmEntranceNote,
            color = SemanticColors.Optional,
            fontSize = 22.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SettingsScreen(
    p: Phrases,
    onBack: () -> Unit,
    onDeviceSettings: () -> Unit,
    onAppSettings: () -> Unit,
) {
    StandardScreen(p.settings, p.back, onBack) {
        LargeAction(
            label = p.deviceSettings,
            supportingText = p.deviceSettingsSupport,
            onClick = onDeviceSettings,
            modifier = Modifier.weight(1f),
        )
        LargeAction(
            label = p.appSettings,
            supportingText = p.appSettingsSupport,
            onClick = onAppSettings,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DeviceSettingsScreen(
    p: Phrases,
    guidancePhrases: GuidancePhrases,
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
    StandardScreen(p.deviceSettings, p.back, onBack, scrollable = true) {
        SettingHeading(p.connection)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    p.useSimulatedSystem,
                    fontSize = 23.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    p.useSimulatedSystemSupport,
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
                    contentDescription = p.useSimulatedSystem
                    stateDescription = if (useMockHardware) p.on else p.off
                },
            )
        }
        Text(
            p.statusText(wearableStatus),
            fontSize = 21.sp,
            lineHeight = 29.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        LargeAction(
            if (useMockHardware) p.startSimulator else p.connectDevice,
            onClick = onConnect,
        )
        LargeOutlinedAction(
            if (useMockHardware) p.stopSimulator else p.disconnectDevice,
            onClick = onDisconnect,
        )
        Text(
            p.lastOutputPrefix.format(p.statusText(deviceCommandStatus)),
            fontSize = 20.sp,
            lineHeight = 28.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        )

        SettingHeading(p.guidanceFeedback)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    guidancePhrases.detailedGuidanceSetting,
                    fontSize = 23.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    guidancePhrases.detailedGuidanceSettingSupport,
                    fontSize = 19.sp,
                    lineHeight = 27.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Switch(
                checked = preferences.detailedPedestrianGuidance,
                onCheckedChange = {
                    onSave(preferences.copy(detailedPedestrianGuidance = it))
                },
                modifier = Modifier.semantics {
                    contentDescription = guidancePhrases.detailedGuidanceSetting
                    stateDescription = if (preferences.detailedPedestrianGuidance) p.on else p.off
                },
            )
        }
        GuidanceMode.entries.forEach { mode ->
            SelectionAction(
                label = when (mode) {
                    GuidanceMode.HAPTIC_AND_SPEECH -> p.guidanceModeHapticSpeech
                    GuidanceMode.HAPTIC_ONLY -> p.guidanceModeHapticOnly
                    GuidanceMode.SPEECH_ONLY -> p.guidanceModeSpeechOnly
                },
                selected = preferences.guidanceMode == mode,
                selectedPrefixTemplate = p.selectedPrefix,
                selectedStateLabel = p.selected,
                notSelectedStateLabel = p.notSelected,
            ) {
                onSave(preferences.copy(guidanceMode = mode))
            }
        }

        SettingHeading(p.vibrationStrength)
        StepSetting(
            value = p.percentValue.format(preferences.vibrationStrength),
            decreaseLabel = p.decrease,
            increaseLabel = p.increase,
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
                Text(p.testLeft)
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
                Text(p.testRight)
            }
        }

        SettingHeading(p.speakerVolume)
        StepSetting(
            value = p.percentValue.format(preferences.speakerVolume),
            decreaseLabel = p.decrease,
            increaseLabel = p.increase,
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
            label = p.testBothSpeakers,
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
            p.safetyOverrideNote,
            color = SemanticColors.Optional,
            fontSize = 20.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AppSettingsScreen(
    p: Phrases,
    preferences: UserPreferences,
    recentCount: Int,
    onBack: () -> Unit,
    onSave: (UserPreferences) -> Unit,
    onClearHistory: () -> Unit,
    onOpenEngineering: () -> Unit,
) {
    StandardScreen(p.appSettings, p.back, onBack, scrollable = true) {
        SettingHeading(p.voiceRecognitionLanguage)
        AppLanguage.entries.forEach { language ->
            SelectionAction(
                label = language.displayName,
                selected = preferences.language == language,
                selectedPrefixTemplate = p.selectedPrefix,
                selectedStateLabel = p.selected,
                notSelectedStateLabel = p.notSelected,
            ) {
                onSave(preferences.copy(language = language))
            }
        }

        SettingHeading(p.navigationSpeechDetail)
        SpeechDetail.entries.forEach { detail ->
            SelectionAction(
                label = when (detail) {
                    SpeechDetail.CONCISE -> p.speechDetailConcise
                    SpeechDetail.STANDARD -> p.speechDetailStandard
                    SpeechDetail.DETAILED -> p.speechDetailDetailed
                },
                selected = preferences.speechDetail == detail,
                selectedPrefixTemplate = p.selectedPrefix,
                selectedStateLabel = p.selected,
                notSelectedStateLabel = p.notSelected,
            ) {
                onSave(preferences.copy(speechDetail = detail))
            }
        }

        SettingHeading(p.screenNarration)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    p.announceEachScreen,
                    fontSize = 23.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    p.announceEachScreenSupport,
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
                    contentDescription = p.announceEachScreen
                    stateDescription = if (preferences.extraSpokenPrompts) p.on else p.off
                },
            )
        }

        SettingHeading(p.destinationHistory)
        Text(p.recentPlacesSaved.format(recentCount), fontSize = 20.sp)
        LargeOutlinedAction(p.deleteDestinationHistory, onClick = onClearHistory)

        SettingHeading(p.developerTools)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    p.simulateMovementSetting,
                    fontSize = 23.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    p.simulateMovementSettingSupport,
                    fontSize = 19.sp,
                    lineHeight = 27.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Switch(
                checked = preferences.simulateNavigationMovement,
                onCheckedChange = {
                    onSave(preferences.copy(simulateNavigationMovement = it))
                },
                modifier = Modifier.semantics {
                    contentDescription = p.simulateMovementSetting
                    stateDescription = if (preferences.simulateNavigationMovement) p.on else p.off
                },
            )
        }
        Text(
            p.engineeringConsoleNote,
            fontSize = 18.sp,
            lineHeight = 26.sp,
        )
        LargeOutlinedAction(p.openEngineeringConsole, onClick = onOpenEngineering)
    }
}

internal fun ProductionScreen.backDestination(): ProductionScreen = when (this) {
    ProductionScreen.HOME -> ProductionScreen.HOME
    ProductionScreen.WHERE_AM_I -> ProductionScreen.HOME
    ProductionScreen.DESTINATION,
    ProductionScreen.SETTINGS,
    -> ProductionScreen.HOME

    ProductionScreen.DESTINATION_METHODS,
    ProductionScreen.DESTINATION_COLLECTIONS,
    ProductionScreen.NEARBY_CATEGORIES,
    -> ProductionScreen.DESTINATION

    ProductionScreen.NEARBY_RESULTS -> ProductionScreen.NEARBY_CATEGORIES
    ProductionScreen.VOICE_DESTINATION -> ProductionScreen.DESTINATION_METHODS
    ProductionScreen.TYPE_DESTINATION,
    ProductionScreen.RECENT_PLACES,
    ProductionScreen.SEARCHING,
    ProductionScreen.SEARCH_RESULTS,
    -> ProductionScreen.DESTINATION

    ProductionScreen.SAVED_PLACES -> ProductionScreen.DESTINATION_COLLECTIONS
    ProductionScreen.MAP_DESTINATION -> ProductionScreen.DESTINATION_METHODS
    ProductionScreen.CONFIRM_PLACE -> ProductionScreen.DESTINATION
    ProductionScreen.ROUTE_OPTIONS -> ProductionScreen.CONFIRM_PLACE
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
