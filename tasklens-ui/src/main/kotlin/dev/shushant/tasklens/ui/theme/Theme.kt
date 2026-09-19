package dev.shushant.tasklens.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF0D1117)
val DarkSurface = Color(0xFF161B22)
val DarkBorder = Color(0xFF30363D)
val AccentBlue = Color(0xFF58A6FF)
val SuccessGreen = Color(0xFF3FB950)
val DangerRed = Color(0xFFF85149)
val WarningYellow = Color(0xFFD29922)
val TextPrimary = Color(0xFFF0F6FC)
val TextSecondary = Color(0xFF8B949E)

private val TaskLensColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.Black,
    secondary = Color(0xFF1F6FEB),
    onSecondary = Color.White,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    error = DangerRed,
    onError = Color.White,
    outline = DarkBorder
)

@Composable
fun TaskLensTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TaskLensColorScheme,
        content = content
    )
}
