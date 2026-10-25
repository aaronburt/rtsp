package uk.co.aaronburt.rtsp.ui.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface DisplayPreferences {
    val showClock: StateFlow<Boolean>
    val allowRecording: StateFlow<Boolean>

    fun setShowClock(show: Boolean)

    fun setAllowRecording(allow: Boolean)
}

class InMemoryDisplayPreferences(
    initialShowClock: Boolean = false,
    initialAllowRecording: Boolean = false,
) : DisplayPreferences {
    private val mutableShowClock = MutableStateFlow(initialShowClock)
    private val mutableAllowRecording = MutableStateFlow(initialAllowRecording)

    override val showClock: StateFlow<Boolean> = mutableShowClock.asStateFlow()
    override val allowRecording: StateFlow<Boolean> = mutableAllowRecording.asStateFlow()

    override fun setAllowRecording(allow: Boolean) {
        mutableAllowRecording.value = allow
    }

    override fun setShowClock(show: Boolean) {
        mutableShowClock.value = show
    }
}

class SharedDisplayPreferences(private val prefs: SharedPreferences) : DisplayPreferences {
    private val mutableShowClock = MutableStateFlow(prefs.getBoolean(KEY_SHOW_CLOCK, false))

    private val mutableAllowRecording = MutableStateFlow(prefs.getBoolean(KEY_ALLOW_RECORDING, false))

    override val showClock: StateFlow<Boolean> = mutableShowClock.asStateFlow()
    override val allowRecording: StateFlow<Boolean> = mutableAllowRecording.asStateFlow()

    override fun setAllowRecording(allow: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_RECORDING, allow).apply()
        mutableAllowRecording.value = allow
    }

    override fun setShowClock(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_CLOCK, show).apply()
        mutableShowClock.value = show
    }

    companion object {
        private const val PREFS = "display"
        private const val KEY_SHOW_CLOCK = "show_clock"
        private const val KEY_ALLOW_RECORDING = "allow_recording"

        fun create(context: Context) =
            SharedDisplayPreferences(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    }
}
