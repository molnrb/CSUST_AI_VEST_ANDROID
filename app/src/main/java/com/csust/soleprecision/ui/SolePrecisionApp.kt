package com.csust.soleprecision.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.csust.soleprecision.device.AudioCue
import com.csust.soleprecision.device.DeviceTestCommand
import com.csust.soleprecision.device.OutputSide
import com.csust.soleprecision.device.VibrationPattern
import com.csust.soleprecision.navigation.LocationValidity
import com.csust.soleprecision.navigation.Maneuver
import com.csust.soleprecision.navigation.NavigationInstruction

private val TestConsoleColors = darkColorScheme(
    primary = Color(0xFFFFC857),
    onPrimary = Color(0xFF102A43),
    secondary = Color(0xFF5EEAD4),
    background = Color(0xFF071A2B),
    surface = Color(0xFF102A43),
    surfaceVariant = Color(0xFF163A59),
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
internal fun EngineeringConsoleApp(
    hasMapConsent: Boolean,
    navigationStatus: String,
    wearableStatus: String,
    deviceCommandStatus: String,
    instruction: NavigationInstruction?,
    lastPacketHex: String,
    onAcceptMapPrivacy: () -> Unit,
    onRequestPermissions: () -> Unit,
    onConnectWearable: () -> Unit,
    onDisconnectWearable: () -> Unit,
    onStartWalkingRoute: (Double, Double, Double, Double, Boolean) -> Unit,
    onStopNavigation: () -> Unit,
    onDemoInstruction: (Maneuver, Int) -> Unit,
    onSendDeviceCommand: (DeviceTestCommand) -> Unit,
    onSendRawPacket: (String) -> Unit,
) {
    MaterialTheme(colorScheme = TestConsoleColors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (!hasMapConsent) {
                MapPrivacyScreen(onAccept = onAcceptMapPrivacy)
            } else {
                EngineeringTestConsole(
                    navigationStatus = navigationStatus,
                    wearableStatus = wearableStatus,
                    deviceCommandStatus = deviceCommandStatus,
                    instruction = instruction,
                    lastPacketHex = lastPacketHex,
                    onRequestPermissions = onRequestPermissions,
                    onConnectWearable = onConnectWearable,
                    onDisconnectWearable = onDisconnectWearable,
                    onStartWalkingRoute = onStartWalkingRoute,
                    onStopNavigation = onStopNavigation,
                    onDemoInstruction = onDemoInstruction,
                    onSendDeviceCommand = onSendDeviceCommand,
                    onSendRawPacket = onSendRawPacket,
                )
            }
        }
    }
}

@Composable
internal fun MapPrivacyScreen(onAccept: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "AMap navigation consent",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Sole Precision uses the AMap Navigation SDK to search for destinations, " +
                "calculate walking routes and provide route guidance. AMap may process " +
                "location, device and network information while route guidance is active.",
            fontSize = 20.sp,
            lineHeight = 29.sp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Prototype notice only. Replace this notice with the complete Chinese " +
                "privacy policy before research with participants or release.",
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 18.sp,
            lineHeight = 26.sp,
        )
        Spacer(Modifier.height(28.dp))
        PrimaryAction("I agree and continue", onAccept)
    }
}

@Composable
internal fun EngineeringTestConsole(
    navigationStatus: String,
    wearableStatus: String,
    deviceCommandStatus: String,
    instruction: NavigationInstruction?,
    lastPacketHex: String,
    onRequestPermissions: () -> Unit,
    onConnectWearable: () -> Unit,
    onDisconnectWearable: () -> Unit,
    onStartWalkingRoute: (Double, Double, Double, Double, Boolean) -> Unit,
    onStopNavigation: () -> Unit,
    onDemoInstruction: (Maneuver, Int) -> Unit,
    onSendDeviceCommand: (DeviceTestCommand) -> Unit,
    onSendRawPacket: (String) -> Unit,
    useMockHardware: Boolean = false,
    onSetMockHardware: (Boolean) -> Unit = {},
    onExit: (() -> Unit)? = null,
) {
    var vibrationSide by rememberSaveable { mutableStateOf(OutputSide.BOTH) }
    var vibrationPattern by rememberSaveable { mutableStateOf(VibrationPattern.PULSE) }
    var vibrationIntensity by rememberSaveable { mutableFloatStateOf(70f) }
    var vibrationDuration by rememberSaveable { mutableStateOf("500") }
    var vibrationRepeat by rememberSaveable { mutableFloatStateOf(1f) }

    var audioSide by rememberSaveable { mutableStateOf(OutputSide.BOTH) }
    var audioCue by rememberSaveable { mutableStateOf(AudioCue.TEST_TONE) }
    var audioVolume by rememberSaveable { mutableFloatStateOf(70f) }
    var audioRepeat by rememberSaveable { mutableFloatStateOf(1f) }

    var manualManeuver by rememberSaveable { mutableStateOf(Maneuver.LEFT) }
    var manualDistance by rememberSaveable { mutableStateOf("20") }

    var startLatitude by rememberSaveable { mutableStateOf("28.2282") }
    var startLongitude by rememberSaveable { mutableStateOf("112.9388") }
    var endLatitude by rememberSaveable { mutableStateOf("28.2270") }
    var endLongitude by rememberSaveable { mutableStateOf("112.9400") }
    var simulateMovement by rememberSaveable { mutableStateOf(true) }
    var routeError by rememberSaveable { mutableStateOf<String?>(null) }

    var rawPacket by rememberSaveable {
        mutableStateOf("53 01 12 01 00 03 00 00 00 00 00 42")
    }
    var commandError by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Device Engineering Console",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.semantics { heading() },
        )
        onExit?.let {
            OutlinedButton(
                onClick = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                Text("Exit engineering console", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF5B2B19)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "TEMPORARY TEST UI — not the interface intended for blind users. " +
                    "The packet format and UUIDs are placeholders until the hardware team " +
                    "provides its production interface.",
                modifier = Modifier.padding(16.dp),
                color = Color(0xFFFFD8B4),
                fontWeight = FontWeight.Bold,
                lineHeight = 23.sp,
            )
        }

        StatusCard("Wearable", wearableStatus)
        StatusCard("Last device command", deviceCommandStatus)
        StatusCard("AMap", navigationStatus)
        StatusCard(
            title = "Current AMap instruction",
            body = instruction?.message ?: "No instruction received",
            isLive = true,
        )
        StatusCard("Last packet bytes", lastPacketHex)

        SectionCard(
            title = "1. Connection",
            subtitle = if (useMockHardware) {
                "Simulator replaces the upper controller, USB-to-TTL link, ESP32 and outputs."
            } else {
                "Temporary BLE is selected until the upper-controller interface is supplied."
            },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Use simulated system", fontWeight = FontWeight.Bold)
                    Text(
                        "Recommended for Android Emulator and software-only testing.",
                        color = MaterialTheme.colorScheme.secondary,
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
            PrimaryAction("Grant required permissions", onRequestPermissions)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onConnectWearable,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                ) {
                    Text(if (useMockHardware) "Start simulator" else "Connect")
                }
                OutlinedButton(
                    onClick = onDisconnectWearable,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                ) {
                    Text(if (useMockHardware) "Stop simulator" else "Disconnect")
                }
            }
        }

        SectionCard(
            title = "2. Vibration output",
            subtitle = "Select the motor side and exact temporary haptic parameters.",
        ) {
            ControlLabel("Motor side")
            SidePicker(vibrationSide) { vibrationSide = it }

            ControlLabel("Pattern")
            VibrationPatternPicker(vibrationPattern) { vibrationPattern = it }

            LabeledSlider(
                label = "Intensity",
                value = vibrationIntensity,
                valueRange = 0f..100f,
                displayValue = "${vibrationIntensity.toInt()}%",
                onValueChange = { vibrationIntensity = it },
            )
            OutlinedTextField(
                value = vibrationDuration,
                onValueChange = { vibrationDuration = it.filter(Char::isDigit) },
                label = { Text("Duration in milliseconds (10–10000)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LabeledSlider(
                label = "Repeat count",
                value = vibrationRepeat,
                valueRange = 1f..10f,
                steps = 8,
                displayValue = vibrationRepeat.toInt().toString(),
                onValueChange = { vibrationRepeat = it },
            )
            PrimaryAction("Send vibration test") {
                val duration = vibrationDuration.toIntOrNull()
                if (duration == null || duration !in 10..10_000) {
                    commandError = "Vibration duration must be between 10 and 10000 ms"
                } else {
                    commandError = null
                    onSendDeviceCommand(
                        DeviceTestCommand.Vibration(
                            side = vibrationSide,
                            intensityPercent = vibrationIntensity.toInt(),
                            durationMs = duration,
                            pattern = vibrationPattern,
                            repeatCount = vibrationRepeat.toInt(),
                        ),
                    )
                }
            }
        }

        SectionCard(
            title = "3. Speaker / voice cue output",
            subtitle = "Sends a cue code to the selected speaker. It does not transmit audio.",
        ) {
            ControlLabel("Speaker side")
            SidePicker(audioSide) { audioSide = it }

            ControlLabel("Cue")
            AudioCuePicker(audioCue) { audioCue = it }

            LabeledSlider(
                label = "Volume",
                value = audioVolume,
                valueRange = 0f..100f,
                displayValue = "${audioVolume.toInt()}%",
                onValueChange = { audioVolume = it },
            )
            LabeledSlider(
                label = "Repeat count",
                value = audioRepeat,
                valueRange = 1f..10f,
                steps = 8,
                displayValue = audioRepeat.toInt().toString(),
                onValueChange = { audioRepeat = it },
            )
            PrimaryAction("Send speaker test") {
                commandError = null
                onSendDeviceCommand(
                    DeviceTestCommand.Audio(
                        side = audioSide,
                        cue = audioCue,
                        volumePercent = audioVolume.toInt(),
                        repeatCount = audioRepeat.toInt(),
                    ),
                )
            }
        }

        SectionCard(
            title = "4. Manual navigation packet",
            subtitle = "Tests device direction handling without starting AMap.",
        ) {
            ManeuverPicker(manualManeuver) { manualManeuver = it }
            OutlinedTextField(
                value = manualDistance,
                onValueChange = { manualDistance = it.filter(Char::isDigit) },
                label = { Text("Distance in metres") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryAction("Send manual navigation cue") {
                val distance = manualDistance.toIntOrNull()
                if (distance == null || distance !in 0..65_535) {
                    commandError = "Navigation distance must be between 0 and 65535 metres"
                } else {
                    commandError = null
                    onDemoInstruction(manualManeuver, distance)
                }
            }
        }

        SectionCard(
            title = "5. AMap navigation without movement",
            subtitle = "Simulated mode advances along the calculated route while the device stays still.",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Simulated movement", fontWeight = FontWeight.Bold)
                    Text(
                        if (simulateMovement) {
                            "AMap emulator navigation"
                        } else {
                            "Real GPS navigation"
                        },
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Switch(
                    checked = simulateMovement,
                    onCheckedChange = { simulateMovement = it },
                )
            }

            CoordinateField("Start latitude", startLatitude) { startLatitude = it }
            CoordinateField("Start longitude", startLongitude) { startLongitude = it }
            CoordinateField("Destination latitude", endLatitude) { endLatitude = it }
            CoordinateField("Destination longitude", endLongitude) { endLongitude = it }

            routeError?.let { ErrorText(it) }
            PrimaryAction(
                if (simulateMovement) "Start stationary AMap simulation" else "Start GPS navigation",
            ) {
                val startLat = startLatitude.toDoubleOrNull()
                val startLng = startLongitude.toDoubleOrNull()
                val endLat = endLatitude.toDoubleOrNull()
                val endLng = endLongitude.toDoubleOrNull()
                when {
                    startLat == null || startLng == null || endLat == null || endLng == null ->
                        routeError = "Enter four valid decimal coordinates"
                    !LocationValidity.isValidCoordinate(startLat, startLng) ->
                        routeError = "Start coordinates are out of range"
                    !LocationValidity.isValidCoordinate(endLat, endLng) ->
                        routeError = "Destination coordinates are out of range"
                    startLat == endLat && startLng == endLng ->
                        routeError = "Start and destination must differ"
                    else -> {
                        routeError = null
                        onStartWalkingRoute(startLat, startLng, endLat, endLng, simulateMovement)
                    }
                }
            }
            OutlinedButton(
                onClick = onStopNavigation,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text("Stop AMap navigation")
            }
        }

        SectionCard(
            title = "6. Raw BLE packet",
            subtitle = "Escape hatch for the hardware team. Accepts 1–20 hexadecimal bytes.",
        ) {
            OutlinedTextField(
                value = rawPacket,
                onValueChange = { rawPacket = it },
                label = { Text("Hex bytes") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryAction("Send raw packet") {
                commandError = null
                onSendRawPacket(rawPacket)
            }
        }

        commandError?.let { ErrorText(it) }

        Button(
            onClick = {
                commandError = null
                onSendDeviceCommand(DeviceTestCommand.StopAll)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFB3261E),
                contentColor = Color.White,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Text("STOP ALL DEVICE OUTPUTS", fontWeight = FontWeight.Black)
        }

        Text(
            text = "Safety architecture remains unchanged: backpack obstacle detection must " +
                "run locally and override phone-generated navigation commands.",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            lineHeight = 23.sp,
        )
    }
}

@Composable
private fun SidePicker(selected: OutputSide, onSelect: (OutputSide) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutputSide.entries.forEach { side ->
            FilterChip(
                selected = side == selected,
                onClick = { onSelect(side) },
                label = { Text(side.displayName) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun VibrationPatternPicker(
    selected: VibrationPattern,
    onSelect: (VibrationPattern) -> Unit,
) {
    VibrationPattern.entries.chunked(2).forEach { rowPatterns ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rowPatterns.forEach { pattern ->
                FilterChip(
                    selected = pattern == selected,
                    onClick = { onSelect(pattern) },
                    label = { Text(pattern.displayName) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AudioCuePicker(selected: AudioCue, onSelect: (AudioCue) -> Unit) {
    AudioCue.entries.chunked(2).forEach { rowCues ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rowCues.forEach { cue ->
                FilterChip(
                    selected = cue == selected,
                    onClick = { onSelect(cue) },
                    label = { Text(cue.displayName) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (rowCues.size == 1) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ManeuverPicker(selected: Maneuver, onSelect: (Maneuver) -> Unit) {
    val testManeuvers = listOf(
        Maneuver.STRAIGHT,
        Maneuver.LEFT,
        Maneuver.RIGHT,
        Maneuver.CROSSWALK,
        Maneuver.ARRIVED,
    )
    testManeuvers.chunked(2).forEach { rowManeuvers ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rowManeuvers.forEach { maneuver ->
                FilterChip(
                    selected = maneuver == selected,
                    onClick = { onSelect(maneuver) },
                    label = { Text(maneuver.spokenLabel) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (rowManeuvers.size == 1) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit,
    steps: Int = 0,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontWeight = FontWeight.Bold)
        Text(displayValue, color = MaterialTheme.colorScheme.secondary)
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
    )
}

@Composable
private fun CoordinateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.secondary,
                lineHeight = 22.sp,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            content()
        }
    }
}

@Composable
private fun ControlLabel(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
}

@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        color = Color(0xFFFFA8A8),
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun StatusCard(
    title: String,
    body: String,
    isLive: Boolean = false,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isLive) {
                    Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                } else {
                    Modifier
                },
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(body, fontSize = 18.sp, lineHeight = 25.sp)
        }
    }
}
