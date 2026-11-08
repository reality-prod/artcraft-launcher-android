package app.artcraft.launcher.data

import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * A saved catalog of direct download links, generated on a schedule by a GitHub Action
 * (tools/update_catalog.py) and served from raw.githubusercontent.com. Phones read one small
 * file instead of calling the rate-limited GitHub API, and the links and SHA-256 checksums
 * are already resolved. Everything is optional: if it is missing or unreadable the launcher
 * falls back to the API, then to the public release pages, then to the last saved links.
 */
object ManifestSource {
    const val DEFAULT_URL =
        "https://raw.githubusercontent.com/reality-prod/artcraft-launcher-android/main/catalog.json"

    class Manifest(val generated: String, val releases: Map<String, ReleaseInfo>, val extras: List<Extra>)

    /** A repo the generator found that the launcher doesn't list by name yet. */
    class Extra(val owner: String, val repo: String, val name: String, val description: String)

    /** Only GitHub release downloads are accepted, whatever the file says. */
    fun allowedUrl(url: String) =
        url.startsWith("https://github.com/") && url.contains("/releases/download/")

    fun fetch(cacheDir: File?, url: String = DEFAULT_URL): Manifest? = try {
        Http.getText(url, cacheDir)?.let(::parse)
    } catch (_: IOException) {
        null
    }

    fun parse(text: String): Manifest? = try {
        val root = JSONObject(text)
        if (root.optInt("schema") != 1) null
        else {
            val apps = root.optJSONArray("apps")
            val releases = mutableMapOf<String, ReleaseInfo>()
            val extras = mutableListOf<Extra>()
            for (i in 0 until (apps?.length() ?: 0)) {
                val a = apps!!.getJSONObject(i)
                val repoId = a.getString("repo")
                val assets = a.optJSONArray("assets")
                val apks = buildList {
                    for (j in 0 until (assets?.length() ?: 0)) {
                        val x = assets!!.getJSONObject(j)
                        val name = x.getString("name")
                        val u = x.getString("url")
                        if (!name.endsWith(".apk", true) || !allowedUrl(u)) continue
                        val sha = if (x.isNull("sha256")) null
                                  else x.optString("sha256").takeIf { it.length == 64 }
                        add(ApkAsset(name, u, x.optLong("size"), sha))
                    }
                }
                if (a.optString("tag").isNotEmpty()) {
                    releases[repoId.lowercase()] = ReleaseInfo(
                        tag = a.getString("tag"),
                        title = a.optString("title"),
                        publishedAt = a.optString("published"),
                        prerelease = a.optBoolean("prerelease"),
                        pageUrl = a.optString("page"),
                        apks = apks,
                        notes = a.optString("notes"),
                    )
                }
                val owner = repoId.substringBefore('/')
                val repo = repoId.substringAfter('/')
                if (owner.isNotEmpty() && repo.isNotEmpty()) {
                    extras += Extra(owner, repo, a.optString("name").ifBlank { Sources.prettyName(repo) },
                        a.optString("description"))
                }
            }
            Manifest(root.optString("generated"), releases, extras)
        }
    } catch (_: Exception) {
        null
    }
}
