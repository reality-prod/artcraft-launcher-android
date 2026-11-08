package app.artcraft.launcher.bridge

import android.content.Context
import app.artcraft.launcher.data.ApkAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Streams a release APK to cacheDir with progress, and verifies GitHub's SHA-256 when it provides one. */
class Downloader(private val ctx: Context) {

    fun dir(): File = File(ctx.cacheDir, "apks").apply { mkdirs() }

    fun purge() { dir().listFiles()?.forEach { it.delete() } }

    suspend fun download(asset: ApkAsset, onProgress: (done: Long, total: Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            val safe = asset.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val dest = File(dir(), "${asset.url.hashCode().toUInt().toString(16)}-$safe")
            val part = File(dest.path + ".part")

            // Reuse a previous download only if we can prove it's intact.
            if (dest.exists() && asset.sha256 != null && NativeCore.verify(dest.path, asset.sha256).isEmpty()) return@withContext dest

            val c = URL(asset.url).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "ArtCraft-Launcher-Android")
            try {
                if (c.responseCode !in 200..299) throw IOException("Download failed (HTTP ${c.responseCode})")
                val total = c.contentLengthLong.takeIf { it > 0 } ?: asset.size
                val md = MessageDigest.getInstance("SHA-256")
                c.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            md.update(buf, 0, n)
                            done += n
                            onProgress(done, total)
                            ensureActive()
                        }
                    }
                }
                asset.sha256?.let { expected ->
                    val got = md.digest().joinToString("") { "%02x".format(it) }
                    if (!got.equals(expected, true)) throw IOException("Checksum mismatch. The download was discarded.")
                }
                if (dest.exists()) dest.delete()
                if (!part.renameTo(dest)) throw IOException("Could not save the APK")
                dest
            } finally {
                c.disconnect()
                part.delete() // no-op after a successful rename
            }
        }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val b = ByteArray(64 * 1024); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
