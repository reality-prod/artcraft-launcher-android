package app.artcraft.launcher.ui

import android.app.Application
import android.content.Intent
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
import app.artcraft.launcher.data.bestApk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

data class UiState(
    val cards: List<CardState> = emptyList(),
    val loading: Boolean = false,
    val warning: String? = null,
    val summary: String? = null,
)

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data object NeedInstallPermission : UiEvent
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = CatalogRepository(application)
    private val local = LocalApps(application)
    private val downloader = Downloader(application)
    private val installer = ApkInstaller(application)

    // All of this is touched on the main thread only.
    private var apps: List<CatalogApp> = repo.cached() ?: repo.placeholders()
    private val busy = mutableMapOf<String, Busy>()
    private val pendingTag = mutableMapOf<String, String>()
    private val jobs = mutableMapOf<String, Job>()
    private var loading = false
    private var warning: String? = null
    private var summary: String? = null

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
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
            } catch (e: Exception) {
                warning = e.message ?: "Scan failed"
            } finally {
                loading = false
                publish()
            }
        }
    }

    fun onResume() {
        local.invalidate()
        publish()
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
        else getApplication<Application>().startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun cancel(card: CardState) {
        jobs.remove(card.app.id)?.cancel()
        pendingTag.remove(card.app.id)
        setBusy(card.app.id, null)
    }

    private fun install(card: CardState) {
        val apk = card.apk ?: return
        val rel = card.app.release ?: return
        val id = card.app.id
        if (!installer.canInstall()) { _events.tryEmit(UiEvent.NeedInstallPermission); return }
        if (jobs[id]?.isActive == true) return

        jobs[id] = viewModelScope.launch {
            try {
                setBusy(id, Busy.Downloading(0f))
                var lastPct = -1
                val file = downloader.download(apk) { done, total ->
                    val pct = if (total > 0) (done * 100 / total).toInt() else 0
                    if (pct != lastPct) {
                        lastPct = pct
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            if (busy[id] is Busy.Downloading) setBusy(id, Busy.Downloading(pct / 100f))
                        }
                    }
                }
                val pkg = installer.packageNameOf(file) ?: throw IOException("That file isn't a valid APK")
                if (pkg == getApplication<Application>().packageName) throw IOException("Refusing to replace the launcher itself")
                local.rememberPackage(id, pkg)
                pendingTag[id] = rel.tag
                setBusy(id, Busy.Installing)
                withContext(Dispatchers.IO) { installer.install(file, id) }
                // Result arrives through InstallEvents.
            } catch (e: CancellationException) {
                setBusy(id, null)
                throw e
            } catch (e: Exception) {
                pendingTag.remove(id)
                setBusy(id, null)
                message(e.message ?: "Install failed")
            }
        }
    }

    private fun onInstallEvent(e: InstallEvent) {
        val name = apps.firstOrNull { it.id == e.appId }?.name ?: "App"
        when (e) {
            is InstallEvent.Success -> {
                pendingTag.remove(e.appId)?.let { local.rememberTag(e.appId, it) }
                local.invalidate()
                setBusy(e.appId, null)
                viewModelScope.launch(Dispatchers.IO) { downloader.purge() }
                message("$name installed")
            }
            is InstallEvent.Failure -> {
                pendingTag.remove(e.appId)
                setBusy(e.appId, null)
                message(e.reason)
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
        _state.value = UiState(apps.map(::buildCard), loading, warning, summary)
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
