
package com.zeus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = ZeusBlack,
    onPrimary = Color.White,
    primaryContainer = ZeusBlack,
    secondary = ZeusGray600,
    background = ZeusWhite,
    surface = ZeusWhite,
    surfaceVariant = ZeusGray100,
    surfaceContainer = ZeusSurface,
    outline = ZeusGray200,
    error = ZeusError
)

private val DarkColors = darkColorScheme(
    primary = ZeusWhite,
    onPrimary = ZeusBlack,
    primaryContainer = ZeusWhite,
    secondary = ZeusGray400,
    background = Color(0xFF0A0A0A),
    surface = ZeusSurfaceDark,
    surfaceVariant = ZeusCardDark,
    surfaceContainer = Color(0xFF1A1A1A),
    outline = Color(0xFF2A2A2A),
    error = ZeusError
)

@Composable
fun ZeusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ZeusTypography,
        content = content
    )
}
