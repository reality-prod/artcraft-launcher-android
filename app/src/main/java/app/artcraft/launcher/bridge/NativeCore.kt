package app.artcraft.launcher.bridge

import app.artcraft.launcher.data.ApkAsset
import org.json.JSONObject

/**
 * Kotlin face of the Rust bridge (rust/bridge). The Rust side ports the logic of
 * storytold/craft-launcher's core crate: version ordering, picking the right asset per
 * platform, and SHA-256 handling. The library is optional: if it isn't packaged
 * (build without -PbuildRust) every call falls back to a Kotlin implementation, so the
 * launcher always works.
 *
 * The external function names must stay in sync with the JNI exports in rust/bridge/src/lib.rs.
 */
object NativeCore {
    private val loaded: Boolean = try {
        System.loadLibrary("artcraft_launcher_bridge")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    val available: Boolean get() = loaded

    @JvmStatic external fun pickApk(releaseJson: String, abisCsv: String): String
    @JvmStatic external fun compareVersions(a: String, b: String): Int
    @JvmStatic external fun verifySha256(path: String, expectedHex: String): String
    @JvmStatic external fun findSha256(sumsText: String, fileName: String): String
    @JvmStatic external fun bridgeVersion(): String
    @JvmStatic external fun humanSize(bytes: Long): String
    @JvmStatic external fun plainNotes(markdown: String, maxChars: Int): String
    @JvmStatic external fun relativeTime(iso: String, nowEpochSeconds: Long): String

    private inline fun <T> tryNative(block: () -> T): T? =
        if (!loaded) null else try { block() } catch (_: UnsatisfiedLinkError) { null }

    /** "78.4 MB". */
    fun size(bytes: Long): String = tryNative { humanSize(bytes) } ?: KotlinFallback.humanSize(bytes)

    /** Release-note markdown as readable plain text; [maxChars] 0 means no limit. */
    fun notes(markdown: String, maxChars: Int = 0): String =
        tryNative { plainNotes(markdown, maxChars) } ?: KotlinFallback.plainNotes(markdown, maxChars)

    /** "3 days ago" for a GitHub timestamp, or "" when it can't be read. */
    fun ago(iso: String): String {
        val now = System.currentTimeMillis() / 1000
        return tryNative { relativeTime(iso, now) } ?: KotlinFallback.relativeTime(iso, now)
    }

    /** Version of the native library, or "Kotlin fallback". */
    fun describe(): String = tryNative { "Rust " + bridgeVersion() } ?: "Kotlin fallback"

    /** -1, 0 or 1. */
    fun compare(a: String, b: String): Int {
        if (loaded) {
            try { return compareVersions(a, b) } catch (_: UnsatisfiedLinkError) { }
        }
        return KotlinFallback.compare(a, b)
    }

    /** Empty string when the file matches, otherwise why not. */
    fun verify(path: String, expectedHex: String): String {
        if (loaded) {
            try { return verifySha256(path, expectedHex) } catch (_: UnsatisfiedLinkError) { }
        }
        return KotlinFallback.verify(path, expectedHex)
    }

    /** The checksum for [fileName] in a SHA256SUMS.txt body, or null. */
    fun checksumFor(sumsText: String, fileName: String): String? {
        val hit = if (loaded) {
            try { findSha256(sumsText, fileName) } catch (_: UnsatisfiedLinkError) { null }
        } else null
        return (hit ?: KotlinFallback.findSha256(sumsText, fileName)).ifEmpty { null }
    }

    /** Native asset picker for a raw GitHub release object; null when there is no APK or no bridge. */
    fun pickFromReleaseJson(releaseJson: String, abis: Array<String>): ApkAsset? {
        if (!loaded) return null
        return try {
            val o = JSONObject(pickApk(releaseJson, abis.joinToString(",")))
            if (!o.optBoolean("ok")) return null
            val a = o.optJSONObject("asset") ?: return null
            ApkAsset(a.getString("name"), a.getString("url"), a.optLong("size"),
                if (a.isNull("sha256")) null else a.getString("sha256"))
        } catch (_: Exception) { null } catch (_: UnsatisfiedLinkError) { null }
    }
}

/** Same behaviour as the Rust code, for devices/builds without the native library. */
internal object KotlinFallback {
    private class Parsed(val nums: List<Long>, val pre: List<String>)

    private fun parse(raw: String): Parsed {
        val s = raw.trim().dropWhile { !it.isDigit() }.substringBefore('+')
        val core = s.substringBefore('-')
        val pre = if (s.contains('-')) s.substringAfter('-').split('.', '-') else emptyList()
        val nums = core.split('.').map { p -> p.takeWhile { it.isDigit() }.toLongOrNull() ?: 0L }
        return Parsed(nums, pre)
    }

    fun compare(a: String, b: String): Int {
        val x = parse(a); val y = parse(b)
        for (i in 0 until maxOf(x.nums.size, y.nums.size)) {
            val c = (x.nums.getOrElse(i) { 0L }).compareTo(y.nums.getOrElse(i) { 0L })
            if (c != 0) return if (c < 0) -1 else 1
        }
        if (x.pre.isEmpty() && y.pre.isEmpty()) return 0
        if (x.pre.isEmpty()) return 1
        if (y.pre.isEmpty()) return -1
        for (i in 0 until minOf(x.pre.size, y.pre.size)) {
            val m = x.pre[i].toLongOrNull(); val n = y.pre[i].toLongOrNull()
            val c = when {
                m != null && n != null -> m.compareTo(n)
                m != null -> -1
                n != null -> 1
                else -> x.pre[i].compareTo(y.pre[i])
            }
            if (c != 0) return if (c < 0) -1 else 1
        }
        return x.pre.size.compareTo(y.pre.size).coerceIn(-1, 1)
    }

    fun verify(path: String, expectedHex: String): String = try {
        val want = expectedHex.trim()
        if (want.length != 64 || !want.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "expected checksum is not a SHA-256 hex string"
        } else {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            java.io.File(path).inputStream().use { i ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = i.read(buf); if (n < 0) break; md.update(buf, 0, n) }
            }
            val got = md.digest().joinToString("") { "%02x".format(it) }
            if (got.equals(want, ignoreCase = true)) "" else "checksum mismatch"
        }
    } catch (e: java.io.IOException) { "cannot read file: ${e.message}" }

    fun humanSize(b: Long): String {
        if (b < 1024) return "$b B"
        var v = b.toDouble()
        var i = -1
        val units = listOf("KB", "MB", "GB", "TB")
        while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
        return "%.1f %s".format(v, units[i])
    }

    fun relativeTime(iso: String, now: Long): String {
        val then = try { java.time.Instant.parse(iso.trim()).epochSecond } catch (_: Exception) { return "" }
        val d = now - then
        fun ago(n: Long, unit: String) = if (n == 1L) "1 $unit ago" else "$n ${unit}s ago"
        return when {
            d < 60 -> "just now"
            d < 3_600 -> ago(d / 60, "minute")
            d < 86_400 -> ago(d / 3_600, "hour")
            d < 172_800 -> "yesterday"
            d < 2_592_000 -> ago(d / 86_400, "day")
            d < 31_536_000 -> ago(d / 2_592_000, "month")
            else -> ago(d / 31_536_000, "year")
        }
    }

    fun plainNotes(md: String, max: Int): String {
        var t = md
            .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), "")
            .replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "\$1")
            .replace(Regex("^```.*$", RegexOption.MULTILINE), "")
            .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE), "• ")
            .replace(Regex("(\\*\\*|__|~~|`)"), "")
            .replace(Regex("<[a-zA-Z/!][^>]*>"), "")
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        if (max > 0 && t.length > max) t = t.take(max).trimEnd() + "…"
        return t
    }

    fun findSha256(text: String, fileName: String): String {
        for (line in text.lineSequence()) {
            val t = line.trim()
            val i = t.indexOfFirst { it.isWhitespace() }
            if (i != 64) continue
            val digest = t.substring(0, 64)
            if (!digest.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) continue
            val name = t.substring(i).trim().trimStart('*').removePrefix("./")
            if (name == fileName) return digest.lowercase()
        }
        return ""
    }
}
