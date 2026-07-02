package com.thesmallmarket.arrumacomigo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightColors = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = PurpleContainer,
    onPrimaryContainer = PurplePrimaryDark,
    secondary = VioletSecondary,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    tertiary = LilacTertiary,
    background = LavenderBackground,
    onBackground = InkText,
    surface = LavenderSurface,
    onSurface = InkText,
    surfaceVariant = LavenderSurface,
    onSurfaceVariant = MutedText,
    error = SoftError,
    outline = MutedText,
)

private val DarkColors = darkColorScheme(
    primary = PurplePrimaryLight,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF2A1D5E),
    primaryContainer = PurpleContainerDark,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,
    secondary = VioletSecondaryDark,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    tertiary = LilacTertiary,
    background = DarkBackground,
    onBackground = InkTextDark,
    surface = DarkSurface,
    onSurface = InkTextDark,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = MutedTextDark,
    error = SoftError,
    outline = MutedTextDark,
)

@Composable
fun ArrumaComigoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val neo = if (darkTheme) {
        NeumorphicColors(DarkSurface, NeoDarkHighlight, NeoDarkShadow)
    } else {
        NeumorphicColors(LavenderSurface, NeoLightHighlight, NeoLightShadow)
    }

    CompositionLocalProvider(LocalNeumorphicColors provides neo) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
