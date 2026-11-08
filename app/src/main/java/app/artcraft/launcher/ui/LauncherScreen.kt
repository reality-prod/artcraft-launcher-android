package app.artcraft.launcher.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.artcraft.launcher.bridge.ApkInstaller
import app.artcraft.launcher.data.CatalogApp
import app.artcraft.launcher.data.IconLoader

private const val TAB_APPS = 0
private const val TAB_INSTALLED = 1
private const val TAB_SETTINGS = 2
private const val TAB_ABOUT = 3

/**
 * Mobile-first: one column, bottom navigation, store-style rows with a single thumb-reachable
 * action, details in a bottom sheet. Desktop-only products are shown as native mobile cards,
 * never as a desktop UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(vm: LauncherViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(TAB_APPS) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showInstallScreen by rememberSaveable { mutableStateOf<CardState?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is UiEvent.Message -> snack.showSnackbar(e.text)
                UiEvent.NeedInstallPermission -> {
                    val r = snack.showSnackbar(
                        "Allow ArtCraft Launcher to install apps, then tap Install again",
                        actionLabel = "Settings",
                    )
                    if (r == SnackbarResult.ActionPerformed) ctx.startActivity(ApkInstaller.unknownSourcesIntent(ctx))
                }
            }
        }
    }

    val uninstall: (CardState) -> Unit = { card ->
        card.local?.let { l ->
            runCatching { ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${l.packageName}"))) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("ArtCraft", fontWeight = FontWeight.Bold)
                    Text("Open-source creative apps", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == TAB_APPS, { tab = TAB_APPS },
                    { Icon(Icons.Default.Home, null) }, label = { Text("Apps") })
                NavigationBarItem(tab == TAB_INSTALLED, { tab = TAB_INSTALLED },
                    { Icon(Icons.Default.Check, null) }, label = { Text("Installed") })
                NavigationBarItem(tab == TAB_SETTINGS, { tab = TAB_SETTINGS },
                    { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
                NavigationBarItem(tab == TAB_ABOUT, { tab = TAB_ABOUT },
                    { Icon(Icons.Default.Info, null) }, label = { Text("About") })
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = vm::refresh,
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            when (tab) {
                TAB_SETTINGS -> SettingsScreen(onBack = { tab = TAB_APPS })
                TAB_ABOUT -> AboutTab()
                else -> AppList(
                    cards = if (tab == TAB_INSTALLED) state.cards.filter { it.local != null } else state.cards,
                    installedTab = tab == TAB_INSTALLED,
                    warning = state.warning,
                    summary = state.summary,
                    onOpenDetails = { selectedId = it.app.id },
                    onPrimary = { card ->
                        if (card.action == CardAction.INSTALL || card.action == CardAction.UPDATE) {
                            showInstallScreen = card
                        } else {
                            vm.onPrimary(card)
                        }
                    },
                    onCancel = vm::cancel,
                )
            }
        }
    }

    val card = state.cards.firstOrNull { it.app.id == selectedId }
    if (card != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedId = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Details(card, { 
                if (card.action == CardAction.INSTALL || card.action == CardAction.UPDATE) {
                    showInstallScreen = card
                    selectedId = null
                } else {
                    vm.onPrimary(card)
                }
            }, { vm.cancel(card) }, { uninstall(card) })
        }
    }
    
    // Install screen with custom animations
    showInstallScreen?.let { installCard ->
        val progressCard = state.cards.firstOrNull { it.app.id == installCard.app.id }
        val busy = progressCard?.busy
        
        if (busy != null && busy is Busy.Downloading) {
            ModalBottomSheet(
                onDismissRequest = {
                    if (busy is Busy.Downloading || busy is Busy.Installing) {
                        vm.cancel(installCard)
                    }
                    showInstallScreen = null
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                InstallContent(
                    card = progressCard!!,
                    onCancel = {
                        vm.cancel(installCard)
                        showInstallScreen = null
                    }
                )
            }
        } else {
            showInstallScreen = null
        }
    }
}

@Composable
private fun InstallContent(card: CardState, onCancel: () -> Unit) {
    val busy = card.busy
    val apk = card.apk
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = card.app.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        when (busy) {
            is Busy.Downloading -> {
                Text("Downloading APK", style = MaterialTheme.typography.bodyMedium)
                
                LinearProgressIndicator(
                    progress = { busy.fraction },
                    modifier = Modifier.fillMaxWidth()
                )
                
                apk?.let {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${(busy.fraction * 100).toInt()}%")
                        Text("${it.size / 1_048_576} MB")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
            Busy.Installing -> {
                Text("Waiting for installer", style = MaterialTheme.typography.bodyMedium)
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Please confirm the installation in the Android installer dialog")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
            null -> {
                // Should not happen, but just in case
                Text("Preparing installation", style = MaterialTheme.typography.bodyMedium)
                CircularProgressIndicator()
            }
        }
    }
}

// ------------------------------------------------------------------ list
@Composable
private fun AppList(
    cards: List<CardState>,
    installedTab: Boolean,
    warning: String?,
    summary: String?,
    onOpenDetails: (CardState) -> Unit,
    onPrimary: (CardState) -> Unit,
    onCancel: (CardState) -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!installedTab && summary != null) {
                item { Text("Scanned $summary", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            warning?.let { w -> item { Banner(w) } }
            if (installedTab && cards.isEmpty()) {
                item { Banner("Nothing installed yet. Get an app from the Apps tab.") }
            }
            items(cards, key = { it.app.id }) { card ->
                AppRow(card, { onOpenDetails(card) }, { onPrimary(card) }, { onCancel(card) })
            }
        }
    }
}

@Composable
private fun AppRow(card: CardState, onDetails: () -> Unit, onPrimary: () -> Unit, onCancel: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val soon = card.action == CardAction.COMING_SOON
    Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onDetails)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.alpha(if (soon) 0.55f else 1f)) { AppIcon(card.app, 60.dp) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(card.app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(card.app.tagline, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(statusLine(card), style = MaterialTheme.typography.labelMedium,
                        color = if (soon || card.outdated) cs.primary else cs.onSurfaceVariant)
                }
                if (card.busy == null) {
                    PrimaryChip(card, onPrimary)
                }
            }
            when (val b = card.busy) {
                is Busy.Downloading -> BusyBar(b.fraction, "Downloading ${(b.fraction * 100).toInt()}%", "Cancel", onCancel)
                Busy.Installing -> BusyBar(null, "Waiting for the installer…", "Dismiss", onCancel)
                null -> Unit
            }
        }
    }
}

@Composable
private fun BusyBar(fraction: Float?, label: String, action: String, onAction: () -> Unit) {
    if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(action) }
    }
}

/** Compact store-style button on the right of a row. */
@Composable
private fun PrimaryChip(card: CardState, onClick: () -> Unit) {
    val label = when (card.action) {
        CardAction.INSTALL -> "Get"
        CardAction.UPDATE -> "Update"
        CardAction.OPEN -> "Open"
        CardAction.COMING_SOON -> "Soon"
    }
    FilledTonalButton(onClick = onClick, enabled = card.action != CardAction.COMING_SOON,
        modifier = Modifier.heightIn(min = 40.dp)) { Text(label) }
}

private fun statusLine(card: CardState): String = when {
    card.action == CardAction.COMING_SOON -> card.note ?: "Coming soon"
    card.outdated -> "Update available: ${card.app.release?.tag}"
    card.local != null -> "Installed" + (card.local.versionName?.let { " · $it" } ?: "")
    else -> listOfNotNull(
        card.app.release?.tag,
        card.apk?.size?.takeIf { it > 0 }?.let { "%.1f MB".format(it / 1_048_576.0) },
        if (card.app.release?.prerelease == true) "Beta" else null,
    ).joinToString(" · ")
}

// ------------------------------------------------------------------ details sheet
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Details(card: CardState, onPrimary: () -> Unit, onCancel: () -> Unit, onUninstall: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val soon = card.action == CardAction.COMING_SOON
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppIcon(card.app, 80.dp)
            Column {
                Text(card.app.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(statusLine(card), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
        Text(card.app.tagline, style = MaterialTheme.typography.bodyLarge)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (soon) Pill("Coming soon", cs.secondary.copy(alpha = 0.18f), cs.secondary)
            if (card.app.desktopUrl != null) Pill("Desktop edition exists")
            if (card.app.release?.prerelease == true) Pill("Beta")
            if (card.local != null) Pill(if (card.outdated) "Update available" else "Installed",
                cs.primary.copy(alpha = 0.18f), cs.primary)
        }

        card.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.primary) }

        card.apk?.let { apk ->
            Text("File for your phone: ${apk.name}" +
                (apk.size.takeIf { it > 0 }?.let { " (%.1f MB)".format(it / 1_048_576.0) } ?: "") +
                if (apk.sha256 != null) ". Checksum verified on download." else ".",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }

        when (val b = card.busy) {
            is Busy.Downloading -> BusyBar(b.fraction, "Downloading ${(b.fraction * 100).toInt()}%", "Cancel", onCancel)
            Busy.Installing -> BusyBar(null, "Waiting for the installer…", "Dismiss", onCancel)
            null -> {
                Button(onClick = onPrimary, enabled = !soon,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(when (card.action) {
                        CardAction.INSTALL -> "Install"
                        CardAction.UPDATE -> "Update to ${card.app.release?.tag ?: ""}"
                        CardAction.OPEN -> "Open"
                        CardAction.COMING_SOON -> "Coming soon"
                    })
                }
                if (card.local != null) {
                    OutlinedButton(onClick = onUninstall, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Uninstall")
                    }
                }
            }
        }

        Column {
            card.app.release?.pageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                TextButton(onClick = { uri.openUri(url) }) { Text("Release notes") }
            }
            TextButton(onClick = { uri.openUri(card.app.repoUrl) }) { Text("Source on GitHub") }
            card.app.desktopUrl?.let { url -> TextButton(onClick = { uri.openUri(url) }) { Text("Desktop edition") } }
        }
    }
}

// ------------------------------------------------------------------ about
@Composable
private fun AboutTab() {
    val uri = LocalUriHandler.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("What is this?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("A launcher for the open-source ArtCraft family on Android. It scans the " +
                "artcraft-android GitHub org and storytold/artcraft, shows what has an Android build, " +
                "and installs the latest release for your phone. Anything without an APK yet is marked " +
                "Coming soon. Desktop-only tools are rebuilt as native Kotlin apps for mobile, and appear " +
                "here once those builds ship.")
            Text("Pull down on the Apps tab to rescan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { uri.openUri("https://github.com/artcraft-android") }) { Text("github.com/artcraft-android") }
            TextButton(onClick = { uri.openUri("https://github.com/storytold/artcraft") }) { Text("github.com/storytold/artcraft") }
            Text("Unofficial community launcher. Not affiliated with Adobe; Acrobat, Lightroom and " +
                "Premiere are trademarks of Adobe Inc.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------------ bits
@Composable
private fun Banner(text: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, Modifier.padding(14.dp).fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Pill(text: String, bg: Color = MaterialTheme.colorScheme.surfaceVariant,
                 fg: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(shape = RoundedCornerShape(50), color = bg, contentColor = fg) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AppIcon(app: CatalogApp, size: Dp) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, app.iconUrls) { value = IconLoader.load(ctx, app.iconUrls) }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.22f)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) Image(b, app.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(app.name.take(1), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    }
}
