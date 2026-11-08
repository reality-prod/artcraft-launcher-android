package app.artcraft.launcher.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.StatFs
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.artcraft.launcher.bridge.ApkInstaller
import app.artcraft.launcher.bridge.Downloader
import app.artcraft.launcher.bridge.InstallEvent
import app.artcraft.launcher.bridge.InstallEvents
import app.artcraft.launcher.bridge.LocalApps
import app.artcraft.launcher.bridge.LocalInfo
import app.artcraft.launcher.bridge.NativeCore
import app.artcraft.launcher.data.ApkAsset
import app.artcraft.launcher.data.CatalogApp
import app.artcraft.launcher.data.CatalogRepository
import app.artcraft.launcher.data.ReleaseInfo
import app.artcraft.launcher.data.SettingsState
import app.artcraft.launcher.data.SettingsStore
import app.artcraft.launcher.data.bestApk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException

sealed interface Busy {
    data class Downloading(val fraction: Float) : Busy
    data object Installing : Busy
}

enum class CardAction { INSTALL, UPDATE, OPEN, COMING_SOON }

data class CardState(
    val app: CatalogApp,
    val apk: ApkAsset?,
    val local: LocalInfo?,
    val busy: Busy?,
    val action: CardAction,
    val note: String?,
    val outdated: Boolean,
)

data class SelfUpdate(val tag: String, val apk: ApkAsset, val release: ReleaseInfo)

data class UiState(
    val cards: List<CardState> = emptyList(),
    val loading: Boolean = false,
    val warning: String? = null,
    val summary: String? = null,
    val selfUpdate: SelfUpdate? = null,
    val updatesAvailable: Int = 0,
)

/** The full-screen install flow the user sees after tapping Get / Update. */
enum class Phase { NEEDS_PERMISSION, DOWNLOADING, VERIFYING, INSTALLING, DONE, FAILED }

data class InstallSession(
    val id: String,
    val name: String,
    val iconUrls: List<String>,
    val tag: String,
    val phase: Phase,
    val fraction: Float = 0f,
    val doneBytes: Long = 0,
    val totalBytes: Long = 0,
    val bytesPerSec: Long = 0,
    /** true = checksum matched, false/null with phase past VERIFYING = none was published. */
    val verified: Boolean? = null,
    val error: String? = null,
    val failedPhase: Phase? = null,
    val openable: Boolean = false,
)

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data object NeedInstallPermission : UiEvent
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    companion object { const val SELF_ID = "self" }

    private val ctx: Context = application
    private val repo = CatalogRepository(application)
    private val local = LocalApps(application)
    private val downloader = Downloader(application)
    private val installer = ApkInstaller(application)
    private val settingsStore = SettingsStore(application)

    // All of this is touched on the main thread only.
    private var apps: List<CatalogApp> = repo.cached() ?: repo.placeholders()
    private val busy = mutableMapOf<String, Busy>()
    private val pendingTag = mutableMapOf<String, String>()
    private val jobs = mutableMapOf<String, Job>()
    private val waiters = mutableMapOf<String, CompletableDeferred<Unit>>()
    private var loading = false
    private var warning: String? = null
    private var summary: String? = null
    private var selfUpdate: SelfUpdate? = null
    private var lastTarget: Target? = null

    private class Target(
        val id: String, val name: String, val iconUrls: List<String>,
        val tag: String, val apk: ApkAsset, val self: Boolean,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private val _session = MutableStateFlow<InstallSession?>(null)
    val session: StateFlow<InstallSession?> = _session.asStateFlow()
    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<SettingsState> = _settings.asStateFlow()
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        settingsStore.apply(_settings.value)
        publish()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { downloader.purge() }
            InstallEvents.flow.collect(::onInstallEvent)
        }
        refresh()
    }

    // ---------------------------------------------------------------- scanning
    fun refresh() {
        if (loading) return
        loading = true
        local.invalidate()
        publish()
        viewModelScope.launch {
            try {
                val r = repo.scan()
                apps = r.apps
                warning = r.warning
                summary = r.summary
                selfUpdate = repo.selfRelease()?.let { rel ->
                    val apk = rel.bestApk()
                    if (apk != null && NativeCore.compare(ownVersion(), rel.tag) < 0) SelfUpdate(rel.tag, apk, rel) else null
                }
            } catch (e: Exception) {
                warning = e.message ?: "Scan failed"
            } finally {
                loading = false
                publish()
            }
        }
    }

    /** Back in the foreground: re-read what's installed, and resume if the user just allowed installs. */
    fun onResume() {
        local.invalidate()
        val s = _session.value
        if (s?.phase == Phase.NEEDS_PERMISSION && installer.canInstall()) {
            lastTarget?.let { start(it) }
        }
        publish()
    }

    @Suppress("DEPRECATION")
    private fun ownVersion(): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
    } catch (_: Exception) { "0" }

    // ---------------------------------------------------------------- settings
    fun updateSettings(change: (SettingsState) -> SettingsState) {
        val old = _settings.value
        val new = change(old)
        _settings.value = new
        settingsStore.save(new)
        settingsStore.apply(new)
        if (old.includePre != new.includePre || old.token != new.token) refresh()
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            downloader.purge()
            for (d in listOf("github", "web", "icons")) File(ctx.cacheDir, d).deleteRecursively()
        }
        message("Cache cleared")
    }

    // ---------------------------------------------------------------- actions
    fun onPrimary(card: CardState) = when (card.action) {
        CardAction.INSTALL, CardAction.UPDATE -> install(card)
        CardAction.OPEN -> open(card)
        CardAction.COMING_SOON -> Unit
    }

    fun open(card: CardState) {
        val pkg = card.local?.packageName ?: return
        val intent = local.launchIntent(pkg)
        if (intent == null) message("${card.app.name} has no launchable screen")
        else ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openFromSession() {
        val id = _session.value?.id ?: return
        _state.value.cards.firstOrNull { it.app.id == id }?.let(::open)
    }

    fun cancel(card: CardState) = cancelId(card.app.id)

    fun cancelSession() { _session.value?.id?.let(::cancelId) }

    private fun cancelId(id: String) {
        jobs.remove(id)?.cancel()
        pendingTag.remove(id)
        waiters.remove(id)
        setBusy(id, null)
        if (_session.value?.id == id) _session.value = null
    }

    fun dismissSession() { _session.value = null }

    fun retrySession() { lastTarget?.let { start(it) } }

    fun updateSelf() {
        val s = selfUpdate ?: return
        start(Target(SELF_ID, "ArtCraft Launcher", emptyList(), s.tag, s.apk, self = true))
    }

    fun updateAll() {
        if (!installer.canInstall()) { _events.tryEmit(UiEvent.NeedInstallPermission); return }
        val queue = _state.value.cards.filter { it.action == CardAction.UPDATE && it.busy == null }
        if (queue.isEmpty()) { message("Everything is up to date"); return }
        viewModelScope.launch {
            for (c in queue) {
                install(c)
                jobs[c.app.id]?.join()
            }
        }
    }

    private fun install(card: CardState) {
        val apk = card.apk ?: return
        val rel = card.app.release ?: return
        start(Target(card.app.id, card.app.name, card.app.iconUrls, rel.tag, apk, self = false))
    }

    private fun openSession(t: Target, phase: Phase) {
        _session.value = InstallSession(t.id, t.name, t.iconUrls, t.tag, phase)
    }

    private fun updateSession(id: String, f: (InstallSession) -> InstallSession) {
        _session.update { s -> if (s != null && s.id == id) f(s) else s }
    }

    // ---------------------------------------------------------------- the install flow
    private fun start(t: Target) {
        lastTarget = t
        if (jobs[t.id]?.isActive == true) { openSession(t, _session.value?.phase ?: Phase.DOWNLOADING); return }
        if (!installer.canInstall()) {
            // Don't just toast: show a screen that explains and resumes by itself afterwards.
            openSession(t, Phase.NEEDS_PERMISSION)
            return
        }
        openSession(t, Phase.DOWNLOADING)
        val waiter = CompletableDeferred<Unit>()
        waiters[t.id] = waiter

        jobs[t.id] = viewModelScope.launch {
            try {
                preflight(t.apk)
                setBusy(t.id, Busy.Downloading(0f))
                val started = SystemClock.elapsedRealtime()
                var lastPct = -1
                val file = downloader.download(t.apk) { done, total ->
                    val pct = if (total > 0) (done * 100 / total).toInt() else 0
                    if (pct != lastPct) {
                        lastPct = pct
                        val elapsed = (SystemClock.elapsedRealtime() - started).coerceAtLeast(1)
                        val bps = done * 1000 / elapsed
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            if (busy[t.id] is Busy.Downloading) {
                                setBusy(t.id, Busy.Downloading(pct / 100f))
                                updateSession(t.id) { it.copy(fraction = pct / 100f, doneBytes = done, totalBytes = total, bytesPerSec = bps) }
                            }
                        }
                    }
                }

                setBusy(t.id, Busy.Installing)
                updateSession(t.id) { it.copy(phase = Phase.VERIFYING, fraction = 1f) }
                val verified: Boolean? = t.apk.sha256?.let { sha ->
                    val err = withContext(Dispatchers.IO) { NativeCore.verify(file.path, sha) }
                    if (err.isNotEmpty()) throw IOException("Checksum check failed: $err")
                    true
                }
                val pkg = installer.packageNameOf(file) ?: throw IOException("That file isn't a valid APK")
                if (!t.self && pkg == ctx.packageName) throw IOException("Refusing to replace the launcher itself")
                if (!t.self) local.rememberPackage(t.id, pkg)
                pendingTag[t.id] = t.tag

                updateSession(t.id) { it.copy(phase = Phase.INSTALLING, verified = verified) }
                withContext(Dispatchers.IO) { installer.install(file, t.id) }
                // The result arrives through InstallEvents; wait for it so the job stays "busy".
                withTimeout(10 * 60_000L) { waiter.await() }
            } catch (e: TimeoutCancellationException) {
                fail(t.id, "Timed out waiting for the Android installer")
            } catch (e: CancellationException) {
                setBusy(t.id, null)
                throw e
            } catch (e: Exception) {
                fail(t.id, e.message ?: "Install failed")
            }
        }
    }

    private fun preflight(apk: ApkAsset) {
        if (_settings.value.wifiOnly && isMetered()) {
            throw IOException("Wi-Fi only is on and you're on mobile data. Connect to Wi-Fi or turn it off in Settings.")
        }
        if (apk.size > 0) {
            val free = StatFs(ctx.cacheDir.path).availableBytes
            if (free < apk.size * 2) {
                throw IOException("Not enough free space: about ${NativeCore.size(apk.size * 2)} needed, ${NativeCore.size(free)} free.")
            }
        }
    }

    private fun isMetered(): Boolean =
        (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered

    private fun fail(id: String, reason: String) {
        pendingTag.remove(id)
        waiters.remove(id)
        setBusy(id, null)
        updateSession(id) { it.copy(phase = Phase.FAILED, error = reason, failedPhase = it.phase) }
        message(reason)
    }

    private fun onInstallEvent(e: InstallEvent) {
        when (e) {
            is InstallEvent.Success -> {
                pendingTag.remove(e.appId)?.let { if (e.appId != SELF_ID) local.rememberTag(e.appId, it) }
                local.invalidate()
                setBusy(e.appId, null)
                val opens = e.appId != SELF_ID && apps.firstOrNull { it.id == e.appId }
                    ?.let { local.info(it.id, it.token) } != null
                updateSession(e.appId) { it.copy(phase = Phase.DONE, fraction = 1f, openable = opens) }
                viewModelScope.launch(Dispatchers.IO) { downloader.purge() }
                waiters.remove(e.appId)?.complete(Unit)
            }
            is InstallEvent.Failure -> {
                waiters.remove(e.appId)?.complete(Unit)
                fail(e.appId, e.reason)
            }
        }
    }

    // ---------------------------------------------------------------- state
    private fun setBusy(id: String, b: Busy?) {
        if (b == null) busy.remove(id) else busy[id] = b
        publish()
    }

    private fun message(text: String) { _events.tryEmit(UiEvent.Message(text)) }

    private fun publish() {
        val cards = apps.map(::buildCard)
        _state.value = UiState(
            cards, loading, warning, summary, selfUpdate,
            updatesAvailable = cards.count { it.action == CardAction.UPDATE },
        )
    }

    private fun buildCard(app: CatalogApp): CardState {
        val rel = app.release
        val apk = rel?.bestApk()
        val li = local.info(app.id, app.token)
        val outdated = li != null && rel != null && apk != null && isOutdated(li, rel.tag)
        val action = when {
            apk == null -> if (li != null) CardAction.OPEN else CardAction.COMING_SOON
            li == null -> CardAction.INSTALL
            outdated -> CardAction.UPDATE
            else -> CardAction.OPEN
        }
        val note = when {
            apk != null -> null
            rel == null -> if (app.desktopUrl != null) "Desktop only so far. A mobile build is planned." else "No Android release yet"
            else -> "Latest release (${rel.tag}) has no APK yet"
        }
        return CardState(app, apk, li, busy[app.id], action, note, outdated)
    }

    /** Ordering comes from the Rust bridge when it is packaged, else the Kotlin fallback. */
    private fun isOutdated(li: LocalInfo, tag: String): Boolean {
        val installed = li.installedTag ?: li.versionName ?: return false
        return NativeCore.compare(installed, tag) < 0
    }
}
