package app.artcraft.launcher.data

import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

data class ApkAsset(val name: String, val url: String, val size: Long, val sha256: String?)

data class ReleaseInfo(
    val tag: String,
    val title: String,
    val publishedAt: String,
    val prerelease: Boolean,
    val pageUrl: String,
    val apks: List<ApkAsset>,
    val notes: String = "",
)

data class Repo(val owner: String, val repo: String) {
    val id get() = "$owner/$repo"
}

data class CatalogApp(
    val id: String,
    val name: String,
    val tagline: String,
    /** The repo the shown release came from (or the primary candidate when there is none). */
    val owner: String,
    val repo: String,
    val iconUrls: List<String>,
    val release: ReleaseInfo?,
    /** Desktop edition, if one exists. Never installable here; the mobile UI is the Kotlin app. */
    val desktopUrl: String?,
) {
    val repoUrl get() = "https://github.com/$owner/$repo"
    /** Short lowercase token used to spot an already-installed copy, e.g. "pdfcraft". */
    val token get() = id.substringAfter('/').removeSuffix("-android").lowercase()
}

/**
 * One product. [candidates] are checked in order, artcraft-android first; the first release
 * that carries an APK wins, so a desktop repo that later adds an APK is picked up too.
 */
data class KnownApp(
    val name: String,
    val tagline: String,
    val candidates: List<Repo>,
    val desktopUrl: String? = null,
)

object Sources {
    const val ANDROID_ORG = "artcraft-android"

    /** GitHub orgs scanned for "*-android" repos. */
    val ORGS = listOf(ANDROID_ORG)

    /** Where this launcher itself is published; used for the self-update check. */
    val SELF = Repo("reality-prod", "artcraft-launcher-android")

    val KNOWN = listOf(
        KnownApp("PdfCraft", "PDF reader and editor: comments, forms, sign, read aloud",
            listOf(Repo(ANDROID_ORG, "pdfcraft-android")), "https://github.com/storytold/pdfcraft"),
        KnownApp("FilmCraft", "Video editor: timeline, filters, transitions, captions",
            listOf(Repo(ANDROID_ORG, "filmcraft-android")), "https://github.com/storytold/filmcraft"),
        KnownApp("LightCraft", "Photo editor with a Lightroom-style workflow",
            listOf(Repo(ANDROID_ORG, "lightcraft-android"))),
        // Desktop-only today. The Android org is checked first in case a port appears.
        KnownApp("ArtCraft", "The IDE for artists: AI image and video crafting",
            listOf(Repo(ANDROID_ORG, "artcraft-android"), Repo("storytold", "artcraft")),
            "https://getartcraft.com"),
    ) + desktopSuite()

    /**
     * The rest of the Craft App Suite as the desktop launcher lists it. None ship an APK today, so
     * they show as Coming soon; the storytold repo is still checked in case an APK ever appears.
     * Taglines are deliberately generic until each app's own description is confirmed.
     */
    private fun desktopSuite(): List<KnownApp> = listOf(
        "PhotoCraft", "VectorCraft", "DesignCraft", "EffectCraft", "WordCraft", "DeckCraft",
        "GridCraft", "CADCraft", "SoundCraft", "FigCraft", "TextureCraft", "MaterialCraft",
    ).map { n ->
        val id = n.lowercase()
        KnownApp(n, "Craft App Suite", listOf(Repo("storytold", id)), "https://github.com/storytold/$id")
    }

    /** Desktop launcher keeps every suite icon at assets/app-icons/<id>.png. */
    private fun suiteIcon(id: String) =
        "https://raw.githubusercontent.com/storytold/craft-launcher/main/assets/app-icons/$id.png"

    fun iconCandidates(repos: List<Repo>, suiteId: String? = null): List<String> =
        (repos.flatMap { r ->
            val base = r.repo.removeSuffix("-android")
            listOf(
                "https://raw.githubusercontent.com/${r.owner}/${r.repo}/main/assets/app-icon/$base-1024.png",
                "https://raw.githubusercontent.com/${r.owner}/${r.repo}/main/assets/app-icon/$base.png",
            )
        } + listOfNotNull(suiteId?.let(::suiteIcon)) +
            repos.map { "https://github.com/${it.owner}.png?size=256" }).distinct()

    fun prettyName(repo: String): String =
        repo.removeSuffix("-android").split('-', '_')
            .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}

/** Pick the APK that matches this device. Releases ship a plain (arm64) APK and an -armeabi-v7a one. */
fun ReleaseInfo.bestApk(abis: Array<String> = Build.SUPPORTED_ABIS): ApkAsset? {
    if (apks.isEmpty()) return null
    fun has(a: ApkAsset, s: String) = a.name.lowercase().contains(s)
    val tags = listOf("armeabi", "armv7", "arm64", "aarch64", "x86_64", "x86")
    val plain = apks.firstOrNull { a -> tags.none { has(a, it) } }
    for (abi in abis) {
        val hit = when (abi) {
            "arm64-v8a" -> apks.firstOrNull { has(it, "arm64") || has(it, "aarch64") } ?: plain
            "armeabi-v7a", "armeabi" -> apks.firstOrNull { has(it, "armeabi") || has(it, "armv7") }
            "x86_64" -> apks.firstOrNull { has(it, "x86_64") }
            "x86" -> apks.firstOrNull { has(it, "x86") && !has(it, "x86_64") }
            else -> null
        }
        if (hit != null) return hit
    }
    return plain ?: apks.first()
}

// ---- tiny JSON cache (org.json, no reflection) ----
fun CatalogApp.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("name", name); put("tagline", tagline)
    put("owner", owner); put("repo", repo)
    put("icons", JSONArray(iconUrls))
    desktopUrl?.let { put("desktop", it) }
    release?.let { r ->
        put("release", JSONObject().apply {
            put("tag", r.tag); put("title", r.title); put("at", r.publishedAt)
            put("pre", r.prerelease); put("page", r.pageUrl); put("notes", r.notes)
            put("apks", JSONArray().also { arr ->
                r.apks.forEach { a ->
                    arr.put(JSONObject().apply {
                        put("n", a.name); put("u", a.url); put("s", a.size)
                        a.sha256?.let { put("h", it) }
                    })
                }
            })
        })
    }
}

fun catalogAppFromJson(o: JSONObject): CatalogApp {
    val icons = o.getJSONArray("icons").let { a -> List(a.length()) { a.getString(it) } }
    val release = o.optJSONObject("release")?.let { r ->
        val a = r.getJSONArray("apks")
        ReleaseInfo(
            tag = r.getString("tag"), title = r.optString("title"), publishedAt = r.optString("at"),
            prerelease = r.optBoolean("pre"), pageUrl = r.optString("page"), notes = r.optString("notes"),
            apks = List(a.length()) {
                val x = a.getJSONObject(it)
                ApkAsset(x.getString("n"), x.getString("u"), x.optLong("s"),
                    if (x.has("h")) x.getString("h") else null)
            },
        )
    }
    return CatalogApp(
        o.getString("id"), o.getString("name"), o.optString("tagline"),
        o.getString("owner"), o.getString("repo"), icons, release,
        if (o.has("desktop")) o.getString("desktop") else null,
    )
}
