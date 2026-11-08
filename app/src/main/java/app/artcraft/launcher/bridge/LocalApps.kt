package app.artcraft.launcher.bridge

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class LocalInfo(val packageName: String, val versionName: String?, val installedTag: String?)

/**
 * Knows which ArtCraft apps are on this phone.
 * Package ids are learned from downloaded APKs; for copies installed some other way
 * (F-Droid, Obtainium) we fall back to matching the app token in installed package ids.
 */
class LocalApps(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("local_apps", Context.MODE_PRIVATE)
    private val pm: PackageManager = ctx.packageManager
    private var installedIds: List<String>? = null

    /** Call when the app returns to the foreground; the next lookup rescans. */
    fun invalidate() { installedIds = null }

    fun rememberPackage(appId: String, pkg: String) = prefs.edit().putString("pkg:$appId", pkg).apply()
    fun rememberTag(appId: String, tag: String) = prefs.edit().putString("tag:$appId", tag).apply()

    @Suppress("DEPRECATION")
    private fun versionOf(pkg: String): String? = try {
        pm.getPackageInfo(pkg, 0).versionName
    } catch (_: PackageManager.NameNotFoundException) { null }

    @Suppress("DEPRECATION")
    private fun scanInstalled(): List<String> =
        installedIds ?: pm.getInstalledPackages(0).map { it.packageName }.also { installedIds = it }

    fun info(appId: String, token: String): LocalInfo? {
        val known = prefs.getString("pkg:$appId", null)
        val pkg = when {
            known != null && scanInstalled().contains(known) -> known
            else -> scanInstalled().firstOrNull {
                it != ctx.packageName && it.lowercase().contains(token)
            }?.also { rememberPackage(appId, it) }
        } ?: return null
        return LocalInfo(pkg, versionOf(pkg), prefs.getString("tag:$appId", null))
    }

    fun launchIntent(pkg: String): Intent? = pm.getLaunchIntentForPackage(pkg)
}
