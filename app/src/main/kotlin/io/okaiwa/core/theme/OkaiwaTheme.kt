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

// Brand colors — aligned with iOS teal (#009688)
private val OkaiwaPrimary = Color(0xFF009688)
private val OkaiwaPrimaryVariant = Color(0xFF00796B)
private val OkaiwaSecondary = Color(0xFF00BCD4)
private val OkaiwaError = Color(0xFFE53935)

// Surface colors
private val OkaiwaSurfaceLight = Color(0xFFFAFAFA)
private val OkaiwaSurfaceDark = Color(0xFF121212)
private val OkaiwaBackgroundLight = Color(0xFFFFFFFF)
private val OkaiwaBackgroundDark = Color(0xFF0A0A0A)

private val DarkColorScheme = darkColorScheme(
    primary = OkaiwaPrimary,
    onPrimary = Color.White,
    primaryContainer = OkaiwaPrimaryVariant,
    secondary = OkaiwaSecondary,
    onSecondary = Color.Black,
    error = OkaiwaError,
    onError = Color.White,
    background = OkaiwaBackgroundDark,
    onBackground = Color(0xFFE0E0E0),
    surface = OkaiwaSurfaceDark,
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF424242),
)

private val LightColorScheme = lightColorScheme(
    primary = OkaiwaPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    secondary = OkaiwaSecondary,
    onSecondary = Color.White,
    error = OkaiwaError,
    onError = Color.White,
    background = OkaiwaBackgroundLight,
    onBackground = Color(0xFF1C1B1F),
    surface = OkaiwaSurfaceLight,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFFCAC4D0),
)

private val OkaiwaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
)

/**
 * Okaiwa Material 3 theme.
 *
 * Supports light/dark mode and Android 12+ dynamic color when available.
 * Falls back to Okaiwa brand colors on older devices.
 */
@Composable
fun OkaiwaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OkaiwaTypography,
        content = content,
    )
}
