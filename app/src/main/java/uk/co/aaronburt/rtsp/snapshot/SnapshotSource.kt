package uk.co.aaronburt.rtsp.snapshot

import android.graphics.Bitmap

fun interface SnapshotSource {
    /** Returns a fresh video frame owned by the caller, or null if live capture is unavailable. */
    suspend fun capture(): Bitmap?
}
