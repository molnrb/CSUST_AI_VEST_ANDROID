package com.csust.soleprecision.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.csust.soleprecision.navigation.Maneuver
import com.csust.soleprecision.navigation.NavigationInstruction

private val SolePrecisionColors = darkColorScheme(
    primary = Color(0xFFFFC857),
    onPrimary = Color(0xFF102A43),
    secondary = Color(0xFF5EEAD4),
    background = Color(0xFF071A2B),
    surface = Color(0xFF102A43),
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun SolePrecisionApp(
    hasMapConsent: Boolean,
    navigationStatus: String,
    wearableStatus: String,
    instruction: NavigationInstruction?,
    lastPacketHex: String,
    onAcceptMapPrivacy: () -> Unit,
    onRequestPermissions: () -> Unit,
    onConnectWearable: () -> Unit,
    onDisconnectWearable: () -> Unit,
    onStartWalkingRoute: (Double, Double, Double, Double) -> Unit,
    onStopNavigation: () -> Unit,
    onDemoInstruction: (Maneuver) -> Unit,
) {
    MaterialTheme(colorScheme = SolePrecisionColors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (!hasMapConsent) {
                MapPrivacyScreen(onAccept = onAcceptMapPrivacy)
            } else {
                MainScreen(
                    navigationStatus = navigationStatus,
                    wearableStatus = wearableStatus,
                    instruction = instruction,
                    lastPacketHex = lastPacketHex,
                    onRequestPermissions = onRequestPermissions,
                    onConnectWearable = onConnectWearable,
                    onDisconnectWearable = onDisconnectWearable,
                    onStartWalkingRoute = onStartWalkingRoute,
                    onStopNavigation = onStopNavigation,
                    onDemoInstruction = onDemoInstruction,
                )
            }
        }
    }
}

@Composable
private fun MapPrivacyScreen(onAccept: () -> Unit) {
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
            text = "Map privacy notice",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Sole Precision uses the AMap Navigation SDK to calculate walking routes " +
                "and receive live turn instructions. When route guidance is enabled, AMap " +
                "may process location, device and network information to provide that service.",
            fontSize = 20.sp,
            lineHeight = 29.sp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "This is a prototype notice. Before user testing or release, replace it " +
                "with the complete Chinese privacy policy and the disclosures required by AMap.",
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 18.sp,
            lineHeight = 26.sp,
        )
        Spacer(Modifier.height(28.dp))
        PrimaryAction(
            label = "I agree and continue",
            onClick = onAccept,
        )
    }
}

@Composable
private fun MainScreen(
    navigationStatus: String,
    wearableStatus: String,
    instruction: NavigationInstruction?,
    lastPacketHex: String,
    onRequestPermissions: () -> Unit,
    onConnectWearable: () -> Unit,
    onDisconnectWearable: () -> Unit,
    onStartWalkingRoute: (Double, Double, Double, Double) -> Unit,
    onStopNavigation: () -> Unit,
    onDemoInstruction: (Maneuver) -> Unit,
) {
    var startLatitude by rememberSaveable { mutableStateOf("28.2282") }
    var startLongitude by rememberSaveable { mutableStateOf("112.9388") }
    var endLatitude by rememberSaveable { mutableStateOf("28.2270") }
    var endLongitude by rememberSaveable { mutableStateOf("112.9400") }
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Sole Precision",
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Phone route guidance + independent backpack obstacle safety",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.secondary,
        )

        StatusCard(
            title = "Current guidance",
            body = instruction?.message ?: "No active instruction",
            isLive = true,
        )
        StatusCard(title = "Navigation", body = navigationStatus)
        StatusCard(title = "Wearable", body = wearableStatus)

        SectionTitle("1. Permissions and backpack")
        PrimaryAction("Grant required permissions", onRequestPermissions)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onConnectWearable,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
            ) {
                Text("Connect", fontSize = 18.sp)
            }
            OutlinedButton(
                onClick = onDisconnectWearable,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
            ) {
                Text("Disconnect", fontSize = 18.sp)
            }
        }

        SectionTitle("2. Walking route")
        Text(
            "Prototype input uses AMap/GCJ-02 coordinates. Destination search comes next.",
            fontSize = 16.sp,
        )
        CoordinateField("Start latitude", startLatitude) { startLatitude = it }
        CoordinateField("Start longitude", startLongitude) { startLongitude = it }
        CoordinateField("Destination latitude", endLatitude) { endLatitude = it }
        CoordinateField("Destination longitude", endLongitude) { endLongitude = it }
        inputError?.let {
            Text(it, color = Color(0xFFFFA8A8), fontWeight = FontWeight.Bold)
        }
        PrimaryAction(
            label = "Start walking guidance",
            onClick = {
                val values = listOf(
                    startLatitude.toDoubleOrNull(),
                    startLongitude.toDoubleOrNull(),
                    endLatitude.toDoubleOrNull(),
                    endLongitude.toDoubleOrNull(),
                )
                if (values.any { it == null }) {
                    inputError = "Enter four valid decimal coordinates"
                } else {
                    inputError = null
                    onStartWalkingRoute(
                        values[0]!!,
                        values[1]!!,
                        values[2]!!,
                        values[3]!!,
                    )
                }
            },
        )
        OutlinedButton(
            onClick = onStopNavigation,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
        ) {
            Text("Stop navigation", fontSize = 19.sp)
        }

        SectionTitle("3. Hardware test mode")
        Text(
            "These controls create the same BLE packets as live AMap callbacks.",
            fontSize = 16.sp,
        )
        DemoRow("Straight", Maneuver.STRAIGHT, onDemoInstruction)
        DemoRow("Turn left", Maneuver.LEFT, onDemoInstruction)
        DemoRow("Turn right", Maneuver.RIGHT, onDemoInstruction)
        DemoRow("Crosswalk", Maneuver.CROSSWALK, onDemoInstruction)
        DemoRow("Arrived", Maneuver.ARRIVED, onDemoInstruction)

        StatusCard(
            title = "Last 12-byte packet",
            body = lastPacketHex,
        )
        Text(
            text = "Safety rule: obstacle warnings generated on the backpack must always " +
                "override these route cues. The backpack must continue detecting obstacles " +
                "if the app, GPS or Bluetooth connection fails.",
            fontSize = 17.sp,
            lineHeight = 25.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
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
private fun DemoRow(
    label: String,
    maneuver: Maneuver,
    onDemoInstruction: (Maneuver) -> Unit,
) {
    OutlinedButton(
        onClick = { onDemoInstruction(maneuver) },
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
    ) {
        Text(label, fontSize = 18.sp)
    }
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
            .height(64.dp),
    ) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() },
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
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                title,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(body, fontSize = 21.sp, lineHeight = 29.sp)
        }
    }
}
