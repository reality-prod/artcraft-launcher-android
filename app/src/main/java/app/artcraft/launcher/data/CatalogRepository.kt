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

/**
 * Finds the newest release of every product without depending on the GitHub API:
 *   1. the saved catalog of direct links (ManifestSource), one request for everything
 *   2. api.github.com with ETag caching (a 304 costs no rate limit)
 *   3. the public release pages (WebReleases), when the API is rate-limited or down
 *   4. the links saved on this phone from the last good scan
 */
class CatalogRepository(ctx: Context) {
    private val cacheFile = File(ctx.filesDir, "catalog.json")
    private val webCache = File(ctx.cacheDir, "web")

    @Volatile private var fallbackNote: String? = null

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

    private fun releaseFor(c: Repo, manifest: ManifestSource.Manifest?, previous: CatalogApp?): ReleaseInfo? {
        manifest?.releases?.get(c.id.lowercase())?.let { return it }
        try {
            return GitHubApi.latestRelease(c.owner, c.repo)
        } catch (e: IOException) {
            val limited = (e as? GitHubException)?.rateLimited == true
            WebReleases.latest(c.owner, c.repo, webCache)?.let {
                fallbackNote = if (limited) "GitHub's API limit is reached, so download links came from the public release pages."
                               else "GitHub's API is unreachable, so download links came from the public release pages."
                return it
            }
            val saved = previous?.takeIf { it.owner.equals(c.owner, true) && it.repo.equals(c.repo, true) }?.release
            if (saved != null) {
                fallbackNote = "Using download links saved from the last scan."
                return saved
            }
            throw e
        }
    }

    /** Checks candidates in order; the first release with an APK wins, else the first release found. */
    private fun resolve(k: KnownApp, manifest: ManifestSource.Manifest?, previous: CatalogApp?): CatalogApp {
        var best: Pair<Repo, ReleaseInfo>? = null
        for (c in k.candidates) {
            val r = releaseFor(c, manifest, previous) ?: continue
            if (r.apks.isNotEmpty()) { best = c to r; break }
            if (best == null) best = c to r
        }
        return build(k, best?.first, best?.second)
    }

    suspend fun scan(): ScanResult = withContext(Dispatchers.IO) {
        val previous = cached().orEmpty().associateBy { it.id }
        var warning: String? = null
        fallbackNote = null

        val manifest = ManifestSource.fetch(webCache)

        // 1) apps in the artcraft-android org that we don't list by name yet
        val discovered = mutableListOf<KnownApp>()
        var orgCount: Int? = null
        fun isKnown(owner: String, repo: String) =
            Sources.KNOWN.any { k -> k.candidates.any { it.owner.equals(owner, true) && it.repo.equals(repo, true) } }
        if (manifest != null) {
            val org = manifest.extras.filter { it.owner.equals(Sources.ANDROID_ORG, true) }
            orgCount = org.size
            for (x in org) if (!isKnown(x.owner, x.repo) && x.repo.endsWith("-android")) {
                discovered += KnownApp(x.name, x.description.ifBlank { "Community ArtCraft app" }, listOf(Repo(x.owner, x.repo)))
            }
        } else {
            try {
                var seen = 0
                for (org in Sources.ORGS) for (r in GitHubApi.listOrgRepos(org)) {
                    seen++
                    if (!isKnown(org, r.name) && !r.archived && !r.fork && r.name.endsWith("-android")) {
                        discovered += KnownApp(Sources.prettyName(r.name),
                            r.description.ifBlank { "Community ArtCraft app" }, listOf(Repo(org, r.name)))
                    }
                }
                orgCount = seen
            } catch (e: IOException) {
                warning = e.message
            }
        }

        // 2) newest release per product, in parallel; one failure never sinks the rest
        val all = Sources.KNOWN + discovered
        val results = coroutineScope {
            all.map { k ->
                async {
                    val prev = previous[k.candidates.first().id]
                    try {
                        resolve(k, manifest, prev) to null
                    } catch (e: IOException) {
                        (prev ?: build(k, null, null)) to (e.message ?: "Network error")
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
        if (warning == null) warning = fallbackNote
        if (failures.size < results.size) save(apps)

        val source = when {
            manifest != null -> "saved catalog"
            fallbackNote != null -> "release pages"
            else -> "GitHub API"
        }
        val summary = buildString {
            append(Sources.ANDROID_ORG)
            if (orgCount != null) append(": $orgCount repos") else append(": unreachable")
            append(" · storytold/artcraft checked · via $source")
        }
        ScanResult(apps, warning, summary)
    }

    /** Newest launcher release for the self-update check; null when unknown or offline. */
    suspend fun selfRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val self = Sources.SELF
        try {
            ManifestSource.fetch(webCache)?.releases?.get(self.id.lowercase())
                ?: GitHubApi.latestRelease(self.owner, self.repo)
        } catch (_: IOException) {
            WebReleases.latest(self.owner, self.repo, webCache)
        }
    }

    /** What to show before the first scan finishes. */
    fun placeholders(): List<CatalogApp> = Sources.KNOWN.map { build(it, null, null) }
}
