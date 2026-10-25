package uk.co.aaronburt.rtsp.snapshot

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import java.lang.ref.WeakReference

private const val MAX_CAPTURE_WIDTH = 640

class SurfaceCapture {
    private var view: WeakReference<SurfaceView>? = null

    fun attach(surfaceView: SurfaceView) {
        view = WeakReference(surfaceView)
    }

    fun request(onResult: (Bitmap?) -> Unit) {
        val surfaceView = view?.get()
        if (surfaceView == null || surfaceView.width <= 0 || surfaceView.height <= 0 || !surfaceView.holder.surface.isValid) {
            onResult(null)
            return
        }
        val width = minOf(surfaceView.width, MAX_CAPTURE_WIDTH)
        val height = maxOf(1, surfaceView.height * width / surfaceView.width)
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
}
