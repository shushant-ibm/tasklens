package dev.shushant.tasklens.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TlPrimary = Color(0xFF5B8DEF)
private val TlPrimaryVariant = Color(0xFF3A6CD0)
private val TlSecondary = Color(0xFF03DAC6)
private val TlSurface = Color(0xFFFAFAFA)
private val TlSurfaceDark = Color(0xFF1E1E2E)
private val TlError = Color(0xFFCF6679)
private val TlOnPrimary = Color.White
private val TlOnSecondary = Color.Black
private val TlOnBackground = Color(0xFF1C1B1F)
private val TlOnSurface = Color(0xFF1C1B1F)

private val LightColorScheme = lightColorScheme(
    primary = TlPrimary,
    onPrimary = TlOnPrimary,
    secondary = TlSecondary,
    onSecondary = TlOnSecondary,
    error = TlError,
    background = TlSurface,
    onBackground = TlOnBackground,
    surface = TlSurface,
    onSurface = TlOnSurface
)

private val DarkColorScheme = darkColorScheme(
    primary = TlPrimaryVariant,
    onPrimary = TlOnPrimary,
    secondary = TlSecondary,
    onSecondary = TlOnSecondary,
    error = TlError,
    background = TlSurfaceDark,
    onBackground = Color.White,
    surface = TlSurfaceDark,
    onSurface = Color.White
)

@Composable
fun TaskLensUiTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
