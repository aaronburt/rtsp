package uk.co.aaronburt.rtsp.storage

import java.net.URLDecoder

data class SplitUrl(
    val url: String,
    val username: String?,
    val password: String?,
)

private const val SCHEME = "rtsp://"
private const val UNRESERVED = "-._~"
private const val HEX = "0123456789ABCDEF"

fun splitUrlCredentials(input: String): SplitUrl {
    val trimmed = input.trim()
    if (!trimmed.startsWith(SCHEME, ignoreCase = true)) return SplitUrl(trimmed, null, null)
    val rest = trimmed.substring(SCHEME.length)
    val authorityEnd = rest.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) rest.length else it }
    val authority = rest.substring(0, authorityEnd)
    val at = authority.lastIndexOf('@')
    if (at < 0) return SplitUrl(trimmed, null, null)
    val userInfo = authority.substring(0, at)
    val colon = userInfo.indexOf(':')
    val user = if (colon < 0) userInfo else userInfo.substring(0, colon)
    val password = if (colon < 0) null else userInfo.substring(colon + 1)
    val cleaned = SCHEME + authority.substring(at + 1) + rest.substring(authorityEnd)
    return SplitUrl(cleaned, decode(user), password?.let(::decode))
}

fun stripUrlCredentials(input: String): String = splitUrlCredentials(input).url

fun injectUrlCredentials(url: String, username: String, password: String): String {
    val clean = stripUrlCredentials(url)
    if (username.isEmpty() || !clean.startsWith(SCHEME, ignoreCase = true)) return clean
    val userInfo = if (password.isEmpty()) encode(username) else "${encode(username)}:${encode(password)}"
    return SCHEME + userInfo + "@" + clean.substring(SCHEME.length)
}

private fun encode(value: String): String = buildString {
    for (byte in value.toByteArray(Charsets.UTF_8)) {
        val code = byte.toInt() and 0xFF
        val c = code.toChar()
        if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in UNRESERVED) {
            append(c)
        } else {
            append('%')
            append(HEX[code shr 4])
            append(HEX[code and 0xF])
        }
    }
}

private fun decode(value: String): String =
    runCatching { URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrDefault(value)
