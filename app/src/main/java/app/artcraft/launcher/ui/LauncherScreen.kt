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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.artcraft.launcher.bridge.ApkInstaller
import app.artcraft.launcher.bridge.NativeCore
import app.artcraft.launcher.data.CatalogApp
import app.artcraft.launcher.data.SettingsState
import app.artcraft.launcher.data.IconLoader

private const val TAB_APPS = 0
private const val TAB_INSTALLED = 1
private const val TAB_ABOUT = 2

/**
 * Mobile-first: one column, bottom navigation, store-style rows with a single thumb-reachable
 * action, details in a bottom sheet. Desktop-only products are shown as native mobile cards,
 * never as a desktop UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(vm: LauncherViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(TAB_APPS) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

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
                NavigationBarItem(tab == TAB_ABOUT, { tab = TAB_ABOUT },
                    { Icon(Icons.Default.Info, null) }, label = { Text("Settings") })
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = vm::refresh,
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            if (tab == TAB_ABOUT) SettingsTab(settings, { f -> vm.updateSettings(f) }, vm::clearCache)
            else AppList(
                cards = if (tab == TAB_INSTALLED) state.cards.filter { it.local != null } else state.cards,
                installedTab = tab == TAB_INSTALLED,
                warning = state.warning,
                summary = state.summary,
                selfUpdate = state.selfUpdate,
                updates = state.updatesAvailable,
                onUpdateSelf = vm::updateSelf,
                onUpdateAll = vm::updateAll,
                onOpenDetails = { selectedId = it.app.id },
                onPrimary = vm::onPrimary,
                onCancel = vm::cancel,
            )
        }
    }

    val card = state.cards.firstOrNull { it.app.id == selectedId }
    if (card != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedId = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Details(card, { vm.onPrimary(card) }, { vm.cancel(card) }, { uninstall(card) })
        }
    }

    session?.let { sess ->
        InstallScreen(
            s = sess,
            onBackground = vm::dismissSession,
            onCancel = vm::cancelSession,
            onRetry = vm::retrySession,
            onOpen = { vm.openFromSession(); vm.dismissSession() },
        )
    }
}

// ------------------------------------------------------------------ list
@Composable
private fun AppList(
    cards: List<CardState>,
    installedTab: Boolean,
    warning: String?,
    summary: String?,
    selfUpdate: SelfUpdate?,
    updates: Int,
    onUpdateSelf: () -> Unit,
    onUpdateAll: () -> Unit,
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
            selfUpdate?.let { u ->
                item(key = "self-update") {
                    UpdateBanner("ArtCraft Launcher ${u.tag} is available", "Update", onUpdateSelf)
                }
            }
            if (updates > 1) {
                item(key = "update-all") { UpdateBanner("$updates app updates available", "Update all", onUpdateAll) }
            }
            if (installedTab && cards.isEmpty()) {
                item { Banner("Nothing installed yet. Get an app from the Apps tab.") }
            }
            items(cards, key = { it.app.id }) { card ->
                AppRow(card, { onOpenDetails(card) }, { onPrimary(card) }, { onCancel(card) }, Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun AppRow(card: CardState, onDetails: () -> Unit, onPrimary: () -> Unit, onCancel: () -> Unit,
                   modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val soon = card.action == CardAction.COMING_SOON
    Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().clickable(onClick = onDetails)) {
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

        card.app.release?.let { r ->
            val ago = NativeCore.ago(r.publishedAt)
            if (ago.isNotEmpty()) Text("Released $ago", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            val notes = NativeCore.notes(r.notes, 700)
            if (notes.isNotBlank()) {
                Text("What's new", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(notes, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }

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

// ------------------------------------------------------------------ settings
@Composable
private fun SettingsTab(
    s: SettingsState,
    onChange: ((SettingsState) -> SettingsState) -> Unit,
    onClearCache: () -> Unit,
) {
    val uri = LocalUriHandler.current
    val ctx = LocalContext.current
    var token by rememberSaveable(s.token) { mutableStateOf(s.token) }
    val version = remember {
        @Suppress("DEPRECATION")
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            SettingRow("Download on Wi-Fi only", "Skips downloads while you're on mobile data.", s.wifiOnly) { v ->
                onChange { it.copy(wifiOnly = v) }
            }
            SettingRow("Include pre-releases", "Most ArtCraft Android apps are still in beta.", s.includePre) { v ->
                onChange { it.copy(includePre = v) }
            }
            OutlinedTextField(
                value = token, onValueChange = { token = it }, singleLine = true,
                label = { Text("GitHub token (optional)") },
                supportingText = { Text("Raises the scan limit from 60 to 5,000 requests an hour. Kept on this device only.") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (token != s.token) {
                Button(onClick = { onChange { it.copy(token = token.trim()) } }) { Text("Save token") }
            }
            OutlinedButton(onClick = onClearCache) { Text("Clear download cache") }
            Text("Launcher $version · native core: ${NativeCore.describe()}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("What is this?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("A launcher for the open-source ArtCraft family on Android. It reads a saved list of direct " +
                "download links (falling back to GitHub's API and release pages), shows what has an Android build, " +
                "and installs the latest release for your phone, checking the SHA-256 when one is published. " +
                "Anything without an APK yet is marked Coming soon.")
            TextButton(onClick = { uri.openUri("https://github.com/artcraft-android") }) { Text("github.com/artcraft-android") }
            TextButton(onClick = { uri.openUri("https://github.com/storytold/artcraft") }) { Text("github.com/storytold/artcraft") }
            TextButton(onClick = { uri.openUri("https://github.com/reality-prod/artcraft-launcher-android") }) {
                Text("github.com/reality-prod/artcraft-launcher-android")
            }
            Text("Unofficial community launcher. Not affiliated with Adobe; Acrobat, Lightroom and " +
                "Premiere are trademarks of Adobe Inc.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun UpdateBanner(text: String, action: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Button(onClick = onClick) { Text(action) }
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
private fun AppIcon(app: CatalogApp, size: Dp) = IconTile(app.name, app.iconUrls, size)

@Composable
internal fun IconTile(name: String, iconUrls: List<String>, size: Dp) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, iconUrls) { value = IconLoader.load(ctx, iconUrls) }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.22f)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) Image(b, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(name.take(1), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    }
}
