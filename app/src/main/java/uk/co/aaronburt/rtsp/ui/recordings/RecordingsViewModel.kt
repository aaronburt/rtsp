package uk.co.aaronburt.rtsp.ui.recordings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.aaronburt.rtsp.data.AppContainer
import uk.co.aaronburt.rtsp.recording.Recording
import uk.co.aaronburt.rtsp.recording.RecordingStore

class RecordingsViewModel(private val store: RecordingStore) : ViewModel() {
    private val mutableRecordings = MutableStateFlow<List<Recording>>(emptyList())

    val recordings: StateFlow<List<Recording>> = mutableRecordings

    fun refresh() {
        viewModelScope.launch {
            mutableRecordings.value = withContext(Dispatchers.IO) { store.list() }
        }
    }

    fun delete(recording: Recording) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(recording.id) }
            mutableRecordings.value = withContext(Dispatchers.IO) { store.list() }
        }
    }

    fun fileFor(recording: Recording): File? = store.file(recording.id)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { RecordingsViewModel(container.recordingStore) }
        }
    }
}
