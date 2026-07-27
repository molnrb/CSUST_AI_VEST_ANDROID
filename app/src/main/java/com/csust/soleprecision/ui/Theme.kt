package com.csust.soleprecision.ui

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Colour carries meaning here, it is not decoration. The same hue always means the
 * same kind of action, so low-vision users can act on colour alone and the spoken
 * vocabulary and the visuals agree:
 *
 * - amber [Confirm]: go, confirm, continue — the primary path (swipe right)
 * - orange [Optional]: an extra, non-essential action (swipe up)
 * - red [Decline]: decline, stop, destructive (swipe left, end)
 * - white [Neutral]: back and movement between screens (swipe down)
 */
object SemanticColors {
    val Confirm = Color(0xFFFFC400)
    val Optional = Color(0xFFFF8A1F)
    val Decline = Color(0xFFFF5A4E)
    val Neutral = Color(0xFFF2F2F2)
    val Background = Color(0xFF07070A)
    val Surface = Color(0xFF15151A)
    val SurfaceRaised = Color(0xFF23232B)
    val OnDark = Color.White
    val OnLight = Color(0xFF101014)
}

internal val ProductionColorScheme = darkColorScheme(
    primary = SemanticColors.Confirm,
    onPrimary = SemanticColors.OnLight,
    secondary = SemanticColors.Optional,
    onSecondary = SemanticColors.OnLight,
    tertiary = SemanticColors.Optional,
    background = SemanticColors.Background,
    onBackground = SemanticColors.OnDark,
    surface = SemanticColors.Surface,
    onSurface = SemanticColors.OnDark,
    surfaceVariant = SemanticColors.SurfaceRaised,
    onSurfaceVariant = SemanticColors.OnDark,
    error = SemanticColors.Decline,
    onError = SemanticColors.OnLight,
    outline = Color(0xFF4A4A55),
)

/**
 * Display face for short labels and titles. Uses the device's heaviest condensed
 * family so the app gets a genuinely different typeface rather than faked bold,
 * with no bundled or downloaded font — this app has to work offline.
 */
internal val DisplayFontFamily = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-condensed"), weight = FontWeight.Black),
    Font(DeviceFontFamilyName("sans-serif-condensed"), weight = FontWeight.Bold),
    Font(DeviceFontFamilyName("sans-serif"), weight = FontWeight.Black),
)

internal fun labelStyle(
    fontSize: Int,
    weight: FontWeight = FontWeight.Black,
): TextStyle = TextStyle(
    fontFamily = DisplayFontFamily,
    fontWeight = weight,
    fontSize = fontSize.sp,
    lineHeight = (fontSize * 1.14f).sp,
    letterSpacing = 0.05.em,
)

internal val ProductionTypography = Typography(
    displayLarge = labelStyle(52),
    displayMedium = labelStyle(42),
    headlineLarge = labelStyle(34),
    headlineMedium = labelStyle(28),
    titleLarge = labelStyle(24, FontWeight.Bold),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 30.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
)

/**
 * Caps are applied only to short labels. All-caps destroys word shape, which is
 * exactly the cue low-vision readers rely on, and screen readers sometimes spell
 * short all-caps strings out letter by letter — so sentences stay in sentence case.
 * Chinese is unaffected by [String.uppercase], which keeps this safe to apply
 * uniformly across languages.
 */
internal fun capsLabel(text: String): String =
    if (text.length <= 24 && text.count { it == ' ' } <= 3) text.uppercase() else text
