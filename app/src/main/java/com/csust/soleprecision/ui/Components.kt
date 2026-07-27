package com.csust.soleprecision.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
internal fun StandardScreen(
    title: String,
    backLabel: String,
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
            .background(SemanticColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .then(contentModifier)
            .padding(16.dp)
            .semantics { paneTitle = title },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OutlinedButton(
            onClick = onBack,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = SemanticColors.SurfaceRaised,
                contentColor = SemanticColors.OnDark,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Text(
                capsLabel(backLabel),
                style = labelStyle(24, FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            capsLabel(title),
            style = labelStyle(38),
            color = SemanticColors.OnDark,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
        )
        content()
    }
}

@Composable
internal fun HomeButton(
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
internal fun LargeAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    optional: Boolean = false,
    stateDescription: String? = null,
    live: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                destructive -> SemanticColors.Decline
                optional -> SemanticColors.Optional
                else -> SemanticColors.Confirm
            },
            contentColor = SemanticColors.OnLight,
        ),
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
                capsLabel(label),
                style = labelStyle(30),
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
internal fun LargeOutlinedAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SemanticColors.SurfaceRaised,
            contentColor = SemanticColors.OnDark,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 116.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                capsLabel(label),
                style = labelStyle(26, FontWeight.Bold),
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
internal fun SelectionAction(
    label: String,
    selected: Boolean,
    selectedPrefixTemplate: String,
    selectedStateLabel: String,
    notSelectedStateLabel: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) SemanticColors.Confirm else SemanticColors.Surface,
            contentColor = if (selected) SemanticColors.OnLight else SemanticColors.OnDark,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .semantics {
                stateDescription = if (selected) selectedStateLabel else notSelectedStateLabel
            },
    ) {
        Text(
            if (selected) selectedPrefixTemplate.format(label) else label,
            style = labelStyle(22, if (selected) FontWeight.Black else FontWeight.Bold),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun SettingHeading(text: String) {
    Text(
        capsLabel(text),
        style = labelStyle(26),
        color = SemanticColors.Optional,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .semantics { heading() },
    )
}

@Composable
internal fun StepSetting(
    value: String,
    decreaseLabel: String,
    increaseLabel: String,
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
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = SemanticColors.SurfaceRaised,
                contentColor = SemanticColors.OnDark,
            ),
            modifier = Modifier
                .weight(1f)
                .height(76.dp),
        ) {
            Text(capsLabel(decreaseLabel), style = labelStyle(19, FontWeight.Bold))
        }
        OutlinedButton(
            onClick = onIncrease,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = SemanticColors.SurfaceRaised,
                contentColor = SemanticColors.OnDark,
            ),
            modifier = Modifier
                .weight(1f)
                .height(76.dp),
        ) {
            Text(capsLabel(increaseLabel), style = labelStyle(19, FontWeight.Bold))
        }
    }
}

internal fun distanceMeters(
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
