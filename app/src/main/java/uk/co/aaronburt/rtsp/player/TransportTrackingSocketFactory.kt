package uk.co.aaronburt.rtsp.player

import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

/** Media3 exposes its RTSP socket factory, but no negotiated-transport callback. */
internal class TransportTrackingSocketFactory(
    private val onTransport: (RtspTransport) -> Unit,
) : SocketFactory() {
    override fun createSocket(): Socket = TrackingSocket(onTransport)

    override fun createSocket(host: String, port: Int): Socket = connected(InetSocketAddress(host, port))

    override fun createSocket(host: InetAddress, port: Int): Socket = connected(InetSocketAddress(host, port))

    override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int): Socket =
        connected(InetSocketAddress(host, port), InetSocketAddress(local, localPort))

    override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int): Socket =
        connected(InetSocketAddress(host, port), InetSocketAddress(local, localPort))

    private fun connected(remote: InetSocketAddress, local: InetSocketAddress? = null): Socket {
        val socket = createSocket()
        try {
            if (local != null) socket.bind(local)
            socket.connect(remote, 8_000)
            return socket
        } catch (error: Exception) {
            socket.close()
            throw error
        }
    }
}

private class TrackingSocket(onTransport: (RtspTransport) -> Unit) : Socket() {
    private val observer = RtspResponseObserver(onTransport)
    private var observedInput: InputStream? = null

    override fun getInputStream(): InputStream {
        observedInput?.let { return it }
        val input = super.getInputStream()
        return object : InputStream() {
            override fun read(): Int = input.read().also(observer::accept)

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                input.read(buffer, offset, length).also { count ->
                    if (count > 0) observer.accept(buffer, offset, count)
                }

            override fun available(): Int = input.available()
            override fun close() = input.close()
        }.also { observedInput = it }
    }
}
