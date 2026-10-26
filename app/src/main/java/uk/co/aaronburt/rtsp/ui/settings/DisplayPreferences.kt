package uk.co.aaronburt.rtsp.ui.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface DisplayPreferences {
    val showClock: StateFlow<Boolean>
    val pinchToZoom: StateFlow<Boolean>
    val multiviewMainStream: StateFlow<Boolean>

    fun setShowClock(show: Boolean)

    fun setPinchToZoom(enabled: Boolean)

    fun setMultiviewMainStream(enabled: Boolean)
}

class InMemoryDisplayPreferences(
    initialShowClock: Boolean = false,
    initialPinchToZoom: Boolean = false,
    initialMultiviewMainStream: Boolean = false,
) : DisplayPreferences {
    private val mutableShowClock = MutableStateFlow(initialShowClock)
    private val mutablePinchToZoom = MutableStateFlow(initialPinchToZoom)
    private val mutableMultiviewMainStream = MutableStateFlow(initialMultiviewMainStream)

    override val showClock: StateFlow<Boolean> = mutableShowClock.asStateFlow()
    override val pinchToZoom: StateFlow<Boolean> = mutablePinchToZoom.asStateFlow()
    override val multiviewMainStream: StateFlow<Boolean> = mutableMultiviewMainStream.asStateFlow()

    override fun setMultiviewMainStream(enabled: Boolean) {
        mutableMultiviewMainStream.value = enabled
    }

    override fun setPinchToZoom(enabled: Boolean) {
        mutablePinchToZoom.value = enabled
    }

    override fun setShowClock(show: Boolean) {
        mutableShowClock.value = show
    }
}

class SharedDisplayPreferences(private val prefs: SharedPreferences) : DisplayPreferences {
    private val mutableShowClock = MutableStateFlow(prefs.getBoolean(KEY_SHOW_CLOCK, false))

    private val mutablePinchToZoom = MutableStateFlow(prefs.getBoolean(KEY_PINCH_TO_ZOOM, false))
    private val mutableMultiviewMainStream = MutableStateFlow(prefs.getBoolean(KEY_MULTIVIEW_MAIN_STREAM, false))

    override val showClock: StateFlow<Boolean> = mutableShowClock.asStateFlow()
    override val pinchToZoom: StateFlow<Boolean> = mutablePinchToZoom.asStateFlow()
    override val multiviewMainStream: StateFlow<Boolean> = mutableMultiviewMainStream.asStateFlow()

    override fun setMultiviewMainStream(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MULTIVIEW_MAIN_STREAM, enabled).apply()
        mutableMultiviewMainStream.value = enabled
    }

    override fun setPinchToZoom(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PINCH_TO_ZOOM, enabled).apply()
        mutablePinchToZoom.value = enabled
    }

    override fun setShowClock(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_CLOCK, show).apply()
        mutableShowClock.value = show
    }

    companion object {
        private const val PREFS = "display"
        private const val KEY_SHOW_CLOCK = "show_clock"
        private const val KEY_PINCH_TO_ZOOM = "pinch_to_zoom"
        private const val KEY_MULTIVIEW_MAIN_STREAM = "multiview_main_stream"

        fun create(context: Context) =
            SharedDisplayPreferences(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    }
}
