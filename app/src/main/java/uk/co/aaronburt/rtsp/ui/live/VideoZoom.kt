package uk.co.aaronburt.rtsp.ui.live

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uk.co.aaronburt.rtsp.R
import uk.co.aaronburt.rtsp.ui.theme.Scrim

internal data class VideoZoomTransform(
    val scale: Float = 1f,
    val x: Float = 0f,
    val y: Float = 0f,
) {
    fun constrained(viewWidth: Float, viewHeight: Float, videoWidth: Float, videoHeight: Float): VideoZoomTransform {
        val boundedScale = scale.coerceIn(1f, 4f)
        if (boundedScale == 1f) return VideoZoomTransform()
        val maxX = ((videoWidth * boundedScale - viewWidth) / 2f).coerceAtLeast(0f)
        val maxY = ((videoHeight * boundedScale - viewHeight) / 2f).coerceAtLeast(0f)
        return VideoZoomTransform(
            boundedScale,
            if (maxX == 0f) 0f else x.coerceIn(-maxX, maxX),
            if (maxY == 0f) 0f else y.coerceIn(-maxY, maxY),
        )
    }

    // Focal coordinates are relative to the centre of the viewport.
    fun changed(factor: Float, panX: Float, panY: Float, focalX: Float, focalY: Float): VideoZoomTransform {
        val newScale = (scale * factor).coerceIn(1f, 4f)
        val ratio = newScale / scale
        return VideoZoomTransform(
            scale = newScale,
            x = x * ratio + focalX * (1f - ratio) + panX,
            y = y * ratio + focalY * (1f - ratio) + panY,
        )
    }
}

internal class VideoZoomState(initial: VideoZoomTransform = VideoZoomTransform()) {
    var transform by mutableStateOf(initial)
        private set
    private var viewWidth = 0f
    private var viewHeight = 0f
    private var videoWidth = 0f
    private var videoHeight = 0f

    fun resize(width: Int, height: Int, contentWidth: Int, contentHeight: Int) {
        viewWidth = width.toFloat()
        viewHeight = height.toFloat()
        videoWidth = contentWidth.toFloat()
        videoHeight = contentHeight.toFloat()
        transform = transform.constrained(viewWidth, viewHeight, videoWidth, videoHeight)
    }

    fun change(factor: Float, panX: Float, panY: Float, focalX: Float, focalY: Float) {
        if (viewWidth <= 0f || viewHeight <= 0f) return
        transform = transform.changed(factor, panX, panY, focalX - viewWidth / 2f, focalY - viewHeight / 2f)
            .constrained(viewWidth, viewHeight, videoWidth, videoHeight)
    }

    fun reset() {
        transform = VideoZoomTransform()
    }

    companion object {
        val StateSaver = Saver<VideoZoomState, FloatArray>(
            save = { floatArrayOf(it.transform.scale, it.transform.x, it.transform.y) },
            restore = { VideoZoomState(VideoZoomTransform(it[0], it[1], it[2])) },
        )
    }
}

internal val LocalVideoZoom = staticCompositionLocalOf<VideoZoomState?> { null }

@Composable
internal fun ZoomableVideo(enabled: Boolean, content: @Composable () -> Unit) {
    val zoom = rememberSaveable(enabled, saver = VideoZoomState.StateSaver) { VideoZoomState() }
    Box(
        modifier = Modifier.fillMaxSize().clipToBounds().then(
            if (enabled) {
                Modifier.pointerInput(zoom) {
                    detectTransformGestures { centroid, pan, factor, _ ->
                        zoom.change(factor, pan.x, pan.y, centroid.x, centroid.y)
                    }
                }
            } else {
                Modifier
            },
        ),
    ) {
        CompositionLocalProvider(LocalVideoZoom provides if (enabled) zoom else null) {
            content()
        }
        if (enabled && zoom.transform.scale > 1f) {
            Surface(
                onClick = zoom::reset,
                color = Scrim,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            ) {
                Text(stringResource(R.string.reset_zoom), modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
            }
        }
    }
}
