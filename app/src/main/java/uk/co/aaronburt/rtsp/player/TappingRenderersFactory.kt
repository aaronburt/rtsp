package uk.co.aaronburt.rtsp.player

import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.decoder.DecoderInputBuffer

private const val MAX_DROPPED_FRAMES_TO_NOTIFY = 50

@OptIn(UnstableApi::class)
class TappingRenderersFactory(
    context: Context,
    private val tap: VideoSampleTap?,
) : DefaultRenderersFactory(context) {

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        val builder = MediaCodecVideoRenderer.Builder(context)
            .setCodecAdapterFactory(codecAdapterFactory)
            .setMediaCodecSelector(mediaCodecSelector)
            .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
            .setEnableDecoderFallback(enableDecoderFallback)
            .setEventHandler(eventHandler)
            .setEventListener(eventListener)
            .setMaxDroppedFramesToNotify(MAX_DROPPED_FRAMES_TO_NOTIFY)
        out.add(TappingVideoRenderer(builder, tap))
    }
}

@OptIn(UnstableApi::class)
private class TappingVideoRenderer(
    builder: Builder,
    private val tap: VideoSampleTap?,
) : MediaCodecVideoRenderer(builder) {

    override fun onInputFormatChanged(formatHolder: FormatHolder): DecoderReuseEvaluation? {
        val evaluation = super.onInputFormatChanged(formatHolder)
        formatHolder.format?.let { tap?.onFormat(it) }
        return evaluation
    }

    override fun onQueueInputBuffer(buffer: DecoderInputBuffer) {
        super.onQueueInputBuffer(buffer)
        val data = buffer.data ?: return
        tap?.onSample(data.asReadOnlyBuffer(), buffer.timeUs, buffer.isKeyFrame)
    }
}
