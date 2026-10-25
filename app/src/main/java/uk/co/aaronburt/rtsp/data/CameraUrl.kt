package uk.co.aaronburt.rtsp.data

import java.net.URLDecoder

data class UrlParts(
    val url: String,
    val username: String?,
    val password: String?,
)

private const val SCHEME = "rtsp://"

fun splitCredentials(input: String): UrlParts {
    val trimmed = input.trim()
    if (!trimmed.startsWith(SCHEME)) return UrlParts(trimmed, null, null)
    val rest = trimmed.removePrefix(SCHEME)
    val authorityEnd = rest.indexOf('/').let { if (it < 0) rest.length else it }
    val authority = rest.substring(0, authorityEnd)
    val at = authority.lastIndexOf('@')
    if (at < 0) return UrlParts(trimmed, null, null)
    val userInfo = authority.substring(0, at)
    val host = authority.substring(at + 1)
    val colon = userInfo.indexOf(':')
    val user = if (colon < 0) userInfo else userInfo.substring(0, colon)
    val password = if (colon < 0) null else userInfo.substring(colon + 1)
    val cleaned = SCHEME + host + rest.substring(authorityEnd)
    return UrlParts(cleaned, decode(user), password?.let(::decode))
}

fun displayUrl(camera: Camera): String = splitCredentials(camera.url).url

private fun decode(value: String): String =
    runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
