package uk.co.aaronburt.rtsp.player

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class RtspResponseObserverTest {
    @Test
    fun reportsUdpAndThenTcpFallbackAcrossFragmentedMessages() {
        val actual = mutableListOf<RtspTransport>()
        val observer = RtspResponseObserver(actual::add)
        val messages = "RTSP/1.0 200 OK\r\nTransport: RTP/AVP;unicast;server_port=8000-8001\r\n\r\n" +
            "RTSP/1.0 200 OK\r\ntransport: RTP/AVP/TCP;interleaved=0-1\r\n\r\n"
        messages.toByteArray().forEach { observer.accept(it.toInt() and 0xff) }
        assertEquals(listOf(RtspTransport.UDP, RtspTransport.TCP), actual)
    }

    @Test
    fun skipsSdpAndInterleavedBinaryDataContainingFakeTransportHeaders() {
        val actual = mutableListOf<RtspTransport>()
        val observer = RtspResponseObserver(actual::add)
        val fake = "RTSP/1.0 200 OK\r\nTransport: RTP/AVP/UDP\r\n\r\n".toByteArray()
        val bytes = ByteArrayOutputStream().apply {
            write("RTSP/1.0 200 OK\r\nContent-Length: ${fake.size}\r\n\r\n".toByteArray())
            write(fake)
            write(byteArrayOf('$'.code.toByte(), 0, 0, fake.size.toByte()))
            write(fake)
            write("RTSP/1.0 461 Unsupported Transport\r\nTransport: RTP/AVP/UDP\r\n\r\n".toByteArray())
            write("RTSP/1.0 200 OK\r\nTransport: RTP/AVP/TCP;interleaved=0-1\r\n\r\n".toByteArray())
        }.toByteArray()
        // Chunks cut through both the headers and binary frame boundaries.
        bytes.asList().chunked(7).forEach { chunk ->
            val data = chunk.toByteArray()
            observer.accept(data, 0, data.size)
        }
        assertEquals(listOf(RtspTransport.TCP), actual)
    }

    @Test
    fun malformedUnboundedHeaderDisablesOnlyObservation() {
        val actual = mutableListOf<RtspTransport>()
        val observer = RtspResponseObserver(actual::add)
        val bytes = "a".repeat(40_000).toByteArray()
        observer.accept(bytes, 0, bytes.size)
        assertEquals(emptyList<RtspTransport>(), actual)
    }
}
