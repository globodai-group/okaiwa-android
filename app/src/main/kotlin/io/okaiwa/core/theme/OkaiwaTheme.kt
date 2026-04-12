package io.okaiwa.core.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Okaiwa brand palette — matches the Figma "OKAIWA" design kit.
 *
 * The visual identity is dark-first: a near-black background punctuated by
 * the lime yellow whale/wave mark (#E5F240). The lighter tones are used
 * for surfaces, the darker tones for hierarchy.
 */
object OkaiwaColors {
    // Brand
    val Lime = Color(0xFFE5F240)
    val LimePressed = Color(0xFFC8D935)
    val LimeDim = Color(0xFF7A8221)

    // Dark canvas — the main app background.
    val Black = Color(0xFF0F0F0F)
    val BlackElevated = Color(0xFF1A1A1A)
    val BlackCard = Color(0xFF242424)
    val BlackBorder = Color(0xFF2F2F2F)

    // Text
    val White = Color(0xFFFFFFFF)
    val WhiteDim = Color(0xFFCFCFCF)
    val Muted = Color(0xFF8A8A8A)
    val Placeholder = Color(0xFF5C5C5C)

    // Semantic
    val Error = Color(0xFFE53935)
    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFFFB300)
}

private val OkaiwaDarkScheme = darkColorScheme(
    primary = OkaiwaColors.Lime,
    onPrimary = OkaiwaColors.Black,
    primaryContainer = OkaiwaColors.LimePressed,
    onPrimaryContainer = OkaiwaColors.Black,

    secondary = OkaiwaColors.Lime,
    onSecondary = OkaiwaColors.Black,
    secondaryContainer = OkaiwaColors.BlackElevated,
    onSecondaryContainer = OkaiwaColors.White,

    tertiary = OkaiwaColors.Lime,
    onTertiary = OkaiwaColors.Black,

    background = OkaiwaColors.Black,
    onBackground = OkaiwaColors.White,

    surface = OkaiwaColors.Black,
    onSurface = OkaiwaColors.White,
    surfaceVariant = OkaiwaColors.BlackCard,
    onSurfaceVariant = OkaiwaColors.WhiteDim,
    surfaceContainer = OkaiwaColors.BlackElevated,
    surfaceContainerHigh = OkaiwaColors.BlackCard,

    outline = OkaiwaColors.BlackBorder,
    outlineVariant = OkaiwaColors.BlackBorder,

    error = OkaiwaColors.Error,
    onError = OkaiwaColors.White,
)

private val OkaiwaLightScheme = lightColorScheme(
    // We deliberately ship a "light mode" that is still dark-tinted — the
    // Okaiwa brand is dark-canvas regardless of system setting. Pure white
    // would dilute the identity. System light users just get slightly
    // warmer surfaces.
    primary = OkaiwaColors.Lime,
    onPrimary = OkaiwaColors.Black,
    primaryContainer = OkaiwaColors.LimePressed,
    onPrimaryContainer = OkaiwaColors.Black,

    secondary = OkaiwaColors.Lime,
    onSecondary = OkaiwaColors.Black,

    background = OkaiwaColors.Black,
    onBackground = OkaiwaColors.White,
    surface = OkaiwaColors.Black,
    onSurface = OkaiwaColors.White,
    surfaceVariant = OkaiwaColors.BlackCard,
    onSurfaceVariant = OkaiwaColors.WhiteDim,

    outline = OkaiwaColors.BlackBorder,
    error = OkaiwaColors.Error,
    onError = OkaiwaColors.White,
)

/**
 * Typography — placeholder using the system sans-serif until the final
 * Figma typography system is imported via the Figma MCP.
 */
private val OkaiwaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

/**
 * Okaiwa Material 3 theme.
 *
 * The brand is dark-first. Dynamic Material You is intentionally disabled
 * so the lime identity renders identically on every device. Once the
 * Figma MCP is wired up the typography block will be swapped for the
 * canonical type ramp.
 */
@Composable
fun OkaiwaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> OkaiwaDarkScheme
        else -> OkaiwaLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OkaiwaTypography,
        content = content,
    )
}
