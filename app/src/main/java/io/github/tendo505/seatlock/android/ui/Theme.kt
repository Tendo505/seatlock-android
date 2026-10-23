package io.github.tendo505.seatlock.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

private val Teal = Color(0xFF0F766E)
private val TealLight = Color(0xFF2DD4BF)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Color(0xFF475569),
    secondaryContainer = Color(0xFFD5EFEA),
    onSecondaryContainer = Color(0xFF0B3B37),
    tertiary = Color(0xFFC2410C),
    background = Color(0xFFF6F7F5),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDF0EE),
    surfaceContainer = Color(0xFFEEF2F0),
    error = Color(0xFFB42318),
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF134E4A),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFFCBD5E1),
    secondaryContainer = Color(0xFF1E3A37),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFFFB923C),
    background = Color(0xFF0E1312),
    surface = Color(0xFF151B1A),
    surfaceVariant = Color(0xFF1F2725),
    surfaceContainer = Color(0xFF1A2120),
    error = Color(0xFFF87171),
)

@Composable
fun SeatLockTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = base.copy(
            headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        ),
        content = content,
    )
}
