package uk.co.aaronburt.rtsp.player

import java.net.URLEncoder

private const val SCHEME = "rtsp://"

fun isRtspUrl(url: String): Boolean = url.startsWith(SCHEME)

fun withCredentials(url: String, username: String, password: String): String {
    if (username.isBlank() || !url.startsWith(SCHEME)) return url
    val rest = url.removePrefix(SCHEME)
    val authorityEnd = rest.indexOf('/').let { if (it < 0) rest.length else it }
    if (rest.substring(0, authorityEnd).contains('@')) return url
    val userInfo = if (password.isEmpty()) encode(username) else encode(username) + ":" + encode(password)
    return SCHEME + userInfo + "@" + rest
}

private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
