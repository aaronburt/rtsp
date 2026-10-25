package uk.co.aaronburt.rtsp.ui.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface DisplayPreferences {
    val showClock: StateFlow<Boolean>

    fun setShowClock(show: Boolean)
}

class InMemoryDisplayPreferences(initialShowClock: Boolean = false) : DisplayPreferences {
    private val mutableShowClock = MutableStateFlow(initialShowClock)

    override val showClock: StateFlow<Boolean> = mutableShowClock.asStateFlow()

    override fun setShowClock(show: Boolean) {
        mutableShowClock.value = show
    }
}

class SharedDisplayPreferences(private val prefs: SharedPreferences) : DisplayPreferences {
    private val mutableShowClock = MutableStateFlow(prefs.getBoolean(KEY_SHOW_CLOCK, false))

    override val showClock: StateFlow<Boolean> = mutableShowClock.asStateFlow()

    override fun setShowClock(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_CLOCK, show).apply()
        mutableShowClock.value = show
    }

    companion object {
        private const val PREFS = "display"
        private const val KEY_SHOW_CLOCK = "show_clock"

        fun create(context: Context) =
            SharedDisplayPreferences(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    }
}
