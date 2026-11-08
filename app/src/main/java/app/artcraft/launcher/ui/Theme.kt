package app.artcraft.launcher.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFFFF8A3D), onPrimary = Color(0xFF2B1200),
    secondary = Color(0xFF6EA8FF), onSecondary = Color(0xFF00203F),
    background = Color(0xFF0F1115), onBackground = Color(0xFFE6E8EE),
    surface = Color(0xFF0F1115), onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF232832), onSurfaceVariant = Color(0xFFA9AFBD),
    surfaceContainerLow = Color(0xFF171A21), surfaceContainerHigh = Color(0xFF1E222B),
    error = Color(0xFFFF6B6B),
)

private val Light = lightColorScheme(
    primary = Color(0xFFB85100), onPrimary = Color.White,
    secondary = Color(0xFF1F5FBF),
    background = Color(0xFFFAF8F6), surface = Color(0xFFFAF8F6),
    surfaceContainerLow = Color(0xFFF2EEEA),
)

@Composable
fun ArtCraftTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
