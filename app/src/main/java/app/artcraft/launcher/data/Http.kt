package app.artcraft.launcher.data

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Plain GET with ETag caching for hosts that are not api.github.com: the saved catalog on
 * raw.githubusercontent.com and the public github.com release pages. None of these count
 * against the GitHub API rate limit.
 */
object Http {
    const val USER_AGENT = "ArtCraft-Launcher-Android"

    /** Returns the body, or null on 404. Throws IOException on anything else that fails. */
    fun getText(url: String, cacheDir: File?, accept: String? = null): String? {
        val key = url.hashCode().toUInt().toString(16)
        val etagFile = cacheDir?.let { File(it, "$key.etag") }
        val bodyFile = cacheDir?.let { File(it, "$key.body") }
        val cached = if (etagFile?.isFile == true && bodyFile?.isFile == true)
            etagFile.readText() to bodyFile.readText() else null

        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", USER_AGENT)
        accept?.let { c.setRequestProperty("Accept", it) }
        if (cached != null) c.setRequestProperty("If-None-Match", cached.first)
        try {
            return when (val code = c.responseCode) {
                200 -> c.inputStream.bufferedReader().use { it.readText() }.also { body ->
                    val etag = c.getHeaderField("ETag")
                    if (etag != null && cacheDir != null) {
                        try {
                            cacheDir.mkdirs()
                            File(cacheDir, "$key.body").writeText(body)
                            File(cacheDir, "$key.etag").writeText(etag)
                        } catch (_: IOException) { }
                    }
                }
                304 -> cached?.second ?: throw IOException("304 without a cached copy")
                404 -> null
                else -> throw IOException("${URL(url).host} returned HTTP $code")
            }
        } finally {
            c.disconnect()
        }
    }
}
