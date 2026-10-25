package uk.co.aaronburt.rtsp.snapshot

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.co.aaronburt.rtsp.data.Camera
import uk.co.aaronburt.rtsp.data.FakeCameraRepository

class SnapshotPruningRepositoryTest {
    private class RecordingStore : SnapshotStore {
        override val snapshots: StateFlow<Map<String, Snapshot>> = MutableStateFlow(emptyMap())
        val deleted = mutableListOf<String>()

        override fun save(cameraId: String, jpeg: ByteArray) = Unit

        override fun delete(cameraId: String) {
            deleted += cameraId
        }
    }

    @Test
    fun deletingACameraDeletesItsSnapshot() {
        val store = RecordingStore()
        val repository = SnapshotPruningRepository(
            FakeCameraRepository(listOf(Camera(id = "a", name = "A", url = "rtsp://h/s"))),
            store,
        )
        repository.delete("a")
        assertNull(repository.get("a"))
        assertEquals(listOf("a"), store.deleted)
    }

    @Test
    fun savingDoesNotTouchSnapshots() {
        val store = RecordingStore()
        val repository = SnapshotPruningRepository(FakeCameraRepository(emptyList()), store)
        repository.save(Camera(id = "b", name = "B", url = "rtsp://h/s"))
        assertEquals(emptyList<String>(), store.deleted)
        assertEquals("B", repository.get("b")?.name)
    }
}
