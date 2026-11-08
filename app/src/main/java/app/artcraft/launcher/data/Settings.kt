package app.artcraft.launcher.data

import android.content.Context

data class SettingsState(
    val wifiOnly: Boolean = false,
    val includePre: Boolean = true,
    /** Optional GitHub token. Stored in this app's private storage only. */
    val token: String = "",
)

class SettingsStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load() = SettingsState(
        wifiOnly = p.getBoolean("wifi", false),
        includePre = p.getBoolean("pre", true),
        token = p.getString("token", "") ?: "",
    )

    fun save(s: SettingsState) {
        p.edit().putBoolean("wifi", s.wifiOnly).putBoolean("pre", s.includePre).putString("token", s.token).apply()
    }

    /** Pushes the settings that the network layer reads. */
    fun apply(s: SettingsState) {
        GitHubApi.includePrereleases = s.includePre
        GitHubApi.token = s.token.trim().ifEmpty { null }
    }
}
