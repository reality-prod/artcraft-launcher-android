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
    @JvmStatic external fun nativeValidateApk(path: String): String
    @JvmStatic external fun nativeIsApkCompatible(apkName: String, abisCsv: String): Boolean
    @JvmStatic external fun nativeGetAbiFromApkName(apkName: String): String
    @JvmStatic external fun nativeExtractPackageName(path: String): String
    @JvmStatic external fun nativeExtractVersionCode(apkName: String): Int
    @JvmStatic external fun nativeExtractVersionName(apkName: String): String
    @JvmStatic external fun nativeGetApkHash(path: String): String
    @JvmStatic external fun nativeIsSplitApk(apkName: String): Boolean
    @JvmStatic external fun nativeGetSupportedAbis(apkName: String): String

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

    /** Validate an APK file. Returns null on success, or error message on failure. */
    fun validateApk(path: String): String? {
        if (!loaded) return KotlinFallback.validateApk(path)
        return try {
            val result = nativeValidateApk(path)
            if (result.isEmpty()) null else result
        } catch (_: Exception) { "Validation failed" } catch (_: UnsatisfiedLinkError) { null }
    }

    /** Check if APK is compatible with given ABIs. */
    fun isApkCompatible(apkName: String, abis: Array<String>): Boolean {
        if (!loaded) return KotlinFallback.isApkCompatible(apkName, abis)
        return try {
            nativeIsApkCompatible(apkName, abis.joinToString(","))
        } catch (_: Exception) { false } catch (_: UnsatisfiedLinkError) { false }
    }

    /** Get ABI from APK filename. Returns null if universal or not determinable. */
    fun getAbiFromApkName(apkName: String): String? {
        if (!loaded) return KotlinFallback.getAbiFromApkName(apkName)
        return try {
            val abi = nativeGetAbiFromApkName(apkName)
            if (abi.isEmpty()) null else abi
        } catch (_: Exception) { null } catch (_: UnsatisfiedLinkError) { null }
    }

    /** Extract package name from APK file. */
    fun extractPackageName(path: String): String? {
        if (!loaded) return KotlinFallback.extractPackageName(path)
        return try {
            val name = nativeExtractPackageName(path)
            if (name.isEmpty()) null else name
        } catch (_: Exception) { null } catch (_: UnsatisfiedLinkError) { null }
    }

    /** Extract version code from APK filename. */
    fun extractVersionCode(apkName: String): Int {
        if (!loaded) return KotlinFallback.extractVersionCode(apkName)
        return try {
            nativeExtractVersionCode(apkName)
        } catch (_: Exception) { 0 } catch (_: UnsatisfiedLinkError) { KotlinFallback.extractVersionCode(apkName) }
    }

    /** Extract version name from APK filename. */
    fun extractVersionName(apkName: String): String? {
        if (!loaded) return KotlinFallback.extractVersionName(apkName)
        return try {
            val name = nativeExtractVersionName(apkName)
            if (name.isEmpty()) null else name
        } catch (_: Exception) { null } catch (_: UnsatisfiedLinkError) { null }
    }

    /** Get SHA-256 hash of APK file. */
    fun getApkHash(path: String): String? {
        if (!loaded) return KotlinFallback.getApkHash(path)
        return try {
            val hash = nativeGetApkHash(path)
            if (hash.isEmpty()) null else hash
        } catch (_: Exception) { null } catch (_: UnsatisfiedLinkError) { null }
    }

    /** Check if APK is a split APK. */
    fun isSplitApk(apkName: String): Boolean {
        if (!loaded) return KotlinFallback.isSplitApk(apkName)
        return try {
            nativeIsSplitApk(apkName)
        } catch (_: Exception) { false } catch (_: UnsatisfiedLinkError) { KotlinFallback.isSplitApk(apkName) }
    }

    /** Get supported ABIs from APK filename. */
    fun getSupportedAbis(apkName: String): List<String> {
        if (!loaded) return KotlinFallback.getSupportedAbis(apkName)
        return try {
            val abis = nativeGetSupportedAbis(apkName)
            if (abis.isEmpty()) KotlinFallback.getSupportedAbis(apkName) else abis.split(',').map { it.trim() }
        } catch (_: Exception) { KotlinFallback.getSupportedAbis(apkName) } catch (_: UnsatisfiedLinkError) { KotlinFallback.getSupportedAbis(apkName) }
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

    fun validateApk(path: String): String? {
        return try {
            val apkFile = java.io.File(path)
            if (!apkFile.exists()) return "File not found"
            if (apkFile.length() == 0L) return "APK file is empty"
            if (!path.lowercase().endsWith(".apk")) return "Not an APK file"
            
            // Try to read as ZIP
            java.util.zip.ZipFile(apkFile).use { zip ->
                // Check if it's a valid ZIP
                if (zip.size() == 0) return "Empty ZIP file"
            }
            
            null // Success
        } catch (e: Exception) {
            e.message ?: "Validation failed"
        }
    }

    fun isApkCompatible(apkName: String, abis: Array<String>): Boolean {
        val nameLower = apkName.lowercase()
        
        // If no specific ABI in filename, it's usually universal
        if (!abis.any { nameLower.contains(it.lowercase()) }) {
            return true
        }
        
        // Check for specific ABI matches
        for (abi in abis) {
            when (abi.lowercase()) {
                "arm64-v8a" -> if (nameLower.contains("arm64") || nameLower.contains("aarch64")) return true
                "armeabi-v7a", "armeabi" -> if (nameLower.contains("armeabi") || nameLower.contains("armv7")) return true
                "x86_64" -> if (nameLower.contains("x86_64")) return true
                "x86" -> if (nameLower.contains("x86") && !nameLower.contains("x86_64")) return true
            }
        }
        
        return false
    }

    fun getAbiFromApkName(apkName: String): String? {
        val nameLower = apkName.lowercase()
        
        return when {
            nameLower.contains("arm64") || nameLower.contains("aarch64") -> "arm64-v8a"
            nameLower.contains("armeabi-v7a") || nameLower.contains("armv7") || nameLower.contains("armeabi") -> "armeabi-v7a"
            nameLower.contains("x86_64") -> "x86_64"
            nameLower.contains("x86") -> "x86"
            else -> null
        }
    }

    fun extractPackageName(path: String): String? {
        return try {
            // Extract from filename
            val filename = java.io.File(path).name
            val name = filename.substringBeforeLast(".apk")
            // Convert to package name format
            name.lowercase().replace("-", ".")
        } catch (e: Exception) {
            null
        }
    }

    fun extractVersionCode(apkName: String): Int {
        // Try to extract version from filename
        val versionRegex = Regex("(-|_|\\s)v?(\\d+\\.\\d+\\.\\d+)")
        val match = versionRegex.find(apkName.lowercase())
        if (match != null) {
            val version = match.groupValues[2]
            val parts = version.split('.')
            if (parts.size >= 3) {
                try {
                    val major = parts[0].toInt()
                    val minor = parts[1].toInt()
                    val patch = parts[2].toInt()
                    return major * 10000 + minor * 100 + patch
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        return 0
    }

    fun extractVersionName(apkName: String): String? {
        // Similar to version code but return the version string
        val versionRegex = Regex("(-|_|\\s)v?(\\d+\\.\\d+\\.\\d+)")
        val match = versionRegex.find(apkName.lowercase())
        return match?.groupValues?.get(2)
    }

    fun getApkHash(path: String): String? {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            java.io.File(path).inputStream().use { i ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = i.read(buf); if (n < 0) break; md.update(buf, 0, n) }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    fun isSplitApk(apkName: String): Boolean {
        val nameLower = apkName.lowercase()
        val abiSuffixes = listOf("-arm64-v8a", "-armeabi-v7a", "-x86_64", "-x86", "-aarch64", "-armv7")
        return abiSuffixes.any { nameLower.contains(it) }
    }

    fun getSupportedAbis(apkName: String): List<String> {
        val nameLower = apkName.lowercase()
        val abis = mutableListOf<String>()
        
        if (nameLower.contains("arm64") || nameLower.contains("aarch64")) {
            abis.add("arm64-v8a")
        }
        if (nameLower.contains("armeabi-v7a") || nameLower.contains("armv7") || nameLower.contains("armeabi")) {
            abis.add("armeabi-v7a")
        }
        if (nameLower.contains("x86_64")) {
            abis.add("x86_64")
        }
        if (nameLower.contains("x86") && !nameLower.contains("x86_64")) {
            abis.add("x86")
        }
        
        if (abis.isEmpty()) {
            abis.add("universal")
        }
        
        return abis
    }

}
