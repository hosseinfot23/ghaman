package com.zaminchaman.app.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection

val PitchGreen = Color(0xFF1B7F3B)
val DebtRed = Color(0xFFC62828)
val SettledGreen = Color(0xFF2E7D32)
val PlannedBlue = Color(0xFF1565C0)
val MutedGray = Color(0xFF6B7A6F)

/**
 * To use Vazirmatn: put vazirmatn_regular.ttf / vazirmatn_bold.ttf in res/font and replace with
 * FontFamily(Font(R.font.vazirmatn_regular), Font(R.font.vazirmatn_bold, FontWeight.Bold)).
 * Until then the Android system font (which fully supports Persian) is used.
 */
val AppFontFamily: FontFamily = FontFamily.Default

private val Scheme = lightColorScheme(
    primary = PitchGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F0DD),
    onPrimaryContainer = Color(0xFF0A3A1A),
    secondary = Color(0xFF4F6354),
    background = Color(0xFFF6F8F6),
    surface = Color.White,
    surfaceVariant = Color(0xFFE6EEE7),
    error = DebtRed
)

private fun Typography.withFont(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f), headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f), bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f)
)

/** The whole app is right-to-left, independent of the phone's language. */
@Composable
fun ZaminTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = Scheme, typography = Typography().withFont(AppFontFamily), content = content)
    }
}
