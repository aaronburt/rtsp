package uk.co.aaronburt.rtsp.snapshot

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Snapshot(val cameraId: String, val file: File, val capturedAt: Long)

interface SnapshotStore {
    val snapshots: StateFlow<Map<String, Snapshot>>

    fun save(cameraId: String, jpeg: ByteArray)

    fun delete(cameraId: String)
}

object NoSnapshotStore : SnapshotStore {
    override val snapshots: StateFlow<Map<String, Snapshot>> = MutableStateFlow(emptyMap<String, Snapshot>()).asStateFlow()

    override fun save(cameraId: String, jpeg: ByteArray) = Unit

    override fun delete(cameraId: String) = Unit
}
