package uk.co.aaronburt.rtsp.player

import androidx.media3.common.Format
import java.nio.ByteBuffer

interface VideoSampleTap {
    fun onFormat(format: Format)

    /** [data] is only valid for the duration of the call; copy it if it is needed later. */
    fun onSample(data: ByteBuffer, timeUs: Long, keyFrame: Boolean)

    fun onStopped()
}
