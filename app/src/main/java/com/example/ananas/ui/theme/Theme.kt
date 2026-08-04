package com.example.ananas.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Ananas intentionally uses a studio-dark workspace. Photo colors remain neutral,
 * controls stay legible in every system theme, and the canvas does not visually shift
 * when the device toggles light/dark mode.
 */
private val StudioColors = darkColorScheme(
    primary = Color(0xFFB7A6FF),
    onPrimary = Color(0xFF160D3A),
    primaryContainer = Color(0xFF30245F),
    onPrimaryContainer = Color(0xFFE8E2FF),
    secondary = Color(0xFF63E6C3),
    onSecondary = Color(0xFF00251C),
    secondaryContainer = Color(0xFF123F37),
    onSecondaryContainer = Color(0xFFB8F8E7),
    tertiary = Color(0xFFFFB86A),
    onTertiary = Color(0xFF351900),
    background = Color(0xFF07080C),
    onBackground = Color(0xFFF7F7FC),
    surface = Color(0xFF101218),
    onSurface = Color(0xFFF7F7FC),
    surfaceVariant = Color(0xFF1B1E27),
    onSurfaceVariant = Color(0xFFC5C8D5),
    outline = Color(0xFF444957),
    outlineVariant = Color(0xFF292D38),
    error = Color(0xFFFF7A8A),
    onError = Color(0xFF3D0010),
    errorContainer = Color(0xFF5A1525),
    onErrorContainer = Color(0xFFFFD9DE),
    scrim = Color.Black,
)

private val StudioTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 38.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
)

private val StudioShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(34.dp),
)

@Composable
fun AnanasTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            run {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(
        colorScheme = StudioColors,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content,
    )
}
