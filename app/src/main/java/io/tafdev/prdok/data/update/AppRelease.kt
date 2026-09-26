package io.tafdev.prdok.data.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A version as the release workflow names it: the git tag without its "v".
 *
 * [code] follows the same rule as versionCodeOf in app/build.gradle.kts (1.2.3 → 10203), so a
 * published release can be compared with the installed app's BuildConfig.VERSION_CODE.
 */
data class AppVersion(val name: String, val code: Int) {
    companion object {
        private val TAG = Regex("""v?(\d+)\.(\d{1,2})\.(\d{1,2})""")

        /** Null for tags that aren't release versions; the update check ignores those. */
        fun fromTag(tag: String): AppVersion? {
            val match = TAG.matchEntire(tag.trim()) ?: return null
            val (major, minor, patch) = match.destructured
            return AppVersion(
                name = "$major.$minor.$patch",
                code = major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt(),
            )
        }
    }
}

/**
 * One published GitHub Release. [apkUrl] is the attached APK, or null if the release has none;
 * [downloadUrl] then falls back to the release page, where the user can still find it.
 */
data class AppRelease(
    val version: AppVersion,
    val pageUrl: String,
    val apkUrl: String?,
) {
    val downloadUrl: String get() = apkUrl ?: pageUrl
}

object ReleaseParser {

    /**
     * Reads GitHub's `releases/latest` response. Null when the tag isn't a release version.
     * Throws IllegalArgumentException (which includes SerializationException) on a body that
     * isn't a JSON object.
     */
    fun parse(body: String): AppRelease? {
        val release = Json.parseToJsonElement(body).jsonObject
        val version = release.text("tag_name")?.let(AppVersion::fromTag) ?: return null
        val pageUrl = release.text("html_url") ?: return null
        val apkUrl = release["assets"]?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it.text("name")?.endsWith(".apk") == true }
            ?.text("browser_download_url")
        return AppRelease(version, pageUrl, apkUrl)
    }

    private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}
