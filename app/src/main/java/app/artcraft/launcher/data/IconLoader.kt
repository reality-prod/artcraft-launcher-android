package app.artcraft.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Tries each candidate URL in order, caches bytes on disk and bitmaps in memory. */
object IconLoader {
    private val mem = LruCache<String, Bitmap>(24)

    suspend fun load(ctx: Context, urls: List<String>): ImageBitmap? = withContext(Dispatchers.IO) {
        for (u in urls) {
            mem.get(u)?.let { return@withContext it.asImageBitmap() }
            val f = File(ctx.cacheDir, "icons/${u.hashCode().toUInt().toString(16)}.bin")
            val bytes = if (f.exists()) f.readBytes()
                        else fetch(u)?.also { f.parentFile?.mkdirs(); f.writeBytes(it) }
            val bmp = bytes?.let(::decode)
            if (bmp != null) {
                mem.put(u, bmp)
                return@withContext bmp.asImageBitmap()
            }
            if (f.exists()) f.delete() // bad cache entry
        }
        null
    }

    private fun fetch(url: String): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "ArtCraft-Launcher-Android")
        return try {
            if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null
        } catch (_: IOException) { null } finally { c.disconnect() }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0) return null
        var s = 1
        while (o.outWidth / (s * 2) >= 256) s *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
    }
}
