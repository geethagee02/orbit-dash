package dev.geetha.orbitdash.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF071018)
val InkRaised = Color(0xFF10202B)
val Paper = Color(0xFFF3EDE2)
val Mute = Color(0xFF8AA0A6)
val Teal = Color(0xFF3DDCB5)
val Amber = Color(0xFFFFB703)
val Coral = Color(0xFFFF5A45)

private val scheme = darkColorScheme(
    primary = Teal,
    onPrimary = Ink,
    secondary = Amber,
    onSecondary = Ink,
    background = Ink,
    onBackground = Paper,
    surface = InkRaised,
    onSurface = Paper,
    error = Coral,
    onError = Ink,
)

private val type = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 42.sp,
        lineHeight = 46.sp,
        color = Paper,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
        color = Paper,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = Paper,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = Paper,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Paper,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.6.sp,
        color = Mute,
    ),
)

@Composable
fun OrbitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
