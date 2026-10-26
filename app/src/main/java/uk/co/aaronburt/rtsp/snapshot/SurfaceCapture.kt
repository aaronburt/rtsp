package uk.co.aaronburt.rtsp.snapshot

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import java.lang.ref.WeakReference
import kotlinx.coroutines.suspendCancellableCoroutine
import uk.co.aaronburt.rtsp.player.FrameSize
import kotlin.math.roundToInt

private const val MAX_CAPTURE_WIDTH = 640

class SurfaceCapture {
    private var view: WeakReference<SurfaceView>? = null

    fun attach(surfaceView: SurfaceView) {
        view = WeakReference(surfaceView)
    }

    fun request(maxDimension: Int = MAX_CAPTURE_WIDTH, sourceSize: FrameSize? = null, onResult: (Bitmap?) -> Unit) {
        val surfaceView = view?.get()
        if (surfaceView == null || surfaceView.width <= 0 || surfaceView.height <= 0 || !surfaceView.holder.surface.isValid) {
            onResult(null)
            return
        }
        val sourceWidth = sourceSize?.takeIf { it.width > 0 && it.height > 0 }
            ?.let { it.width * it.pixelRatio.toDouble() }?.takeIf { it.isFinite() && it > 0 }
            ?: surfaceView.width.toDouble()
        val sourceHeight = sourceSize?.height?.takeIf { it > 0 }?.toDouble() ?: surfaceView.height.toDouble()
        val limit = maxDimension.coerceIn(1, 1920)
        val scale = minOf(1.0, limit / sourceWidth, limit / sourceHeight)
        val width = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(surfaceView, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) {
                    onResult(bitmap)
                } else {
                    bitmap.recycle()
                    onResult(null)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (error: IllegalArgumentException) {
            bitmap.recycle()
            onResult(null)
        }
    }

    suspend fun captureFrame(size: FrameSize?, maxDimension: Int = 1920): Bitmap? = suspendCancellableCoroutine { continuation ->
        request(maxDimension = maxDimension, sourceSize = size) { bitmap ->
            continuation.resume(bitmap) { _, value, _ -> value?.recycle() }
        }
    }
}
