package uk.co.aaronburt.rtsp.recording

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Mp4Muxer
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

interface RecordingWriter : Closeable {
    fun write(data: ByteArray, timeUs: Long, keyFrame: Boolean)
}

fun interface RecordingWriterFactory {
    fun open(file: File, format: Format): RecordingWriter
}

@OptIn(UnstableApi::class)
class Mp4RecordingWriter(file: File, format: Format) : RecordingWriter {
    private val stream = FileOutputStream(file)
    private val muxer: Mp4Muxer
    private val track: Int
    private var originUs = Long.MIN_VALUE
    private var lastUs = -1L

    init {
        try {
            muxer = Mp4Muxer.Builder(stream).build()
            track = muxer.addTrack(format)
        } catch (e: Exception) {
            stream.close()
            throw e
        }
    }

    override fun write(data: ByteArray, timeUs: Long, keyFrame: Boolean) {
        if (originUs == Long.MIN_VALUE) originUs = timeUs
        val pts = maxOf(timeUs - originUs, lastUs + 1)
        lastUs = pts
        val info = BufferInfo(pts, data.size, if (keyFrame) C.BUFFER_FLAG_KEY_FRAME else 0)
        muxer.writeSampleData(track, ByteBuffer.wrap(data), info)
    }

    override fun close() {
        try {
            muxer.close()
        } finally {
            stream.close()
        }
    }

    companion object {
        val Factory = RecordingWriterFactory { file, format -> Mp4RecordingWriter(file, format) }
    }
}
