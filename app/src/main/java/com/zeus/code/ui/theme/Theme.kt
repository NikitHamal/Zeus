package com.zeus.code.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.zeus.code.R

private val ZeusYellow = Color(0xFFFFD84D)
private val ZeusInk = Color(0xFF17151F)
private val ZeusCream = Color(0xFFFFFBF2)
private val ZeusSurface = Color(0xFFFAF9FD)

private val LightColors = lightColorScheme(
    primary = ZeusInk,
    onPrimary = Color.White,
    primaryContainer = ZeusYellow,
    onPrimaryContainer = ZeusInk,
    secondary = Color(0xFF655F78),
    secondaryContainer = Color(0xFFECE6F4),
    background = ZeusSurface,
    onBackground = ZeusInk,
    surface = Color.White,
    onSurface = ZeusInk,
    surfaceVariant = Color(0xFFF0EEF5),
    outlineVariant = Color(0xFFE2DFE8),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = ZeusYellow,
    onPrimary = ZeusInk,
    primaryContainer = Color(0xFF3F3510),
    onPrimaryContainer = Color(0xFFFFF0A8),
    secondary = Color(0xFFCCC3DD),
    background = Color(0xFF111016),
    onBackground = Color(0xFFF0ECF5),
    surface = Color(0xFF19171F),
    onSurface = Color(0xFFF0ECF5),
    surfaceVariant = Color(0xFF25222D),
    outlineVariant = Color(0xFF3A3643)
)

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private fun poppins(weight: FontWeight) = Font(
    googleFont = GoogleFont("Poppins"),
    fontProvider = provider,
    weight = weight
)

private val Poppins = FontFamily(
    poppins(FontWeight.Normal),
    poppins(FontWeight.Medium),
    poppins(FontWeight.SemiBold),
    poppins(FontWeight.Bold)
)

private val ZeusTypography = Typography(
    displaySmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
)

@Composable
fun ZeusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
        }
    }
    MaterialTheme(colorScheme = colors, typography = ZeusTypography, content = content)
}
