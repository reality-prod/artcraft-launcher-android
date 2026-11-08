package app.artcraft.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemSecurityUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.artcraft.launcher.ui.ThemePreference

/**
 * Settings screen for ArtCraft Launcher.
 * 
 * Provides configuration options for the launcher.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    
    // Settings state
    var themePreference by rememberSaveable { mutableStateOf(ThemePreference.SYSTEM) }
    var autoCheckUpdates by rememberSaveable { mutableStateOf(true) }
    var notifyNewReleases by rememberSaveable { mutableStateOf(true) }
    var useRustBridge by rememberSaveable { mutableStateOf(true) }
    var expandedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var showClearCacheDialog by rememberSaveable { mutableStateOf(false) }
    var clearingCache by rememberSaveable { mutableStateOf(false) }
    var cacheSize by rememberSaveable { mutableStateOf(245) } // MB
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Appearance Section
            SettingsCategory(
                title = "Appearance",
                icon = Icons.Default.DarkMode,
                isExpanded = expandedCategory == "appearance",
                onToggle = { 
                    expandedCategory = if (expandedCategory == "appearance") null else "appearance"
                }
            ) {
                if (expandedCategory == "appearance") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Theme selection with radio buttons
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(8.dp)
                        ) {
                            ThemeOption(
                                selected = themePreference == ThemePreference.LIGHT,
                                icon = Icons.Default.LightMode,
                                label = "Light",
                                onClick = { themePreference = ThemePreference.LIGHT }
                            )
                            ThemeOption(
                                selected = themePreference == ThemePreference.DARK,
                                icon = Icons.Default.DarkMode,
                                label = "Dark",
                                onClick = { themePreference = ThemePreference.DARK }
                            )
                            ThemeOption(
                                selected = themePreference == ThemePreference.SYSTEM,
                                icon = Icons.Default.SystemSecurityUpdate,
                                label = "System",
                                onClick = { themePreference = ThemePreference.SYSTEM }
                            )
                        }
                    }
                }
            }
            
            // Updates Section
            SettingsCategory(
                title = "Updates",
                icon = Icons.Default.SystemSecurityUpdate,
                isExpanded = expandedCategory == "updates",
                onToggle = { 
                    expandedCategory = if (expandedCategory == "updates") null else "updates"
                }
            ) {
                if (expandedCategory == "updates") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingSwitch(
                            title = "Auto-check for updates",
                            description = "Automatically check for new app versions",
                            checked = autoCheckUpdates,
                            onCheckedChange = { autoCheckUpdates = it }
                        )
                        
                        SettingSwitch(
                            title = "Notify new releases",
                            description = "Get notifications when new apps are released",
                            checked = notifyNewReleases,
                            onCheckedChange = { notifyNewReleases = it }
                        )
                        
                        SettingItem(
                            title = "Check for updates now",
                            description = "Manually check for updates",
                            icon = Icons.Default.Refresh,
                            onClick = {
                                scope.launch {
                                    val msg = snack.showSnackbar("Checking for updates...")
                                    delay(2000)
                                    snack.showSnackbar("All apps are up to date")
                                }
                            }
                        )
                    }
                }
            }
            
            // Storage Section
            SettingsCategory(
                title = "Storage",
                icon = Icons.Default.Storage,
                isExpanded = expandedCategory == "storage",
                onToggle = { 
                    expandedCategory = if (expandedCategory == "storage") null else "storage"
                }
            ) {
                if (expandedCategory == "storage") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingItem(
                            title = "Cache",
                            description = "Cached data: ${cacheSize}MB",
                            trailing = {
                                OutlinedButton(
                                    onClick = { showClearCacheDialog = true },
                                    enabled = !clearingCache
                                ) {
                                    if (clearingCache) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Clear")
                                    }
                                }
                            }
                        )
                        
                        // Progress bar for cache
                        if (clearingCache) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        }
                    }
                }
            }
            
            // About Section
            SettingsCategory(
                title = "About",
                icon = Icons.Default.Info,
                isExpanded = expandedCategory == "about",
                onToggle = { 
                    expandedCategory = if (expandedCategory == "about") null else "about"
                }
            ) {
                if (expandedCategory == "about") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingItem(
                            title = "Version",
                            description = "ArtCraft Launcher v2.0.0",
                            onClick = {}
                        )
                        
                        SettingItem(
                            title = "Source Code",
                            description = "View source on GitHub",
                            onClick = {
                                uriHandler.openUri("https://github.com/reality-prod/artcraft-launcher-android")
                            }
                        )
                        
                        SettingItem(
                            title = "ArtCraft Android",
                            description = "Official ArtCraft Android organization",
                            onClick = {
                                uriHandler.openUri("https://github.com/artcraft-android")
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Unofficial community launcher. Not affiliated with Adobe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
    
    // Clear cache confirmation dialog
    if (showClearCacheDialog) {
        ModalBottomSheet(
            onDismissRequest = { showClearCacheDialog = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.Storage,
                    null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    "Clear Cache",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    "Are you sure you want to clear ${cacheSize}MB of cached data? " +
                    "This will not affect installed apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = { showClearCacheDialog = false },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            showClearCacheDialog = false
                            clearingCache = true
                            scope.launch {
                                delay(1500)
                                clearingCache = false
                                cacheSize = 0
                                snack.showSnackbar("Cache cleared successfully")
                            }
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Clear Cache")
                    }
                }
            }
        }
    }
}

// =============================================================================
// Settings Components
// =============================================================================

/**
 * Settings category with expandable content
 */
@Composable
private fun SettingsCategory(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    icon,
                    null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Icon(
                Icons.Default.ArrowDropDown,
                null,
                modifier = Modifier.rotate(if (isExpanded) 180f else 0f)
            )
        }
        
        content()
    }
}

/**
 * Setting item with switch
 */
@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onCheckedChange(!checked) })
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * Setting item with click action
 */
@Composable
private fun SettingItem(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            icon?.let {
                Icon(
                    it,
                    null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        trailing?.let { it() }
    }
}

/**
 * Theme option radio button
 */
@Composable
private fun ThemeOption(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(label)
        Spacer(modifier = Modifier.weight(1f))
        RadioButton(
            selected = selected,
            onClick = onClick
        )
    }
}
