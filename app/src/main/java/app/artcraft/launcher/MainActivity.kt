package app.artcraft.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.artcraft.launcher.ui.ArtCraftTheme
import app.artcraft.launcher.ui.LocalThemePreference
import app.artcraft.launcher.ui.LauncherScreen
import app.artcraft.launcher.ui.SplashScreen
import app.artcraft.launcher.ui.ThemePreference

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        setContent {
            var themePreference by remember { mutableStateOf(ThemePreference.SYSTEM) }
            
            ArtCraftTheme(themePreference = themePreference) {
                var showSplash by remember { mutableStateOf(true) }
                
                if (showSplash) {
                    SplashScreen(onTimeout = { showSplash = false })
                } else {
                    LauncherScreen()
                }
            }
        }
    }
}
