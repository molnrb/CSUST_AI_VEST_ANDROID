package com.csust.soleprecision.ui

import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

internal enum class SwipeDirection {
    LEFT,
    RIGHT,
    UP,
    DOWN,
}

internal data class SwipeAction(
    val label: String,
    val symbol: String,
    val color: Color,
)

internal enum class SwipeScreenLayout {
    MENU,
    DECISION,
}

private enum class DragAxis {
    HORIZONTAL,
    VERTICAL,
}

/** The set of actions a screen currently offers, for spoken announcement. */
internal data class ScreenActions(
    val title: String,
    val right: String?,
    val left: String?,
    val up: String?,
    val down: String?,
    val usesButtons: Boolean,
)

/**
 * Receives the action set of whichever directional screen is showing. Provided by
 * the app shell so it can be spoken in the user's language at the right priority.
 */
internal val LocalActionAnnouncer =
    staticCompositionLocalOf<(ScreenActions) -> Unit> { {} }

/**
 * Full-screen directional interaction surface.
 *
 * Two operating modes:
 * 1. Swipe mode (default): one whole-screen card that moves on a single locked axis.
 * 2. Accessibility-service mode: when TalkBack touch exploration (or any service that
 *    consumes gestures) is active, raw swipes never reach the app, so the same actions
 *    are rendered as large conventional buttons and exposed as custom accessibility
 *    actions. This keeps every screen operable with a screen reader.
 */
@Composable
internal fun SwipeOnlyScreen(
    title: String,
    actions: Map<SwipeDirection, SwipeAction>,
    onSwipe: (SwipeDirection) -> Unit,
    modifier: Modifier = Modifier,
    layout: SwipeScreenLayout = SwipeScreenLayout.DECISION,
    content: @Composable ColumnScope.() -> Unit,
) {
    val usesButtons = rememberTouchExplorationEnabled()

    // Every available action is announced from the live action map, so a control can
    // never be shown without being spoken — screens with their own content text used
    // to leave their buttons unmentioned.
    val announcer = LocalActionAnnouncer.current
    LaunchedEffect(title, actions.keys.toList(), actions.values.map { it.label }) {
        announcer(
            ScreenActions(
                title = title,
                right = actions[SwipeDirection.RIGHT]?.label,
                left = actions[SwipeDirection.LEFT]?.label,
                up = actions[SwipeDirection.UP]?.label,
                down = actions[SwipeDirection.DOWN]?.label,
                usesButtons = usesButtons,
            ),
        )
    }

    if (usesButtons) {
        AccessibilityServiceScreen(
            title = title,
            actions = actions,
            onAction = onSwipe,
            modifier = modifier,
            content = content,
        )
        return
    }

    var offset by remember { mutableStateOf(Offset.Zero) }
    var isSettling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 80.dp.toPx() }
    var screenSize by remember { mutableStateOf(IntSize(1, 1)) }

    fun settle(direction: SwipeDirection?) {
        val action = direction?.let(actions::get)
        if (isSettling) return
        isSettling = true
        scope.launch {
            val animation = Animatable(offset, Offset.VectorConverter)
            if (direction == null || action == null) {
                animation.animateTo(Offset.Zero, tween(180)) {
                    offset = value
                }
                isSettling = false
                return@launch
            }
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            val exit = when (direction) {
                SwipeDirection.LEFT -> Offset(-screenSize.width * 1.1f, 0f)
                SwipeDirection.RIGHT -> Offset(screenSize.width * 1.1f, 0f)
                SwipeDirection.UP -> Offset(0f, -screenSize.height * 1.1f)
                SwipeDirection.DOWN -> Offset(0f, screenSize.height * 1.1f)
            }
            animation.animateTo(exit, tween(220)) {
                offset = value
            }
            onSwipe(direction)
            offset = Offset.Zero
            isSettling = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SemanticColors.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SemanticColors.Background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .semantics {
                    paneTitle = title
                    customActions = actions.map { (direction, action) ->
                        CustomAccessibilityAction(action.label) {
                            onSwipe(direction)
                            true
                        }
                    }
                }
                .graphicsLayer {
                    translationX = offset.x
                    translationY = offset.y
                }
            .onSizeChanged {
                screenSize = IntSize(
                    it.width.coerceAtLeast(1),
                    it.height.coerceAtLeast(1),
                )
            }
            .pointerInput(actions) {
                var totalDrag = Offset.Zero
                var lockedAxis: DragAxis? = null
                detectDragGestures(
                    onDragStart = {
                        totalDrag = Offset.Zero
                        lockedAxis = null
                        offset = Offset.Zero
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        if (!isSettling) {
                            totalDrag += amount
                            if (lockedAxis == null && totalDrag.getDistance() > 12.dp.toPx()) {
                                lockedAxis = if (abs(totalDrag.x) >= abs(totalDrag.y)) {
                                    DragAxis.HORIZONTAL
                                } else {
                                    DragAxis.VERTICAL
                                }
                            }
                            offset = when (lockedAxis) {
                                DragAxis.HORIZONTAL -> when {
                                    totalDrag.x < 0f &&
                                        actions.containsKey(SwipeDirection.LEFT) ->
                                        Offset(totalDrag.x, 0f)
                                    totalDrag.x > 0f &&
                                        actions.containsKey(SwipeDirection.RIGHT) ->
                                        Offset(totalDrag.x, 0f)
                                    else -> Offset.Zero
                                }
                                DragAxis.VERTICAL -> when {
                                    totalDrag.y < 0f &&
                                        actions.containsKey(SwipeDirection.UP) ->
                                        Offset(0f, totalDrag.y)
                                    totalDrag.y > 0f &&
                                        actions.containsKey(SwipeDirection.DOWN) ->
                                        Offset(0f, totalDrag.y)
                                    else -> Offset.Zero
                                }
                                null -> Offset.Zero
                            }
                        }
                    },
                    onDragCancel = { settle(null) },
                    onDragEnd = {
                        val direction = if (lockedAxis == DragAxis.HORIZONTAL) {
                            when {
                                totalDrag.x <= -threshold -> SwipeDirection.LEFT
                                totalDrag.x >= threshold -> SwipeDirection.RIGHT
                                else -> null
                            }
                        } else {
                            when {
                                totalDrag.y <= -threshold -> SwipeDirection.UP
                                totalDrag.y >= threshold -> SwipeDirection.DOWN
                                else -> null
                            }
                        }
                        settle(direction)
                    },
                )
            },
        ) {
            when (layout) {
                SwipeScreenLayout.MENU -> SwipeMenu(actions)
                SwipeScreenLayout.DECISION -> SwipeDecision(actions, content)
            }
        }
    }
}

/** Observes TalkBack-style touch exploration so the UI can switch to button mode live. */
@Composable
private fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val accessibilityManager = remember(context) {
        context.getSystemService(AccessibilityManager::class.java)
    }
    var enabled by remember {
        mutableStateOf(accessibilityManager?.isTouchExplorationEnabled == true)
    }
    DisposableEffect(accessibilityManager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener {
            enabled = it
        }
        accessibilityManager?.addTouchExplorationStateChangeListener(listener)
        onDispose {
            accessibilityManager?.removeTouchExplorationStateChangeListener(listener)
        }
    }
    return enabled
}

/**
 * Conventional large-button rendering of a swipe screen for screen-reader users.
 * Order: main content, then UP, RIGHT (confirm), LEFT (decline/next), DOWN (back),
 * so the confirming action is reached first while back stays last, matching the
 * spoken directional vocabulary used elsewhere.
 */
@Composable
private fun AccessibilityServiceScreen(
    title: String,
    actions: Map<SwipeDirection, SwipeAction>,
    onAction: (SwipeDirection) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SemanticColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .semantics { paneTitle = title },
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            capsLabel(title),
            color = SemanticColors.OnDark,
            style = labelStyle(32),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        content()
        val ordered = listOf(
            SwipeDirection.UP,
            SwipeDirection.RIGHT,
            SwipeDirection.LEFT,
            SwipeDirection.DOWN,
        )
        ordered.forEach { direction ->
            val action = actions[direction] ?: return@forEach
            val isNeutral = action.color == SemanticColors.Neutral || action.color == Color.White
            Button(
                onClick = { onAction(direction) },
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isNeutral) {
                        SemanticColors.SurfaceRaised
                    } else {
                        action.color
                    },
                    contentColor = if (isNeutral) {
                        SemanticColors.OnDark
                    } else {
                        SemanticColors.OnLight
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp),
            ) {
                Text(
                    capsLabel(action.label),
                    style = labelStyle(26),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.SwipeMenu(actions: Map<SwipeDirection, SwipeAction>) {
    actions.forEach { (direction, action) ->
        SwipeMenuSection(
            action = action,
            direction = direction,
            modifier = Modifier
                .fillMaxWidth()
                .weight(
                    when (direction) {
                        SwipeDirection.UP -> 0.65f
                        SwipeDirection.DOWN -> 0.45f
                        SwipeDirection.LEFT,
                        SwipeDirection.RIGHT,
                        -> 1f
                    },
                ),
        )
    }
}

@Composable
private fun ColumnScope.SwipeDecision(
    actions: Map<SwipeDirection, SwipeAction>,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(2f)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
    actions[SwipeDirection.UP]?.let { action ->
        SwipeMenuSection(
            action = action,
            direction = SwipeDirection.UP,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.55f),
        )
    }
    if (
        actions.containsKey(SwipeDirection.LEFT) ||
        actions.containsKey(SwipeDirection.RIGHT)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.72f),
        ) {
            actions[SwipeDirection.LEFT]?.let {
                SwipeDecisionSection(it, SwipeDirection.LEFT, Modifier.weight(1f))
            } ?: Box(Modifier.weight(1f))
            actions[SwipeDirection.RIGHT]?.let {
                SwipeDecisionSection(it, SwipeDirection.RIGHT, Modifier.weight(1f))
            } ?: Box(Modifier.weight(1f))
        }
    }
    actions[SwipeDirection.DOWN]?.let { action ->
        SwipeMenuSection(
            action = action,
            direction = SwipeDirection.DOWN,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.46f),
        )
    }
}

/**
 * A whole-width band for one direction. The direction's semantic colour is carried
 * by a leading edge bar and the arrow, so a low-vision user can tell confirm from
 * decline from back without reading the label.
 */
@Composable
private fun SwipeMenuSection(
    action: SwipeAction,
    direction: SwipeDirection,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.background(SemanticColors.Background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 10.dp)
                .width(12.dp)
                .fillMaxHeight()
                .background(action.color, RoundedCornerShape(6.dp)),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                capsLabel(action.label),
                color = SemanticColors.OnDark,
                style = labelStyle(44),
                textAlign = TextAlign.Center,
            )
            AnimatedDirectionArrow(direction, action.color)
        }
    }
}

@Composable
private fun SwipeDecisionSection(
    action: SwipeAction,
    direction: SwipeDirection,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.background(SemanticColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (action.symbol) {
                "✓" -> Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = action.color,
                    modifier = Modifier.size(84.dp),
                )
                "✕" -> Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    tint = action.color,
                    modifier = Modifier.size(84.dp),
                )
                else -> AnimatedDirectionArrow(direction, action.color)
            }
            Text(
                capsLabel(action.label),
                color = action.color,
                style = labelStyle(26, FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

private fun SwipeDirection.arrowIcon(): ImageVector = when (this) {
    SwipeDirection.LEFT -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
    SwipeDirection.RIGHT -> Icons.AutoMirrored.Filled.KeyboardArrowRight
    SwipeDirection.UP -> Icons.Filled.KeyboardArrowUp
    SwipeDirection.DOWN -> Icons.Filled.KeyboardArrowDown
}

@Composable
private fun AnimatedDirectionArrow(
    direction: SwipeDirection,
    tint: Color = Color.White,
) {
    val transition = rememberInfiniteTransition(label = "swipe arrow")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100),
            repeatMode = RepeatMode.Restart,
        ),
        label = "swipe arrow progress",
    )
    val distance = 14f * progress
    val x = when (direction) {
        SwipeDirection.LEFT -> -distance
        SwipeDirection.RIGHT -> distance
        else -> 0f
    }
    val y = when (direction) {
        SwipeDirection.UP -> -distance
        SwipeDirection.DOWN -> distance
        else -> 0f
    }

    Icon(
        imageVector = direction.arrowIcon(),
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(96.dp)
            .graphicsLayer {
                translationX = x
                translationY = y
                alpha = 0.45f + (1f - progress) * 0.55f
            },
    )
}
