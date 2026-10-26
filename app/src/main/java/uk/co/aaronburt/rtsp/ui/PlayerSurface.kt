package uk.co.aaronburt.rtsp.ui

import android.content.Context
import android.graphics.Rect
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.aaronburt.rtsp.player.LiveStreamController
import uk.co.aaronburt.rtsp.snapshot.SurfaceCapture
import uk.co.aaronburt.rtsp.ui.live.LocalVideoZoom
import uk.co.aaronburt.rtsp.ui.live.VideoZoomState
import uk.co.aaronburt.rtsp.ui.live.VideoZoomTransform
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun PlayerSurface(
    controller: LiveStreamController,
    modifier: Modifier = Modifier,
    capture: SurfaceCapture? = null,
    keepScreenOn: Boolean = true,
) {
    val frameSize by controller.frameSize.collectAsStateWithLifecycle()
    val aspect = frameSize?.aspectRatio?.takeIf { it > 0f }
    val zoom = LocalVideoZoom.current
    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (zoom == null) {
            AndroidView(
                modifier = if (aspect != null) Modifier.aspectRatio(aspect) else Modifier.fillMaxSize(),
                factory = { context -> createVideoSurface(context, controller, capture, keepScreenOn) },
            )
        } else {
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { context -> VideoSurfaceView(context, controller, capture, keepScreenOn) },
                update = { it.update(aspect, zoom) },
            )
        }
    }
}

// A native viewport keeps the SurfaceView's separate video layer clipped while it is scaled.
private class VideoSurfaceView(
    context: Context,
    controller: LiveStreamController,
    capture: SurfaceCapture?,
    keepScreenOn: Boolean,
) : FrameLayout(context) {
    private val video = createVideoSurface(context, controller, capture, keepScreenOn)
    private var aspect: Float? = null
    private var zoom: VideoZoomState? = null

    init {
        clipChildren = true
        addView(video, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun update(aspect: Float?, zoom: VideoZoomState?) {
        val needsLayout = this.aspect != aspect || this.zoom !== zoom
        this.aspect = aspect
        this.zoom = zoom
        // Read the transform in AndroidView's observed update block as gestures change it.
        applyZoom(zoom?.transform ?: VideoZoomTransform())
        if (needsLayout) requestLayout()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (width <= 0 || height <= 0) return
        val ratio = aspect ?: (width.toFloat() / height)
        val videoWidth = minOf(width, (height * ratio).roundToInt()).coerceAtLeast(1)
        val videoHeight = minOf(height, (width / ratio).roundToInt()).coerceAtLeast(1)
        video.measure(
            MeasureSpec.makeMeasureSpec(videoWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(videoHeight, MeasureSpec.EXACTLY),
        )
        val x = (width - videoWidth) / 2
        val y = (height - videoHeight) / 2
        video.layout(x, y, x + videoWidth, y + videoHeight)
        zoom?.resize(width, height, videoWidth, videoHeight)
        applyZoom(zoom?.transform ?: VideoZoomTransform())
    }

    private fun applyZoom(transform: VideoZoomTransform) {
        video.pivotX = video.width / 2f
        video.pivotY = video.height / 2f
        video.scaleX = transform.scale
        video.scaleY = transform.scale
        video.translationX = transform.x
        video.translationY = transform.y
        val imageLeft = video.left + video.width * (1f - transform.scale) / 2f + transform.x
        val imageTop = video.top + video.height * (1f - transform.scale) / 2f + transform.y
        video.clipBounds = Rect(
            floor(-imageLeft / transform.scale).toInt().coerceIn(0, video.width),
            floor(-imageTop / transform.scale).toInt().coerceIn(0, video.height),
            ceil((width - imageLeft) / transform.scale).toInt().coerceIn(0, video.width),
            ceil((height - imageTop) / transform.scale).toInt().coerceIn(0, video.height),
        )
    }
}

private fun createVideoSurface(context: Context, controller: LiveStreamController, capture: SurfaceCapture?, screenOn: Boolean) =
    SurfaceView(context).apply {
        keepScreenOn = screenOn
        capture?.attach(this)
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                controller.setSurface(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                controller.clearSurface(holder.surface)
            }
        })
    }
