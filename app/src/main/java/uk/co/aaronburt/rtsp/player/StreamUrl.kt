package uk.co.aaronburt.rtsp.player

fun isRtspUrl(url: String): Boolean = url.startsWith("rtsp://")
