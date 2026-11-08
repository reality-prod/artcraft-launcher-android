package app.artcraft.launcher.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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

private val MediumContrastDark = darkColorScheme(
    primary = Color(0xFFFFB564), onPrimary = Color(0xFF2B1200),
    secondary = Color(0xFF8AB4FF), onSecondary = Color(0xFF00203F),
    background = Color(0xFF1A1C20), onBackground = Color(0xFFE6E8EE),
    surface = Color(0xFF1A1C20), onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF2D323C), onSurfaceVariant = Color(0xFFA9AFBD),
    surfaceContainerLow = Color(0xFF22262D), surfaceContainerHigh = Color(0xFF2A2E36),
    error = Color(0xFFFF8989),
)

private val HighContrastDark = darkColorScheme(
    primary = Color(0xFFFFD7A8), onPrimary = Color(0xFF2B1200),
    secondary = Color(0xFFC4D8FF), onSecondary = Color(0xFF00203F),
    background = Color(0xFF000000), onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000), onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF42464E), onSurfaceVariant = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFF2D323C), surfaceContainerHigh = Color(0xFF42464E),
    error = Color(0xFFFFB4AB),
)

// Theme preference for the app
val LocalThemePreference = staticCompositionLocalOf { ThemePreference.SYSTEM }

enum class ThemePreference {
    LIGHT, DARK, SYSTEM
}

@Composable
fun ArtCraftTheme(
    themePreference: ThemePreference = LocalThemePreference.current,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themePreference) {
        ThemePreference.LIGHT -> Light
        ThemePreference.DARK -> Dark
        ThemePreference.SYSTEM -> if (isSystemInDarkTheme()) Dark else Light
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
