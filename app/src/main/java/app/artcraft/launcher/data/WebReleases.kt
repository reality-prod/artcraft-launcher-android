package app.artcraft.launcher.data

import android.net.Uri
import java.io.File
import java.io.IOException
import java.net.URLDecoder

/**
 * Last-resort release lookup that never touches api.github.com: the public releases.atom feed
 * for the newest tags, and the "expanded assets" fragment GitHub's own releases page loads
 * for the file list. These are scraped, so every step is defensive and a miss is just null.
 * No sizes or checksums are available this way (the download itself reports its size).
 */
object WebReleases {
    private val entryRe = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
    private val hrefRe = Regex("""<link[^>]*rel="alternate"[^>]*href="([^"]+)"""")
    private val titleRe = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
    private val updatedRe = Regex("<updated>(.*?)</updated>")

    fun latest(owner: String, repo: String, cacheDir: File?): ReleaseInfo? {
        return try {
            val atom = Http.getText("https://github.com/$owner/$repo/releases.atom", cacheDir) ?: return null
            var first: ReleaseInfo? = null
            for (entry in entryRe.findAll(atom).take(3).map { it.groupValues[1] }) {
                val href = hrefRe.find(entry)?.groupValues?.get(1) ?: continue
                val tag = URLDecoder.decode(href.substringAfter("/releases/tag/", ""), "UTF-8")
                if (tag.isBlank()) continue
                val apks = assetsFor(owner, repo, tag, cacheDir)
                val rel = ReleaseInfo(
                    tag = tag,
                    title = titleRe.find(entry)?.groupValues?.get(1)?.trim().orEmpty(),
                    publishedAt = updatedRe.find(entry)?.groupValues?.get(1).orEmpty(),
                    prerelease = false,
                    pageUrl = "https://github.com/$owner/$repo/releases/tag/${Uri.encode(tag)}",
                    apks = apks,
                )
                if (apks.isNotEmpty()) return rel
                if (first == null) first = rel
            }
            first
        } catch (_: IOException) {
            null
        }
    }

    private fun assetsFor(owner: String, repo: String, tag: String, cacheDir: File?): List<ApkAsset> {
        val html = Http.getText(
            "https://github.com/$owner/$repo/releases/expanded_assets/${Uri.encode(tag)}", cacheDir,
        ) ?: return emptyList()
        val re = Regex("""href="(/${Regex.escape(owner)}/${Regex.escape(repo)}/releases/download/[^"]+?\.apk)"""",
            RegexOption.IGNORE_CASE)
        return re.findAll(html).map { it.groupValues[1] }.distinct().map { path ->
            ApkAsset(URLDecoder.decode(path.substringAfterLast('/'), "UTF-8"),
                "https://github.com$path", 0L, null)
        }.toList()
    }
}
