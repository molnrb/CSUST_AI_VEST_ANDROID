package com.csust.soleprecision.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
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

@Composable
internal fun SwipeOnlyScreen(
    title: String,
    actions: Map<SwipeDirection, SwipeAction>,
    onSwipe: (SwipeDirection) -> Unit,
    modifier: Modifier = Modifier,
    layout: SwipeScreenLayout = SwipeScreenLayout.DECISION,
    content: @Composable ColumnScope.() -> Unit,
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isSettling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
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
            .background(Color.Black),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
                .semantics { paneTitle = title }
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

@Composable
private fun SwipeMenuSection(
    action: SwipeAction,
    direction: SwipeDirection,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                action.label,
                color = Color.White,
                fontSize = 48.sp,
                lineHeight = 56.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            AnimatedDirectionArrow(direction)
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
        modifier = modifier
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (action.symbol == "✕" || action.symbol == "✓") {
                    action.symbol
                } else {
                    action.label
                },
                color = action.color,
                fontSize = 96.sp,
                lineHeight = 102.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            AnimatedDirectionArrow(direction)
        }
    }
}

@Composable
private fun AnimatedDirectionArrow(direction: SwipeDirection) {
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
    val arrow = when (direction) {
        SwipeDirection.LEFT -> "←"
        SwipeDirection.RIGHT -> "→"
        SwipeDirection.UP -> "↑"
        SwipeDirection.DOWN -> "↓"
    }

    Text(
        text = arrow,
        fontSize = 88.sp,
        lineHeight = 94.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.graphicsLayer {
            translationX = x
            translationY = y
            alpha = 0.45f + (1f - progress) * 0.55f
        },
    )
}
