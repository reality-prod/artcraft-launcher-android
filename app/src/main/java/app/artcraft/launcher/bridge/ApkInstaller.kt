package app.artcraft.launcher.bridge

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

sealed interface InstallEvent {
    val appId: String
    data class Success(override val appId: String) : InstallEvent
    data class Failure(override val appId: String, val reason: String) : InstallEvent
}

/** Process-wide bus: the manifest receiver publishes, the ViewModel listens. */
object InstallEvents {
    private val _flow = MutableSharedFlow<InstallEvent>(extraBufferCapacity = 16)
    val flow: SharedFlow<InstallEvent> = _flow.asSharedFlow()
    fun emit(e: InstallEvent) { _flow.tryEmit(e) }
}

/** Bridge to the system PackageInstaller session API. */
class ApkInstaller(private val ctx: Context) {

    fun canInstall(): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** Package id read straight from the APK, so we never have to guess it. */
    @Suppress("DEPRECATION")
    fun packageNameOf(apk: File): String? =
        ctx.packageManager.getPackageArchiveInfo(apk.path, 0)?.packageName

    fun install(apk: File, appId: String) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(ctx, InstallResultReceiver::class.java)
                .setAction(InstallResultReceiver.ACTION)
                .putExtra(InstallResultReceiver.EXTRA_APP_ID, appId)
            // Must be mutable (the system adds extras) and explicit (Android 14+).
            val pi = PendingIntent.getBroadcast(
                ctx, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pi.intentSender)
        }
    }

    companion object {
        fun unknownSourcesIntent(ctx: Context): Intent =
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appId = intent.getStringExtra(EXTRA_APP_ID) ?: return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirm)
                } else InstallEvents.emit(InstallEvent.Failure(appId, "Installer did not ask for confirmation"))
            }
            PackageInstaller.STATUS_SUCCESS -> InstallEvents.emit(InstallEvent.Success(appId))
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                InstallEvents.emit(InstallEvent.Failure(appId, when (status) {
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "Install cancelled"
                    PackageInstaller.STATUS_FAILURE_CONFLICT ->
                        "Conflicts with an installed copy signed by a different key. Uninstall it first."
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This APK isn't compatible with your device"
                    else -> msg ?: "Install failed (code $status)"
                }))
            }
        }
    }

    companion object {
        const val ACTION = "app.artcraft.launcher.INSTALL_RESULT"
        const val EXTRA_APP_ID = "app_id"
    }
}
