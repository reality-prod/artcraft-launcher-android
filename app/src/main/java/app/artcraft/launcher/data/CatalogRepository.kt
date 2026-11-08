package app.artcraft.launcher.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.IOException

data class ScanResult(val apps: List<CatalogApp>, val warning: String?, val summary: String?)

class CatalogRepository(ctx: Context) {
    private val cacheFile = File(ctx.filesDir, "catalog.json")

    init { GitHubApi.cacheDir = File(ctx.cacheDir, "github") }

    fun cached(): List<CatalogApp>? = try {
        if (!cacheFile.exists()) null
        else JSONArray(cacheFile.readText()).let { a -> List(a.length()) { catalogAppFromJson(a.getJSONObject(it)) } }
    } catch (_: Exception) { null }

    private fun save(apps: List<CatalogApp>) {
        try { cacheFile.writeText(JSONArray().also { a -> apps.forEach { a.put(it.toJson()) } }.toString()) }
        catch (_: IOException) { }
    }

    private fun build(k: KnownApp, source: Repo?, release: ReleaseInfo?): CatalogApp {
        val first = k.candidates.first()
        val src = source ?: first
        return CatalogApp(
            id = first.id, name = k.name, tagline = k.tagline,
            owner = src.owner, repo = src.repo,
            iconUrls = Sources.iconCandidates(k.candidates, k.name.lowercase().replace(" ", "")),
            release = release, desktopUrl = k.desktopUrl,
        )
    }

    /** Checks candidates in order; the first release with an APK wins, else the first release found. */
    private fun resolve(k: KnownApp): CatalogApp {
        var best: Pair<Repo, ReleaseInfo>? = null
        for (c in k.candidates) {
            val r = GitHubApi.latestRelease(c.owner, c.repo) ?: continue
            if (r.apks.isNotEmpty()) { best = c to r; break }
            if (best == null) best = c to r
        }
        return build(k, best?.first, best?.second)
    }

    /** Scans the Android org for *-android repos, then asks each product's repos for their newest release. */
    suspend fun scan(): ScanResult = withContext(Dispatchers.IO) {
        val previous = cached().orEmpty().associateBy { it.id }
        var warning: String? = null
        var orgCount: Int? = null

        // 1) discover repos in artcraft-android that we don't know yet
        val discovered = mutableListOf<KnownApp>()
        try {
            var seen = 0
            for (org in Sources.ORGS) {
                for (r in GitHubApi.listOrgRepos(org)) {
                    seen++
                    val known = Sources.KNOWN.any { k -> k.candidates.any { it.owner.equals(org, true) && it.repo.equals(r.name, true) } }
                    if (!known && !r.archived && !r.fork && r.name.endsWith("-android")) {
                        discovered += KnownApp(Sources.prettyName(r.name),
                            r.description.ifBlank { "Community ArtCraft app" }, listOf(Repo(org, r.name)))
                    }
                }
            }
            orgCount = seen
        } catch (e: IOException) {
            warning = e.message
        }

        // 2) latest release per product, in parallel; one failure never sinks the rest
        val all = Sources.KNOWN + discovered
        val results = coroutineScope {
            all.map { k ->
                async {
                    try {
                        resolve(k) to null
                    } catch (e: IOException) {
                        (previous[k.candidates.first().id] ?: build(k, null, null)) to (e.message ?: "Network error")
                    }
                }
            }.awaitAll()
        }
        val apps = results.map { it.first }
        val failures = results.mapNotNull { it.second }
        if (warning == null && failures.isNotEmpty()) {
            warning = if (failures.size == results.size) "Couldn't reach GitHub. Showing the last known list."
                      else failures.first()
        }
        if (failures.size < results.size) save(apps)

        val summary = buildString {
            append(Sources.ANDROID_ORG)
            if (orgCount != null) append(": $orgCount repos") else append(": unreachable")
            append(" · storytold/artcraft checked")
        }
        ScanResult(apps, warning, summary)
    }

    /** What to show before the first scan finishes. */
    fun placeholders(): List<CatalogApp> = Sources.KNOWN.map { build(it, null, null) }
}
