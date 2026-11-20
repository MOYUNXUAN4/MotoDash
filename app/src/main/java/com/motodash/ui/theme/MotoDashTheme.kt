package com.motodash.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object MotoDashColors {
    val PureBlack = Color(0xFF000000)
    val DeckSurface = Color(0xFF0C0F14)
    val CardSurface = Color(0xFF141920)
    val PillSurface = Color(0xFF161B22)
    val PillBorder = Color(0xFF2B333E)
    val HairlineDivider = Color(0xFF1F2630)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9CA3AF)
    val TextDimmed = Color(0xFF6B7280)

    val SpotifyGreen = Color(0xFF1DB954)
    val SpotifyGreenSubtle = Color(0x1F1DB954)

    val SpeedNormal = Color(0xFFFFFFFF)
    val SpeedAmber = Color(0xFFFFC107)
    val SpeedRed = Color(0xFFFF3B30)

    val GpsGreen = Color(0xFF22C55E)
    val GpsYellow = Color(0xFFF59E0B)
    val GpsRed = Color(0xFFEF4444)

    val WazeCyan = Color(0xFF33CCFF)
    val SunlightAmber = Color(0xFFFFB300)
}

private val MotoDashDarkColorScheme = darkColorScheme(
    primary = MotoDashColors.SpotifyGreen,
    onPrimary = MotoDashColors.PureBlack,
    secondary = MotoDashColors.WazeCyan,
    onSecondary = MotoDashColors.PureBlack,
    background = MotoDashColors.PureBlack,
    onBackground = MotoDashColors.TextPrimary,
    surface = MotoDashColors.DeckSurface,
    onSurface = MotoDashColors.TextPrimary,
    surfaceVariant = MotoDashColors.CardSurface,
    onSurfaceVariant = MotoDashColors.TextSecondary,
    error = MotoDashColors.SpeedRed,
    onError = MotoDashColors.TextPrimary
)

@Composable
fun MotoDashTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MotoDashDarkColorScheme,
        content = content
    )
}
