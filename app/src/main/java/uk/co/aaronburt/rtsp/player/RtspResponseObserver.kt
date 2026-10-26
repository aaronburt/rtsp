package uk.co.aaronburt.rtsp.player

/** Observes SETUP response headers without reading SDP bodies or interleaved RTP as text. */
internal class RtspResponseObserver(private val onTransport: (RtspTransport) -> Unit) {
    private val header = StringBuilder()
    private var bodyRemaining = 0L
    private var binaryHeaderBytes = 0
    private var binaryLength = 0
    private var disabled = false

    fun accept(data: ByteArray, offset: Int, length: Int) {
        var index = offset
        val end = offset + length
        while (index < end && !disabled) {
            if (bodyRemaining > 0) {
                val skip = minOf(bodyRemaining, (end - index).toLong()).toInt()
                bodyRemaining -= skip
                index += skip
                continue
            }
            accept(data[index++].toInt() and 0xff)
        }
    }

    fun accept(value: Int) {
        if (disabled || value < 0) return
        if (bodyRemaining > 0) {
            bodyRemaining--
            return
        }
        if (binaryHeaderBytes > 0) {
            if (binaryHeaderBytes < 3) binaryLength = (binaryLength shl 8) or value
            binaryHeaderBytes--
            if (binaryHeaderBytes == 0) bodyRemaining = binaryLength.toLong()
            return
        }
        if (header.isEmpty() && value == '$'.code) {
            binaryHeaderBytes = 3 // Channel, length high byte, length low byte.
            binaryLength = 0
            return
        }
        header.append(value.toChar())
        if (header.length > 32_768) {
            disabled = true // Malformed diagnostics must never interrupt playback or grow unbounded.
            header.clear()
            return
        }
        if (!header.endsWith("\r\n\r\n")) return
        val lines = header.toString().split("\r\n")
        header.clear()
        val contentLength = lines.firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toLongOrNull()
        bodyRemaining = contentLength?.coerceAtLeast(0) ?: 0
        if (!lines.first().startsWith("RTSP/1.0 2")) return
        val transport = lines.firstOrNull { it.startsWith("Transport:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.substringBefore(';') ?: return
        when {
            transport.equals("RTP/AVP/TCP", ignoreCase = true) -> onTransport(RtspTransport.TCP)
            transport.equals("RTP/AVP", ignoreCase = true) ||
                transport.equals("RTP/AVP/UDP", ignoreCase = true) -> onTransport(RtspTransport.UDP)
        }
    }
}
