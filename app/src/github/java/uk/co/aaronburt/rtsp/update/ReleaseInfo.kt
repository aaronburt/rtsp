package uk.co.aaronburt.rtsp.update

import org.json.JSONException
import org.json.JSONObject

data class ReleaseInfo(
    val tag: String,
    val name: String,
    val notes: String,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long,
    val pageUrl: String,
)

object ReleaseParser {
    fun parse(json: String): ReleaseInfo? {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            return null
        }
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        val tag = root.optString("tag_name").takeIf { Version.parse(it) != null } ?: return null
        val assets = root.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (name.endsWith(".apk", ignoreCase = true) && url.startsWith("https://")) {
                return ReleaseInfo(
                    tag = tag,
                    name = root.optString("name").ifBlank { tag },
                    notes = ReleaseNotes.plain(root.optString("body")),
                    apkName = name,
                    apkUrl = url,
                    apkSize = asset.optLong("size", -1L),
                    pageUrl = root.optString("html_url"),
                )
            }
        }
        return null
    }
}

object ReleaseNotes {
    fun plain(markdown: String): String =
        markdown.lineSequence()
            .map { it.trim() }
            .filterNot { it.startsWith("**Full Changelog**", ignoreCase = true) }
            .map { line ->
                when {
                    line.startsWith("#") -> line.trimStart('#').trim()
                    line.startsWith("* ") || line.startsWith("- ") -> "• " + line.drop(2).trim()
                    else -> line
                }
            }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
}
