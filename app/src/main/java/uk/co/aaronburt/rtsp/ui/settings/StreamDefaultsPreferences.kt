package uk.co.aaronburt.rtsp.ui.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.aaronburt.rtsp.data.Transport

data class StreamDefaults(
    val transport: Transport = Transport.Tcp,
    val latencyMs: Int = 200,
    val startMuted: Boolean = true,
)

interface StreamDefaultsPreferences {
    val defaults: StateFlow<StreamDefaults>
    fun setTransport(transport: Transport)
    fun setLatency(latencyMs: Int)
    fun setStartMuted(muted: Boolean)
}

class InMemoryStreamDefaultsPreferences(initial: StreamDefaults = StreamDefaults()) : StreamDefaultsPreferences {
    private val mutable = MutableStateFlow(initial)
    override val defaults = mutable.asStateFlow()
    override fun setTransport(transport: Transport) { mutable.value = mutable.value.copy(transport = transport) }
    override fun setLatency(latencyMs: Int) { mutable.value = mutable.value.copy(latencyMs = latencyMs.coerceIn(0, 2000)) }
    override fun setStartMuted(muted: Boolean) { mutable.value = mutable.value.copy(startMuted = muted) }
}

class SharedStreamDefaultsPreferences(private val prefs: SharedPreferences) : StreamDefaultsPreferences {
    private val mutable = MutableStateFlow(StreamDefaults(
        transport = Transport.entries.firstOrNull { it.name == prefs.getString("transport", null) } ?: Transport.Tcp,
        latencyMs = prefs.getInt("latency_ms", 200).coerceIn(0, 2000),
        startMuted = prefs.getBoolean("start_muted", true),
    ))
    override val defaults = mutable.asStateFlow()

    override fun setTransport(transport: Transport) = save(mutable.value.copy(transport = transport))
    override fun setLatency(latencyMs: Int) = save(mutable.value.copy(latencyMs = latencyMs.coerceIn(0, 2000)))
    override fun setStartMuted(muted: Boolean) = save(mutable.value.copy(startMuted = muted))

    private fun save(value: StreamDefaults) {
        prefs.edit().putString("transport", value.transport.name).putInt("latency_ms", value.latencyMs)
            .putBoolean("start_muted", value.startMuted).apply()
        mutable.value = value
    }

    companion object {
        fun create(context: Context) = SharedStreamDefaultsPreferences(
            context.getSharedPreferences("stream_defaults", Context.MODE_PRIVATE),
        )
    }
}
