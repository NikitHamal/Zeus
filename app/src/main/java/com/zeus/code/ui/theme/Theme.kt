package com.zeus.code.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.zeus.code.R

/** Theme preference persisted in Settings. LIGHT is the product default. */
enum class ZeusThemeMode(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System")
}

/* ------------------------------------------------------------------------- */
/* Zeus brand palette — warm paper, deep ink, electric gold                   */
/* ------------------------------------------------------------------------- */

val ZeusGold = Color(0xFFFFD34D)
val ZeusGoldSoft = Color(0xFFFFE9A8)
private val Ink = Color(0xFF211D2B)
private val InkSoft = Color(0xFF47424F)
private val Paper = Color(0xFFFCFAF4)
private val PaperDeep = Color(0xFFF4F1E9)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = ZeusGold,
    onPrimaryContainer = Color(0xFF403000),
    inversePrimary = ZeusGold,
    secondary = Color(0xFF7C5E00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE9A8),
    onSecondaryContainer = Color(0xFF4A3A00),
    tertiary = Color(0xFF5B5670),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6E0F5),
    onTertiaryContainer = Color(0xFF2E2942),
    background = Paper,
    onBackground = Color(0xFF1E1B16),
    surface = Paper,
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFEFEADB),
    onSurfaceVariant = Color(0xFF4E4738),
    surfaceTint = Ink,
    inverseSurface = Color(0xFF333027),
    inverseOnSurface = Color(0xFFF5F0E4),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF807662),
    outlineVariant = Color(0xFFD3CCB8),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFDF8),
    surfaceDim = PaperDeep,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F5EC),
    surfaceContainer = Color(0xFFF2EFE5),
    surfaceContainerHigh = Color(0xFFECE9DE),
    surfaceContainerHighest = Color(0xFFE6E3D8)
)

private val DarkColors = darkColorScheme(
    primary = ZeusGold,
    onPrimary = Color(0xFF332700),
    primaryContainer = Color(0xFF5C4700),
    onPrimaryContainer = ZeusGoldSoft,
    inversePrimary = Color(0xFF7C5E00),
    secondary = Color(0xFFE0C26C),
    onSecondary = Color(0xFF3B2F00),
    secondaryContainer = Color(0xFF564500),
    onSecondaryContainer = Color(0xFFFFE9A8),
    tertiary = Color(0xFFCDC6E9),
    onTertiary = Color(0xFF322C4D),
    tertiaryContainer = Color(0xFF494365),
    onTertiaryContainer = Color(0xFFE6E0F5),
    background = Color(0xFF141118),
    onBackground = Color(0xFFEAE4DD),
    surface = Color(0xFF141118),
    onSurface = Color(0xFFEAE4DD),
    surfaceVariant = Color(0xFF4B473F),
    onSurfaceVariant = Color(0xFFD1C7B5),
    surfaceTint = ZeusGold,
    inverseSurface = Color(0xFFEAE4DD),
    inverseOnSurface = Color(0xFF333028),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF9A917F),
    outlineVariant = Color(0xFF4B473F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3B373D),
    surfaceDim = Color(0xFF141118),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1A20),
    surfaceContainer = Color(0xFF211E24),
    surfaceContainerHigh = Color(0xFF2C282F),
    surfaceContainerHighest = Color(0xFF37333A)
)

/* ------------------------------------------------------------------------- */
/* Shapes — Material 3 Expressive geometry                                    */
/* ------------------------------------------------------------------------- */

private val ZeusShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/* ------------------------------------------------------------------------- */
/* Typography — compact M3 ramp.                                              */
/* Brand voice stays on Poppins for display/headings/titles. Body & label    */
/* text use the platform font: Poppins lacks many glyphs (arrows, box        */
/* drawing, math, CJK…) which previously rendered as tofu — the platform     */
/* font has full fallback coverage.                                           */
/* ------------------------------------------------------------------------- */

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

private val PlatformFont = FontFamily.Default

private val ZeusTypography = Typography(
    displayLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp),
    displaySmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 25.sp, lineHeight = 31.sp),
    headlineMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    headlineSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = PlatformFont, fontWeight = FontWeight.Normal, fontSize = 14.5.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodyMedium = TextStyle(fontFamily = PlatformFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontFamily = PlatformFont, fontWeight = FontWeight.Normal, fontSize = 11.5.sp, lineHeight = 15.sp, letterSpacing = 0.3.sp),
    labelLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = PlatformFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = PlatformFont, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 0.4.sp)
)

/* ------------------------------------------------------------------------- */
/* Theme                                                                     */
/* ------------------------------------------------------------------------- */

@Composable
fun ZeusTheme(
    mode: ZeusThemeMode = ZeusThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val darkTheme = when (mode) {
        ZeusThemeMode.LIGHT -> false
        ZeusThemeMode.DARK -> true
        ZeusThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = ZeusTypography,
        shapes = ZeusShapes,
        content = content
    )
}
