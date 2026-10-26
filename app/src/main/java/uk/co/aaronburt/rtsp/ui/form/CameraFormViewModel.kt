package uk.co.aaronburt.rtsp.ui.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uk.co.aaronburt.rtsp.data.AppContainer
import uk.co.aaronburt.rtsp.data.Camera
import uk.co.aaronburt.rtsp.data.CameraRepository
import uk.co.aaronburt.rtsp.data.Transport
import uk.co.aaronburt.rtsp.data.splitCredentials
import uk.co.aaronburt.rtsp.player.isRtspUrl
import uk.co.aaronburt.rtsp.player.CameraConnectionTester
import uk.co.aaronburt.rtsp.player.ConnectionResult
import uk.co.aaronburt.rtsp.player.NoConnectionTester
import uk.co.aaronburt.rtsp.ui.settings.StreamDefaults

data class FormErrors(
    val nameMissing: Boolean = false,
    val urlInvalid: Boolean = false,
    val subUrlInvalid: Boolean = false,
) {
    val any: Boolean get() = nameMissing || urlInvalid || subUrlInvalid
}

private fun isValidRtsp(url: String): Boolean {
    val cleaned = splitCredentials(url).url
    return isRtspUrl(cleaned) && cleaned.removePrefix("rtsp://").isNotBlank()
}

fun validateForm(name: String, url: String, subUrl: String = ""): FormErrors = FormErrors(
    nameMissing = name.isBlank(),
    urlInvalid = !isValidRtsp(url),
    subUrlInvalid = subUrl.isNotBlank() && !isValidRtsp(subUrl),
)

data class FormState(
    val editingId: String? = null,
    val name: String = "",
    val url: String = "",
    val subUrl: String = "",
    val username: String = "",
    val password: String = "",
    val transport: Transport = Transport.Tcp,
    val latencyMs: Int = 200,
    val autoReconnect: Boolean = true,
    val startMuted: Boolean = true,
    val errors: FormErrors = FormErrors(),
    val showErrors: Boolean = false,
    val testing: Boolean = false,
    val connectionResult: ConnectionResult? = null,
) {
    val isEdit: Boolean get() = editingId != null
}

class CameraFormViewModel(
    private val editingId: String?,
    private val repository: CameraRepository,
    private val connectionTester: CameraConnectionTester = NoConnectionTester,
    defaults: StreamDefaults = StreamDefaults(),
) : ViewModel() {
    private var testJob: Job? = null
    private val existing = editingId?.let(repository::get)
    private val mutableState = MutableStateFlow(
        existing?.let {
            FormState(
                editingId = it.id,
                name = it.name,
                url = it.url,
                subUrl = it.subUrl.orEmpty(),
                username = it.username,
                password = it.password,
                transport = it.transport,
                latencyMs = it.latencyMs,
                autoReconnect = it.autoReconnect,
                startMuted = it.startMuted,
            )
        } ?: FormState(transport = defaults.transport, latencyMs = defaults.latencyMs, startMuted = defaults.startMuted),
    )

    val state: StateFlow<FormState> = mutableState

    fun setName(value: String) = change { it.copy(name = value) }

    fun setUrl(value: String) = change { it.copy(url = value) }

    fun setSubUrl(value: String) = change { it.copy(subUrl = value) }

    fun setUsername(value: String) = change { it.copy(username = value) }

    fun setPassword(value: String) = change { it.copy(password = value) }

    fun setTransport(value: Transport) = change { it.copy(transport = value) }

    fun setLatency(value: Int) = change { it.copy(latencyMs = value) }

    fun setAutoReconnect(value: Boolean) = change { it.copy(autoReconnect = value) }

    fun setStartMuted(value: Boolean) = change { it.copy(startMuted = value) }

    fun normaliseUrl() {
        val current = mutableState.value
        val normalised = applyCredentials(current)
        if (current != normalised) change { normalised }
    }

    fun testConnection() {
        testJob?.cancel()
        val current = applyCredentials(mutableState.value)
        if (!isValidRtsp(current.url)) {
            mutableState.value = current.copy(errors = current.errors.copy(urlInvalid = true), showErrors = true)
            return
        }
        mutableState.value = current.copy(testing = true, connectionResult = null)
        val camera = Camera(
            id = editingId.orEmpty(), name = current.name, url = current.url,
            username = current.username, password = current.password,
            transport = current.transport, latencyMs = current.latencyMs,
        )
        testJob = viewModelScope.launch {
            val result = try {
                connectionTester.test(camera)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ConnectionResult.Failed("Could not connect to the camera")
            }
            mutableState.update { it.copy(testing = false, connectionResult = result) }
        }
    }

    fun save(): Boolean {
        val current = applyCredentials(mutableState.value)
        val errors = validateForm(current.name, current.url, current.subUrl)
        mutableState.value = current.copy(errors = errors, showErrors = errors.any)
        if (errors.any) return false
        val base = existing ?: Camera(id = repository.newId(), name = "", url = "", lastFrameAt = null)
        repository.save(
            base.copy(
                name = current.name.trim(),
                url = current.url,
                subUrl = splitCredentials(current.subUrl).url.takeIf { it.isNotBlank() },
                username = current.username.trim(),
                password = current.password,
                transport = current.transport,
                latencyMs = current.latencyMs,
                autoReconnect = current.autoReconnect,
                startMuted = current.startMuted,
            ),
        )
        return true
    }

    fun delete() {
        editingId?.let(repository::delete)
    }

    private fun change(transform: (FormState) -> FormState) {
        testJob?.cancel()
        mutableState.update { current ->
            val next = transform(current).copy(testing = false, connectionResult = null)
            if (next.showErrors) next.copy(errors = validateForm(next.name, next.url, next.subUrl)) else next
        }
    }

    private fun applyCredentials(form: FormState): FormState {
        val parts = splitCredentials(form.url)
        return form.copy(
            url = parts.url,
            username = parts.username ?: form.username,
            password = parts.password ?: form.password,
        )
    }

    companion object {
        const val ARG_ID = "id"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val handle: SavedStateHandle = createSavedStateHandle()
                CameraFormViewModel(
                    handle.get<String>(ARG_ID), container.cameraRepository, container.connectionTester,
                    container.streamDefaults.defaults.value,
                )
            }
        }
    }
}
