package uk.co.aaronburt.rtsp.ui.live

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

data class PictureInPictureState(val active: Boolean = false, val entering: Boolean = false) {
    val videoOnly: Boolean get() = active || entering
}

interface PictureInPictureController {
    val supported: Boolean
    val state: StateFlow<PictureInPictureState>
    fun enter(aspectRatio: Float, sourceBounds: Rect?): Boolean
}

object NoPictureInPicture : PictureInPictureController {
    override val supported = false
    override val state = MutableStateFlow(PictureInPictureState()).asStateFlow()
    override fun enter(aspectRatio: Float, sourceBounds: Rect?) = false
}

val LocalPictureInPicture = staticCompositionLocalOf<PictureInPictureController> { NoPictureInPicture }

class ActivityPictureInPicture(private val activity: Activity) : PictureInPictureController {
    private val mutable = MutableStateFlow(PictureInPictureState(active = activity.isInPictureInPictureMode))
    override val state = mutable.asStateFlow()
    override val supported: Boolean
        get() = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    override fun enter(aspectRatio: Float, sourceBounds: Rect?): Boolean {
        if (!supported || activity.isFinishing || state.value.videoOnly) return false
        val ratio = aspectRatio.takeIf { it.isFinite() && it > 0 }?.coerceIn(1f / 2.39f, 2.39f) ?: (16f / 9f)
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational((ratio * 10_000).roundToInt().coerceIn(4185, 23900), 10_000))
            .apply {
                if (sourceBounds != null && !sourceBounds.isEmpty) setSourceRectHint(sourceBounds)
                if (Build.VERSION.SDK_INT >= 31) {
                    setAutoEnterEnabled(false) // Entry is an explicit user action.
                    setSeamlessResizeEnabled(true)
                }
            }.build()
        mutable.value = PictureInPictureState(entering = true)
        return try {
            activity.enterPictureInPictureMode(params).also { entered ->
                if (!entered) mutable.value = PictureInPictureState()
            }
        } catch (_: RuntimeException) {
            mutable.value = PictureInPictureState()
            false
        }
    }

    fun onModeChanged(active: Boolean) {
        mutable.value = PictureInPictureState(active = active)
    }
}
