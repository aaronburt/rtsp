package uk.co.aaronburt.rtsp.snapshot

private const val BLANK_LUMA_THRESHOLD = 12

fun isBlankFrame(pixels: IntArray): Boolean {
    if (pixels.isEmpty()) return true
    return pixels.none { argb ->
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        (red * 299 + green * 587 + blue * 114) / 1000 > BLANK_LUMA_THRESHOLD
    }
}
