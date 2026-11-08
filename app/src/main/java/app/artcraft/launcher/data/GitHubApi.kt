package app.artcraft.launcher.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class GitHubException(message: String, val rateLimited: Boolean = false) : IOException(message)

data class RepoInfo(val name: String, val description: String, val archived: Boolean, val fork: Boolean)

/** Unauthenticated GitHub REST (60 req/h per IP). A full scan costs ~1 + N calls. */
object GitHubApi {
    private const val BASE = "https://api.github.com"

    /** Set once at startup. Holds ETag + body per request so unchanged answers cost no rate limit. */
    @Volatile var cacheDir: File? = null

    private fun key(path: String) = path.hashCode().toUInt().toString(16)
    private fun readCache(path: String): Pair<String, String>? = try {
        val d = cacheDir
        val e = d?.let { File(it, key(path) + ".etag") }
        val b = d?.let { File(it, key(path) + ".json") }
        if (e != null && b != null && e.isFile && b.isFile) e.readText() to b.readText() else null
    } catch (_: IOException) { null }
    private fun writeCache(path: String, etag: String, body: String) {
        try {
            val d = cacheDir ?: return
            d.mkdirs()
            File(d, key(path) + ".json").writeText(body)
            File(d, key(path) + ".etag").writeText(etag)
        } catch (_: IOException) { }
    }

    /** Returns the body, or null on 404. */
    private fun get(path: String): String? {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        c.setRequestProperty("User-Agent", "ArtCraft-Launcher-Android")
        val cached = readCache(path)
        if (cached != null) c.setRequestProperty("If-None-Match", cached.first)
        try {
            return when (val code = c.responseCode) {
                200 -> c.inputStream.bufferedReader().use { it.readText() }.also { body ->
                    c.getHeaderField("ETag")?.let { writeCache(path, it, body) }
                }
                304 -> cached?.second ?: throw GitHubException("GitHub sent 304 without a cached copy")
                404 -> null
                403, 429 -> {
                    val limited = c.getHeaderField("X-RateLimit-Remaining") == "0" || code == 429
                    throw GitHubException(
                        if (limited) "GitHub rate limit reached. Try again in a few minutes."
                        else "GitHub refused the request (HTTP 403).",
                        limited,
                    )
                }
                else -> throw GitHubException("GitHub returned HTTP $code")
            }
        } finally {
            c.disconnect()
        }
    }

    fun listOrgRepos(org: String): List<RepoInfo> {
        val body = get("/orgs/$org/repos?per_page=100&type=public") ?: return emptyList()
        val arr = JSONArray(body)
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            RepoInfo(
                o.getString("name"),
                if (o.isNull("description")) "" else o.getString("description"),
                o.optBoolean("archived"), o.optBoolean("fork"),
            )
        }
    }

    /**
     * Newest non-draft release that has an APK (pre-releases count: these apps are in beta).
     * Falls back to the newest release without APKs so the UI can say "no APK yet".
     */
    fun latestRelease(owner: String, repo: String): ReleaseInfo? {
        val body = get("/repos/$owner/$repo/releases?per_page=10") ?: return null
        val arr = JSONArray(body)
        var fallback: ReleaseInfo? = null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optBoolean("draft")) continue
            val r = parseRelease(o)
            if (r.apks.isNotEmpty()) return r
            if (fallback == null) fallback = r
        }
        return fallback
    }

    private fun parseRelease(o: JSONObject): ReleaseInfo {
        val assets = o.optJSONArray("assets") ?: JSONArray()
        val apks = buildList {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.getString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                // GitHub now publishes "sha256:<hex>" per asset; verify it when present.
                val digest = if (a.isNull("digest")) null else a.getString("digest")
                add(ApkAsset(name, a.getString("browser_download_url"), a.optLong("size"),
                    digest?.removePrefix("sha256:")?.takeIf { digest.startsWith("sha256:") }))
            }
        }
        return ReleaseInfo(
            tag = o.getString("tag_name"),
            title = if (o.isNull("name")) "" else o.optString("name"),
            publishedAt = if (o.isNull("published_at")) "" else o.optString("published_at"),
            prerelease = o.optBoolean("prerelease"),
            pageUrl = o.optString("html_url"),
            apks = apks,
        )
    }
}
