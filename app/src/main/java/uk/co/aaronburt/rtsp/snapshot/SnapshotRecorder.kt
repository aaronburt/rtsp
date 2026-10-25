package uk.co.aaronburt.rtsp.snapshot

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val JPEG_QUALITY = 70
private const val SAMPLE_WIDTH = 16
private const val SAMPLE_HEIGHT = 9

fun interface SnapshotRecorder {
    fun record(cameraId: String, capture: SurfaceCapture)
}

object NoSnapshotRecorder : SnapshotRecorder {
    override fun record(cameraId: String, capture: SurfaceCapture) = Unit
}

class StoreSnapshotRecorder(
    private val store: SnapshotStore,
    private val scope: CoroutineScope,
) : SnapshotRecorder {
    override fun record(cameraId: String, capture: SurfaceCapture) {
        capture.request { bitmap ->
            if (bitmap == null) return@request
            scope.launch {
                try {
                    if (!isBlank(bitmap)) store.save(cameraId, encode(bitmap))
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun isBlank(bitmap: Bitmap): Boolean {
        val sample = Bitmap.createScaledBitmap(bitmap, SAMPLE_WIDTH, SAMPLE_HEIGHT, false)
        val pixels = IntArray(SAMPLE_WIDTH * SAMPLE_HEIGHT)
        sample.getPixels(pixels, 0, SAMPLE_WIDTH, 0, 0, SAMPLE_WIDTH, SAMPLE_HEIGHT)
        if (sample !== bitmap) sample.recycle()
        return isBlankFrame(pixels)
    }

    private fun encode(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        return output.toByteArray()
    }
}
